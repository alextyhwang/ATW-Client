#include <QApplication>
#include <QStandardPaths>
#include <QCommandLineParser>
#include <QFontDatabase>
#include <QFileInfo>
#include <QFile>
#include <QTextStream>

#include "gui/mainwindow.h"
#include "launch/offlinelauncher.h"
#include "util/fs.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
#if defined(ATW_CONFIG_ONLY)
    QApplication::setApplicationName(QStringLiteral("atw-config"));
#elif defined(ATW_LAUNCH_ONLY)
    QApplication::setApplicationName(QStringLiteral("atw-launch"));
#else
    QApplication::setApplicationName(QStringLiteral("atw-client"));
#endif

    // Use Fusion style for consistent cross-platform look (avoids Windows native inputs)
    app.setStyle(QStringLiteral("Fusion"));

    // Add the bundled Minecraft-style font from resources.
    QString fontPath = QStringLiteral(":/res/fonts/Minecraft.ttf");

    // Font already handles capitalization correctly now that text-transform: none is set
    int fontId = QFontDatabase::addApplicationFont(fontPath);
    QString fontFamily = QStringLiteral("Minecraft");
    if (fontId >= 0) {
        QStringList families = QFontDatabase::applicationFontFamilies(fontId);
        if (!families.isEmpty()) {
            for (const QString& f : families) {
                if (f.contains("Medium", Qt::CaseInsensitive)) {
                    fontFamily = f;
                    break;
                }
            }
            if (fontFamily == QStringLiteral("Minecraft")) {
                fontFamily = families.first();
            }
            QFont mainFont(fontFamily, 14);
            mainFont.setStyleStrategy(QFont::PreferAntialias);
            mainFont.setWeight(QFont::Medium);
            // Tell Qt we specifically want the font family string to just work
            mainFont.setFamily(fontFamily);
            app.setFont(mainFont);
        }
    }

    QCommandLineParser parser;
    parser.addHelpOption();
    parser.addVersionOption();  

    QCommandLineOption noGuiOption("nogui",
        QCoreApplication::translate("main", "Launch ATW Client without GUI. Enables the use of overriding options"));
    parser.addOption(noGuiOption);

    QCommandLineOption guiOption("gui",
        QCoreApplication::translate("main", "Force the launcher GUI to open even when autoLaunchOnOpen is enabled"));
    parser.addOption(guiOption);

    QCommandLineOption versionOption("gameVersion",
        QCoreApplication::translate("gameVersionOverride", "Override Minecraft version"),
        QCoreApplication::translate("gameVersionOverride", "e.g. 1.8.9 or 1.19 (exactly as listed in the launcher)"));
    parser.addOption(versionOption);

    QCommandLineOption assetIndexOption("assetIndex",
        QCoreApplication::translate("assetIndexOverride", "Override the assetIndex version which on default is derived from the game version but some new versions may break it"),
        QCoreApplication::translate("assetIndexOverride", "e.g. for 1.8.9 it is 1.8 but for 1.7.10 it is 1.7.10 as well"));
    parser.addOption(assetIndexOption);

    QCommandLineOption xmsOption("xms",
        QCoreApplication::translate("xmsOverride", "Override initial memory"),
        QCoreApplication::translate("xmsOverride", "Specify the initial memory in megabytes"));
    parser.addOption(xmsOption);

    QCommandLineOption xmxOption("xmx",
        QCoreApplication::translate("xmxOverride", "override maximum memory"),
        QCoreApplication::translate("xmxOverride", "Specify the maximum memory in megabytes"));
    parser.addOption(xmxOption);

    parser.process(app);

#ifdef ATW_CONFIG_ONLY
    MainWindow mainWindow;
    mainWindow.show();
    return QApplication::exec();
#else
    Config config = Config::load();
    if (
#ifdef ATW_LAUNCH_ONLY
        true
#else
        parser.isSet(noGuiOption) || (config.autoLaunchOnOpen && !parser.isSet(guiOption))
#endif
    ) {
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
        QObject::connect(&launcher, &OfflineLauncher::processFinished, &app, &QApplication::quit);

        if (!launcher.launch())
            return 1;

        return QApplication::exec();
    }
    else {
        MainWindow mainWindow;
        mainWindow.show();
        QApplication::exec();
        return 1;
    }
#endif
}
