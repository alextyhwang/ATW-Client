//
// Other tab - contains Agents, Helpers, Logs (pages without specific tab SVGs)
//

#ifndef OTHERPAGE_H
#define OTHERPAGE_H

#include <QTabWidget>

#include "configurationpage.h"
#include "helperspage.h"
#include "logspage.h"

class OtherPage : public ConfigurationPage {
    Q_OBJECT
public:
    explicit OtherPage(Config& config, QWidget* parent = nullptr);

    QString title() override { return QStringLiteral("Other"); }
    QString description() override { return QStringLiteral("Helpers, and logs."); }
    QIcon icon() override { return QIcon(":/res/icons/cog.svg"); }
    void apply() override;
    void load() override;

    void showLogsTab();

    LogsPage* logsPage;

private:
    QTabWidget* subTabs;
    HelpersPage* helpersPage;
};

#endif // OTHERPAGE_H
