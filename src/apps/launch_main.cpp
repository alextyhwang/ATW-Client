#include <QApplication>
#include <QIcon>
#include <QCommandLineParser>
#include <QDir>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTextStream>
#include <QTimer>
#include <QUuid>

#include "auth/accountgate.h"
#include "auth/accountstore.h"
#include "auth/msaauth.h"
#include "auth/onlinereadiness.h"
#include "auth/passworddialog.h"
#include "auth/signindialog.h"
#include "config/config.h"
#include "launch/offlinelauncher.h"
#include "util/fs.h"

namespace {
// Explicit one-time transfer between the owner's machines. Consume the private
// input immediately, then obtain a fresh session and protect it on this OS.
int importAccount(QApplication& app, const QString& path) {
    const QFileInfo info(path);
    const auto publicAccess = QFileDevice::ReadGroup | QFileDevice::WriteGroup
        | QFileDevice::ReadOther | QFileDevice::WriteOther;
    if (!info.isFile() || info.isSymLink()
#ifdef Q_OS_MACOS
            || (info.permissions() & publicAccess)
#endif
    ) {
        AccountStore::log(QStringLiteral("Account import: expected a private regular file."));
        return 1;
    }
    QFile input(path);
    if (!input.open(QIODevice::ReadOnly) || input.size() > 64 * 1024) {
        AccountStore::log(QStringLiteral("Account import: cannot read private input."));
        return 1;
    }
    const QByteArray bytes = input.readAll();
    input.close();
    if (!input.remove()) {
        AccountStore::log(QStringLiteral("Account import: cannot consume private input."));
        return 1;
    }
    const QJsonObject data = QJsonDocument::fromJson(bytes).object();
    const QString refresh = data["refreshToken"].toString();
    const QString clientId = data["clientId"].toString();
    const QString expectedUuid = data["uuid"].toString();
    if (refresh.isEmpty() || clientId.isEmpty() || expectedUuid.isEmpty()) {
        AccountStore::log(QStringLiteral("Account import: required fields missing."));
        return 1;
    }
    MsaAuth auth(clientId);
    QObject::connect(&auth, &MsaAuth::succeeded, &app, [&](const MinecraftAccount& account) {
        if (account.uuid != expectedUuid) {
            AccountStore::log(QStringLiteral("Account import: profile mismatch; existing sign-in preserved."));
            return app.exit(1);
        }
        AccountStore::SavedAccount saved{
            QUuid::createUuid().toString(QUuid::WithoutBraces), clientId, account};
        PasswordDialog passwordDialog(PasswordDialog::Mode::Create, account.name);
        if (passwordDialog.exec() != QDialog::Accepted) {
            AccountStore::log(QStringLiteral("Account import: no master password chosen; existing sign-in preserved."));
            return app.exit(1);
        }
        QString error;
        if (!AccountStore::save(saved, passwordDialog.password(), &error)) {
            AccountStore::log(QStringLiteral("Account import could not save sign-in: ") + error);
            return app.exit(1);
        }
        AccountStore::log(QStringLiteral("Imported and refreshed sign-in for %1.").arg(account.name));
        app.exit(0);
    });
    QObject::connect(&auth, &MsaAuth::failed, &app, [&](const QString& message, bool) {
        AccountStore::log(QStringLiteral("Account import failed: ") + message);
        app.exit(1);
    });
    QTimer::singleShot(0, &auth, [&]() { auth.refresh(refresh); });
    return QApplication::exec();
}

// Sign-in without a desktop session (for example over SSH or from an agent):
// prints the Microsoft device code, then saves the sign-in under the master
// password read from a private file. Prints no tokens or passwords.
int headlessSignIn(QApplication& app, const QString& clientId, const QString& passwordFile) {
    QTextStream out(stdout);
    QFile input(passwordFile);
    if (!input.open(QIODevice::ReadOnly) || input.size() > 4096) {
        out << "FAIL cannot read the master password file." << Qt::endl;
        return 1;
    }
    QString password = QString::fromUtf8(input.readAll());
    input.close();
    while (password.endsWith(QLatin1Char('\n')) || password.endsWith(QLatin1Char('\r')))
        password.chop(1);
    if (password.size() < 4) {
        out << "FAIL the master password must have at least 4 characters." << Qt::endl;
        return 1;
    }
    MsaAuth auth(clientId);
    QObject::connect(&auth, &MsaAuth::deviceCode, &app, [&](const QString& code, const QUrl& uri) {
        out << "CODE " << code << " at " << uri.toString() << Qt::endl;
    });
    QObject::connect(&auth, &MsaAuth::succeeded, &app, [&](const MinecraftAccount& account) {
        AccountStore::SavedAccount saved{QUuid::createUuid().toString(QUuid::WithoutBraces),
                                         MsaAuth::resolveClientId(clientId), account};
        QString error;
        if (!AccountStore::save(saved, password, &error)) {
            out << "FAIL signed in, but could not save: " << error << Qt::endl;
            return app.exit(1);
        }
        AccountStore::log(QStringLiteral("Signed in as %1 (headless) with a master password.").arg(account.name));
        out << "PASS signed in as " << account.name << "; saved under the master password." << Qt::endl;
        app.exit(0);
    });
    QObject::connect(&auth, &MsaAuth::failed, &app, [&](const QString& message, bool) {
        out << "FAIL " << message << Qt::endl;
        app.exit(1);
    });
    QTimer::singleShot(0, &auth, [&]() { auth.startDeviceLogin(); });
    return QApplication::exec();
}

// Checks the parts of sign-in that need no user: DPAPI, Lunar's date format,
// device-code issuance and polling, and rejection of a bad refresh token.
// Prints no codes or tokens and never touches account files.
int runAuthSelfTest(QApplication& app, const QString& clientId) {
    QTextStream out(stdout);
    int failures = 0;
    auto check = [&](bool ok, const QString& name) {
        out << (ok ? "PASS " : "FAIL ") << name << Qt::endl;
        failures += ok ? 0 : 1;
    };

    const QByteArray probe("atw-dpapi-probe");
    const QByteArray sealed = AccountStore::protect(probe);
    check(!sealed.isEmpty() && sealed != probe && AccountStore::unprotect(sealed) == probe, "DPAPI round trip");
    QByteArray tampered = sealed;
    if (!tampered.isEmpty())
        tampered[tampered.size() / 2] = char(tampered[tampered.size() / 2] ^ 0x5A);
    check(AccountStore::unprotect(tampered).isEmpty(), "DPAPI rejects damaged data");
    check(QDateTime::fromString(QStringLiteral("2026-10-06T12:30:00.123456789Z"), Qt::ISODateWithMs).isValid(),
          "Lunar nanosecond expiry parses");

    MsaAuth auth(clientId);
    out << "Client ID: " << MsaAuth::resolveClientId(clientId) << Qt::endl;
    bool issued = false;
    bool expectRefreshFailure = false;

    QObject::connect(&auth, &MsaAuth::deviceCode, [&](const QString& code, const QUrl& uri) {
        issued = true;
        check(!code.isEmpty(), QStringLiteral("device code issued (%1-character user code, %2)")
                                   .arg(code.size()).arg(uri.toString()));
        // Let polls run; "pending" is the only expected answer without a sign-in.
        QTimer::singleShot(12000, &app, [&]() {
            auth.cancel();
            check(true, "no token poll error within 12 s (authorization pending)");
            expectRefreshFailure = true;
            auth.refresh(QStringLiteral("not-a-real-refresh-token"));
        });
    });
    QObject::connect(&auth, &MsaAuth::failed, [&](const QString& message, bool needsSignIn) {
        if (expectRefreshFailure) {
            check(needsSignIn, "invalid refresh token asks for sign-in: " + message);
        } else {
            check(false, (issued ? "token poll: " : "device code: ") + message);
        }
        app.exit(failures == 0 ? 0 : 1);
    });
    QObject::connect(&auth, &MsaAuth::succeeded, [&](const MinecraftAccount&) {
        check(false, "unexpected sign-in success");
        app.exit(1);
    });

    auth.startDeviceLogin();
    return QApplication::exec();
}

// Saves a fake account, checks both files, then signs out. Refuses to run
// beside real account files, so use it on a scratch copy of the package.
int runAccountStoreSelfTest() {
    QTextStream out(stdout);
    const QString lunarPath = FS::getLunarAccountsPath();
    if (QFile::exists(AccountStore::accountFilePath()) || QFile::exists(lunarPath)) {
        out << "Refusing: account files already exist here." << Qt::endl;
        return 2;
    }
    int failures = 0;
    auto check = [&](bool ok, const char* name) {
        out << (ok ? "PASS " : "FAIL ") << name << Qt::endl;
        failures += ok ? 0 : 1;
    };
    auto readAll = [](const QString& path) {
        QFile file(path);
        return file.open(QIODevice::ReadOnly) ? file.readAll() : QByteArray();
    };

    // Shaped like a real Minecraft token: Lunar reads its exp and iat claims.
    const QDateTime expiry = QDateTime::currentDateTimeUtc().addSecs(3600);
    auto base64Url = [](const QByteArray& data) {
        return data.toBase64(QByteArray::Base64UrlEncoding | QByteArray::OmitTrailingEquals);
    };
    const QString fakeToken = QString::fromLatin1(
        base64Url(R"({"alg":"none"})") + '.'
        + base64Url(QJsonDocument(QJsonObject{{"exp", expiry.toSecsSinceEpoch()},
                                              {"iat", QDateTime::currentSecsSinceEpoch()},
                                              {"fake-minecraft-token", true}})
                        .toJson(QJsonDocument::Compact))
        + ".unsigned");

    AccountStore::SavedAccount saved{QStringLiteral("11111111-2222-3333-4444-555555555555"), QString(), {}};
    saved.account = {QStringLiteral("AtwTest"), QStringLiteral("0123456789abcdef0123456789abcdef"),
                     QStringLiteral("2535400000000000"), fakeToken, expiry, QStringLiteral("fake-refresh-token")};
    const QString password = QStringLiteral("correct horse battery");
    QElapsedTimer timer;
    timer.start();
    check(AccountStore::save(saved, password), "save account with master password");
    out << "INFO key derivation and save took " << timer.elapsed() << " ms" << Qt::endl;
    check(!AccountStore::save(saved, QString()), "save refuses an empty master password");
    check(AccountStore::isPasswordProtected(), "account.dat is a master-password file");

    AccountStore::SavedAccount loaded;
    check(AccountStore::load(loaded) == AccountStore::LoadStatus::Locked && loaded.account.name == "AtwTest",
          "load without password is Locked but shows the name");
    check(AccountStore::load(loaded, QStringLiteral("wrong password")) == AccountStore::LoadStatus::WrongPassword,
          "wrong master password is rejected");
    check(AccountStore::load(loaded, password) == AccountStore::LoadStatus::Ok
              && loaded.account.refreshToken == saved.account.refreshToken
              && loaded.account.uuid == saved.account.uuid && loaded.localId == saved.localId,
          "master password unlocks the account");
    QString metadataName;
    check(AccountStore::savedAccountMetadata(&metadataName) && metadataName == "AtwTest",
          "settings window reads the name without the password");
    const QByteArray sealed = readAll(AccountStore::accountFilePath());
    check(!sealed.contains("fake-refresh-token") && !sealed.contains(fakeToken.toLatin1()),
          "account.dat holds no plain tokens");

    const QByteArray lunar = readAll(lunarPath);
    const QJsonObject root = QJsonDocument::fromJson(lunar).object();
    const QJsonObject entry = root["accounts"].toObject()[saved.localId].toObject();
    check(root["activeAccountLocalId"].toString() == saved.localId
              && entry["accessToken"].toString() == saved.account.accessToken
              && entry["minecraftProfile"].toObject()["name"].toString() == saved.account.name,
          "accounts.json has the active Minecraft token");
    check(!lunar.contains("fake-refresh-token"), "accounts.json holds no refresh token");
    // Lunar refreshes any account whose refreshToken is non-null, even "", and
    // wipes it when that refresh fails. The key must be absent.
    check(!entry.contains(QStringLiteral("refreshToken")), "accounts.json entry has no refreshToken key");
    check(AccountStore::hasUsableLunarAccount(), "accounts.json expiry reads back as fresh");

    // The Lunar contract test loads this file with Lunar's own account classes.
    // It holds only the fake tokens above.
    const QString fixture = qEnvironmentVariable("ATW_LUNAR_ACCOUNT_FIXTURE");
    if (!fixture.isEmpty()) {
        QFile::remove(fixture);
        check(QFile::copy(lunarPath, fixture), "copy accounts.json fixture");
    }

    // A damaged ciphertext fails the authentication tag instead of decrypting.
    QJsonObject damaged = QJsonDocument::fromJson(sealed).object();
    QByteArray secret = QByteArray::fromBase64(damaged["secret"].toString().toLatin1());
    secret[secret.size() / 2] = char(secret[secret.size() / 2] ^ 0x5A);
    damaged["secret"] = QString::fromLatin1(secret.toBase64());
    QFile file(AccountStore::accountFilePath());
    if (file.open(QIODevice::WriteOnly))
        file.write(QJsonDocument(damaged).toJson());
    file.close();
    check(AccountStore::load(loaded, password) != AccountStore::LoadStatus::Ok, "damaged account.dat is rejected");

    AccountStore::signOut();
    check(!QFile::exists(AccountStore::accountFilePath()), "sign-out deletes account.dat");
    const QByteArray cleared = readAll(lunarPath);
    check(!cleared.contains(fakeToken.toLatin1()) && !AccountStore::hasUsableLunarAccount(),
          "sign-out strips accounts.json");
    QFile::remove(lunarPath);
    return failures == 0 ? 0 : 1;
}
}

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("atw-launch"));
    QApplication::setApplicationDisplayName(QStringLiteral("ATW Client"));
    QApplication::setWindowIcon(QIcon(QStringLiteral(":/res/branding/atw.svg")));
    app.setStyle(QStringLiteral("Fusion"));
    // The sign-in dialog closes before the game starts; keep running for it.
    app.setQuitOnLastWindowClosed(false);

    QCommandLineParser parser;
    parser.addHelpOption();
    parser.addVersionOption();

    QCommandLineOption assetIndexOption("assetIndex",
        QCoreApplication::translate("assetIndexOverride", "Override the asset index version"),
        QCoreApplication::translate("assetIndexOverride", "e.g. 1.8 for Minecraft 1.8.9"));
    parser.addOption(assetIndexOption);

    QCommandLineOption xmsOption("xms",
        QCoreApplication::translate("xmsOverride", "Override initial memory"),
        QCoreApplication::translate("xmsOverride", "memory in megabytes"));
    parser.addOption(xmsOption);

    QCommandLineOption xmxOption("xmx",
        QCoreApplication::translate("xmxOverride", "Override maximum memory"),
        QCoreApplication::translate("xmxOverride", "memory in megabytes"));
    parser.addOption(xmxOption);

    QCommandLineOption offlineOption("offline",
        QCoreApplication::translate("offline", "Start offline without signing in"));
    parser.addOption(offlineOption);

    QCommandLineOption authSelfTestOption("auth-selftest",
        QCoreApplication::translate("authSelfTest", "Check Microsoft sign-in endpoints and exit"));
    parser.addOption(authSelfTestOption);

    QCommandLineOption storeSelfTestOption("account-store-selftest",
        QCoreApplication::translate("storeSelfTest", "Check account storage in an empty package copy and exit"));
    parser.addOption(storeSelfTestOption);

    QCommandLineOption readinessOption("online-readiness",
        QCoreApplication::translate("onlineReadiness", "Check the saved sign-in for online play (redacted) and exit"));
    parser.addOption(readinessOption);

    QCommandLineOption importOption("import-account",
        QCoreApplication::translate("importAccount", "Consume a private account-transfer file and save a fresh sign-in"),
        QStringLiteral("file"));
    parser.addOption(importOption);

    QCommandLineOption signInOption("sign-in", QStringLiteral("Manage Microsoft sign-in without launching the game"));
    QCommandLineOption signOutOption("sign-out", QStringLiteral("Sign out of this ATW installation without launching the game"));
    parser.addOption(signInOption);
    parser.addOption(signOutOption);

    QCommandLineOption headlessSignInOption("headless-sign-in",
        QStringLiteral("Sign in with a printed device code; save under the master password in this file"),
        QStringLiteral("password-file"));
    parser.addOption(headlessSignInOption);

    parser.process(app);

    if (parser.isSet(importOption))
        return importAccount(app, parser.value(importOption));

    Config config = Config::load();
    if (parser.isSet(headlessSignInOption))
        return headlessSignIn(app, config.msaClientId, parser.value(headlessSignInOption));
    if (parser.isSet(signOutOption)) {
        AccountStore::signOut();
        return QFile::exists(AccountStore::accountFilePath()) ? 1 : 0;
    }
    if (parser.isSet(signInOption)) {
        SignInDialog dialog(config.msaClientId, false, QString());
        return dialog.exec() == QDialog::Accepted ? 0 : 1;
    }
    if (parser.isSet(authSelfTestOption))
        return runAuthSelfTest(app, config.msaClientId);
    if (parser.isSet(storeSelfTestOption))
        return runAccountStoreSelfTest();
    if (parser.isSet(readinessOption))
        return runOnlineReadiness();

    config.gameVersion = QStringLiteral("1.8.9");
    config.modLoader = QStringLiteral("Optifine");
    config.keepMemorySame = true;

    if (parser.isSet(xmsOption))
        config.initialMemory = parser.value(xmsOption).toInt();
    if (parser.isSet(xmxOption))
        config.maximumMemory = parser.value(xmxOption).toInt();
    if (config.keepMemorySame)
        config.initialMemory = config.maximumMemory;

    OfflineLauncher launcher(config, parser.isSet(assetIndexOption), parser.value(assetIndexOption), &app);
    QObject::connect(&launcher, &OfflineLauncher::error, [&app](const QString& message) {
        QDir().mkpath(FS::getLunarLogsPath());
        QFile logFile(FS::combinePaths(FS::getLunarLogsPath(), QStringLiteral("main.log")));
        if (logFile.open(QIODevice::Append | QIODevice::Text)) {
            QTextStream stream(&logFile);
            stream << "[Launcher Error] " << message << Qt::endl;
        }
        app.quit();
    });
    QObject::connect(&launcher, &OfflineLauncher::processFinished, &app, &QCoreApplication::quit);

    auto startGame = [&app, &launcher](bool offline) {
        launcher.setOfflineMode(offline);
        if (!launcher.launch())
            app.exit(1);
    };

    AccountGate gate(config.msaClientId);
    QObject::connect(&gate, &AccountGate::ready, startGame);
    QObject::connect(&gate, &AccountGate::cancelled, &app, &QCoreApplication::quit);
    QTimer::singleShot(0, &app, [&]() {
        if (parser.isSet(offlineOption))
            startGame(true);
        else
            gate.start();
    });

    return QApplication::exec();
}
