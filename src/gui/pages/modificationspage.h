#ifndef LUNAR_CLIENT_QT_MODIFICATIONSPAGE_H
#define LUNAR_CLIENT_QT_MODIFICATIONSPAGE_H

#include <QTabWidget>
#include <QCheckBox>
#include <QPushButton>
#include <QTableView>
#include <QListWidget>

#include "configurationpage.h"
#include "gui/agents/agentsmodel.h"
#include "gui/agents/agentsview.h"
#include "gui/mods/modsmodel.h"
#include "gui/mods/modsview.h"

class ModificationsPage : public ConfigurationPage {
    Q_OBJECT
public:
    explicit ModificationsPage(Config& config, QWidget* parent = nullptr);

    QString title() override;
    QString description() override;
    QIcon icon() override;
    void apply() override;
    void load() override;

private slots:
    void onAgentsSelect(const QItemSelection& selected, const QItemSelection& deselected);
    void onModsSelect(const QItemSelection& selected, const QItemSelection& deselected);

private:
    QTabWidget* tabWidget;

    // Agents tab
    AgentsModel* agentsModel;
    AgentsView* agentsView;
    QPushButton* agentsAdd;
    QPushButton* agentsRemove;
    QPushButton* agentsMoveUp;
    QPushButton* agentsMoveDown;

    // Mods tab
    ModsModel* modsModel;
    ModsView* modsView;
    QCheckBox* useWeave;
    QPushButton* modsAdd;
    QPushButton* modsRemove;
    QPushButton* modsMoveUp;
    QPushButton* modsMoveDown;
    QPushButton* modsOpenFolder;
};

#endif // LUNAR_CLIENT_QT_MODIFICATIONSPAGE_H
