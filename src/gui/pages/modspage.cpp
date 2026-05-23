#include "modspage.h"
#include "util/fs.h"

#include <QDir>
#include <QVBoxLayout>
#include <QPushButton>
#include <QLabel>
#include <QFileDialog>
#include <QJsonArray>
#include <QJsonObject>
#include <QProxyStyle>
#include <QHeaderView>
#include <QItemSelectionModel>
#include <QDesktopServices>
#include <QUrl>

ModsPage::ModsPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    QVBoxLayout* mainLayout = new QVBoxLayout(this);
    mainLayout->setSpacing(12);
    mainLayout->setContentsMargins(0, 0, 0, 0);

    // --- Weave Mods Section ---
    QHBoxLayout* weaveHeaderLayout = new QHBoxLayout();
    QLabel* weaveLabel = new QLabel(QStringLiteral("Weave Mods"));
    useWeave = new QCheckBox(QStringLiteral("Enable Weave"));
    weaveHeaderLayout->addWidget(weaveLabel);
    weaveHeaderLayout->addStretch();
    weaveHeaderLayout->addWidget(useWeave);

    mods = new ModsView(this);
    mods->setModel((modsModel = new ModsModel(config.mods, this)));

    modsAdd = new QPushButton(QStringLiteral("Add"));
    modsRemove = new QPushButton(QStringLiteral("Remove"));
    modsMoveUp = new QPushButton(QStringLiteral("Move Up"));
    modsMoveDown = new QPushButton(QStringLiteral("Move Down"));
    openFolder = new QPushButton(QStringLiteral("Open Folder"));

    connect(mods->selectionModel(), &QItemSelectionModel::selectionChanged, this, &ModsPage::onModsSelect);

    modsRemove->setDisabled(true);
    modsMoveUp->setDisabled(true);
    modsMoveDown->setDisabled(true);

    connect(modsAdd, &QPushButton::clicked, [this]() {
        QStringList files = QFileDialog::getOpenFileNames(
            nullptr, QStringLiteral("Open Mods Jar"), {}, QStringLiteral("Weave Mod (*.jar)")
        );
        for (const QString& str : files) {
            modsModel->addMod(QFileInfo(str));
        }
        mods->selectRow(modsModel->rowCount(QModelIndex()) - 1);
    });

    connect(modsRemove, &QPushButton::clicked, [this]() {
        for (const QModelIndex& item : mods->selectionModel()->selectedRows()) {
            modsModel->removeRow(item.row());
        }
    });

    connect(modsMoveUp, &QPushButton::clicked, [this]() {
        QModelIndexList selected = mods->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow > 0) {
                modsModel->moveRow(QModelIndex(), currentRow - 1, QModelIndex(), currentRow + 1);
            }
        }
    });

    connect(modsMoveDown, &QPushButton::clicked, [this]() {
        QModelIndexList selected = mods->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow < modsModel->rowCount(QModelIndex()) - 1) {
                modsModel->moveRow(QModelIndex(), currentRow, QModelIndex(), currentRow + 2);
            }
        }
    });

    connect(openFolder, &QPushButton::clicked, []() {
        QDesktopServices::openUrl(QUrl::fromLocalFile(FS::getWeaveModsDirectory()));
    });

    QGridLayout* modsContainer = new QGridLayout();
    modsContainer->setSpacing(6);
    modsContainer->addWidget(mods, 0, 0, 6, 1);
    modsContainer->addWidget(modsAdd, 0, 1);
    modsContainer->addWidget(modsRemove, 1, 1);
    modsContainer->addWidget(modsMoveUp, 2, 1);
    modsContainer->addWidget(modsMoveDown, 3, 1);
    modsContainer->addWidget(openFolder, 4, 1);

    // --- Java Agents Section ---
    QLabel* agentsLabel = new QLabel(QStringLiteral("Java Agents"));

    agents = new AgentsView(this);
    agents->setModel((agentsModel = new AgentsModel(config.agents, this)));

    agentsAdd = new QPushButton(QStringLiteral("Add"));
    agentsRemove = new QPushButton(QStringLiteral("Remove"));
    agentsMoveUp = new QPushButton(QStringLiteral("Move Up"));
    agentsMoveDown = new QPushButton(QStringLiteral("Move Down"));

    connect(agents->selectionModel(), &QItemSelectionModel::selectionChanged, this, &ModsPage::onAgentsSelect);

    agentsRemove->setDisabled(true);
    agentsMoveUp->setDisabled(true);
    agentsMoveDown->setDisabled(true);

    connect(agentsAdd, &QPushButton::clicked, [this]() {
        QStringList files = QFileDialog::getOpenFileNames(
                nullptr, QStringLiteral("Open Agent Jar"), {}, QStringLiteral("Java Agent (*.jar)")
        );
        for(const QString& str : files){
            agentsModel->addAgent(str, {});
        }
        agents->selectRow(agentsModel->rowCount(QModelIndex()) - 1);
    });

    connect(agentsRemove, &QPushButton::clicked, [this]() {
        for(const QModelIndex& item : agents->selectionModel()->selectedRows()){
            agentsModel->removeRow(item.row());
        }
    });

    connect(agentsMoveUp, &QPushButton::clicked, [this]() {
        QModelIndexList selected = agents->selectionModel()->selectedRows();
        if(!selected.isEmpty()){
            int currentRow = selected[0].row();
            if(currentRow > 0){
                agentsModel->moveRow(QModelIndex(), currentRow - 1, QModelIndex(), currentRow + 1);
            }
        }
    });

    connect(agentsMoveDown, &QPushButton::clicked, [this]() {
        QModelIndexList selected = agents->selectionModel()->selectedRows();
        if(!selected.isEmpty()){
            int currentRow = selected[0].row();
            if(currentRow < agentsModel->rowCount(QModelIndex()) - 1){
                agentsModel->moveRow(QModelIndex(), currentRow, QModelIndex(), currentRow + 2);
            }
        }
    });

    QGridLayout* agentsContainer = new QGridLayout();
    agentsContainer->setSpacing(6);
    agentsContainer->addWidget(agents, 0, 0, 5, 1);
    agentsContainer->addWidget(agentsAdd, 0, 1);
    agentsContainer->addWidget(agentsRemove, 1, 1);
    agentsContainer->addWidget(agentsMoveUp, 2, 1);
    agentsContainer->addWidget(agentsMoveDown, 3, 1);

    mainLayout->addLayout(weaveHeaderLayout);
    mainLayout->addLayout(modsContainer, 1);
    mainLayout->addWidget(agentsLabel);
    mainLayout->addLayout(agentsContainer, 1);

    setLayout(mainLayout);
}

QString ModsPage::title() {
    return QStringLiteral("Mods");
}

QIcon ModsPage::icon() {
    return QIcon(":/res/icons/mod.svg");
}

void ModsPage::apply() {
    config.useWeave = useWeave->isChecked();
}

void ModsPage::load() {
    useWeave->setChecked(config.useWeave);
}

void ModsPage::onModsSelect(const QItemSelection& selected, const QItemSelection& deselected) {
    QModelIndexList selectedRows = qobject_cast<QItemSelectionModel*>(sender())->selectedRows();

    if (selectedRows.isEmpty()) {
        modsRemove->setDisabled(true);
        modsMoveUp->setDisabled(true);
        modsMoveDown->setDisabled(true);
    }
    else {
        modsRemove->setEnabled(true);
        modsMoveUp->setEnabled(true);
        modsMoveDown->setEnabled(true);
    }
}

void ModsPage::onAgentsSelect(const QItemSelection& selected, const QItemSelection& deselected) {
    QModelIndexList selectedRows = qobject_cast<QItemSelectionModel*>(sender())->selectedRows();

    if (selectedRows.isEmpty()) {
        agentsRemove->setDisabled(true);
        agentsMoveUp->setDisabled(true);
        agentsMoveDown->setDisabled(true);
    }
    else {
        agentsRemove->setEnabled(true);
        agentsMoveUp->setEnabled(true);
        agentsMoveDown->setEnabled(true);
    }
}

QString ModsPage::description() {
    return "List of Weave mods and Java agents.";
}
