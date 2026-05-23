#include "weavemods.h"

#include "util/fs.h"

#include <QDir>
#include <QFile>

namespace {
QString stripModSuffix(QString fileName) {
    return fileName.remove(QStringLiteral(".jar.disabled")).remove(QStringLiteral(".jar"));
}

QString modPath(const QString& name, bool enabled) {
    return FS::combinePaths(
        FS::getWeaveModsDirectory(),
        name + (enabled ? QStringLiteral(".jar") : QStringLiteral(".jar.disabled"))
    );
}

bool replaceFile(const QString& source, const QString& destination) {
    if (source == destination)
        return true;

    QFile::remove(destination);
    return QFile::rename(source, destination);
}
}

QList<Mod> Atw::Core::WeaveMods::list() {
    QDir modsDir(FS::getWeaveModsDirectory());
    if (!modsDir.exists())
        modsDir.mkpath(modsDir.path());

    QFileInfoList files = modsDir.entryInfoList({ "*.jar", "*.jar.disabled" }, QDir::Files, QDir::Name);
    QList<Mod> mods;
    for (const QFileInfo& file : files) {
        mods.append(Mod(stripModSuffix(file.fileName()), !file.completeSuffix().endsWith(QStringLiteral("jar.disabled"))));
    }

    return mods;
}

bool Atw::Core::WeaveMods::contains(const QList<Mod>& mods, const QString& name) {
    for (const Mod& mod : mods) {
        if (mod.name == name)
            return true;
    }

    return false;
}

bool Atw::Core::WeaveMods::install(const QFileInfo& modFileInfo, Mod* installed) {
    const QString name = stripModSuffix(modFileInfo.fileName());

    QDir().mkpath(FS::getWeaveModsDirectory());
    const QString destination = FS::combinePaths(FS::getWeaveModsDirectory(), modFileInfo.fileName());
    QFile::remove(destination);
    if (!QFile::copy(modFileInfo.absoluteFilePath(), destination))
        return false;

    if (installed)
        *installed = Mod(name, !modFileInfo.completeSuffix().endsWith(QStringLiteral("jar.disabled")));

    return true;
}

bool Atw::Core::WeaveMods::remove(const Mod& mod) {
    const bool removedEnabled = QFile::remove(modPath(mod.name, true));
    const bool removedDisabled = QFile::remove(modPath(mod.name, false));
    return removedEnabled || removedDisabled;
}

bool Atw::Core::WeaveMods::setEnabled(Mod& mod, bool enabled) {
    if (mod.enabled == enabled)
        return true;

    if (!replaceFile(modPath(mod.name, mod.enabled), modPath(mod.name, enabled)))
        return false;

    mod.enabled = enabled;
    return true;
}

bool Atw::Core::WeaveMods::rename(Mod& mod, const QString& newName) {
    if (newName.isEmpty() || mod.name == newName)
        return true;

    if (!replaceFile(modPath(mod.name, mod.enabled), modPath(newName, mod.enabled)))
        return false;

    mod.name = newName;
    return true;
}
