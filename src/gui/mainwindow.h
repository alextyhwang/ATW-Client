//
// Created by nils on 11/4/21.
//

#ifndef LUNAR_CLIENT_QT_MAINWINDOW_H
#define LUNAR_CLIENT_QT_MAINWINDOW_H

#include <QMainWindow>
#include "widgets/customtabwidget.h"
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

class MainWindow : public QMainWindow {
Q_OBJECT
public:
    explicit MainWindow(QWidget* parent = nullptr);

private:
    void launchOrEndProcess();
    void closeEvent(QCloseEvent* closeEvent) override;
    void apply();
    void load();
private slots:
    void resetLaunchButtons();
    void updateLaunchButtonState();
    void errorCallback(const QString& message);

#ifdef INCLUDE_UPDATER
    void updateAvailable(const QString& url);
    void noUpdatesAvailable();
#endif
private:
    CustomTabWidget* tabWidget;
    QPushButton* launchButton;

    QList<ConfigurationPage*> pages;

    OfflineLauncher offlineLauncher;
    LogsPage* logsPage = nullptr;
    QTimer* processCheckTimer = nullptr;

    Config config;

#ifdef INCLUDE_UPDATER
    UpdateChecker updaterChecker;
#endif
};


#endif //LUNAR_CLIENT_QT_MAINWINDOW_H
