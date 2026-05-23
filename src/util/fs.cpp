//
// Created by nils on 6/7/22.
//

#include "fs.h"

#include <QDir>
#include <QApplication>
#include <QStandardPaths>

#include "buildconfig.h"


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
    return combinePaths(QApplication::applicationDirPath(), BuildConfig::LIBS_DIR);
}

QString FS::getAgentsDirectory() {
    return combinePaths(QApplication::applicationDirPath(), BuildConfig::AGENTS_DIR);
}

QString FS::getLunarDirectory() {
#if defined(ATW_TEST_PORTABLE) || defined(ATW_PACKAGE_MODE)
    return combinePaths(QApplication::applicationDirPath(), "data", "lunarclient");
#else
    return combinePaths(QDir::homePath(), ".lunarclient");
#endif
}

QString FS::getLunarAccountsPath() {
    return combinePaths(getLunarDirectory(), "settings", "game", "accounts.json");
}

QString FS::getLunarLogsPath() {
#ifdef ATW_PACKAGE_MODE
    return combinePaths(QApplication::applicationDirPath(), "logs", "launcher");
#else
    return combinePaths(getLunarDirectory(), "logs", "launcher");
#endif
}

QString FS::getMinecraftDirectory() {
#ifdef ATW_TEST_PORTABLE
    return combinePaths(QApplication::applicationDirPath(), "data", "minecraft");
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
#if defined(ATW_TEST_PORTABLE) && !defined(ATW_PACKAGE_MODE)
    return combinePaths(QApplication::applicationDirPath(), "data", "weave", "mods");
#else
    return combinePaths(QApplication::applicationDirPath(), "weave-mods");
#endif
}
