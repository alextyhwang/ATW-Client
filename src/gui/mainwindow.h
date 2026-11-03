//
// Created by nils on 11/4/21.
//

#ifndef LUNAR_CLIENT_QT_MAINWINDOW_H
#define LUNAR_CLIENT_QT_MAINWINDOW_H

#include <QMainWindow>
#include <QPushButton>
#include <QTimer>
#include <QStandardPaths>

#include "launch/offlinelauncher.h"
#include "launch/launcher.h"
#include "pages/configurationpage.h"
#include "pages/gamepage.h"
#include "pages/modspage.h"
#include "pages/otherpage.h"
#include "pages/packspage.h"
#include "config/config.h"

#ifdef INCLUDE_UPDATER
#include "updater/updatechecker.h"
#endif

class QLabel;
class QStackedWidget;
class CustomTabBar;
class LaunchButton;
class WindowFrame;

class MainWindow : public QMainWindow {
Q_OBJECT
public:
    explicit MainWindow(QWidget* parent = nullptr);
    ~MainWindow() override;

protected:
    void resizeEvent(QResizeEvent* event) override;

private:
    void launchOrEndProcess();
    void closeEvent(QCloseEvent* closeEvent) override;
    void apply();
    void load();
    void showStatus(const QString& message, bool error = false, int timeoutMs = 6000);
    void positionToast();
    void fitToScreen();
    void showLogs();
#ifdef ATW_CONFIG_ONLY
    // atw-config starts the canonical launcher, which owns sign-in and launch.
    bool startLauncherHandoff();
    void pollHandoff();
    void endHandoffTracking();
#endif
private slots:
    void resetLaunchButtons();
    void updateLaunchButtonState();
    void errorCallback(const QString& message);

#ifdef INCLUDE_UPDATER
    void updateAvailable(const QString& url);
    void noUpdatesAvailable();
#endif
private:
    WindowFrame* frame = nullptr;
    CustomTabBar* tabBar = nullptr;
    QStackedWidget* pageStack = nullptr;
    LaunchButton* launchButton = nullptr;
    QLabel* toast = nullptr;
    QTimer* toastTimer = nullptr;

    QList<ConfigurationPage*> pages;

    OfflineLauncher offlineLauncher;
    LogsPage* logsPage = nullptr;
    QTimer* processCheckTimer = nullptr;

#ifdef ATW_CONFIG_ONLY
    qint64 handoffPid = 0;
    void* handoffHandle = nullptr;   // Windows process handle while tracking
    QTimer* handoffTimer = nullptr;
#endif

    Config config;

#ifdef INCLUDE_UPDATER
    UpdateChecker updaterChecker;
#endif
};


#endif //LUNAR_CLIENT_QT_MAINWINDOW_H
