//
// Created by nils on 11/4/21.
//

#include "mainwindow.h"

#include <QCoreApplication>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QStatusBar>
#include <QStandardPaths>
#include <QJsonDocument>
#include <QFileSystemModel>
#include <QIODevice>
#include <QFrame>

#include "pages/configurationpage.h"
#include "pages/gamepage.h"
#include "pages/logspage.h"
#include "pages/otherpage.h"
#include "pages/packspage.h"
#include "launch/launcher.h"
#include "buildconfig.h"
#include "widgets/widgetutils.h"
#include "widgets/customtitlebar.h"
#include "widgets/customtabwidget.h"
#include "util/fs.h"
#include <QGraphicsDropShadowEffect>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent), config(Config::load()), offlineLauncher(config, false, NULL){
#ifdef ATW_CONFIG_ONLY
    setWindowTitle(QStringLiteral("ATW Config v") + BuildConfig::VERSION);
    setWindowIcon(QIcon(QStringLiteral(":/res/icons/config.svg")));
#else
    setWindowTitle(QStringLiteral("ATW Client v") + BuildConfig::VERSION);
    static QString icon = FS::combinePaths(QCoreApplication::applicationDirPath(), QStringLiteral("icon.ico"));
    if (QFile::exists(icon))
        setWindowIcon(QIcon(icon));
    else {
        QString lcloc =
#if defined(Q_OS_WIN)
            QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation) + QStringLiteral("/Programs/lunarclient/Lunar Client.exe");
#elif defined(Q_OS_DARWIN)
            QStandardPaths::writableLocation(QStandardPaths::AppDataLocation) + "/lunarclient/Lunar Client"; //Need location
#else
            QDir::homePath() + "/lunarclient/Lunar Client"; // Need location
#endif
        QFileInfo fin(lcloc);
        QFileSystemModel* model = new QFileSystemModel;
        QIcon ic = model->fileIcon(model->index(fin.filePath()));
        QPixmap pixmap = ic.pixmap(ic.actualSize(QSize(1028, 1028)));
        setWindowIcon(pixmap);
        QFile file(icon);
        file.open(QIODevice::WriteOnly);
        pixmap.save(&file, "ICO");
    }
#endif
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
    

    launchButton = new QPushButton();
    launchButton->setObjectName("launchButton");
    launchButton->setFixedSize(362, 98);
    launchButton->setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
    launchButton->setCursor(Qt::PointingHandCursor);
    launchButton->setFlat(true);
    launchButton->setAutoFillBackground(false);
#ifndef ATW_CONFIG_ONLY
    connect(launchButton, &QPushButton::clicked, this, &MainWindow::launchOrEndProcess);

    QHBoxLayout* launchButtonLayout = new QHBoxLayout();
    launchButtonLayout->setAlignment(Qt::AlignCenter);
    launchButtonLayout->setContentsMargins(0, 0, 0, 12);
    launchButtonLayout->addStretch();
    launchButtonLayout->addWidget(launchButton, 0, Qt::AlignCenter);
    launchButtonLayout->addStretch();
#else
    launchButton->hide();
#endif

    tabWidget = new CustomTabWidget(this);
    tabWidget->setContentsMargins(0, 0, 0, 0);
    tabWidget->tabBar()->setExpanding(false);
    tabWidget->tabBar()->setCursor(Qt::PointingHandCursor);

    pages = {
        new GamePage(config),
        new ModsPage(config),
        new PacksPage(config),
        new OtherPage(config)
    };
    logsPage = static_cast<OtherPage*>(pages.last())->logsPage;

    tabWidget->addTab(pages[0], pages[0]->title());
    tabWidget->addTab(pages[1], pages[1]->title());
    tabWidget->addTab(pages[2], pages[2]->title());
    tabWidget->addTab(pages[3], pages[3]->title());

    tabWidget->setTabType(0, QStringLiteral("game"));
    tabWidget->setTabType(1, QStringLiteral("mods"));
    tabWidget->setTabType(2, QStringLiteral("packs"));
    tabWidget->setTabType(3, QStringLiteral("other"));

#ifndef ATW_CONFIG_ONLY
    connect(&offlineLauncher, &OfflineLauncher::error, this, &MainWindow::errorCallback);
    connect(&offlineLauncher, &OfflineLauncher::processStarted, this, &MainWindow::updateLaunchButtonState);
    connect(&offlineLauncher, &OfflineLauncher::processFinished, this, &MainWindow::updateLaunchButtonState);

    processCheckTimer = new QTimer(this);
    connect(processCheckTimer, &QTimer::timeout, this, &MainWindow::updateLaunchButtonState);
    processCheckTimer->start(1000);

    resetLaunchButtons();
#endif

    CustomTitleBar* titleBar = new CustomTitleBar(this);

    // Tab bar container div
    QWidget* tabBarContainer = new QWidget(this);
    tabBarContainer->setFixedSize(484, 74);
    tabBarContainer->setObjectName("tabBarContainer");
    tabBarContainer->setStyleSheet("QWidget#tabBarContainer { background-color: #242425; border-radius: 0px; }");
    QHBoxLayout* tabBarLayout = new QHBoxLayout(tabBarContainer);
    tabBarLayout->setContentsMargins(9, 0, 0, 0);
    tabBarLayout->setAlignment(Qt::AlignCenter);
    tabBarLayout->addWidget(tabWidget->tabBar());
    
    QHBoxLayout* tabBarCenterLayout = new QHBoxLayout();
    tabBarCenterLayout->addStretch();
    tabBarCenterLayout->addWidget(tabBarContainer);
    tabBarCenterLayout->addStretch();
    tabBarCenterLayout->setContentsMargins(0, 55, 0, 14);

    QWidget* tabContentContainer = new QWidget();
    tabContentContainer->setObjectName("tabContentContainer");
    tabContentContainer->setStyleSheet("QWidget#tabContentContainer { background-color: #303031; border-radius: 0px; }");
    QVBoxLayout* tabContentLayout = new QVBoxLayout(tabContentContainer);
    tabContentLayout->setContentsMargins(0, 0, 0, 0);
    tabContentLayout->setSpacing(0);
    tabContentLayout->addLayout(tabBarCenterLayout);
    tabContentLayout->addWidget(tabWidget);

    QVBoxLayout* mainLayout = new QVBoxLayout();
    mainLayout->setSpacing(0);
    mainLayout->setContentsMargins(0, 0, 0, 0);
    mainLayout->addWidget(titleBar);
#ifndef ATW_CONFIG_ONLY
    mainLayout->addLayout(launchButtonLayout);
#endif
    mainLayout->addWidget(tabContentContainer, 1);

    QWidget* central = WidgetUtils::layoutToWidget(mainLayout);
    central->setObjectName("centralWidget");
    setCentralWidget(central);
    setFixedSize(572, 749);

    QString css = R"(
        * {
            font-family: "%1";
        }
        QMainWindow, QWidget#centralWidget {
            background-color: #1F1F20;
            color: #929292;
            font-size: 16px;
        }
        QLabel {
            color: #929292;
            font-size: 18px;
            text-transform: none;
        }
        QTabWidget::pane {
            border: none;
            background: transparent;
        }
        QWidget#configPage {
            background-color: #242425;
            border-radius: 0px;
            margin: 0px 88px 31px 88px;
            padding: 18px 20px;
        }
        /* Nested config pages inside other tabs shouldn't double-margin */
        QWidget#configPage QWidget#configPage {
            margin: 0;
            padding: 0;
            border-radius: 0;
            background: transparent;
        }
        QTabWidget::tab-bar {
            alignment: center;
        }
        QTabBar {
            background-color: transparent;
            padding: 0;
            margin: 0;
        }
        QTabBar#mainTabBar::tab {
            color: transparent;
            margin-right: 10px;
            width: 120px;
            height: 48px;
            border: none;
            background: transparent;
        }
        QTabBar#mainTabBar::tab:last {
            margin-right: 0px;
        }
        QTabBar#mainTabBar::tab:!selected {
            color: transparent;
            background: transparent;
            border: none;
        }
        QTabBar#mainTabBar::tab:selected {
            color: transparent;
            background: transparent;
            border: none;
        }
        QTabBar::tab:!selected {
            color: #5C5C60;
            background: transparent;
            padding: 8px 16px;
            border-bottom: 2px solid transparent;
        }
        QTabBar::tab:selected {
            color: #E7EDF9;
            background: transparent;
            padding: 8px 16px;
            border-bottom: 2px solid #4779D8;
        }
        QTabBar::tab:hover:!selected {
            color: #929292;
        }
        QTabBar::tab:last {
            margin-right: 0px;
        }
        QSlider::horizontal {
            min-height: 24px;
        }
        QSlider::groove:horizontal {
            border: none;
            height: 12px;
            border-image: url(:/res/slider/track.svg) 0 0 0 0 stretch stretch;
        }
        QSlider::handle:horizontal {
            image: url(:/res/slider/thumb.svg);
            width: 30px;
            height: 21px;
            margin: -5px 0;
        }
        QLineEdit, QPlainTextEdit {
            background-color: #343435;
            border: none;
            color: #6C6C6C;
            padding: 6px 10px;
            border-radius: 0;
            font-size: 16px;
            selection-background-color: #4779D8;
        }
        QLineEdit:focus, QPlainTextEdit:focus {
            border: none;
        }
        QScrollBar:vertical {
            border: none;
            background: #252526;
            width: 12px;
            margin: 0;
            border-radius: 6px;
        }
        QScrollBar::handle:vertical {
            background: #454545;
            min-height: 20px;
            border-radius: 6px;
            margin: 2px;
        }
        QScrollBar::handle:vertical:hover {
            background: #5C5C60;
        }
        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
            border: none;
            background: none;
            height: 0px;
        }
        QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {
            background: none;
        }
        QCheckBox {
            color: #929292;
            font-size: 18px;
        }
        QCheckBox::indicator {
            width: 68px;
            height: 28px;
        }
        QCheckBox::indicator:unchecked {
            image: url(:/res/toggle/off.svg);
        }
        QCheckBox::indicator:checked {
            image: url(:/res/toggle/on.svg);
        }
        QPushButton {
            background-color: #343435;
            border: 1px solid #454545;
            color: #929292;
            padding: 8px 16px;
            border-radius: 4px;
            font-size: 14px;
        }
        QPushButton:hover {
            background-color: #3d3d3e;
            border: 1px solid #5C5C60;
        }
        QPushButton:pressed {
            background-color: #2d2d2e;
        }
        QPushButton#launchButton {
            background: transparent;
            border-image: url(:/res/launchbutton/normal.png);
        }
        QPushButton#launchButton:hover, QPushButton#launchButton:pressed {
            background: transparent;
            border-image: url(:/res/launchbutton/normal.png);
        }
        QPushButton#launchButton[running="true"] {
            background: transparent;
            border-image: url(:/res/launchbutton/running.png);
        }
    )";
    setStyleSheet(css.arg(font().family()));

    load();

#ifdef INCLUDE_UPDATER
    connect(&updaterChecker, &UpdateChecker::updateAvailable, this, &MainWindow::updateAvailable);
    connect(&updaterChecker, &UpdateChecker::noUpdatesAvailable, this, &MainWindow::noUpdatesAvailable);

    updaterChecker.checkForUpdates(false);
#endif
}

void MainWindow::resetLaunchButtons() {
#ifdef ATW_CONFIG_ONLY
    return;
#else
    updateLaunchButtonState();
#endif
}

void MainWindow::updateLaunchButtonState() {
#ifdef ATW_CONFIG_ONLY
    return;
#else
    if (offlineLauncher.isProcessRunning()) {
        launchButton->setProperty("running", true);
    } else {
        launchButton->setProperty("running", false);
    }
    launchButton->style()->unpolish(launchButton);
    launchButton->style()->polish(launchButton);
    launchButton->setEnabled(true);
#endif
}

void MainWindow::launchOrEndProcess() {
#ifdef ATW_CONFIG_ONLY
    return;
#else
    if (offlineLauncher.isProcessRunning()) {
        offlineLauncher.endProcess();
        if (logsPage)
            logsPage->stopPolling();
        return;
    }
    apply();
    if (offlineLauncher.launch()) {
        if (logsPage) {
            logsPage->startPolling();
            tabWidget->setCurrentIndex(3);  // Other tab
            static_cast<OtherPage*>(pages.last())->showLogsTab();
        }
        if (config.closeOnLaunch)
            close();
    }
#endif
}

void MainWindow::closeEvent(QCloseEvent *event) {
    apply();
    config.save();
    event->accept();
}

void MainWindow::apply() {
    for(ConfigurationPage *page : pages) {
        page->apply();
    }
    config.gameVersion = QStringLiteral("1.8.9");
    config.modLoader = QStringLiteral("Optifine");
}

void MainWindow::load() {
    for(ConfigurationPage* page : pages){
        page->load();
    }
}


void MainWindow::errorCallback(const QString &message) {
    statusBar()->showMessage(message, 10000);
    if (logsPage) {
        logsPage->stopPolling();
        tabWidget->setCurrentIndex(3);  // Other tab
        static_cast<OtherPage*>(pages.last())->showLogsTab();
        logsPage->appendToMainLog(QStringLiteral("[Launcher Error] ") + message);
    }
}

#ifdef INCLUDE_UPDATER

void MainWindow::updateAvailable(const QString &url) {
    statusBar()->showMessage(QStringLiteral("Update available: ") + url, 15000);
}

void MainWindow::noUpdatesAvailable() {
    statusBar()->showMessage(QStringLiteral("No updates available."), 3000);
}

#endif
