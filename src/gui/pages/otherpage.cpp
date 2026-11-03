//
// Other tab - contains Agents, Helpers, Logs
//

#include "otherpage.h"

#include <QTabBar>
#include <QVBoxLayout>

OtherPage::OtherPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    subTabs = new QTabWidget(this);
    subTabs->setObjectName(QStringLiteral("subTabs"));
    subTabs->setDocumentMode(true);
    subTabs->tabBar()->setUsesScrollButtons(false);
    subTabs->tabBar()->setDrawBase(false);
    subTabs->tabBar()->setCursor(Qt::PointingHandCursor);
    helpersPage = new HelpersPage(config, this);
    logsPage = new LogsPage(config, this);

    subTabs->addTab(helpersPage, helpersPage->title());
    subTabs->addTab(logsPage, logsPage->title());

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(subTabs);
}

void OtherPage::apply() {
    helpersPage->apply();
    logsPage->apply();
}

void OtherPage::load() {
    helpersPage->load();
    logsPage->load();
}

void OtherPage::showLogsTab() {
    subTabs->setCurrentIndex(1);  // Logs is now 2nd tab
}
