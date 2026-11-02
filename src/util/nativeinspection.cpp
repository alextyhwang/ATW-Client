#include "nativeinspection.h"

#include <QCryptographicHash>
#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QFileInfo>
#include <QLockFile>
#include <QMap>
#include <QRegularExpression>
#include <QSaveFile>
#include <QSet>
#include <QSysInfo>
#include <QTemporaryDir>
#include <QtEndian>

#include <zlib.h>

#ifdef Q_OS_MACOS
#include <sys/sysctl.h>
#endif

namespace {
constexpr qint64 maxArchiveBytes = 64LL * 1024 * 1024;
constexpr qint64 maxFileBytes = 128LL * 1024 * 1024;
constexpr qint64 maxExpandedBytes = 256LL * 1024 * 1024;
constexpr int maxEntries = 1024;

bool fail(QString* error, const QString& message) {
    if (error)
        *error = message;
    return false;
}

QString normalizeCpu(QString cpu) {
    cpu = cpu.toLower();
    if (cpu == QStringLiteral("aarch64")) return QStringLiteral("arm64");
    if (cpu == QStringLiteral("amd64")) return QStringLiteral("x86_64");
    return cpu;
}

quint16 le16(const QByteArray& bytes, int offset) {
    return qFromLittleEndian<quint16>(reinterpret_cast<const uchar*>(bytes.constData() + offset));
}
quint32 word(const QByteArray& bytes, int offset, bool little = true) {
    const auto* data = reinterpret_cast<const uchar*>(bytes.constData() + offset);
    return little ? qFromLittleEndian<quint32>(data) : qFromBigEndian<quint32>(data);
}
quint64 wide(const QByteArray& bytes, int offset, bool little) {
    const auto* data = reinterpret_cast<const uchar*>(bytes.constData() + offset);
    return little ? qFromLittleEndian<quint64>(data) : qFromBigEndian<quint64>(data);
}

bool readBounded(const QString& path, qint64 limit, QByteArray* bytes) {
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly) || file.size() <= 0 || file.size() > limit)
        return false;
    *bytes = file.read(limit + 1);
    return bytes->size() == file.size();
}

// Validate a real 64-bit Mach-O slice and its bounded load-command table.
// This is binary/architecture evidence, not a JNI ABI or loader qualification.
bool thinMachO(const QByteArray& bytes, int offset, int length, quint32 cpu, quint32 type) {
    if (length < 32 || offset < 0 || offset > bytes.size() - length)
        return false;
    const quint32 magic = word(bytes, offset);
    if (magic != 0xfeedfacf && magic != 0xcffaedfe)
        return false;
    const bool little = magic == 0xfeedfacf;
    if (word(bytes, offset + 4, little) != cpu || word(bytes, offset + 12, little) != type)
        return false;
    // arm64e is not the generic Apple-silicon slice admitted here.
    if (cpu == 0x0100000c && (word(bytes, offset + 8, little) & 0x00ffffff) != 0)
        return false;
    const quint32 count = word(bytes, offset + 16, little);
    const quint32 size = word(bytes, offset + 20, little);
    if (count == 0 || count > 4096 || size > quint32(length - 32))
        return false;
    int cursor = offset + 32;
    const int end = cursor + int(size);
    for (quint32 i = 0; i < count; ++i) {
        if (cursor > end - 8) return false;
        const quint32 commandSize = word(bytes, cursor + 4, little);
        if (commandSize < 8 || commandSize % 8 != 0 || commandSize > quint32(end - cursor))
            return false;
        cursor += int(commandSize);
    }
    return cursor == end;
}

bool matchedMachO(const QByteArray& bytes, const QString& arch, quint32 type) {
    const quint32 cpu = arch == QStringLiteral("arm64") ? 0x0100000c
                        : arch == QStringLiteral("x86_64") ? 0x01000007 : 0;
    if (!cpu || bytes.size() < 8) return false;
    const quint32 magic = word(bytes, 0, false);
    if (magic != 0xcafebabe && magic != 0xbebafeca && magic != 0xcafebabf && magic != 0xbfbafeca)
        return thinMachO(bytes, 0, bytes.size(), cpu, type);
    const bool little = magic == 0xbebafeca || magic == 0xbfbafeca;
    const bool is64 = magic == 0xcafebabf || magic == 0xbfbafeca;
    const int stride = is64 ? 32 : 20;
    const quint32 count = word(bytes, 4, little);
    if (count == 0 || count > 32 || 8 + count * stride > quint32(bytes.size())) return false;
    bool found = false;
    for (quint32 i = 0; i < count; ++i) {
        const int pos = 8 + int(i) * stride;
        const quint64 offset = is64 ? wide(bytes, pos + 8, little) : word(bytes, pos + 8, little);
        const quint64 length = is64 ? wide(bytes, pos + 16, little) : word(bytes, pos + 12, little);
        if (offset < 8 + count * stride || offset > quint64(bytes.size())
                || length > quint64(bytes.size()) - offset) return false;
        if (word(bytes, pos, little) == cpu) {
            if (found || !thinMachO(bytes, int(offset), int(length), cpu, type)) return false;
            found = true;
        }
    }
    return found;
}

bool isWithin(const QString& root, const QString& path) {
    return !root.isEmpty() && path.startsWith(root + QLatin1Char('/'));
}

struct Entry {
    QString name;
    QByteArray bytes;
};

// Decode before touching disk. Central/local names and methods must agree;
// CRC and exact inflated sizes are checked, including data-descriptor ZIPs.
bool readZip(const QByteArray& zip, QList<Entry>* entries, QString* error) {
    auto invalid = [&]() { return fail(error, QStringLiteral("Native ZIP is unsafe, corrupt or outside the supported classic ZIP limits.")); };
    int end = -1;
    for (int pos = zip.size() - 22; pos >= qMax(0, int(zip.size()) - 65557); --pos) {
        if (word(zip, pos) == 0x06054b50 && pos + 22 + le16(zip, pos + 20) == zip.size()) {
            end = pos;
            break;
        }
    }
    if (end < 0 || le16(zip, end + 4) || le16(zip, end + 6)) return invalid();
    const int count = le16(zip, end + 10);
    const quint32 centralSize = word(zip, end + 12);
    const quint32 centralOffset = word(zip, end + 16);
    if (count == 0 || count > maxEntries || le16(zip, end + 8) != count
            || quint64(centralOffset) + centralSize != quint64(end)) return invalid();
    int cursor = int(centralOffset);
    qint64 expanded = 0;
    QSet<QString> paths;
    for (int index = 0; index < count; ++index) {
        if (cursor > end - 46 || word(zip, cursor) != 0x02014b50) return invalid();
        const quint16 flags = le16(zip, cursor + 8);
        const quint16 method = le16(zip, cursor + 10);
        const quint32 crc = word(zip, cursor + 16);
        const quint32 compressed = word(zip, cursor + 20);
        const quint32 size = word(zip, cursor + 24);
        const int nameSize = le16(zip, cursor + 28);
        const int extraSize = le16(zip, cursor + 30);
        const int commentSize = le16(zip, cursor + 32);
        const quint32 attrs = word(zip, cursor + 38);
        const quint32 local = word(zip, cursor + 42);
        const int unixType = (attrs >> 16) & 0170000;
        if ((flags & ~(quint16(0x080e))) || (method != 0 && method != 8)
                || le16(zip, cursor + 34) != 0 || size > maxFileBytes
                || compressed > maxArchiveBytes || nameSize == 0 || nameSize > 512
                || cursor + 46 + nameSize + extraSize + commentSize > end
                || (unixType != 0 && unixType != 0100000 && unixType != 0040000)) return invalid();
        const QByteArray rawName = zip.mid(cursor + 46, nameSize);
        // Deliberately restrict archive paths to portable ASCII. Refuse path
        // aliases, Windows devices, colon/backslash paths and Unicode ambiguity.
        const QString name = QString::fromLatin1(rawName);
        static const QRegularExpression safeName(QStringLiteral("^[A-Za-z0-9_./-]+$"));
        if (!safeName.match(name).hasMatch() || name.startsWith('/') || name.contains(QStringLiteral("//"))) return invalid();
        const bool directory = name.endsWith('/');
        const QString cleanName = directory ? name.left(name.size() - 1) : name;
        const QStringList components = cleanName.split('/');
        if (components.size() > 16) return invalid();
        for (const QString& component : components) {
            const QString base = component.section('.', 0, 0).toUpper();
            static const QRegularExpression device(QStringLiteral("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$"));
            if (component.isEmpty() || component.size() > 128 || component == QStringLiteral(".") || component == QStringLiteral("..")
                    || component.endsWith('.') || component.endsWith(' ') || device.match(base).hasMatch()) return invalid();
        }
        const QString key = cleanName.toLower();
        if (paths.contains(key)) return invalid();
        paths.insert(key);
        if ((unixType == 0040000 || (attrs & 0x10)) && !directory) return invalid();
        if (directory && size != 0) return invalid();
        if (quint64(local) + 30 > centralOffset || word(zip, int(local)) != 0x04034b50
                || le16(zip, int(local) + 6) != flags || le16(zip, int(local) + 8) != method) return invalid();
        const int localName = le16(zip, int(local) + 26);
        const int localExtra = le16(zip, int(local) + 28);
        const quint64 dataOffset = quint64(local) + 30 + localName + localExtra;
        if (dataOffset + compressed > centralOffset || localName != nameSize
                || zip.mid(int(local) + 30, localName) != rawName) return invalid();
        if (!(flags & 8) && (word(zip, int(local) + 14) != crc
                || word(zip, int(local) + 18) != compressed || word(zip, int(local) + 22) != size)) return invalid();
        expanded += size;
        if (expanded > maxExpandedBytes) return invalid();
        QByteArray payload;
        if (method == 0) {
            if (compressed != size) return invalid();
            payload = zip.mid(int(dataOffset), int(size));
        } else {
            payload.resize(int(size) + 1);
            z_stream stream{};
            stream.next_in = reinterpret_cast<Bytef*>(const_cast<char*>(zip.constData() + int(dataOffset)));
            stream.avail_in = compressed;
            stream.next_out = reinterpret_cast<Bytef*>(payload.data());
            stream.avail_out = size + 1;
            if (inflateInit2(&stream, -MAX_WBITS) != Z_OK) return invalid();
            const int result = inflate(&stream, Z_FINISH);
            const bool ok = result == Z_STREAM_END && stream.total_in == compressed && stream.total_out == size;
            inflateEnd(&stream);
            if (!ok) return invalid();
            payload.resize(int(size));
        }
        if (crc32(0, reinterpret_cast<const Bytef*>(payload.constData()), payload.size()) != crc) return invalid();
        if (!directory) entries->append({cleanName, payload});
        cursor += 46 + nameSize + extraSize + commentSize;
    }
    if (cursor != end || entries->isEmpty()) return invalid();
    // A regular file cannot also be the parent of another entry.
    QSet<QString> fileNames;
    for (const Entry& entry : *entries) fileNames.insert(entry.name.toLower());
    for (const Entry& entry : *entries) {
        QString parent = entry.name.contains('/') ? entry.name.section('/', 0, -2).toLower() : QString();
        while (!parent.isEmpty()) {
            if (fileNames.contains(parent)) return invalid();
            parent = parent.contains('/') ? parent.section('/', 0, -2) : QString();
        }
    }
    return true;
}

bool inspectMacEntries(const QList<Entry>& entries, const QString& cpu, QString* error) {
    QSet<QString> families;
    for (const Entry& entry : entries) {
        const QString name = entry.name.toLower();
        if (name.endsWith(QStringLiteral(".dll")) || name.endsWith(QStringLiteral(".so"))
                || entry.bytes.startsWith("MZ") || entry.bytes.startsWith("\x7f" "ELF"))
            return fail(error, QStringLiteral("Mac native archive contains a foreign-platform binary: ") + entry.name);
        if (!name.endsWith(QStringLiteral(".dylib")) && !name.endsWith(QStringLiteral(".jnilib"))) continue;
        if (name.contains('/') || !matchedMachO(entry.bytes, cpu, 6))
            return fail(error, QStringLiteral("Mac native library is not a flat, architecture-matched Mach-O dylib: ") + entry.name);
        for (const char* family : {"lwjgl", "openal", "jinput", "jnidispatch"})
            if (name.contains(QString::fromLatin1(family))) families.insert(QString::fromLatin1(family));
    }
    if (families.size() != 4)
        return fail(error, QStringLiteral("Mac native archive lacks inspected LWJGL, OpenAL, JInput or JNA library evidence. Supply the audited Lunar 1.8.9 native set; Windows or LWJGL 3 substitutes are unsupported."));
    return true;
}

bool verifyExtraction(const QString& directory, const QList<Entry>& entries) {
    const QFileInfo root(directory);
    if (!root.isDir() || root.isSymLink()) return false;
    const QString canonicalRoot = root.canonicalFilePath();
    QSet<QString> expected;
    for (const Entry& entry : entries) {
        const QString path = QDir(directory).filePath(entry.name);
        const QFileInfo info(path);
        QByteArray bytes;
        if (!info.isFile() || info.isSymLink() || !isWithin(canonicalRoot, info.canonicalFilePath())
                || !readBounded(path, maxFileBytes, &bytes) || bytes != entry.bytes) {
            // Zero-byte non-native metadata files are allowed.
            if (!entry.bytes.isEmpty() || !info.isFile() || info.isSymLink() || info.size() != 0
                    || !isWithin(canonicalRoot, info.canonicalFilePath())) return false;
        }
        expected.insert(entry.name);
    }
    QDirIterator it(directory, QDir::AllEntries | QDir::NoDotAndDotDot | QDir::Hidden | QDir::System,
                    QDirIterator::Subdirectories);
    int count = 0;
    while (it.hasNext()) {
        it.next();
        const QFileInfo info = it.fileInfo();
        if (++count > maxEntries * 2 || info.isSymLink() || !isWithin(canonicalRoot, info.canonicalFilePath())
                || (!info.isDir() && (!info.isFile() || !expected.contains(QDir(directory).relativeFilePath(info.filePath()))))) return false;
    }
    return true;
}
}

NativeInspection::Platform NativeInspection::currentPlatform() {
    Platform platform;
#if defined(Q_OS_WIN)
    platform.os = QStringLiteral("windows");
#elif defined(Q_OS_MACOS)
    platform.os = QStringLiteral("macos");
#elif defined(Q_OS_LINUX)
    platform.os = QStringLiteral("linux");
#else
    platform.os = QStringLiteral("unsupported");
#endif
    platform.cpu = normalizeCpu(QSysInfo::buildCpuArchitecture());
    return platform;
}

bool NativeInspection::inspectMacJava17(const QString& executable, const Platform& platform, QString* error) {
    if (platform.os != QStringLiteral("macos")
            || (platform.cpu != QStringLiteral("arm64") && platform.cpu != QStringLiteral("x86_64")))
        return fail(error, QStringLiteral("Unsupported Mac launch process architecture."));
#ifdef Q_OS_MACOS
    int translated = 0;
    size_t translatedSize = sizeof(translated);
    if (sysctlbyname("sysctl.proc_translated", &translated, &translatedSize, nullptr, 0) == 0 && translated)
        return fail(error, QStringLiteral("This Mac preflight requires a native launcher process. Rosetta launch composition remains unqualified; use an arm64 launcher on Apple silicon."));
#endif
    const QFileInfo java(executable);
    const QString canonicalJava = java.canonicalFilePath();
    const QDir bin = QFileInfo(canonicalJava).absoluteDir();
    const QString home = QDir(bin.filePath(QStringLiteral(".."))).canonicalPath();
    QByteArray release;
    if (!java.isExecutable() || java.fileName() != QStringLiteral("java") || bin.dirName() != QStringLiteral("bin")
            || !readBounded(QDir(home).filePath(QStringLiteral("release")), 64 * 1024, &release))
        return fail(error, QStringLiteral("Mac launch requires an intact Java 17 home with bin/java and a readable release file."));
    QMap<QString, QString> values;
    const QRegularExpression assignment(QStringLiteral("^([A-Z0-9_]+)=\"([^\"\r\n]*)\"$"));
    for (const QByteArray& line : release.split('\n')) {
        const auto match = assignment.match(QString::fromUtf8(line).trimmed());
        if (!match.hasMatch()) continue;
        if (values.contains(match.captured(1))) return fail(error, QStringLiteral("Java release file has duplicate keys."));
        values.insert(match.captured(1), match.captured(2));
    }
    const QString version = values.value(QStringLiteral("JAVA_VERSION"));
    const QString os = values.value(QStringLiteral("OS_NAME")).toLower();
    if (!QRegularExpression(QStringLiteral("^17(?:[.+-]|$)")).match(version).hasMatch()
            || (os != QStringLiteral("darwin") && os != QStringLiteral("mac os x") && os != QStringLiteral("macos"))
            || normalizeCpu(values.value(QStringLiteral("OS_ARCH"))) != platform.cpu)
        return fail(error, QStringLiteral("Mac launch requires Java exactly 17, macOS release metadata and OS_ARCH matching the launcher process (%1).").arg(platform.cpu));
    QByteArray javaBytes;
    if (!isWithin(home, canonicalJava) || !readBounded(canonicalJava, maxFileBytes, &javaBytes)
            || !matchedMachO(javaBytes, platform.cpu, 2))
        return fail(error, QStringLiteral("Java executable has no valid matching 64-bit Mach-O executable slice."));
    QSet<QString> core;
    QDirIterator it(home, {QStringLiteral("*.dylib"), QStringLiteral("*.jnilib")}, QDir::Files,
                    QDirIterator::Subdirectories);
    int count = 0;
    while (it.hasNext()) {
        const QString path = it.next();
        const QFileInfo info(path);
        QByteArray bytes;
        if (++count > maxEntries || !isWithin(home, info.canonicalFilePath())
                || !readBounded(path, maxFileBytes, &bytes) || !matchedMachO(bytes, platform.cpu, 6))
            return fail(error, QStringLiteral("Java runtime library lacks a valid matching Mach-O slice: ") + info.fileName());
        core.insert(info.fileName());
    }
    if (!core.contains(QStringLiteral("libjli.dylib")) || !core.contains(QStringLiteral("libjvm.dylib")))
        return fail(error, QStringLiteral("Java home lacks inspected libjli.dylib or libjvm.dylib."));
    return true;
}

bool NativeInspection::prepareNativeArchive(const QString& archive, const QString& packageRoot,
                                           const Platform& platform, const QString& version,
                                           QString* directory, QString* error) {
    QByteArray zip;
    QList<Entry> entries;
    if (!readBounded(archive, maxArchiveBytes, &zip))
        return fail(error, QStringLiteral("Native archive is missing, unreadable or exceeds the 64 MiB limit."));
    if (!readZip(zip, &entries, error)) return false;
    if (platform.os == QStringLiteral("macos") && !inspectMacEntries(entries, platform.cpu, error)) return false;
    // Policy v1 is part of the digest so stronger future validation never
    // inherits a directory produced under an older extraction policy.
    QCryptographicHash identity(QCryptographicHash::Sha256);
    identity.addData(QByteArrayLiteral("atw-native-v1\n") + platform.os.toUtf8() + '\n'
            + platform.cpu.toUtf8() + '\n' + version.toUtf8() + '\n');
    identity.addData(zip);
    const QString hash = QString::fromLatin1(identity.result().toHex());
    const QString package = QDir(packageRoot).canonicalPath();
    if (package.isEmpty()) return fail(error, QStringLiteral("Package root is not a readable directory."));
    // Do not follow a copied data junction/symlink into the original checkout
    // or a host/global native cache. Check each ancestor before creating files.
    QString root = package;
    for (const QString& component : {QStringLiteral("data"), QStringLiteral("natives")}) {
        const QString next = QDir(root).filePath(component);
        const QFileInfo info(next);
        if (info.isSymLink() || (info.exists() && !info.isDir()))
            return fail(error, QStringLiteral("Native cache ancestors must be ordinary package directories."));
        if (!info.exists() && !QDir().mkdir(next))
            return fail(error, QStringLiteral("Cannot create package-native cache directory."));
        const QString resolved = QDir(next).canonicalPath();
#ifdef Q_OS_WIN
        const bool samePath = resolved.compare(next, Qt::CaseInsensitive) == 0;
#else
        const bool samePath = resolved == next;
#endif
        if (!samePath) return fail(error, QStringLiteral("Native cache resolves outside its expected package location."));
        root = resolved;
    }
    const QString destination = QDir(root).filePath(hash);
    QLockFile lock(QDir(root).filePath(hash + QStringLiteral(".lock")));
    lock.setStaleLockTime(0);
    if (!lock.tryLock(0)) return fail(error, QStringLiteral("Native cache is busy; retry after the other launcher finishes."));
    if (QFileInfo::exists(destination) || QFileInfo(destination).isSymLink()) {
        if (!verifyExtraction(destination, entries))
            return fail(error, QStringLiteral("Native cache content is incomplete or changed. Close ATW/Minecraft and remove only data/natives/%1 before retrying.").arg(hash));
    } else {
        QTemporaryDir staging(QDir(root).filePath(QStringLiteral(".extract-XXXXXX")));
        if (!staging.isValid()) return fail(error, QStringLiteral("Cannot create native extraction staging directory."));
        for (const Entry& entry : entries) {
            const QString path = QDir(staging.path()).filePath(entry.name);
            if (!QDir().mkpath(QFileInfo(path).absolutePath()))
                return fail(error, QStringLiteral("Cannot create native extraction subdirectory."));
            QSaveFile file(path);
            if (!file.open(QIODevice::WriteOnly) || file.write(entry.bytes) != entry.bytes.size() || !file.commit())
                return fail(error, QStringLiteral("Cannot write complete native extraction."));
        }
        if (!verifyExtraction(staging.path(), entries) || !QDir().rename(staging.path(), destination))
            return fail(error, QStringLiteral("Cannot publish verified native extraction."));
    }
    *directory = destination;
    return true;
}
