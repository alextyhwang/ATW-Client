//
// Saved Microsoft sign-in. See accountstore.h.
//

#include "accountstore.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonObject>
#include <QSaveFile>
#include <QStandardPaths>
#include <QUuid>

#include "util/fs.h"

#ifdef Q_OS_WIN
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <wincrypt.h>
#endif

#ifdef Q_OS_MACOS
#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#endif

namespace {
const int accountFileVersion = 1;
const char entropy[] = "ATW Client Microsoft account v1";

QJsonObject readJsonFile(const QString& path) {
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly))
        return {};
    return QJsonDocument::fromJson(file.readAll()).object();
}

bool writeJsonFile(const QString& path, const QJsonObject& object, QString* error) {
    QDir().mkpath(QFileInfo(path).absolutePath());
    QSaveFile file(path);
    if (!file.open(QIODevice::WriteOnly)
            || file.write(QJsonDocument(object).toJson()) < 0
            || !file.commit()) {
        if (error)
            *error = QStringLiteral("Cannot write %1: %2").arg(QDir::toNativeSeparators(path), file.errorString());
        return false;
    }
    return true;
}

#ifdef Q_OS_MACOS
constexpr char keychainMarker[] = "keychain-v1";

void clearKeychainSecret() {
    const CFMutableDictionaryRef query = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0, &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);
    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(query, kSecAttrService, CFSTR("com.alextyhwang.atw-client.account.v1"));
    CFDictionarySetValue(query, kSecAttrAccount, CFSTR("microsoft-refresh-token"));
    SecItemDelete(query);
    CFRelease(query);
}
#endif
}

QString AccountStore::accountFilePath() {
#if defined(ATW_PACKAGE_MODE) || defined(ATW_TEST_PORTABLE)
    return FS::combinePaths(FS::getPackageRoot(), QStringLiteral("config"), QStringLiteral("account.dat"));
#else
    return QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation) + QStringLiteral("/atw-client/account.dat");
#endif
}

AccountStore::LoadStatus AccountStore::load(SavedAccount& saved) {
    if (!QFile::exists(accountFilePath()))
        return LoadStatus::Missing;

    const QJsonObject file = readJsonFile(accountFilePath());
    const QByteArray plain = unprotect(QByteArray::fromBase64(file["secret"].toString().toLatin1()));
    const QJsonObject secret = QJsonDocument::fromJson(plain).object();
    if (file["version"].toInt() != accountFileVersion || secret.isEmpty())
        return LoadStatus::Unreadable;

    saved.localId = file["localId"].toString();
    saved.clientId = file["clientId"].toString();
    MinecraftAccount& account = saved.account;
    account.name = file["name"].toString();
    account.uuid = secret["uuid"].toString();
    account.xuid = secret["xuid"].toString();
    account.accessToken = secret["accessToken"].toString();
    account.expiresAt = QDateTime::fromString(secret["expiresAt"].toString(), Qt::ISODate);
    account.refreshToken = secret["refreshToken"].toString();
    if (saved.localId.isEmpty() || account.uuid.isEmpty() || account.refreshToken.isEmpty())
        return LoadStatus::Unreadable;
    return LoadStatus::Ok;
}

bool AccountStore::save(const SavedAccount& saved, QString* error) {
    const MinecraftAccount& account = saved.account;
    const QJsonObject secret{
        {"uuid", account.uuid},
        {"xuid", account.xuid},
        {"accessToken", account.accessToken},
        {"expiresAt", account.expiresAt.toUTC().toString(Qt::ISODate)},
        {"refreshToken", account.refreshToken}};
    const QByteArray encrypted = protect(QJsonDocument(secret).toJson(QJsonDocument::Compact));
    if (encrypted.isEmpty()) {
        if (error)
            *error = QStringLiteral("The operating system could not protect the sign-in for this user.");
        return false;
    }

    // Only the profile name and IDs stay readable, so sign-out works anywhere.
    const QJsonObject file{
        {"version", accountFileVersion},
        {"localId", saved.localId},
        {"clientId", saved.clientId},
        {"name", account.name},
        {"secret", QString::fromLatin1(encrypted.toBase64())}};
    return writeJsonFile(accountFilePath(), file, error) && writeLunarAccount(saved, error);
}

bool AccountStore::writeLunarAccount(const SavedAccount& saved, QString* error) {
    const QString path = FS::getLunarAccountsPath();
    QJsonObject root = readJsonFile(path);
    if (root["mojangClientToken"].toString().isEmpty())
        root["mojangClientToken"] = QUuid::createUuid().toString(QUuid::Id128);

    // Lunar's own field set, minus refreshToken. The refresh token stays in
    // account.dat only, and the key must be absent rather than empty: Lunar
    // refreshes every account whose refreshToken is non-null at startup, and an
    // empty one makes login.live.com return an error, so Lunar wipes the access
    // token and drops the account. See AccountStore::lunarAccountEntry().
    const QJsonObject entry = lunarAccountEntry(saved);

    QJsonObject accounts = root["accounts"].toObject();
    accounts[saved.localId] = entry;
    root["accounts"] = accounts;
    root["activeAccountLocalId"] = saved.localId;
    return writeJsonFile(path, root, error);
}

QJsonObject AccountStore::lunarAccountEntry(const SavedAccount& saved) {
    const MinecraftAccount& account = saved.account;
    return QJsonObject{
        {"accessToken", account.accessToken},
        {"accessTokenExpiresAt", account.expiresAt.toUTC().toString(Qt::ISODateWithMs)},
        {"eligibleForMigration", false},
        {"hasMultipleProfiles", false},
        {"legacy", false},
        {"persistent", true},
        {"localId", saved.localId},
        {"minecraftProfile", QJsonObject{{"id", account.uuid}, {"name", account.name}}},
        {"remoteId", account.xuid.isEmpty() ? account.uuid : account.xuid},
        {"type", "Xbox"},
        {"username", account.name}};
}

void AccountStore::signOut() {
#ifdef Q_OS_MACOS
    clearKeychainSecret();
#endif
    QFile::remove(accountFilePath());

    const QString path = FS::getLunarAccountsPath();
    QJsonObject root = readJsonFile(path);
    if (root.isEmpty())
        return;
    root["accounts"] = QJsonObject();
    root.remove(QStringLiteral("activeAccountLocalId"));
    QString error;
    if (!writeJsonFile(path, root, &error)) {
        log(QStringLiteral("Sign-out could not clear accounts.json: ") + error);
        QFile::remove(path);
    }
}

bool AccountStore::hasUsableLunarAccount(QString* name) {
    const QJsonObject root = readJsonFile(FS::getLunarAccountsPath());
    const QJsonObject account = root["accounts"].toObject()[root["activeAccountLocalId"].toString()].toObject();
    if (account["accessToken"].toString().isEmpty())
        return false;

    MinecraftAccount probe;
    probe.accessToken = account["accessToken"].toString();
    probe.expiresAt = QDateTime::fromString(account["accessTokenExpiresAt"].toString(), Qt::ISODateWithMs);
    if (!probe.expiresAt.isValid())
        probe.expiresAt = QDateTime::fromString(account["accessTokenExpiresAt"].toString(), Qt::ISODate);
    if (!tokenIsFresh(probe))
        return false;
    if (name)
        *name = account["minecraftProfile"].toObject()["name"].toString();
    return true;
}

bool AccountStore::tokenIsFresh(const MinecraftAccount& account, int marginSeconds) {
    return !account.accessToken.isEmpty() && account.expiresAt.isValid()
        && account.expiresAt > QDateTime::currentDateTimeUtc().addSecs(marginSeconds);
}

#ifdef Q_OS_WIN
QByteArray AccountStore::protect(const QByteArray& plain) {
    DATA_BLOB input{DWORD(plain.size()), reinterpret_cast<BYTE*>(const_cast<char*>(plain.data()))};
    DATA_BLOB salt{DWORD(sizeof(entropy) - 1), reinterpret_cast<BYTE*>(const_cast<char*>(entropy))};
    DATA_BLOB output{};
    if (!CryptProtectData(&input, L"ATW Client account", &salt, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &output))
        return {};
    const QByteArray result(reinterpret_cast<const char*>(output.pbData), int(output.cbData));
    LocalFree(output.pbData);
    return result;
}

QByteArray AccountStore::unprotect(const QByteArray& encrypted) {
    if (encrypted.isEmpty())
        return {};
    DATA_BLOB input{DWORD(encrypted.size()), reinterpret_cast<BYTE*>(const_cast<char*>(encrypted.data()))};
    DATA_BLOB salt{DWORD(sizeof(entropy) - 1), reinterpret_cast<BYTE*>(const_cast<char*>(entropy))};
    DATA_BLOB output{};
    if (!CryptUnprotectData(&input, nullptr, &salt, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &output))
        return {};
    const QByteArray result(reinterpret_cast<const char*>(output.pbData), int(output.cbData));
    SecureZeroMemory(output.pbData, output.cbData);
    LocalFree(output.pbData);
    return result;
}
#elif defined(Q_OS_MACOS)
QByteArray AccountStore::protect(const QByteArray& plain) {
    if (plain.isEmpty())
        return {};
    clearKeychainSecret();
    const CFDataRef value = CFDataCreate(kCFAllocatorDefault,
        reinterpret_cast<const UInt8*>(plain.constData()), plain.size());
    const CFMutableDictionaryRef item = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0, &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);
    CFDictionarySetValue(item, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(item, kSecAttrService, CFSTR("com.alextyhwang.atw-client.account.v1"));
    CFDictionarySetValue(item, kSecAttrAccount, CFSTR("microsoft-refresh-token"));
    CFDictionarySetValue(item, kSecAttrAccessible, kSecAttrAccessibleWhenUnlocked);
    CFDictionarySetValue(item, kSecValueData, value);
    const OSStatus status = SecItemAdd(item, nullptr);
    CFRelease(item);
    CFRelease(value);
    return status == errSecSuccess ? QByteArray(keychainMarker) : QByteArray();
}

QByteArray AccountStore::unprotect(const QByteArray& encrypted) {
    if (encrypted != QByteArray(keychainMarker))
        return {};
    const CFMutableDictionaryRef query = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0, &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);
    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(query, kSecAttrService, CFSTR("com.alextyhwang.atw-client.account.v1"));
    CFDictionarySetValue(query, kSecAttrAccount, CFSTR("microsoft-refresh-token"));
    CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue);
    CFTypeRef result = nullptr;
    const OSStatus status = SecItemCopyMatching(query, &result);
    CFRelease(query);
    if (status != errSecSuccess || !result)
        return {};
    const CFDataRef value = static_cast<CFDataRef>(result);
    const QByteArray result(reinterpret_cast<const char*>(CFDataGetBytePtr(value)),
        int(CFDataGetLength(value)));
    CFRelease(value);
    return result;
}
#else
// No OS-backed secret store is wired up outside Windows or macOS.
QByteArray AccountStore::protect(const QByteArray&) { return {}; }
QByteArray AccountStore::unprotect(const QByteArray&) { return {}; }
#endif

void AccountStore::log(const QString& message) {
    const QString logsDir = FS::getLunarLogsPath();
    QDir().mkpath(logsDir);
    QFile logFile(FS::combinePaths(logsDir, QStringLiteral("main.log")));
    if (logFile.open(QIODevice::Append | QIODevice::Text))
        logFile.write((QStringLiteral("[Account] ") + message + QStringLiteral("\n")).toUtf8());
}
