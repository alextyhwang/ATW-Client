//
// Game settings - memory, JRE, Minecraft dir, JVM args.
//

#include "gamepage.h"

#include <QVBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QFileDialog>
#include <QGroupBox>
#include <QCheckBox>
#include <QComboBox>

#include "gui/style/atwtheme.h"
#include "gui/widgets/accountwidget.h"
#include "gui/widgets/filechooser.h"
#include "gui/widgets/pixelslider.h"
#include "gui/widgets/pixeltoggle.h"
#include "gui/widgets/widgetutils.h"
#include <QDesktopServices>
#include <QUrl>
#include <QStandardPaths>

#include "util/fs.h"

#ifndef _WIN32
#include <unistd.h>
unsigned long long getSystemMemory() {
	long pages = sysconf(_SC_PHYS_PAGES);
	long pageSize = sysconf(_SC_PAGE_SIZE);
	return pages * pageSize;
}
#else
#include <windows.h>
unsigned long long getSystemMemory() {
	MEMORYSTATUSEX status;
	status.dwLength = sizeof(status);
	GlobalMemoryStatusEx(&status);
	return status.ullTotalPhys;
}
#endif

using namespace AtwTheme;

GamePage::GamePage(Config& config, QWidget *parent) : ConfigurationPage(config, parent) {
    // Figma card content: groups 14 px apart; each label block is 24 px.
    QVBoxLayout* mainLayout = new QVBoxLayout();
    mainLayout->setSpacing(Metrics::GroupGap);
    mainLayout->setContentsMargins(0, 0, 0, 0);

    unsigned long long systemMemory = getSystemMemory();
    size_t mibMemory = (size_t)(systemMemory / 1024 / 1024);
    size_t pageStep = (size_t)(mibMemory / 16);

    QHBoxLayout* memLabelLayout = new QHBoxLayout();
    memLabelLayout->setSpacing(0);
    QLabel* memoryLabel = WidgetUtils::fieldLabel(QStringLiteral("Memory"));
    QLabel* memoryValLabel = WidgetUtils::fieldValue();
    memLabelLayout->addWidget(memoryLabel);
    memLabelLayout->addStretch();
    memLabelLayout->addWidget(memoryValLabel);

    memorySlider = new PixelSlider();
    memorySlider->setMinimum(1024);
    memorySlider->setMaximum(mibMemory);
    memorySlider->setPageStep(pageStep);
    memorySlider->setSingleStep(256);
    memorySlider->setAccessibleName(QStringLiteral("Memory"));

    connect(memorySlider, &QSlider::valueChanged, [memoryValLabel](int val){
        double gb = val / 1024.0;
        memoryValLabel->setText(QString::number(gb, 'f', 1) + QStringLiteral("GB"));
    });

    QVBoxLayout* memorySliderContainer = new QVBoxLayout();
    memorySliderContainer->setSpacing(0);
    memorySliderContainer->addLayout(memLabelLayout);
    memorySliderContainer->addWidget(memorySlider);

    jrePath = new FileChooser(QFileDialog::ExistingFile);
    jrePath->setPlaceholderText(QStringLiteral("Bundled Java runtime"));
    jrePath->setAccessibleLabel(QStringLiteral("JRE Path"));
    QVBoxLayout* jreContainer = WidgetUtils::fieldGroup(WidgetUtils::fieldLabel(QStringLiteral("JRE Path")), jrePath);

    minecraftPathChooser = new FileChooser(QFileDialog::Directory);
    minecraftPathChooser->setPlaceholderText(QStringLiteral("Default Minecraft folder"));
    minecraftPathChooser->setAccessibleLabel(QStringLiteral("Minecraft Path"));
    QVBoxLayout* minecraftContainer =
        WidgetUtils::fieldGroup(WidgetUtils::fieldLabel(QStringLiteral("Minecraft Path")), minecraftPathChooser);

#ifdef ATW_TEST_PORTABLE
    javaProfile = new QComboBox();
    javaProfile->addItem(QStringLiteral("Stable G1, recommended"), QStringLiteral("stable-g1"));
    javaProfile->addItem(QStringLiteral("GraalVM experimental"), QStringLiteral("graal-experimental"));
    javaProfile->addItem(QStringLiteral("Low-pause experimental (Java 21+)"), QStringLiteral("low-pause"));
    QVBoxLayout* profileContainer =
        WidgetUtils::fieldGroup(WidgetUtils::fieldLabel(QStringLiteral("Java Profile")), javaProfile);

    QHBoxLayout* largePagesLayout = new QHBoxLayout();
    largePagesLayout->addWidget(WidgetUtils::rowLabel(QStringLiteral("Use Large Pages"), Metrics::ToggleHeight));
    useLargePages = new PixelToggle();
    useLargePages->setAccessibleName(QStringLiteral("Use Large Pages"));
    largePagesLayout->addStretch();
    largePagesLayout->addWidget(useLargePages);

    QHBoxLayout* gpuReminderLayout = new QHBoxLayout();
    gpuReminderLayout->addWidget(WidgetUtils::rowLabel(QStringLiteral("Discrete GPU Reminder"), Metrics::ToggleHeight));
    showGpuReminder = new PixelToggle();
    showGpuReminder->setAccessibleName(QStringLiteral("Discrete GPU Reminder"));
    gpuReminderLayout->addStretch();
    gpuReminderLayout->addWidget(showGpuReminder);
#endif

    jvmArgs = new QPlainTextEdit();
    jvmArgs->setObjectName(QStringLiteral("jvmArgs"));
    jvmArgs->setAccessibleName(QStringLiteral("Custom JVM Arguments"));
    jvmArgs->setTabChangesFocus(true);
    // Figma box is 126 px; extra window height goes here, and it can give a
    // little back before the page needs to scroll.
    jvmArgs->setMinimumHeight(90);
    jvmArgs->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    QVBoxLayout* jvmArgsGroup =
        WidgetUtils::fieldGroup(WidgetUtils::fieldLabel(QStringLiteral("Custom JVM Arguments")), jvmArgs);
    jvmArgsGroup->setStretch(1, 1);

    QHBoxLayout* toggleLayout = new QHBoxLayout();
    toggleLayout->setSpacing(0);
    toggleLayout->addWidget(WidgetUtils::rowLabel(QStringLiteral("Launcher Open after Launch"), Metrics::ToggleHeight));
    closeOnLaunch = new PixelToggle();
    closeOnLaunch->setAccessibleName(QStringLiteral("Launcher Open after Launch"));
    closeOnLaunch->setChecked(!config.closeOnLaunch); // Note: we should probably map this properly
    toggleLayout->addStretch();
    toggleLayout->addWidget(closeOnLaunch, 0, Qt::AlignVCenter);

    // Not in the Figma frame: the sign-in row uses the card's free space below
    // the last Figma row so every Figma element keeps its position.
    account = new AccountWidget(config);

    mainLayout->addLayout(memorySliderContainer);
    mainLayout->addLayout(minecraftContainer);
    mainLayout->addLayout(jreContainer);
#ifdef ATW_TEST_PORTABLE
    mainLayout->addLayout(profileContainer);
    mainLayout->addLayout(largePagesLayout);
    mainLayout->addLayout(gpuReminderLayout);
#endif
    mainLayout->addLayout(jvmArgsGroup, 1);
    mainLayout->addLayout(toggleLayout);
    mainLayout->addWidget(account);

    setLayout(mainLayout);
}

QString GamePage::title() {
    return QStringLiteral("Game");
}

QIcon GamePage::icon() {
    return QIcon(":/res/icons/minecraft.svg");
}

void GamePage::apply() {
    config.keepMemorySame = true;
    int mem = memorySlider->value();
    config.initialMemory = mem;
    config.maximumMemory = mem;

    QString jrePathStr = jrePath->getPath().trimmed();
    config.useCustomJre = !jrePathStr.isEmpty();
    config.customJrePath = jrePathStr;

    QString mcPath = minecraftPathChooser->getPath().trimmed();
    config.useCustomMinecraftDir = !mcPath.isEmpty();
    config.customMinecraftDir = mcPath;

    config.jvmArgs = jvmArgs->toPlainText();
#ifdef ATW_TEST_PORTABLE
    config.javaOptimizationProfile = javaProfile->currentData().toString();
    config.useLargePages = useLargePages->isChecked();
    config.showGpuReminder = showGpuReminder->isChecked();
#endif
    config.closeOnLaunch = !closeOnLaunch->isChecked();
}

void GamePage::load() {
    memorySlider->setValue(config.maximumMemory);
    jrePath->setPath(config.customJrePath);
    minecraftPathChooser->setPath(config.customMinecraftDir);
    jvmArgs->setPlainText(config.jvmArgs);
#ifdef ATW_TEST_PORTABLE
    int profileIndex = javaProfile->findData(config.javaOptimizationProfile);
    javaProfile->setCurrentIndex(profileIndex >= 0 ? profileIndex : 0);
    useLargePages->setChecked(config.useLargePages);
    showGpuReminder->setChecked(config.showGpuReminder);
#endif
    closeOnLaunch->setChecked(!config.closeOnLaunch);
}

void GamePage::refreshAccount() {
    account->refresh();
}

QString GamePage::description() {
    return QStringLiteral("Account, memory, Java, and game directory settings.");
}
