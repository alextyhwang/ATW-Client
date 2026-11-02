//
// Created by nils on 6/7/22.
//

#include "fs.h"

#include <QDir>
#include <QApplication>
#include <QStandardPaths>

#include "buildconfig.h"

QString FS::getPackageRoot() {
    QDir directory(QCoreApplication::applicationDirPath());
#ifdef Q_OS_MACOS
    if (directory.dirName() == QStringLiteral("MacOS")) {
        QDir contents(directory);
        if (contents.cdUp() && contents.dirName() == QStringLiteral("Contents")) {
            QDir bundle(contents);
            if (bundle.cdUp() && bundle.dirName().endsWith(QStringLiteral(".app"))) {
                bundle.cdUp();
                return bundle.absolutePath();
            }
        }
    }
#endif
    return directory.absolutePath();
}

QString FS::combinePaths(const QString &a, const QString &b) {
    return QDir::cleanPath(a + QDir::separator() + b);
}

QString FS::combinePaths(const QString& a, const QString& b, const QString& c) {
    return combinePaths(combinePaths(a, b), c);
}

QString FS::combinePaths(const QString& a, const QString& b, const QString& c, const QString& d) {
    return combinePaths(combinePaths(combinePaths(a, b), c), d);
}

QString FS::getLibsDirectory() {
    return combinePaths(getPackageRoot(), BuildConfig::LIBS_DIR);
}

QString FS::getAgentsDirectory() {
    return combinePaths(getPackageRoot(), BuildConfig::AGENTS_DIR);
}

QString FS::getLunarDirectory() {
#if defined(ATW_PACKAGE_MODE)
    return combinePaths(getPackageRoot(), "data/home/.lunarclient");
#elif defined(ATW_TEST_PORTABLE)
    return combinePaths(QApplication::applicationDirPath(), "data/lunarclient");
#else
    return combinePaths(QDir::homePath(), ".lunarclient");
#endif
}

QString FS::getLunarAccountsPath() {
    return combinePaths(getLunarDirectory(), "settings/game/accounts.json");
}

QString FS::getLunarLogsPath() {
#ifdef ATW_PACKAGE_MODE
    return combinePaths(getPackageRoot(), "logs", "launcher");
#else
    return combinePaths(getLunarDirectory(), "logs", "launcher");
#endif
}

QString FS::getMinecraftDirectory() {
#if defined(ATW_PACKAGE_MODE)
    // Keep the portable on-disk contract on every OS; Genesis gets this explicit
    // game/assets path. Do not import a host Mac Minecraft profile implicitly.
    return combinePaths(getPackageRoot(), "data/home/AppData/Roaming/.minecraft/lunarclient");
#elif defined(ATW_TEST_PORTABLE)
    return combinePaths(QApplication::applicationDirPath(), "data/minecraft");
#else
    return combinePaths(
            QDir::homePath(),

#if defined(Q_OS_WIN)
            "AppData/Roaming/.minecraft"
#elif defined(Q_OS_DARWIN)
            "Library/Application Support/minecraft"
#else
            ".minecraft"
#endif
    );
#endif
}

QString FS::getWeaveModsDirectory() {
#if defined(ATW_PACKAGE_MODE)
    return combinePaths(getPackageRoot(), "data/home/.weave/mods");
#elif defined(ATW_TEST_PORTABLE)
    return combinePaths(QApplication::applicationDirPath(), "data/weave/mods");
#else
    return combinePaths(QApplication::applicationDirPath(), "weave-mods");
#endif
}
