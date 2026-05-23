#include <QCommandLineParser>
#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QTextStream>

#include "config/config.h"
#include "launch/offlinelauncher.h"
#include "util/fs.h"

int main(int argc, char *argv[]) {
    QCoreApplication app(argc, argv);
    QCoreApplication::setApplicationName(QStringLiteral("atw-launch"));

    QCommandLineParser parser;
    parser.addHelpOption();
    parser.addVersionOption();

    QCommandLineOption assetIndexOption("assetIndex",
        QCoreApplication::translate("assetIndexOverride", "Override the asset index version"),
        QCoreApplication::translate("assetIndexOverride", "e.g. 1.8 for Minecraft 1.8.9"));
    parser.addOption(assetIndexOption);

    QCommandLineOption xmsOption("xms",
        QCoreApplication::translate("xmsOverride", "Override initial memory"),
        QCoreApplication::translate("xmsOverride", "memory in megabytes"));
    parser.addOption(xmsOption);

    QCommandLineOption xmxOption("xmx",
        QCoreApplication::translate("xmxOverride", "Override maximum memory"),
        QCoreApplication::translate("xmxOverride", "memory in megabytes"));
    parser.addOption(xmxOption);

    parser.process(app);

    Config config = Config::load();
    config.gameVersion = QStringLiteral("1.8.9");
    config.modLoader = QStringLiteral("Optifine");
    config.keepMemorySame = true;

    if (parser.isSet(xmsOption))
        config.initialMemory = parser.value(xmsOption).toInt();
    if (parser.isSet(xmxOption))
        config.maximumMemory = parser.value(xmxOption).toInt();
    if (config.keepMemorySame)
        config.initialMemory = config.maximumMemory;

    OfflineLauncher launcher(config, parser.isSet(assetIndexOption), parser.value(assetIndexOption), &app);
    QObject::connect(&launcher, &OfflineLauncher::error, [&app](const QString& message) {
        QDir().mkpath(FS::getLunarLogsPath());
        QFile logFile(FS::combinePaths(FS::getLunarLogsPath(), QStringLiteral("main.log")));
        if (logFile.open(QIODevice::Append | QIODevice::Text)) {
            QTextStream stream(&logFile);
            stream << "[Launcher Error] " << message << Qt::endl;
        }
        app.quit();
    });
    QObject::connect(&launcher, &OfflineLauncher::processFinished, &app, &QCoreApplication::quit);

    if (!launcher.launch())
        return 1;

    return QCoreApplication::exec();
}

