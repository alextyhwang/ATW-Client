#ifndef LUNAR_CLIENT_QT_MODSPAGE_H
#define LUNAR_CLIENT_QT_MODSPAGE_H

#include <QListWidget>
#include <QTableView>
#include <QPushButton>
#include <QCheckBox>

#include "configurationpage.h"
#include "gui/mods/modsmodel.h"
#include "gui/mods/modsview.h"
#include "gui/agents/agentsmodel.h"
#include "gui/agents/agentsview.h"

class ModsPage : public ConfigurationPage {
    Q_OBJECT
public:
    explicit ModsPage(Config& config, QWidget* parent = nullptr);

    QString title() override;

    QString description() override;

    QIcon icon() override;

    void apply() override;
    void load() override;
private slots:
    void onModsSelect(const QItemSelection& selected, const QItemSelection& deselected);
    void onAgentsSelect(const QItemSelection& selected, const QItemSelection& deselected);

private:
    ModsModel* modsModel;
    ModsView* mods;
    QCheckBox* useWeave;

    QPushButton* modsAdd;
    QPushButton* modsRemove;
    QPushButton* modsMoveUp;
    QPushButton* modsMoveDown;
    QPushButton* openFolder;

    AgentsModel* agentsModel;
    AgentsView* agents;

    QPushButton* agentsAdd;
    QPushButton* agentsRemove;
    QPushButton* agentsMoveUp;
    QPushButton* agentsMoveDown;
};

#endif //LUNAR_CLIENT_QT_MODSPAGE_H
