//
// Saved Microsoft sign-in. See accountstore.h.
//

#include "accountstore.h"

#include <QCoreApplication>
#include <QCryptographicHash>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMessageAuthenticationCode>
#include <QPasswordDigestor>
#include <QRandomGenerator>
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
const int legacyFileVersion = 1;   // DPAPI blob or Mac Keychain reference
const int passwordFileVersion = 2; // master-password encryption
const char entropy[] = "ATW Client Microsoft account v1";

// Version 2 encryption, built only from Qt so Windows and Mac match:
// PBKDF2-HMAC-SHA256 turns the master password into a 32-byte encryption key
// and a 32-byte MAC key; HMAC-SHA256 in counter mode is the keystream; an
// HMAC-SHA256 tag over the header and ciphertext rejects wrong passwords and
// damaged files before anything is decrypted.
const char kdfName[] = "pbkdf2-sha256";
const int kdfIterations = 200000;
const int saltBytes = 16;
const int nonceBytes = 16;

QByteArray randomBytes(int count) {
    QByteArray bytes(count, Qt::Uninitialized);
    for (int i = 0; i < count; ++i)
        bytes[i] = char(QRandomGenerator::system()->bounded(256));
    return bytes;
}

struct PasswordKeys {
    QByteArray encryption;
    QByteArray authentication;
};

PasswordKeys deriveKeys(const QString& password, const QByteArray& salt, int iterations) {
    const QByteArray key = QPasswordDigestor::deriveKeyPbkdf2(
        QCryptographicHash::Sha256, password.toUtf8(), salt, iterations, 64);
    return {key.left(32), key.mid(32)};
}

QByteArray applyKeystream(const QByteArray& data, const QByteArray& key, const QByteArray& nonce) {
    QByteArray output(data.size(), Qt::Uninitialized);
    QMessageAuthenticationCode mac(QCryptographicHash::Sha256, key);
    quint32 counter = 0;
    for (int offset = 0; offset < data.size(); offset += 32, ++counter) {
        const char counterBytes[4] = {char(counter >> 24), char(counter >> 16), char(counter >> 8), char(counter)};
        mac.reset();
        mac.addData(nonce);
        mac.addData(counterBytes, 4);
        const QByteArray block = mac.result();
        for (int i = 0; i < 32 && offset + i < data.size(); ++i)
            output[offset + i] = char(data[offset + i] ^ block[i]);
    }
    return output;
}

QByteArray authenticationTag(const QByteArray& key, const QString& localId, const QByteArray& salt,
                             const QByteArray& nonce, const QByteArray& ciphertext) {
    QMessageAuthenticationCode mac(QCryptographicHash::Sha256, key);
    mac.addData(QByteArray("atw-account-v2"));
    mac.addData(localId.toUtf8());
    mac.addData(salt);
    mac.addData(nonce);
    mac.addData(ciphertext);
    return mac.result();
}

bool sameBytes(const QByteArray& a, const QByteArray& b) {
    if (a.size() != b.size())
        return false;
    char difference = 0;
    for (int i = 0; i < a.size(); ++i)
        difference |= char(a[i] ^ b[i]);
    return difference == 0;
}

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
constexpr char legacyKeychainMarker[] = "keychain-v1";
constexpr char keychainMarkerPrefix[] = "keychain-v2:";

QByteArray uniqueKeychainAccount(const QByteArray& marker) {
    const QByteArray prefix(keychainMarkerPrefix);
    if (!marker.startsWith(prefix) || marker.size() != prefix.size() + 32)
        return {};
    const QByteArray id = marker.mid(prefix.size());
    for (const char character : id)
        if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f')))
            return {};
    return QByteArray("microsoft-refresh-token-") + id;
}

CFMutableDictionaryRef keychainQuery(const QByteArray& marker) {
    const bool legacy = marker == QByteArray(legacyKeychainMarker);
    const QByteArray account = legacy ? QByteArray("microsoft-refresh-token") : uniqueKeychainAccount(marker);
    if (account.isEmpty())
        return nullptr;
    const CFMutableDictionaryRef query = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0, &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);
    const CFStringRef accountName = CFStringCreateWithBytes(kCFAllocatorDefault,
        reinterpret_cast<const UInt8*>(account.constData()), account.size(), kCFStringEncodingUTF8, false);
    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(query, kSecAttrService, legacy
        ? CFSTR("com.alextyhwang.atw-client.account.v1") : CFSTR("com.alextyhwang.atw-client.account.v2"));
    CFDictionarySetValue(query, kSecAttrAccount, accountName);
    CFRelease(accountName);
    return query;
}

void clearReferencedKeychainSecret(const QByteArray& marker) {
    // Legacy v1 was shared by every package. It remains read-only because
    // deleting it could invalidate another installation or a copied package.
    if (uniqueKeychainAccount(marker).isEmpty())
        return;
    const CFMutableDictionaryRef query = keychainQuery(marker);
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

AccountStore::LoadStatus AccountStore::load(SavedAccount& saved, const QString& password, QString* error) {
    saved = {};
    if (error)
        error->clear();
    if (!QFile::exists(accountFilePath()))
        return LoadStatus::Missing;

    QFile input(accountFilePath());
    if (!input.open(QIODevice::ReadOnly)) {
        if (error)
            *error = input.errorString();
        return LoadStatus::Unavailable;
    }
    const QJsonObject file = QJsonDocument::fromJson(input.readAll()).object();
    saved.localId = file["localId"].toString();
    saved.clientId = file["clientId"].toString();
    MinecraftAccount& account = saved.account;
    account.name = file["name"].toString();

    QByteArray plain;
    const int version = file["version"].toInt();
    if (version == passwordFileVersion) {
        if (password.isEmpty())
            return LoadStatus::Locked;
        const QByteArray salt = QByteArray::fromBase64(file["salt"].toString().toLatin1());
        const QByteArray nonce = QByteArray::fromBase64(file["nonce"].toString().toLatin1());
        const QByteArray ciphertext = QByteArray::fromBase64(file["secret"].toString().toLatin1());
        const QByteArray tag = QByteArray::fromBase64(file["tag"].toString().toLatin1());
        const int iterations = file["iterations"].toInt();
        if (file["kdf"].toString() != QLatin1String(kdfName) || iterations < 10000 || iterations > 10000000
                || salt.size() != saltBytes || nonce.size() != nonceBytes || ciphertext.isEmpty()
                || tag.size() != 32 || saved.localId.isEmpty())
            return LoadStatus::Unreadable;
        const PasswordKeys keys = deriveKeys(password, salt, iterations);
        if (!sameBytes(tag, authenticationTag(keys.authentication, saved.localId, salt, nonce, ciphertext)))
            return LoadStatus::WrongPassword;
        plain = applyKeystream(ciphertext, keys.encryption, nonce);
    } else if (version == legacyFileVersion) {
        bool unavailable = false;
        plain = unprotect(QByteArray::fromBase64(file["secret"].toString().toLatin1()), error, &unavailable);
        if (unavailable)
            return LoadStatus::Unavailable;
    } else {
        return LoadStatus::Unreadable;
    }

    const QJsonObject secret = QJsonDocument::fromJson(plain).object();
    if (secret.isEmpty())
        return LoadStatus::Unreadable;
    account.uuid = secret["uuid"].toString();
    account.xuid = secret["xuid"].toString();
    account.accessToken = secret["accessToken"].toString();
    account.expiresAt = QDateTime::fromString(secret["expiresAt"].toString(), Qt::ISODate);
    account.refreshToken = secret["refreshToken"].toString();
    if (saved.localId.isEmpty() || account.uuid.isEmpty() || account.refreshToken.isEmpty())
        return LoadStatus::Unreadable;
    return LoadStatus::Ok;
}

bool AccountStore::isPasswordProtected() {
    return readJsonFile(accountFilePath())["version"].toInt() == passwordFileVersion;
}

bool AccountStore::savedAccountMetadata(QString* name) {
    if (name)
        name->clear();
    const QJsonObject file = readJsonFile(accountFilePath());
    const int version = file["version"].toInt();
    if ((version != legacyFileVersion && version != passwordFileVersion)
            || file["localId"].toString().isEmpty() || file["name"].toString().isEmpty())
        return false;
#ifdef Q_OS_MACOS
    if (version == legacyFileVersion) {
        const QByteArray marker = QByteArray::fromBase64(file["secret"].toString().toLatin1());
        if (marker != QByteArray(legacyKeychainMarker) && uniqueKeychainAccount(marker).isEmpty())
            return false;
    }
#endif
    if (name)
        *name = file["name"].toString();
    return true;
}

bool AccountStore::save(const SavedAccount& saved, const QString& password, QString* error) {
    if (password.isEmpty()) {
        if (error)
            *error = QStringLiteral("A master password is required to save the sign-in.");
        return false;
    }
#ifdef Q_OS_MACOS
    // A version 1 file may reference a Keychain item; drop it once replaced.
    const QJsonObject previousFile = readJsonFile(accountFilePath());
    const QByteArray previous = previousFile["version"].toInt() == legacyFileVersion
        ? QByteArray::fromBase64(previousFile["secret"].toString().toLatin1()) : QByteArray();
#endif
    const MinecraftAccount& account = saved.account;
    const QJsonObject secret{
        {"uuid", account.uuid},
        {"xuid", account.xuid},
        {"accessToken", account.accessToken},
        {"expiresAt", account.expiresAt.toUTC().toString(Qt::ISODate)},
        {"refreshToken", account.refreshToken}};
    const QByteArray salt = randomBytes(saltBytes);
    const QByteArray nonce = randomBytes(nonceBytes);
    const PasswordKeys keys = deriveKeys(password, salt, kdfIterations);
    const QByteArray ciphertext = applyKeystream(
        QJsonDocument(secret).toJson(QJsonDocument::Compact), keys.encryption, nonce);

    // Only the profile name and IDs stay readable, so sign-out and the
    // settings window work without the password.
    const QJsonObject file{
        {"version", passwordFileVersion},
        {"localId", saved.localId},
        {"clientId", saved.clientId},
        {"name", account.name},
        {"kdf", QString::fromLatin1(kdfName)},
        {"iterations", kdfIterations},
        {"salt", QString::fromLatin1(salt.toBase64())},
        {"nonce", QString::fromLatin1(nonce.toBase64())},
        {"secret", QString::fromLatin1(ciphertext.toBase64())},
        {"tag", QString::fromLatin1(
            authenticationTag(keys.authentication, saved.localId, salt, nonce, ciphertext).toBase64())}};
    if (!writeJsonFile(accountFilePath(), file, error))
        return false;
#ifdef Q_OS_MACOS
    clearReferencedKeychainSecret(previous);
#endif
    return writeLunarAccount(saved, error);
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
    const QJsonObject file = readJsonFile(accountFilePath());
    const QByteArray marker = file["version"].toInt() == legacyFileVersion
        ? QByteArray::fromBase64(file["secret"].toString().toLatin1()) : QByteArray();
    if (QFile::remove(accountFilePath()))
        clearReferencedKeychainSecret(marker);
#else
    QFile::remove(accountFilePath());
#endif

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
QByteArray AccountStore::protect(const QByteArray& plain, QString* error) {
    if (error)
        error->clear();
    DATA_BLOB input{DWORD(plain.size()), reinterpret_cast<BYTE*>(const_cast<char*>(plain.data()))};
    DATA_BLOB salt{DWORD(sizeof(entropy) - 1), reinterpret_cast<BYTE*>(const_cast<char*>(entropy))};
    DATA_BLOB output{};
    if (!CryptProtectData(&input, L"ATW Client account", &salt, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &output))
        return {};
    const QByteArray result(reinterpret_cast<const char*>(output.pbData), int(output.cbData));
    LocalFree(output.pbData);
    return result;
}

QByteArray AccountStore::unprotect(const QByteArray& encrypted, QString* error, bool* unavailable) {
    if (error)
        error->clear();
    if (unavailable)
        *unavailable = false;
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
QByteArray AccountStore::protect(const QByteArray& plain, QString* error) {
    if (error)
        error->clear();
    if (plain.isEmpty())
        return {};
    const QByteArray marker = QByteArray(keychainMarkerPrefix)
        + QUuid::createUuid().toString(QUuid::Id128).toLatin1();
    const CFDataRef value = CFDataCreate(kCFAllocatorDefault,
        reinterpret_cast<const UInt8*>(plain.constData()), plain.size());
    const CFMutableDictionaryRef item = keychainQuery(marker);
    CFDictionarySetValue(item, kSecAttrAccessible, kSecAttrAccessibleWhenUnlocked);
    CFDictionarySetValue(item, kSecValueData, value);
    const OSStatus status = SecItemAdd(item, nullptr);
    CFRelease(item);
    CFRelease(value);
    if (status != errSecSuccess && error)
        *error = QStringLiteral("macOS Keychain could not save the sign-in (error %1). Unlock your login Keychain and try again.").arg(status);
    return status == errSecSuccess ? marker : QByteArray();
}

QByteArray AccountStore::unprotect(const QByteArray& encrypted, QString* error, bool* unavailable) {
    if (error)
        error->clear();
    if (unavailable)
        *unavailable = false;
    const CFMutableDictionaryRef query = keychainQuery(encrypted);
    if (!query)
        return {};
    CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue);
    CFTypeRef result = nullptr;
    const OSStatus status = SecItemCopyMatching(query, &result);
    CFRelease(query);
    if (status != errSecSuccess || !result) {
        if (unavailable)
            *unavailable = status != errSecItemNotFound;
        if (error)
            *error = QStringLiteral("macOS Keychain could not read the saved sign-in (error %1). Unlock your login Keychain and try again.").arg(status);
        return {};
    }
    const CFDataRef value = static_cast<CFDataRef>(result);
    const QByteArray plaintext(reinterpret_cast<const char*>(CFDataGetBytePtr(value)),
        int(CFDataGetLength(value)));
    CFRelease(value);
    return plaintext;
}
#else
// No OS-backed secret store is wired up outside Windows or macOS.
QByteArray AccountStore::protect(const QByteArray&, QString* error) {
    if (error)
        *error = QStringLiteral("No operating-system secret store is available.");
    return {};
}
QByteArray AccountStore::unprotect(const QByteArray&, QString* error, bool* unavailable) {
    if (error)
        *error = QStringLiteral("No operating-system secret store is available.");
    if (unavailable)
        *unavailable = true;
    return {};
}
#endif

void AccountStore::log(const QString& message) {
    const QString logsDir = FS::getLunarLogsPath();
    QDir().mkpath(logsDir);
    QFile logFile(FS::combinePaths(logsDir, QStringLiteral("main.log")));
    if (logFile.open(QIODevice::Append | QIODevice::Text))
        logFile.write((QStringLiteral("[Account] ") + message + QStringLiteral("\n")).toUtf8());
}
