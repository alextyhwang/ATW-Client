// Mac-only regression harness. All three Keychain operations are replaced
// before including the production implementation: the login Keychain is never
// read or modified. FS paths point exclusively into a temporary package tree.
// Build with QtCore, CoreFoundation, Security and -I src; pass a scratch parent.
#include <Security/Security.h>

OSStatus mockSecItemAdd(CFDictionaryRef, CFTypeRef*);
OSStatus mockSecItemCopyMatching(CFDictionaryRef, CFTypeRef*);
OSStatus mockSecItemDelete(CFDictionaryRef);
#define SecItemAdd mockSecItemAdd
#define SecItemCopyMatching mockSecItemCopyMatching
#define SecItemDelete mockSecItemDelete
#define ATW_PACKAGE_MODE 1
#include "../../src/auth/accountstore.cpp"
#undef SecItemAdd
#undef SecItemCopyMatching
#undef SecItemDelete

#include <QDebug>
#include <QMap>
#include <QTemporaryDir>

namespace {
QString packageRoot;
QStringList packageRoots;
QMap<QString, QByteArray> secrets;
bool failAdd = false;
bool deletedReferencedItem = false;
int queryCount = 0;

QString cfString(CFTypeRef value) {
    char buffer[256]{};
    if (!value || CFGetTypeID(value) != CFStringGetTypeID()
            || !CFStringGetCString(static_cast<CFStringRef>(value), buffer, sizeof(buffer), kCFStringEncodingUTF8))
        return {};
    return QString::fromUtf8(buffer);
}

QString identity(CFDictionaryRef query) {
    return cfString(CFDictionaryGetValue(query, kSecAttrService)) + QLatin1Char('/')
        + cfString(CFDictionaryGetValue(query, kSecAttrAccount));
}

QByteArray markerAt(const QString& root) {
    return QByteArray::fromBase64(readJsonFile(root + QStringLiteral("/config/account.dat"))
                                 ["secret"].toString().toLatin1());
}

QString markerIdentity(const QByteArray& marker) {
    if (marker == "keychain-v1")
        return QStringLiteral("com.alextyhwang.atw-client.account.v1/microsoft-refresh-token");
    return QStringLiteral("com.alextyhwang.atw-client.account.v2/microsoft-refresh-token-")
        + QString::fromLatin1(marker.mid(QByteArray("keychain-v2:").size()));
}
}

namespace FS {
QString getPackageRoot() { return packageRoot; }
QString combinePaths(const QString& a, const QString& b) { return QDir::cleanPath(a + '/' + b); }
QString combinePaths(const QString& a, const QString& b, const QString& c) { return combinePaths(combinePaths(a, b), c); }
QString getLunarAccountsPath() { return combinePaths(packageRoot, QStringLiteral("lunar/accounts.json")); }
QString getLunarLogsPath() { return combinePaths(packageRoot, QStringLiteral("logs")); }
}

OSStatus mockSecItemAdd(CFDictionaryRef item, CFTypeRef*) {
    if (failAdd) return errSecNotAvailable;
    const QString key = identity(item);
    if (secrets.contains(key)) return errSecDuplicateItem;
    const CFDataRef data = static_cast<CFDataRef>(CFDictionaryGetValue(item, kSecValueData));
    secrets.insert(key, QByteArray(reinterpret_cast<const char*>(CFDataGetBytePtr(data)), CFDataGetLength(data)));
    return errSecSuccess;
}

OSStatus mockSecItemCopyMatching(CFDictionaryRef query, CFTypeRef* result) {
    ++queryCount;
    const auto item = secrets.constFind(identity(query));
    if (item == secrets.cend()) return errSecItemNotFound;
    *result = CFDataCreate(kCFAllocatorDefault, reinterpret_cast<const UInt8*>(item->constData()), item->size());
    return errSecSuccess;
}

OSStatus mockSecItemDelete(CFDictionaryRef query) {
    const QString key = identity(query);
    for (const QString& root : packageRoots) {
        const QByteArray marker = markerAt(root);
        if (!marker.isEmpty() && markerIdentity(marker) == key)
            deletedReferencedItem = true;
    }
    return secrets.remove(key) ? errSecSuccess : errSecItemNotFound;
}

#define CHECK(condition, message) do { if (!(condition)) { qCritical() << "FAIL:" << message; return 1; } } while (false)

int main(int argc, char** argv) {
    QCoreApplication application(argc, argv);
    if (argc != 2) return 2;
    QTemporaryDir scratch(QDir(QString::fromLocal8Bit(argv[1])).filePath(QStringLiteral("accountstore-isolation-XXXXXX")));
    CHECK(scratch.isValid(), "scratch creation");
    for (const QString& name : {QStringLiteral("a"), QStringLiteral("b"), QStringLiteral("failure"), QStringLiteral("legacy")}) {
        const QString root = QDir(scratch.path()).filePath(name);
        CHECK(QDir().mkpath(root), "package creation");
        packageRoots.append(root);
    }
    const QString legacyKey = markerIdentity("keychain-v1");
    secrets.insert(legacyKey, "legacy-fixture-only");
    CHECK(writeJsonFile(packageRoots[3] + QStringLiteral("/config/account.dat"),
        QJsonObject{{"secret", QString::fromLatin1(QByteArray("keychain-v1").toBase64())}}, nullptr), "legacy fixture");

    AccountStore::SavedAccount saved;
    saved.localId = QStringLiteral("fixture-local-id");
    saved.clientId = QStringLiteral("fixture-client-id");
    saved.account.name = QStringLiteral("fixture-name");
    saved.account.uuid = QStringLiteral("00000000000000000000000000000001");
    saved.account.refreshToken = QStringLiteral("fixture-refresh-token");
    saved.account.accessToken = QStringLiteral("fixture-access-token");
    saved.account.expiresAt = QDateTime::currentDateTimeUtc().addSecs(3600);

    packageRoot = packageRoots[0];
    AccountStore::signOut();
    CHECK(secrets.size() == 1 && secrets.contains(legacyKey), "empty scratch sign-out preserves legacy item");
    CHECK(AccountStore::save(saved), "first package save");
    const QByteArray first = markerAt(packageRoot);
    CHECK(first.startsWith("keychain-v2:") && first.size() == 44, "UUID reference marker");
    AccountStore::SavedAccount loaded;
    CHECK(AccountStore::load(loaded) == AccountStore::LoadStatus::Ok
        && loaded.account.refreshToken == saved.account.refreshToken, "first package reload");

    packageRoot = packageRoots[1];
    CHECK(AccountStore::save(saved), "independent package save");
    const QByteArray second = markerAt(packageRoot);
    CHECK(first != second && secrets.contains(markerIdentity(first)) && secrets.contains(legacyKey), "independent item isolation");

    packageRoot = packageRoots[0];
    CHECK(AccountStore::save(saved), "replace first account");
    const QByteArray replacement = markerAt(packageRoot);
    CHECK(replacement != first && !secrets.contains(markerIdentity(first))
        && secrets.contains(markerIdentity(second)), "retire only superseded item");
    const int beforeFailure = secrets.size();
    const QString config = QDir(packageRoot).filePath(QStringLiteral("config"));
    const auto permissions = QFile::permissions(config);
    CHECK(QFile::setPermissions(config, QFile::ReadOwner | QFile::ExeOwner), "read-only fixture setup");
    QString error;
    const bool savedReadOnly = AccountStore::save(saved, &error);
    CHECK(QFile::setPermissions(config, permissions), "read-only fixture restore");
    CHECK(!savedReadOnly && markerAt(packageRoot) == replacement
        && secrets.size() == beforeFailure && secrets.contains(markerIdentity(replacement)), "failed replacement preserves old item and removes uncommitted item");
    failAdd = true;
    CHECK(!AccountStore::save(saved, &error) && markerAt(packageRoot) == replacement
        && secrets.size() == beforeFailure, "failed Keychain add preserves account");
    failAdd = false;

    // Failure after account.dat commit must keep its new item readable.
    CHECK(QFile::remove(FS::getLunarAccountsPath()) && QDir().mkdir(FS::getLunarAccountsPath()), "Lunar write failure setup");
    CHECK(!AccountStore::save(saved, &error), "Lunar write failure");
    CHECK(markerAt(packageRoot) != replacement && !secrets.contains(markerIdentity(replacement))
        && AccountStore::load(loaded) == AccountStore::LoadStatus::Ok, "committed account survives Lunar write failure");
    CHECK(QDir().rmdir(FS::getLunarAccountsPath()), "Lunar write failure cleanup");
    AccountStore::signOut();
    CHECK(!QFile::exists(AccountStore::accountFilePath()) && secrets.size() == 2
        && secrets.contains(markerIdentity(second)) && secrets.contains(legacyKey), "scoped sign-out");

    const int beforeMalformed = queryCount;
    CHECK(AccountStore::unprotect("keychain-v2:../../invalid").isEmpty()
        && queryCount == beforeMalformed, "malformed reference rejected without Keychain query");
    CHECK(AccountStore::unprotect("keychain-v1") == "legacy-fixture-only", "legacy read compatibility");
    packageRoot = packageRoots[3];
    AccountStore::signOut();
    CHECK(secrets.contains(legacyKey), "legacy sign-out cannot delete a globally shared item");
    CHECK(writeJsonFile(AccountStore::accountFilePath(),
        QJsonObject{{"secret", QString::fromLatin1(QByteArray("keychain-v1").toBase64())}}, nullptr), "legacy migration fixture");
    CHECK(AccountStore::save(saved) && secrets.contains(legacyKey), "legacy migration preserves shared item");
    AccountStore::signOut();
    packageRoot = packageRoots[1];
    AccountStore::signOut();
    CHECK(secrets.size() == 1 && secrets.contains(legacyKey) && !deletedReferencedItem, "all deletes happen only after reference removal or replacement");
    qInfo() << "PASS: mocked-Keychain isolation, save retirement, failure cleanup, scoped sign-out and legacy read compatibility";
    return 0;
}
