#include <QApplication>
#include <QCommandLineParser>
#include <QDir>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTextStream>
#include <QTimer>

#include "auth/accountgate.h"
#include "auth/accountstore.h"
#include "auth/msaauth.h"
#include "auth/onlinereadiness.h"
#include "config/config.h"
#include "launch/offlinelauncher.h"
#include "util/fs.h"

namespace {
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
    check(AccountStore::save(saved), "save account");

    AccountStore::SavedAccount loaded;
    check(AccountStore::load(loaded) == AccountStore::LoadStatus::Ok
              && loaded.account.refreshToken == saved.account.refreshToken
              && loaded.account.uuid == saved.account.uuid && loaded.localId == saved.localId,
          "load account back");
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

    QJsonObject damaged = QJsonDocument::fromJson(sealed).object();
    damaged["secret"] = QString::fromLatin1(QByteArray("not dpapi data").toBase64());
    QFile file(AccountStore::accountFilePath());
    if (file.open(QIODevice::WriteOnly))
        file.write(QJsonDocument(damaged).toJson());
    file.close();
    check(AccountStore::load(loaded) == AccountStore::LoadStatus::Unreadable, "foreign account.dat is unreadable");

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

    parser.process(app);

    Config config = Config::load();
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
