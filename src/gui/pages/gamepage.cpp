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

#include "gui/widgets/filechooser.h"
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

GamePage::GamePage(Config& config, QWidget *parent) : ConfigurationPage(config, parent) {
    QVBoxLayout* mainLayout = new QVBoxLayout();
    mainLayout->setSpacing(16); // Even spacing between elements
    mainLayout->setContentsMargins(18, 0, 18, 14); // Match figma padding for config page

    unsigned long long systemMemory = getSystemMemory();
    size_t mibMemory = (size_t)(systemMemory / 1024 / 1024);
    size_t pageStep = (size_t)(mibMemory / 16);

    QHBoxLayout* memLabelLayout = new QHBoxLayout();
    QLabel* memoryLabel = new QLabel(QStringLiteral("Memory"));
    QLabel* memoryValLabel = new QLabel();
    memLabelLayout->addWidget(memoryLabel);
    memLabelLayout->addStretch();
    memLabelLayout->addWidget(memoryValLabel);

    memorySlider = new QSlider(Qt::Horizontal);
    memorySlider->setMinimum(1024);
    memorySlider->setMaximum(mibMemory);
    memorySlider->setPageStep(pageStep);

    memoryValLabel->setStyleSheet("color: #BFBFBF; font-size: 16px;");
    connect(memorySlider, &QSlider::valueChanged, [memoryValLabel](int val){
        double gb = val / 1024.0;
        memoryValLabel->setText(QString::number(gb, 'f', 1) + QStringLiteral("GB"));
    });

    QVBoxLayout* memorySliderContainer = new QVBoxLayout();
    memorySliderContainer->setSpacing(4);
    memorySliderContainer->addLayout(memLabelLayout);
    memorySliderContainer->addWidget(memorySlider);

    QVBoxLayout* jreContainer = new QVBoxLayout();
    jreContainer->setSpacing(4);
    jreContainer->addWidget(new QLabel(QStringLiteral("JRE Path")), 0, Qt::AlignLeft);
    jrePath = new FileChooser(QFileDialog::ExistingFile);
    jreContainer->addWidget(jrePath);

    QVBoxLayout* minecraftContainer = new QVBoxLayout();
    minecraftContainer->setSpacing(4);
    minecraftContainer->addWidget(new QLabel(QStringLiteral("Minecraft Path")), 0, Qt::AlignLeft);
    minecraftPathChooser = new FileChooser(QFileDialog::Directory);
    minecraftContainer->addWidget(minecraftPathChooser);

#ifdef ATW_TEST_PORTABLE
    QVBoxLayout* profileContainer = new QVBoxLayout();
    profileContainer->setSpacing(4);
    profileContainer->addWidget(new QLabel(QStringLiteral("Java Profile")), 0, Qt::AlignLeft);
    javaProfile = new QComboBox();
    javaProfile->addItem(QStringLiteral("Stable G1, recommended"), QStringLiteral("stable-g1"));
    javaProfile->addItem(QStringLiteral("GraalVM experimental"), QStringLiteral("graal-experimental"));
    javaProfile->addItem(QStringLiteral("Low-pause experimental (Java 21+)"), QStringLiteral("low-pause"));
    profileContainer->addWidget(javaProfile);

    QHBoxLayout* largePagesLayout = new QHBoxLayout();
    largePagesLayout->addWidget(new QLabel(QStringLiteral("Use Large Pages")));
    useLargePages = new QCheckBox();
    largePagesLayout->addStretch();
    largePagesLayout->addWidget(useLargePages);

    QHBoxLayout* gpuReminderLayout = new QHBoxLayout();
    gpuReminderLayout->addWidget(new QLabel(QStringLiteral("Discrete GPU Reminder")));
    showGpuReminder = new QCheckBox();
    gpuReminderLayout->addStretch();
    gpuReminderLayout->addWidget(showGpuReminder);
#endif

    QVBoxLayout* jvmArgsGroup = new QVBoxLayout();
    jvmArgsGroup->setSpacing(4);
    jvmArgs = new QPlainTextEdit();
    jvmArgs->setMinimumHeight(126); // Match figma size
    jvmArgs->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    jvmArgsGroup->addWidget(new QLabel(QStringLiteral("Custom JVM Arguments")), 0, Qt::AlignLeft);
    jvmArgsGroup->addWidget(jvmArgs);

    QHBoxLayout* toggleLayout = new QHBoxLayout();
    toggleLayout->addWidget(new QLabel(QStringLiteral("Launcher Open after Launch")));
    closeOnLaunch = new QCheckBox();
    closeOnLaunch->setChecked(!config.closeOnLaunch); // Note: we should probably map this properly
    toggleLayout->addStretch();
    toggleLayout->addWidget(closeOnLaunch);

    mainLayout->addLayout(memorySliderContainer);
    mainLayout->addLayout(minecraftContainer);
    mainLayout->addLayout(jreContainer);
#ifdef ATW_TEST_PORTABLE
    mainLayout->addLayout(profileContainer);
    mainLayout->addLayout(largePagesLayout);
    mainLayout->addLayout(gpuReminderLayout);
#endif
    mainLayout->addLayout(jvmArgsGroup);
    mainLayout->addLayout(toggleLayout);
    mainLayout->addStretch(1); // Push everything to the top

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

QString GamePage::description() {
    return QStringLiteral("Memory, Java, and game directory settings.");
}
