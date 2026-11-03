// Standalone QtCore/zlib test of the public extraction API. Build with
// src/util/nativeinspection.cpp and the repository's QtCore/zlib toolchain.
// Arguments: stock Lunar arm64 v1_8 ZIP, existing empty scratch parent.
// No Java, game, account, global-cache writes or native-library loading.
#include "../../src/util/nativeinspection.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QTemporaryDir>
#include <QDebug>

int main(int argc, char** argv) {
    QCoreApplication application(argc, argv);
    if (argc != 3) return 2;
    QTemporaryDir scratch(QDir(QString::fromLocal8Bit(argv[2])).filePath(QStringLiteral("native-smoke-XXXXXX")));
    if (!scratch.isValid()) return 3;
    const QString archive = QString::fromLocal8Bit(argv[1]);
    const NativeInspection::Platform platform{QStringLiteral("macos"), QStringLiteral("arm64")};
    QString directory, error;
    if (!NativeInspection::prepareNativeArchive(archive, scratch.path(), platform,
                                               QStringLiteral("1.8.9"), &directory, &error)) {
        qCritical() << error;
        return 4;
    }
    const QString helper = QDir(directory).filePath(QStringLiteral("rewind-audio-helper"));
    if (!QFileInfo(helper).isExecutable()) return 5;
    QString cached;
    if (!NativeInspection::prepareNativeArchive(archive, scratch.path(), platform,
                                               QStringLiteral("1.8.9"), &cached, &error)
            || cached != directory) return 6;
    const auto permissions = QFile::permissions(helper);
    if (!QFile::setPermissions(helper, permissions & ~(QFile::ExeOwner | QFile::ExeUser
                                                      | QFile::ExeGroup | QFile::ExeOther))) return 7;
    if (NativeInspection::prepareNativeArchive(archive, scratch.path(), platform,
                                              QStringLiteral("1.8.9"), &cached, &error)) return 8;
    qInfo() << "PASS: stock archive extraction, helper executable permission, cache reuse and permission-loss rejection";
    return 0;
}
