//
// Created by nils on 6/7/22.
//

#include "utils.h"

#include <QStringList>
#include <QDir>
#include <QVersionNumber>
#include <QRegularExpression>

#include "fs.h"

QString Utils::getAgentFlags(const QString &name) {
    return "-javaagent:" + FS::combinePaths(FS::getAgentsDirectory(), name);
}

QString Utils::getAgentFlags(const QString &name, const QString &option) {
    return getAgentFlags(name) + "=" + option;
}

QString Utils::getAssetsIndex(const QString &version) {
    if(version == "1.7")
        return "1.7.10";

    if (version.startsWith("1.8"))
        return "1.8";

    if (version == "1.19.3")
        return "2";

    if (version == "1.19.4")
        return "3";

    if (version.startsWith("1.20"))
        return "5";

    if (version.startsWith("1.17"))
        return "1.17";

    if (version.startsWith("1.18"))
        return "1.18";

    if (version.startsWith("1.19"))
        return "1.19";

    return version;
}

QString Utils::getGameVersion(const QString& version) {
    if (version == "1.20.0")
        return "1.20";
    if (version == "1.19.0")
        return "1.19";
    if (version == "1.12")
        return "1.12.2";
    if (version == "1.8")
        return "1.8.9";
    if (version == "1.7")
        return "1.7.10";
    return version;
}

QString Utils::getVersionFile(const QString& version) {
    if (version.length() < 2)
        return QStringLiteral("v1_8");

    // 1.8.9 uses v1_8 natives (same as 1.8), not v1_89
    if (version == QStringLiteral("1.8.9"))
        return QStringLiteral("v1_8");

    if (version.length() == 3)
        return QString(QStringLiteral("v") + version.at(0)) + QStringLiteral("_") + version.at(2);

    if (version.length() == 4)
        return QString(QStringLiteral("v") + version.at(0)) + QStringLiteral("_") + version.at(2) + version.at(3);

    if (version.length() == 6)
        return QString(QStringLiteral("v") + version.at(0)) + QStringLiteral("_") + version.at(2) + version.at(3) + QStringLiteral("_") + version.at(5);

    return QString(QStringLiteral("v") + version.at(0) + QStringLiteral("_") + version.at(2) +version.at(3));
}


QStringList Utils::getClassPath(const QStringList& files, const QString& version, const QString& modLoader) {
    QStringList classPath = QStringList();

    for (const QString& filename : files) {
        if (filename.length() < 8)
            continue;

        if (!filename.endsWith(QStringLiteral(".jar")))
            continue;

        if (filename.contains(QStringLiteral("v1_")))
            if (!filename.contains(Utils::getVersionFile(version)))
                continue;
            if (filename == QString(Utils::getVersionFile(version)) + QStringLiteral("-0.1.0-SNAPSHOT-all.jar"))
                continue;

        if (filename.contains(QStringLiteral("mixins")))
            if (modLoader == QStringLiteral("Optifine"))
                continue;
            if (modLoader == QStringLiteral("Forge"))
                if (filename.contains(QStringLiteral("Sodium"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Indium"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Iris"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Phosphor"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("ferrite"), Qt::CaseInsensitive))
                    continue;
            if (modLoader == QStringLiteral("Fabric"))
                if (filename.contains(QStringLiteral("NEU"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("LunatriusCore"), Qt::CaseInsensitive)|| filename.contains(QStringLiteral("SBA"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("DSM"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Schematica"), Qt::CaseInsensitive))
                    continue;
        
        if (filename.startsWith(QStringLiteral("argon")) || (filename.startsWith(QStringLiteral("modern")) && !(Utils::getVersionFile(version).startsWith(QStringLiteral("v1_16")) || Utils::getVersionFile(version).startsWith(QStringLiteral("v1_17"))  ||  Utils::getVersionFile(version).startsWith(QStringLiteral("v1_18"))  || Utils::getVersionFile(version).startsWith(QStringLiteral("v1_19"))  || Utils::getVersionFile(version).startsWith(QStringLiteral("v1_2")))))
            continue;

        if ((!filename.contains(QStringLiteral("mixins")) || modLoader == QStringLiteral("Optifine"))  && (filename.startsWith(QStringLiteral("OptiFine_v")) || filename.startsWith(QStringLiteral("Forge_v")) || filename.contains(QStringLiteral("NEU"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("LunatriusCore"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("SBA"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("DSM"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Schematica"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Sodium"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Indium"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Iris"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Phosphor"), Qt::CaseInsensitive) || filename.startsWith(QStringLiteral("FerriteCore"), Qt::CaseInsensitive) || filename.startsWith(QStringLiteral("replaymod"), Qt::CaseInsensitive)))
            continue;

        if (modLoader != QStringLiteral("Fabric"))
            if (filename.contains(QStringLiteral("fabric"), Qt::CaseInsensitive))
                continue;

        if (modLoader != QStringLiteral("Forge"))
            if (filename.contains(QStringLiteral("forge"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("skyblock"), Qt::CaseInsensitive))
                continue;

        if (modLoader == QStringLiteral("Fabric"))
            if (filename.contains(QStringLiteral("optifine"), Qt::CaseInsensitive))
                continue;

        classPath << filename;
    }

    return classPath;
}

QStringList Utils::getExternalFiles(const QStringList& files, const QString& version, const QString &modLoader) {
    QStringList externalFiles = QStringList();
    QString versionFile = Utils::getVersionFile(version);
    bool addOptifine = false;
    QString optifineFileName = QString();

    for (const QString& filename : files) {
        if (filename.length() < 8)
            continue;

        if (!filename.endsWith(QStringLiteral(".jar")))
            continue;

        if (!filename.contains(versionFile))
                continue;

        if (modLoader == "Fabric" && (filename.startsWith(QStringLiteral("Sodium")) || filename.startsWith(QStringLiteral("Indium")) || filename.startsWith(QStringLiteral("Iris")) || filename.startsWith(QStringLiteral("Phosphor")) || filename.startsWith(QStringLiteral("FerriteCore")) || filename.startsWith(QStringLiteral("ReplayMod"))))
                externalFiles << filename;

        if (modLoader == "Forge")
            if (filename.startsWith(QStringLiteral("Forge_v")) || filename.contains(QStringLiteral("ReplayMod"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("NEU"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("LunatriusCore"), Qt::CaseInsensitive)|| filename.contains(QStringLiteral("SBA"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("DSM"), Qt::CaseInsensitive) || filename.contains(QStringLiteral("Schematica"), Qt::CaseInsensitive))
                externalFiles << filename;

        if (modLoader != "Fabric" && filename.startsWith(QStringLiteral("OptiFine_v"))) {
            addOptifine = true;
            optifineFileName = filename;
        }
    }

    if (addOptifine)
        externalFiles << optifineFileName;

    return externalFiles;
}

QString Utils::getNativesFile(const QStringList& files, const QString& version) {
    return getNativesFile(files, version, NativeInspection::currentPlatform(), nullptr);
}

QString Utils::getNativesFile(const QStringList& files, const QString& version,
                             const NativeInspection::Platform& platform, QString* error) {
    QStringList matches;
    // Legacy Windows "x86" archives contain both 32- and 64-bit DLLs. Keep
    // that package convention for x64; it is never an ARM/Mac candidate.
    QStringList osNames;
    QStringList cpuNames;
    if (platform.os == QStringLiteral("windows")) {
        osNames = QStringList{QStringLiteral("win"), QStringLiteral("windows")};
        if (platform.cpu == QStringLiteral("x86_64"))
            cpuNames = QStringList{QStringLiteral("x86"), QStringLiteral("x86_64"), QStringLiteral("amd64"), QStringLiteral("x64")};
        else if (platform.cpu == QStringLiteral("i386"))
            cpuNames = QStringList{QStringLiteral("x86")};
    } else if (platform.os == QStringLiteral("macos")) {
        osNames = QStringList{QStringLiteral("macos"), QStringLiteral("osx"), QStringLiteral("mac")};
        if (platform.cpu == QStringLiteral("arm64"))
            cpuNames = QStringList{QStringLiteral("arm64"), QStringLiteral("aarch64"), QStringLiteral("universal"), QStringLiteral("universal2")};
        else if (platform.cpu == QStringLiteral("x86_64"))
            cpuNames = QStringList{QStringLiteral("x86_64"), QStringLiteral("amd64"), QStringLiteral("x64"), QStringLiteral("universal"), QStringLiteral("universal2")};
    } else if (platform.os == QStringLiteral("linux")) {
        osNames = QStringList{QStringLiteral("linux")};
        if (platform.cpu == QStringLiteral("x86_64"))
            cpuNames = QStringList{QStringLiteral("x86_64"), QStringLiteral("amd64"), QStringLiteral("x64"), QStringLiteral("x86")};
        else if (platform.cpu == QStringLiteral("arm64"))
            cpuNames = QStringList{QStringLiteral("arm64"), QStringLiteral("aarch64")};
    }
    if (osNames.isEmpty() || cpuNames.isEmpty()) {
        if (error) *error = QStringLiteral("Unsupported native platform: %1/%2.").arg(platform.os, platform.cpu);
        return {};
    }
    const QRegularExpression pattern(QStringLiteral("^client-natives-(%1)-(%2)-%3\\.zip$")
        .arg(osNames.join('|'), cpuNames.join('|'), QRegularExpression::escape(getVersionFile(version))),
        QRegularExpression::CaseInsensitiveOption);
    for (const QString& filename : files) {
        if (pattern.match(filename).hasMatch()) matches << filename;
    }
    if (matches.size() != 1) {
        if (error) *error = matches.isEmpty()
            ? QStringLiteral("No explicit %1/%2 Lunar native archive for %3. Supply an audited archive matching this OS/CPU; foreign-platform natives cannot be used.")
                .arg(platform.os, platform.cpu, version)
            : QStringLiteral("Multiple %1/%2 Lunar native archives for %3. Keep one audited archive to avoid ambiguous selection.")
                .arg(platform.os, platform.cpu, version);
        return {};
    }
    return matches.first().left(matches.first().size() - 4);
}


QStringList Utils::getOrderedAvailableVersions() {
    QString lunarDir = FS::getLunarDirectory();

    QDir multiverDir(FS::combinePaths(lunarDir, "offline", "multiver"));

    QStringList list = multiverDir.entryList({"*v1_*.zip"}, QDir::Files, QDir::Name);

    QStringList versionList = QStringList();

    for (const QString& filename : list) {
        QString version = filename;
        version.remove(0, version.indexOf(QStringLiteral("-v1_")));
        version.remove(0, 2);
        version.chop(4);
        version.replace("_", "");
        QString parsedVersion = QString();
        if (version.length() == 4)
            parsedVersion = QString(version.at(0) + QStringLiteral(".") + version.at(1) + version.at(2) + QStringLiteral(".") + version.at(3));
        else if (version.length() == 2)
            parsedVersion = QString(version.at(0) + QStringLiteral(".") + version.at(1));
        else if (version.length() == 3)
            parsedVersion = QString(version.at(0) + QStringLiteral(".") + version.at(1) + version.at(2));
        else
            parsedVersion = QString(version.at(0) + QStringLiteral(".") + version.at(1) + QStringLiteral(".") + version.at(2));
        versionList.append(parsedVersion);
    }

    std::sort(versionList.begin(), versionList.end(), [](const QString& a, const QString& b) {
        return QVersionNumber::fromString(a) < QVersionNumber::fromString(b);
    });

    return versionList;
}
QStringList Utils::getAvailableModLoaders(const QString& version) {
    QString lunarDir = FS::getLunarDirectory();

    QDir multiverDir(FS::combinePaths(lunarDir, "offline", "multiver"));

    QStringList list = multiverDir.entryList({ "*" + Utils::getVersionFile(version) + ".jar" }, QDir::Files, QDir::Name);

    QStringList modloaderList = QStringList();

    for (const QString& filename : list) {
        if (filename.startsWith("Forge", Qt::CaseInsensitive)) {
            modloaderList.append("Forge");
        }
        else if (filename.startsWith("Fabric", Qt::CaseInsensitive)) {
            modloaderList.append("Fabric");
        }
        else if (filename.startsWith("Optifine", Qt::CaseInsensitive)) {
            modloaderList.append("Optifine");
        }
    }
    return modloaderList;
}
