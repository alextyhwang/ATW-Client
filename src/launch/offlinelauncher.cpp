//
// Created by nils on 11/4/21.
//

#include "offlinelauncher.h"

#include <QCoreApplication>
#include <QProcess>
#include <QDir>
#include <QDirIterator>
#include <QStandardPaths>
#include <QTemporaryFile>
#include <QElapsedTimer>
#include <QIODevice>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QTimer>
#include <QRegularExpression>
#include <QHostInfo>

#include "util/fs.h"
#include "util/utils.h"

#ifdef Q_OS_WIN
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

OfflineLauncher::OfflineLauncher(const Config& config, const bool useCustomAssetIndex, const QString& customAssetIndex, QObject *parent) : Launcher(config, useCustomAssetIndex, customAssetIndex, parent) {
}

struct JavaProbeResult {
    int majorVersion = 0;
    bool isGraalVm = false;
    bool ok = false;
    QString output;
};

static QStringList stableG1ProfileArgs() {
    return {
        QStringLiteral("-XX:+UnlockExperimentalVMOptions"),
        QStringLiteral("-XX:+UseG1GC"),
        QStringLiteral("-XX:MaxGCPauseMillis=40"),
        QStringLiteral("-XX:+AlwaysActAsServerClassMachine"),
        QStringLiteral("-XX:MaxTenuringThreshold=1"),
        QStringLiteral("-XX:SurvivorRatio=32"),
        QStringLiteral("-XX:G1HeapRegionSize=8M"),
        QStringLiteral("-XX:G1MixedGCCountTarget=4"),
        QStringLiteral("-XX:G1MixedGCLiveThresholdPercent=90"),
        QStringLiteral("-XX:-UsePerfData"),
        QStringLiteral("-XX:+PerfDisableSharedMem")
    };
}

static QStringList graalExperimentalArgs() {
    return {
        QStringLiteral("-XX:+EnableJVMCIProduct"),
        QStringLiteral("-XX:+EnableJVMCI"),
        QStringLiteral("-XX:+UseJVMCICompiler"),
        QStringLiteral("-XX:+EagerJVMCI"),
        QStringLiteral("-Dgraal.TuneInlinerExploration=1")
    };
}

static QStringList lowPauseProfileArgs() {
    return {
        QStringLiteral("-XX:+UseZGC"),
        QStringLiteral("-XX:+ZGenerational")
    };
}

static void appendLauncherLog(const QString& line) {
    QString logsDir = FS::getLunarLogsPath();
    QDir().mkpath(logsDir);
    QFile logFile(FS::combinePaths(logsDir, QStringLiteral("main.log")));
    if (logFile.open(QIODevice::Append | QIODevice::Text)) {
        logFile.write((line + QStringLiteral("\n")).toUtf8());
    }
}

static QString legacyWeaveModsDirectory() {
    return FS::combinePaths(QDir::homePath(), QStringLiteral(".weave"), QStringLiteral("mods"));
}

static bool isJunctionOrSymlinkTo(const QString& path, const QString& target) {
    QFileInfo info(path);
    if (!info.exists() || !info.isSymLink())
        return false;

    return QDir::cleanPath(info.symLinkTarget()).compare(QDir::cleanPath(target), Qt::CaseInsensitive) == 0;
}

static QString nextBackupPath(const QString& path) {
    QString backup = path + QStringLiteral(".backup-atw");
    if (!QFileInfo::exists(backup))
        return backup;

    for (int index = 1; index < 100; ++index) {
        QString candidate = backup + QStringLiteral("-") + QString::number(index);
        if (!QFileInfo::exists(candidate))
            return candidate;
    }

    return {};
}

static bool runCmd(const QStringList& arguments) {
    QProcess process;
    process.setProgram(QStringLiteral("cmd"));
    process.setArguments(arguments);
    process.start();
    if (!process.waitForFinished(10000))
        return false;

    return process.exitStatus() == QProcess::NormalExit && process.exitCode() == 0;
}

static bool ensureConfiguredWeaveModsDirectory(QString* errorMessage) {
    const QString configuredModsDir = QDir::cleanPath(FS::getWeaveModsDirectory());
    const QString legacyModsDir = QDir::cleanPath(legacyWeaveModsDirectory());

    QDir().mkpath(configuredModsDir);
    QDir().mkpath(QFileInfo(legacyModsDir).absolutePath());

    if (configuredModsDir.compare(legacyModsDir, Qt::CaseInsensitive) == 0)
        return true;

    if (isJunctionOrSymlinkTo(legacyModsDir, configuredModsDir))
        return true;

    QFileInfo legacyInfo(legacyModsDir);
    if (legacyInfo.exists()) {
        if (legacyInfo.isSymLink()) {
            if (!QFile::remove(legacyModsDir)) {
                if (errorMessage)
                    *errorMessage = QStringLiteral("Unable to remove stale Weave mods link: ") + legacyModsDir;
                return false;
            }
        } else {
            const QString backupPath = nextBackupPath(legacyModsDir);
            if (backupPath.isEmpty() || !QDir().rename(legacyModsDir, backupPath)) {
                if (errorMessage)
                    *errorMessage = QStringLiteral("Unable to back up existing Weave mods folder: ") + legacyModsDir;
                return false;
            }
            appendLauncherLog(QStringLiteral("[Weave] Backed up legacy mods folder to ") + backupPath);
        }
    }

#ifdef Q_OS_WIN
    if (!runCmd({QStringLiteral("/c"), QStringLiteral("mklink"), QStringLiteral("/J"),
                 QDir::toNativeSeparators(legacyModsDir),
                 QDir::toNativeSeparators(configuredModsDir)})) {
        if (errorMessage)
            *errorMessage = QStringLiteral("Unable to create Weave mods junction from ")
                    + legacyModsDir + QStringLiteral(" to ") + configuredModsDir;
        return false;
    }
#else
    if (!QFile::link(configuredModsDir, legacyModsDir)) {
        if (errorMessage)
            *errorMessage = QStringLiteral("Unable to link Weave mods directory: ") + legacyModsDir;
        return false;
    }
#endif

    appendLauncherLog(QStringLiteral("[Weave] Linked legacy mods folder to configured folder: ")
            + legacyModsDir + QStringLiteral(" -> ") + configuredModsDir);
    return true;
}

static QString probeJavaExecutable(const QString& executable) {
#ifdef Q_OS_WIN
    QFileInfo info(executable);
    QString java = FS::combinePaths(info.absolutePath(), QStringLiteral("java.exe"));
    if (QFileInfo(java).isExecutable())
        return java;
#endif
    return executable;
}

static JavaProbeResult probeJava(const QString& executable) {
    JavaProbeResult result;
    QProcess probe;
    probe.setProgram(probeJavaExecutable(executable));
    probe.setArguments({QStringLiteral("-version")});
    probe.start();
    if (!probe.waitForFinished(5000)) {
        probe.kill();
        probe.waitForFinished(1000);
        result.output = QStringLiteral("Java version probe timed out.");
        return result;
    }

    result.ok = probe.exitCode() == 0;
    result.output = QString::fromLocal8Bit(probe.readAllStandardError() + probe.readAllStandardOutput());
    result.isGraalVm = result.output.contains(QStringLiteral("GraalVM"), Qt::CaseInsensitive);

    QRegularExpression versionRegex(QStringLiteral("version\\s+\"([0-9]+)(?:\\.([0-9]+))?"));
    QRegularExpressionMatch match = versionRegex.match(result.output);
    if (match.hasMatch()) {
        int first = match.captured(1).toInt();
        int second = match.captured(2).toInt();
        result.majorVersion = first == 1 ? second : first;
    }

    return result;
}

static bool validateJavaFlags(const QString& executable, const QStringList& flags, QString* output) {
    QProcess validator;
    validator.setProgram(probeJavaExecutable(executable));
    validator.setArguments(flags + QStringList{QStringLiteral("-version")});
    validator.start();
    if (!validator.waitForFinished(6000)) {
        validator.kill();
        validator.waitForFinished(1000);
        if (output)
            *output = QStringLiteral("Java flag validation timed out.");
        return false;
    }

    if (output)
        *output = QString::fromLocal8Bit(validator.readAllStandardError() + validator.readAllStandardOutput());

    return validator.exitCode() == 0;
}

static QStringList buildProfileArgs(const Config& config, const QString& executable, const JavaProbeResult& probe) {
    QString profile = config.javaOptimizationProfile.trimmed();
    if (profile.isEmpty())
        profile = QStringLiteral("stable-g1");

    QStringList profileArgs = stableG1ProfileArgs();
    if (profile == QStringLiteral("graal-experimental")) {
        QStringList candidate = profileArgs + graalExperimentalArgs();
        QString validationOutput;
        if (probe.isGraalVm && validateJavaFlags(executable, candidate, &validationOutput)) {
            appendLauncherLog(QStringLiteral("[Java Optimizer] Using GraalVM experimental profile."));
            profileArgs = candidate;
        } else {
            appendLauncherLog(QStringLiteral("[Java Optimizer] GraalVM experimental profile unavailable; falling back to Stable G1."));
            if (!validationOutput.trimmed().isEmpty())
                appendLauncherLog(QStringLiteral("[Java Optimizer] Validation output: ") + validationOutput.trimmed().replace('\n', ' '));
        }
    } else if (profile == QStringLiteral("low-pause")) {
        QStringList candidate = lowPauseProfileArgs();
        QString validationOutput;
        if (probe.majorVersion >= 21 && validateJavaFlags(executable, candidate, &validationOutput)) {
            appendLauncherLog(QStringLiteral("[Java Optimizer] Using low-pause experimental profile."));
            profileArgs = candidate;
        } else {
            appendLauncherLog(QStringLiteral("[Java Optimizer] Low-pause profile requires Java 21+ support; falling back to Stable G1."));
            if (!validationOutput.trimmed().isEmpty())
                appendLauncherLog(QStringLiteral("[Java Optimizer] Validation output: ") + validationOutput.trimmed().replace('\n', ' '));
        }
    } else {
        appendLauncherLog(QStringLiteral("[Java Optimizer] Using Stable G1 profile."));
    }

    if (config.useLargePages) {
        QStringList candidate = profileArgs + QStringList{QStringLiteral("-XX:+UseLargePages")};
        QString validationOutput;
        if (validateJavaFlags(executable, candidate, &validationOutput)) {
            profileArgs << QStringLiteral("-XX:+UseLargePages");
            appendLauncherLog(QStringLiteral("[Java Optimizer] Large Pages requested. Windows must grant Lock pages in memory or the JVM may warn/fall back."));
        } else {
            appendLauncherLog(QStringLiteral("[Java Optimizer] Large Pages rejected by Java validation; continuing without it."));
            if (!validationOutput.trimmed().isEmpty())
                appendLauncherLog(QStringLiteral("[Java Optimizer] Validation output: ") + validationOutput.trimmed().replace('\n', ' '));
        }
    }

    return profileArgs;
}

static void logLatencyDiagnostics(const Config& config, const JavaProbeResult& probe) {
    appendLauncherLog(QStringLiteral("[Latency] JVM flags can reduce stutter but do not directly lower server ping."));
    appendLauncherLog(QStringLiteral("[Java Optimizer] Java major=%1 GraalVM=%2").arg(probe.majorVersion).arg(probe.isGraalVm ? QStringLiteral("true") : QStringLiteral("false")));

    if (config.showGpuReminder)
        appendLauncherLog(QStringLiteral("[Performance] Verify Windows graphics settings use the discrete GPU for javaw.exe if FPS or frame pacing is poor."));

    if (!config.joinServerOnLaunch || config.serverIp.trimmed().isEmpty())
        return;

    QString host = config.serverIp.trimmed();
    int colonIndex = host.indexOf(':');
    if (colonIndex > 0)
        host = host.left(colonIndex);

    QElapsedTimer timer;
    timer.start();
    QHostInfo hostInfo = QHostInfo::fromName(host);
    if (hostInfo.error() == QHostInfo::NoError) {
        appendLauncherLog(QStringLiteral("[Latency] DNS lookup for %1 took %2 ms.").arg(host).arg(timer.elapsed()));
    } else {
        appendLauncherLog(QStringLiteral("[Latency] DNS lookup for %1 failed after %2 ms: %3").arg(host).arg(timer.elapsed()).arg(hostInfo.errorString()));
    }
}

// Load active account from Lunar Client's accounts.json. Returns empty strings if not found.
static void loadActiveAccount(QString& accessToken, QString& username, QString& uuid, QString& userProperties) {
    QFile accountsFile(FS::getLunarAccountsPath());
    if (!accountsFile.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    QJsonDocument doc = QJsonDocument::fromJson(accountsFile.readAll());
    accountsFile.close();

    QJsonObject root = doc.object();
    QString activeId = root["activeAccountLocalId"].toString();
    if (activeId.isEmpty())
        return;

    QJsonObject accounts = root["accounts"].toObject();
    QJsonObject account = accounts[activeId].toObject();
    if (account.isEmpty())
        return;

    accessToken = account["accessToken"].toString();
    if (accessToken.isEmpty())
        return;

    QJsonObject profile = account["minecraftProfile"].toObject();
    username = profile["name"].toString();
    QString rawUuid = profile["id"].toString();
    // Format UUID with hyphens: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    if (rawUuid.length() == 32) {
        uuid = rawUuid.mid(0, 8) + "-" + rawUuid.mid(8, 4) + "-" + rawUuid.mid(12, 4) + "-" + rawUuid.mid(16, 4) + "-" + rawUuid.mid(20, 12);
    } else {
        uuid = rawUuid;
    }

    QJsonValue upVal = account["userProperties"];
    if (upVal.isArray())
        userProperties = QString::fromUtf8(QJsonDocument(upVal.toArray()).toJson(QJsonDocument::Compact));
    else if (upVal.isObject())
        userProperties = QString::fromUtf8(QJsonDocument(upVal.toObject()).toJson(QJsonDocument::Compact));
    else
        userProperties = "{}";
}

bool OfflineLauncher::isProcessRunning() const {
    if (process && process->state() != QProcess::NotRunning)
        return true;
    if (launchedPid != 0 && isPidRunning(launchedPid))
        return true;
    return false;
}

void OfflineLauncher::endProcess() {
    if (process && process->state() != QProcess::NotRunning) {
        process->terminate();
        process->waitForFinished(3000);
        if (process->state() != QProcess::NotRunning)
            process->kill();
        return;
    }
    if (launchedPid != 0) {
#ifdef Q_OS_WIN
        QProcess killProc;
        killProc.setProgram(QStringLiteral("taskkill"));
        killProc.setArguments({QStringLiteral("/PID"), QString::number(launchedPid), QStringLiteral("/F")});
        killProc.start();
        killProc.waitForFinished(3000);
#else
        QProcess killProc;
        killProc.setProgram(QStringLiteral("kill"));
        killProc.setArguments({QStringLiteral("-9"), QString::number(launchedPid)});
        killProc.start();
        killProc.waitForFinished(3000);
#endif
        launchedPid = 0;
    }
    emit processFinished();
}

void OfflineLauncher::onProcessFinished(int, QProcess::ExitStatus) {
    QString logsDir = FS::getLunarLogsPath();
    QDir().mkpath(logsDir);
    QFile logFile(FS::combinePaths(logsDir, QStringLiteral("main.log")));
    if (logFile.open(QIODevice::Append | QIODevice::Text)) {
        logFile.write(QStringLiteral("[Launcher] Java process exited with code %1\n").arg(process ? process->exitCode() : 0).toUtf8());
    }

    launchedPid = 0;
    if (process) {
        process->deleteLater();
        process = nullptr;
    }
    emit processFinished();
}

bool OfflineLauncher::isPidRunning(qint64 pid) {
#ifdef Q_OS_WIN
    QProcess checkProc;
    checkProc.setProgram(QStringLiteral("cmd"));
    checkProc.setArguments({QStringLiteral("/c"), QStringLiteral("tasklist"), QStringLiteral("/FI"),
                           QStringLiteral("PID eq ") + QString::number(pid)});
    checkProc.start();
    checkProc.waitForFinished(2000);
    QString output = QString::fromLocal8Bit(checkProc.readAllStandardOutput());
    return !output.contains(QStringLiteral("INFO: No tasks"));
#else
    QProcess checkProc;
    checkProc.setProgram(QStringLiteral("kill"));
    checkProc.setArguments({QStringLiteral("-0"), QString::number(pid)});
    checkProc.start();
    checkProc.waitForFinished(1000);
    return checkProc.exitCode() == 0;
#endif
}

bool OfflineLauncher::launch() {
    if (config.gameVersion.isEmpty()) {
        emit error("No version selected!\nDo you have lunar installed?");
        return false;
    }
    if (isProcessRunning()) {
        emit error("Game is already running.");
        return false;
    }

    process = new QProcess(qApp);
    connect(process, QOverload<int, QProcess::ExitStatus>::of(&QProcess::finished), this, &OfflineLauncher::onProcessFinished);
    QString executable = config.useCustomJre ? resolveJavaExecutable(config.customJrePath) : findJavaExecutable(config.gameVersion);

    if (executable.isEmpty() || !QFileInfo(executable).isExecutable()) {
        emit error("Unable to find a valid Java executable.\nCheck the JRE Path in Game settings.");
        process->deleteLater();
        process = nullptr;
        return false;
    }

    QString logsDir = FS::getLunarLogsPath();
    QDir().mkpath(logsDir);

#ifdef ATW_TEST_PORTABLE
    JavaProbeResult javaProbe = probeJava(executable);
    if (!javaProbe.ok)
        appendLauncherLog(QStringLiteral("[Java Optimizer] Java version probe failed: ") + javaProbe.output.trimmed().replace('\n', ' '));
    logLatencyDiagnostics(config, javaProbe);
#endif

    process->setProgram(executable);
    process->setStandardInputFile(QProcess::nullDevice());

    QString workingDir = FS::combinePaths(
        FS::getLunarDirectory(),
        "offline",
        "multiver"
    );

    process->setWorkingDirectory(workingDir);

    QStringList workingDirFiles = QDir(workingDir).entryList(QDir::Files, QDir::Time | QDir::Reversed);

    QFileInfoList libsList = QDir(FS::getLibsDirectory()).entryInfoList(QDir::Files);

    QStringList classPath = Utils::getClassPath(workingDirFiles, config.gameVersion, config.modLoader);

    QStringList ichorClassPath = classPath;

    QString nativesFile = Utils::getNativesFile(workingDirFiles, config.gameVersion);

    for(const QFileInfo& info : libsList) {
        classPath << info.absoluteFilePath();
    }

    QStringList args{
         "--add-modules", "jdk.naming.dns",
         "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
         "-Djna.boot.library.path=" + nativesFile,
         "-Dlog4j2.formatMsgNoLookups=true",
         "--add-opens", "java.base/java.io=ALL-UNNAMED",
         "-XX:+UseStringDeduplication",
         QString("-Xms%1m").arg(config.initialMemory),
         QString("-Xmx%1m").arg(config.maximumMemory),
         "-Djava.library.path=" + nativesFile,
         "-cp", classPath.join(QDir::listSeparator())
    };

    args << Utils::getAgentFlags("NativesPrepare", nativesFile);

    for(const Agent& agent : config.agents)
        if(agent.enabled)
            args << "-javaagent:" + agent.path + '=' + agent.option;

    if(config.useWeave) {
        QString weaveModsError;
        if (!ensureConfiguredWeaveModsDirectory(&weaveModsError)) {
            emit error(weaveModsError);
            process->deleteLater();
            process = nullptr;
            return false;
        }
        args << Utils::getAgentFlags("WeaveLoader");
    }

#ifdef ATW_TEST_PORTABLE
    QStringList removedJvmArgs;
    args << buildProfileArgs(config, executable, javaProbe);
    args << sanitizeJvmArgs(QProcess::splitCommand(config.jvmArgs), &removedJvmArgs);
    for (const QString& removedArg : removedJvmArgs)
        appendLauncherLog(QStringLiteral("[Java Optimizer] Removed unsupported JVM arg: ") + removedArg);
#else
    args << sanitizeJvmArgs(QProcess::splitCommand(config.jvmArgs));
#endif

    QString accessToken, username, uuid, userProperties;
    loadActiveAccount(accessToken, username, uuid, userProperties);

    if (accessToken.isEmpty()) {
        accessToken = "0";
        userProperties = "{}";
    }

    QString gameDir = config.useCustomMinecraftDir ? config.customMinecraftDir : FS::getMinecraftDirectory();
    sanitizeMinecraftOptions(gameDir);

    QStringList genesisArgs{
            "com.moonsworth.lunar.genesis.Genesis",
            "--version", Utils::getGameVersion(config.gameVersion),
            "--accessToken", accessToken,
            "--assetIndex", useCustomAssetIndex ? customAssetIndex : Utils::getAssetsIndex(config.gameVersion),
            "--userProperties", userProperties,
            "--gameDir", gameDir,
            "--launcherVersion", "3.1.3",
            "--width", QString::number(config.windowWidth),
            "--height", QString::number(config.windowHeight),
            "--workingDirectory", ".",
            "--classpathDir", ".",
            "--ichorClassPath", ichorClassPath.join(QString(",")),
            "--ichorExternalFiles", Utils::getExternalFiles(workingDirFiles, config.gameVersion, config.modLoader).join(QString(","))
    };

    if (!username.isEmpty())
        genesisArgs << "--username" << username;
    if (!uuid.isEmpty())
        genesisArgs << "--uuid" << uuid;

    args << genesisArgs;

    if(config.joinServerOnLaunch)
        args << "--server" << config.serverIp;

    process->setArguments(args);

    QProcessEnvironment env = QProcessEnvironment::systemEnvironment();
    env.remove("JAVA_OPTS");
    env.remove("_JAVA_OPTS");
    env.remove("JAVA_OPTIONS");
    env.remove("_JAVA_OPTIONS");
    env.remove("JAVA_TOOL_OPTIONS");
    env.remove("_JAVA_TOOL_OPTIONS");
    env.remove("JDK_JAVA_OPTIONS");
    env.remove("_JDK_JAVA_OPTIONS");

    process->setProcessEnvironment(env);

    process->setStandardOutputFile(FS::combinePaths(logsDir, "renderer.log"), QIODevice::Truncate);
#ifdef ATW_TEST_PORTABLE
    process->setStandardErrorFile(FS::combinePaths(logsDir, "main.log"), QIODevice::Append);
#else
    process->setStandardErrorFile(FS::combinePaths(logsDir, "main.log"), QIODevice::Truncate);
#endif

#ifdef Q_OS_WIN
    process->setCreateProcessArgumentsModifier([](QProcess::CreateProcessArguments* args) {
        args->flags |= 0x00000008; // DETACHED_PROCESS - game continues when launcher closes
    });
#endif

    process->start();
    if (!process->waitForStarted(5000)) {
        emit error("Failed to start process: " + process->errorString());
        process->deleteLater();
        process = nullptr;
        return false;
    }

    launchedPid = process->processId();
    scheduleMinecraftWindowIcon(launchedPid);
    emit processStarted();

    if (!config.helpers.isEmpty())
    {
        foreach(const QString & path, config.helpers)
            HelperLaunch(path);
    }

    return true;
}

QString OfflineLauncher::findJavaExecutable(const QString& gameVersion) {
    QDir jreDir = QDir(FS::combinePaths(FS::getLunarDirectory(), "jre"));

    QFileInfoList jreSubDirs = jreDir.entryInfoList(QDir::Dirs, QDir::Time | QDir::Reversed);

    QString targetJrePrefix = QStringLiteral("zulu17");
    if (gameVersion.startsWith(QStringLiteral("1.7")) || 
        gameVersion.startsWith(QStringLiteral("1.8")) || 
        gameVersion.startsWith(QStringLiteral("1.12"))) {
        targetJrePrefix = QStringLiteral("zulu8");
    } else if (gameVersion.startsWith(QStringLiteral("1.16"))) {
        targetJrePrefix = QStringLiteral("zulu16");
    }

    QString fallbackExecutable;

    for (QFileInfo jreSubDir : jreSubDirs) {

        if (jreSubDir.fileName().length() != 40)
            continue;

        QFileInfoList jreSubDirSubDirs = QDir(jreSubDir.absoluteFilePath()).entryInfoList(QDir::Dirs, QDir::Time | QDir::Reversed);

        for (const QFileInfo& jreSubDirSubDir : jreSubDirSubDirs) {
            QString potentialExecutable = FS::combinePaths(
                jreSubDirSubDir.absoluteFilePath(),
                "bin",
#ifdef Q_OS_WIN
                "javaw.exe"
#else
                "java"
#endif

            );

            if (QFileInfo(potentialExecutable).isExecutable()) {
                if (jreSubDirSubDir.fileName().startsWith(targetJrePrefix, Qt::CaseInsensitive)) {
                    return potentialExecutable;
                }
                if (fallbackExecutable.isEmpty()) {
                    fallbackExecutable = potentialExecutable;
                }
            }
        }
    }

    return fallbackExecutable;
}

QString OfflineLauncher::resolveJavaExecutable(const QString& path) {
    QString trimmedPath = QDir::fromNativeSeparators(path.trimmed());
    if (trimmedPath.isEmpty())
        return {};

    QFileInfo info(trimmedPath);
    if (info.isFile()) {
#ifdef Q_OS_WIN
        if (info.fileName().compare(QStringLiteral("java.exe"), Qt::CaseInsensitive) == 0) {
            QString javaw = FS::combinePaths(info.absolutePath(), QStringLiteral("javaw.exe"));
            if (QFileInfo(javaw).isExecutable())
                return javaw;
        }
#endif
        return info.absoluteFilePath();
    }

    if (!info.isDir())
        return {};

    QStringList candidateDirs;
    candidateDirs << info.absoluteFilePath();
    if (info.fileName().compare(QStringLiteral("bin"), Qt::CaseInsensitive) != 0)
        candidateDirs << FS::combinePaths(info.absoluteFilePath(), QStringLiteral("bin"));

    for (const QString& candidateDir : candidateDirs) {
#ifdef Q_OS_WIN
        const QStringList executableNames{QStringLiteral("javaw.exe"), QStringLiteral("java.exe")};
#else
        const QStringList executableNames{QStringLiteral("java")};
#endif
        for (const QString& executableName : executableNames) {
            QString executable = FS::combinePaths(candidateDir, executableName);
            if (QFileInfo(executable).isExecutable())
                return executable;
        }
    }

    return {};
}

QStringList OfflineLauncher::sanitizeJvmArgs(const QStringList& args, QStringList* removedArgs) {
    QStringList filteredArgs;

    for (const QString& arg : args) {
        if (arg == QStringLiteral("-XX:+UseG1GC") ||
            arg == QStringLiteral("-XX:+UnlockExperimentalVMOptions") ||
            arg == QStringLiteral("-XX:+UseStringDeduplication") ||
            arg == QStringLiteral("-XX:+AlwaysActAsServerClassMachine") ||
            arg == QStringLiteral("-XX:-UsePerfData") ||
            arg == QStringLiteral("-XX:+PerfDisableSharedMem") ||
            arg == QStringLiteral("-Xverify:none") ||
            arg.startsWith(QStringLiteral("-Xss")) ||
            arg.startsWith(QStringLiteral("-Xmn")) ||
            arg.startsWith(QStringLiteral("-XX:MaxGCPauseMillis=")) ||
            arg.startsWith(QStringLiteral("-XX:MaxTenuringThreshold=")) ||
            arg.startsWith(QStringLiteral("-XX:SurvivorRatio=")) ||
            arg.startsWith(QStringLiteral("-XX:G1HeapRegionSize=")) ||
            arg.startsWith(QStringLiteral("-XX:G1MixedGCCountTarget=")) ||
            arg.startsWith(QStringLiteral("-XX:G1MixedGCLiveThresholdPercent="))) {
            filteredArgs << arg;
        } else if (arg.startsWith(QStringLiteral("-D")) && !arg.startsWith(QStringLiteral("-Dgraal."))) {
            filteredArgs << arg;
        } else if (removedArgs) {
            removedArgs->append(arg);
        }
    }

    return filteredArgs;
}

void OfflineLauncher::sanitizeMinecraftOptions(const QString& gameDir) {
    QString optionsPath = FS::combinePaths(gameDir, QStringLiteral("options.txt"));
    QFile optionsFile(optionsPath);
    if (!optionsFile.exists() || !optionsFile.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    QList<QByteArray> lines = optionsFile.readAll().split('\n');
    optionsFile.close();

    bool changed = false;
    for (QByteArray& line : lines) {
        QByteArray trimmed = line.trimmed();
        if (trimmed == "streamPreferredServer:") {
            line = "streamPreferredServer:default";
            changed = true;
        }
    }

    if (!changed || !optionsFile.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate))
        return;

    optionsFile.write(lines.join('\n'));
    optionsFile.close();
}

void OfflineLauncher::HelperLaunch(const QString& helper) {
    QProcess process;
#ifdef Q_OS_WIN
    process.setCreateProcessArgumentsModifier(([] (QProcess::CreateProcessArguments* args)
    {
        args->flags |= 0x00000010; //CREATE_NEW_CONSOLE
    }));
#endif
    process.setProgram(helper);
    process.startDetached(); 
}

void OfflineLauncher::scheduleMinecraftWindowIcon(qint64 pid) {
#ifdef Q_OS_WIN
    const QList<int> delays{500, 1500, 3000, 6000, 10000};
    for (int delay : delays) {
        QTimer::singleShot(delay, [pid]() {
            struct IconContext {
                DWORD pid;
                HICON largeIcon;
                HICON smallIcon;
            };

            QString iconPath = FS::combinePaths(qApp->applicationDirPath(), QStringLiteral("minecraft.ico"));
            std::wstring nativeIconPath = QFileInfo::exists(iconPath)
                ? QDir::toNativeSeparators(iconPath).toStdWString()
                : std::wstring();
            IconContext context{
                static_cast<DWORD>(pid),
                nativeIconPath.empty() ? nullptr : static_cast<HICON>(LoadImageW(nullptr, nativeIconPath.c_str(), IMAGE_ICON, 32, 32, LR_LOADFROMFILE)),
                nativeIconPath.empty() ? nullptr : static_cast<HICON>(LoadImageW(nullptr, nativeIconPath.c_str(), IMAGE_ICON, 16, 16, LR_LOADFROMFILE))
            };

            EnumWindows([](HWND hwnd, LPARAM lParam) -> BOOL {
                IconContext* context = reinterpret_cast<IconContext*>(lParam);
                DWORD windowPid = 0;
                GetWindowThreadProcessId(hwnd, &windowPid);
                if (windowPid != context->pid || !IsWindowVisible(hwnd))
                    return TRUE;

                SetWindowTextW(hwnd, L"ATW Client 1.8.9");
                if (context->largeIcon && context->smallIcon) {
                    SendMessageW(hwnd, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(context->largeIcon));
                    SendMessageW(hwnd, WM_SETICON, ICON_SMALL, reinterpret_cast<LPARAM>(context->smallIcon));
                    SetClassLongPtrW(hwnd, GCLP_HICON, reinterpret_cast<LONG_PTR>(context->largeIcon));
                    SetClassLongPtrW(hwnd, GCLP_HICONSM, reinterpret_cast<LONG_PTR>(context->smallIcon));
                }
                return FALSE;
            }, reinterpret_cast<LPARAM>(&context));
        });
    }
#else
    Q_UNUSED(pid);
#endif
}
