//
// Created by nils on 11/4/21.
//

#include "mainwindow.h"

#include <QCloseEvent>
#include <QCoreApplication>
#include <QCursor>
#include <QDir>
#include <QFileInfo>
#include <QFileSystemModel>
#include <QGuiApplication>
#include <QHBoxLayout>
#include <QIODevice>
#include <QLabel>
#include <QProcess>
#include <QScreen>
#include <QScrollArea>
#include <QShortcut>
#include <QStackedWidget>
#include <QStyle>
#include <QVBoxLayout>

#include "pages/configurationpage.h"
#include "pages/gamepage.h"
#include "pages/logspage.h"
#include "pages/otherpage.h"
#include "pages/packspage.h"
#include "launch/launcher.h"
#include "buildconfig.h"
#include "gui/style/atwtheme.h"
#include "widgets/customtabbar.h"
#include "widgets/customtitlebar.h"
#include "widgets/launchbutton.h"
#include "widgets/windowframe.h"
#include "util/fs.h"

#ifdef ATW_CONFIG_ONLY
#ifdef Q_OS_WIN
#include <windows.h>
#else
#include <cerrno>
#include <csignal>
#include <sys/types.h>
#endif
#endif

using namespace AtwTheme;

namespace {
QScrollArea* wrapPage(QWidget* page) {
    // Pages scroll only when the window is shorter than the Figma frame.
    QScrollArea* scroll = new QScrollArea();
    scroll->setObjectName(QStringLiteral("pageScroll"));
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setWidgetResizable(true);
    scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    scroll->setWidget(page);
    scroll->viewport()->setAutoFillBackground(false);
    page->setAutoFillBackground(false);
    return scroll;
}
}

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent), config(Config::load()), offlineLauncher(config, false, NULL){
#ifdef ATW_CONFIG_ONLY
    setWindowTitle(QStringLiteral("ATW Config v") + BuildConfig::VERSION);
    setWindowIcon(QIcon(QStringLiteral(":/res/branding/atw.svg")));
#else
    setWindowTitle(QStringLiteral("ATW Client v") + BuildConfig::VERSION);
    setWindowIcon(QIcon(QStringLiteral(":/res/branding/atw.svg")));
#endif
    // Frameless, rounded Figma frame. The minimize hint keeps taskbar/Dock
    // minimizing working without drawing a native frame.
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window | Qt::WindowMinimizeButtonHint);
    setAttribute(Qt::WA_TranslucentBackground);

    frame = new WindowFrame(this);
    const int m = WindowFrame::shadowMargin();

    CustomTitleBar* titleBar = new CustomTitleBar(frame);

    launchButton = new LaunchButton(frame);
    connect(launchButton, &QAbstractButton::clicked, this, &MainWindow::launchOrEndProcess);

    // Tab tray: 528 x 66 (9 px padding) holding four raised tabs.
    QFrame* tray = new QFrame(frame);
    tray->setObjectName(QStringLiteral("tray"));
    tray->setFixedHeight(Metrics::TrayHeight);
    tabBar = new CustomTabBar(tray);
    QHBoxLayout* trayLayout = new QHBoxLayout(tray);
    // The tab edge is 4 px tall; it overhangs the 48 px slot by 1 px (Figma).
    trayLayout->setContentsMargins(Metrics::TrayPadding, Metrics::TrayPadding,
                                   Metrics::TrayPadding, Metrics::TrayPadding - 1);
    trayLayout->addWidget(tabBar);

    // Content card: 528 wide with 18 px padding.
    QFrame* card = new QFrame(frame);
    card->setObjectName(QStringLiteral("card"));
    pageStack = new QStackedWidget(card);
    pageStack->setObjectName(QStringLiteral("pageStack"));
    QVBoxLayout* cardLayout = new QVBoxLayout(card);
    cardLayout->setContentsMargins(Metrics::CardPadding, Metrics::CardPadding,
                                   Metrics::CardPadding, Metrics::CardPadding);
    cardLayout->addWidget(pageStack);

    pages = {
        new GamePage(config),
        new ModsPage(config),
        new PacksPage(config),
        new OtherPage(config)
    };
    logsPage = static_cast<OtherPage*>(pages.last())->logsPage;
    for (ConfigurationPage* page : pages) {
        tabBar->addTab(page->title());
        pageStack->addWidget(wrapPage(page));
    }
    connect(tabBar, &QTabBar::currentChanged, pageStack, &QStackedWidget::setCurrentIndex);

    // Vertical rhythm follows the Figma frame: title row at 22, LAUNCH at 107,
    // tray at 210, card at 288 and a 27 px bottom margin.
    QVBoxLayout* root = new QVBoxLayout(frame);
    root->setSpacing(0);
    root->setContentsMargins(m + Metrics::Margin, m + Metrics::TitleTop, m + Metrics::Margin, m + Metrics::BottomMargin);
    root->addWidget(titleBar);
    root->addSpacing(Metrics::LaunchTop - Metrics::TitleTop - Metrics::TitleRowHeight);
    root->addWidget(launchButton, 0, Qt::AlignHCenter);
    root->addSpacing(Metrics::TrayTop - (Metrics::LaunchTop + Metrics::LaunchFaceHeight + Metrics::LaunchEdge));
    root->addWidget(tray);
    root->addSpacing(Metrics::CardTop - (Metrics::TrayTop + Metrics::TrayHeight));
    root->addWidget(card, 1);
    setCentralWidget(frame);

    // Transient status line in the frame's bottom margin (replaces a status bar).
    toast = new QLabel(frame);
    toast->setObjectName(QStringLiteral("toast"));
    toast->setAttribute(Qt::WA_TransparentForMouseEvents);
    toast->hide();
    toastTimer = new QTimer(this);
    toastTimer->setSingleShot(true);
    connect(toastTimer, &QTimer::timeout, toast, &QWidget::hide);

    for (int i = 0; i < pages.size(); ++i) {
        QShortcut* shortcut = new QShortcut(QKeySequence(Qt::CTRL | Qt::Key(Qt::Key_1 + i)), this);
        connect(shortcut, &QShortcut::activated, this, [this, i]() { tabBar->setCurrentIndex(i); });
    }
    QShortcut* closeShortcut = new QShortcut(QKeySequence::Close, this);
    connect(closeShortcut, &QShortcut::activated, this, &QWidget::close);

    // Opens at the Figma frame size; resizable, with the card absorbing changes.
    setMinimumSize(Metrics::FrameWidth + 2 * m, 560 + 2 * m);
    resize(Metrics::FrameWidth + 2 * m, Metrics::FrameHeight + 2 * m);
    fitToScreen();

#ifndef ATW_CONFIG_ONLY
    connect(&offlineLauncher, &OfflineLauncher::error, this, &MainWindow::errorCallback);
    connect(&offlineLauncher, &OfflineLauncher::processStarted, this, &MainWindow::updateLaunchButtonState);
    connect(&offlineLauncher, &OfflineLauncher::processFinished, this, &MainWindow::updateLaunchButtonState);

    processCheckTimer = new QTimer(this);
    connect(processCheckTimer, &QTimer::timeout, this, &MainWindow::updateLaunchButtonState);
    processCheckTimer->start(1000);

    resetLaunchButtons();
#else
    handoffTimer = new QTimer(this);
    handoffTimer->setInterval(800);
    connect(handoffTimer, &QTimer::timeout, this, &MainWindow::pollHandoff);
    // First run: show the ATW window, then sign in over it. LAUNCH starts
    // the game once the account and master password are saved.
    QTimer::singleShot(400, this, [this]() {
        static_cast<GamePage*>(pages.first())->promptSignInIfSignedOut();
    });
#endif

    load();

#ifdef INCLUDE_UPDATER
    connect(&updaterChecker, &UpdateChecker::updateAvailable, this, &MainWindow::updateAvailable);
    connect(&updaterChecker, &UpdateChecker::noUpdatesAvailable, this, &MainWindow::noUpdatesAvailable);

    updaterChecker.checkForUpdates(false);
#endif
}

MainWindow::~MainWindow() {
#ifdef ATW_CONFIG_ONLY
    // The handed-off launcher keeps running; only drop our observation handle.
    endHandoffTracking();
#endif
}

void MainWindow::fitToScreen() {
    QScreen* screen = QGuiApplication::screenAt(QCursor::pos());
    if (!screen)
        screen = QGuiApplication::primaryScreen();
    if (!screen)
        return;
    const QRect available = screen->availableGeometry();
    const int m = WindowFrame::shadowMargin();
    // Small or scaled displays: keep the visible frame inside the work area and
    // let the settings card scroll instead.
    const int maxHeight = available.height() + 2 * m;
    if (minimumHeight() > maxHeight)
        setMinimumHeight(qMax(420, maxHeight));
    QSize size = this->size().boundedTo(QSize(available.width() + 2 * m, maxHeight));
    resize(size);
    move(available.center() - QPoint(size.width() / 2, size.height() / 2));
}

void MainWindow::resizeEvent(QResizeEvent* event) {
    QMainWindow::resizeEvent(event);
    positionToast();
}

void MainWindow::positionToast() {
    if (!toast || !frame)
        return;
    const QRect visible = frame->frameRect();
    toast->setMaximumWidth(visible.width() - 2 * Metrics::Margin);
    toast->adjustSize();
    const int y = visible.bottom() + 1 - Metrics::BottomMargin + (Metrics::BottomMargin - toast->height()) / 2;
    toast->move(visible.left() + (visible.width() - toast->width()) / 2, y);
}

void MainWindow::showStatus(const QString& message, bool error, int timeoutMs) {
    toast->setText(message);
    toast->setProperty("tone", error ? QStringLiteral("error") : QStringLiteral("info"));
    toast->setToolTip(message);
    toast->style()->unpolish(toast);
    toast->style()->polish(toast);
    positionToast();
    toast->show();
    toast->raise();
    toastTimer->start(timeoutMs);
}

void MainWindow::showLogs() {
    tabBar->setCurrentIndex(3);  // Other tab
    static_cast<OtherPage*>(pages.last())->showLogsTab();
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
    launchButton->setMode(offlineLauncher.isProcessRunning() ? LaunchButton::Mode::Close
                                                             : LaunchButton::Mode::Launch);
    launchButton->setEnabled(true);
#endif
}

void MainWindow::launchOrEndProcess() {
#ifdef ATW_CONFIG_ONLY
    if (handoffPid) {
        showStatus(QStringLiteral("ATW Client is already running."));
        return;
    }
    apply();
    config.save();
    if (!startLauncherHandoff())
        return;
    if (config.closeOnLaunch) {
        close();
        return;
    }
    launchButton->setMode(LaunchButton::Mode::Busy);
    showStatus(QStringLiteral("Starting ATW Client..."));
    handoffTimer->start();
    // atw-launch writes the same package logs the Logs tab tails.
    if (logsPage)
        logsPage->startPolling();
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
            showLogs();
        }
        if (config.closeOnLaunch)
            close();
    }
#endif
}

#ifdef ATW_CONFIG_ONLY
bool MainWindow::startLauncherHandoff() {
    const QString root = FS::getPackageRoot();
#if defined(Q_OS_MACOS)
    const QString program = QDir(root).filePath(QStringLiteral("atw-launch.app/Contents/MacOS/atw-launch"));
#elif defined(Q_OS_WIN)
    const QString program = QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("atw-launch.exe"));
#else
    const QString program = QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("atw-launch"));
#endif
    const QFileInfo launcher(program);
    if (!launcher.isFile() || !launcher.isExecutable()) {
        showStatus(QStringLiteral("atw-launch is missing from this package."), true, 10000);
        return false;
    }
    qint64 pid = 0;
    if (!QProcess::startDetached(launcher.absoluteFilePath(), {}, root, &pid) || pid <= 0) {
        showStatus(QStringLiteral("Could not start atw-launch."), true, 10000);
        return false;
    }
    handoffPid = pid;
#ifdef Q_OS_WIN
    handoffHandle = OpenProcess(SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, DWORD(pid));
#endif
    return true;
}

void MainWindow::pollHandoff() {
    bool running = false;
#ifdef Q_OS_WIN
    running = handoffHandle && WaitForSingleObject(static_cast<HANDLE>(handoffHandle), 0) == WAIT_TIMEOUT;
#else
    running = handoffPid > 0 && (::kill(pid_t(handoffPid), 0) == 0 || errno == EPERM);
#endif
    if (running)
        return;
    endHandoffTracking();
    if (logsPage)
        logsPage->stopPolling();
    launchButton->setMode(LaunchButton::Mode::Launch);
    // atw-launch may have signed in or out; show the current account.
    static_cast<GamePage*>(pages.first())->refreshAccount();
}

void MainWindow::endHandoffTracking() {
    if (handoffTimer)
        handoffTimer->stop();
#ifdef Q_OS_WIN
    if (handoffHandle)
        CloseHandle(static_cast<HANDLE>(handoffHandle));
#endif
    handoffHandle = nullptr;
    handoffPid = 0;
}
#endif

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
    showStatus(message, true, 10000);
    if (logsPage) {
        logsPage->stopPolling();
        showLogs();
        logsPage->appendToMainLog(QStringLiteral("[Launcher Error] ") + message);
    }
}

#ifdef INCLUDE_UPDATER

void MainWindow::updateAvailable(const QString &url) {
    showStatus(QStringLiteral("Update available: ") + url, false, 15000);
}

void MainWindow::noUpdatesAvailable() {
    showStatus(QStringLiteral("No updates available."), false, 3000);
}

#endif
