#include "modificationspage.h"

#include <QVBoxLayout>
#include <QGridLayout>
#include <QPushButton>
#include <QFileDialog>
#include <QItemSelectionModel>
#include <QDesktopServices>
#include <QUrl>

#include "util/fs.h"

ModificationsPage::ModificationsPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    QVBoxLayout* mainLayout = new QVBoxLayout();
    mainLayout->setSpacing(10);

    tabWidget = new QTabWidget(this);

    // ===== Agents Tab =====
    QWidget* agentsTab = new QWidget();
    QVBoxLayout* agentsLayout = new QVBoxLayout(agentsTab);
    agentsLayout->setSpacing(10);

    agentsView = new AgentsView(this);
    agentsView->setModel((agentsModel = new AgentsModel(config.agents, this)));

    agentsAdd = new QPushButton(QStringLiteral("Add"));
    agentsRemove = new QPushButton(QStringLiteral("Remove"));
    agentsMoveUp = new QPushButton(QStringLiteral("Move Up"));
    agentsMoveDown = new QPushButton(QStringLiteral("Move Down"));

    connect(agentsView->selectionModel(), &QItemSelectionModel::selectionChanged, this, &ModificationsPage::onAgentsSelect);

    agentsRemove->setDisabled(true);
    agentsMoveUp->setDisabled(true);
    agentsMoveDown->setDisabled(true);

    connect(agentsAdd, &QPushButton::clicked, [this]() {
        QStringList files = QFileDialog::getOpenFileNames(
                nullptr,
                QStringLiteral("Open Agent Jar"),
                {},
                QStringLiteral("Java Agent (*.jar)")
        );
        for(const QString& str : files){
            agentsModel->addAgent(str, {});
        }
        agentsView->selectRow(agentsModel->rowCount(QModelIndex()) - 1);
    });

    connect(agentsRemove, &QPushButton::clicked, [this]() {
        for(const QModelIndex &item : agentsView->selectionModel()->selectedRows()) {
            agentsModel->removeRow(item.row());
        }
    });

    connect(agentsMoveUp, &QPushButton::clicked, [this]() {
        QModelIndexList selected = agentsView->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow > 0) {
                agentsModel->moveRow(QModelIndex(), currentRow - 1, QModelIndex(), currentRow + 1);
            }
        }
    });

    connect(agentsMoveDown, &QPushButton::clicked, [this]() {
        QModelIndexList selected = agentsView->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow < agentsModel->rowCount(QModelIndex()) - 1) {
                agentsModel->moveRow(QModelIndex(), currentRow, QModelIndex(), currentRow + 2);
            }
        }
    });

    QGridLayout* agentsContainer = new QGridLayout();
    agentsContainer->setSpacing(6);
    agentsContainer->addWidget(agentsView, 0, 0, 5, 1);
    agentsContainer->addWidget(agentsAdd, 0, 1);
    agentsContainer->addWidget(agentsRemove, 1, 1);
    agentsContainer->addWidget(agentsMoveUp, 2, 1);
    agentsContainer->addWidget(agentsMoveDown, 3, 1);
    agentsLayout->addLayout(agentsContainer);

    tabWidget->addTab(agentsTab, QStringLiteral("Agents"));

    // ===== Mods Tab =====
    QWidget* modsTab = new QWidget();
    QVBoxLayout* modsLayout = new QVBoxLayout(modsTab);
    modsLayout->setSpacing(10);

    useWeave = new QCheckBox(QStringLiteral("Enable Weave"));
    modsLayout->addWidget(useWeave, 0, Qt::AlignCenter);

    modsView = new ModsView(this);
    modsView->setModel((modsModel = new ModsModel(config.mods, this)));

    modsAdd = new QPushButton(QStringLiteral("Add"));
    modsRemove = new QPushButton(QStringLiteral("Remove"));
    modsMoveUp = new QPushButton(QStringLiteral("Move Up"));
    modsMoveDown = new QPushButton(QStringLiteral("Move Down"));
    modsOpenFolder = new QPushButton(QStringLiteral("Open Folder"));

    connect(modsView->selectionModel(), &QItemSelectionModel::selectionChanged, this, &ModificationsPage::onModsSelect);

    modsRemove->setDisabled(true);
    modsMoveUp->setDisabled(true);
    modsMoveDown->setDisabled(true);

    connect(modsAdd, &QPushButton::clicked, [this]() {
        QStringList files = QFileDialog::getOpenFileNames(
            nullptr,
            QStringLiteral("Open Mods Jar"),
            {},
            QStringLiteral("Weave Mod (*.jar)")
        );
        for (const QString& str : files) {
            modsModel->addMod(QFileInfo(str));
        }
        modsView->selectRow(modsModel->rowCount(QModelIndex()) - 1);
    });

    connect(modsRemove, &QPushButton::clicked, [this]() {
        for (const QModelIndex& item : modsView->selectionModel()->selectedRows()) {
            modsModel->removeRow(item.row());
        }
    });

    connect(modsMoveUp, &QPushButton::clicked, [this]() {
        QModelIndexList selected = modsView->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow > 0) {
                modsModel->moveRow(QModelIndex(), currentRow - 1, QModelIndex(), currentRow + 1);
            }
        }
    });

    connect(modsMoveDown, &QPushButton::clicked, [this]() {
        QModelIndexList selected = modsView->selectionModel()->selectedRows();
        if (!selected.isEmpty()) {
            int currentRow = selected[0].row();
            if (currentRow < modsModel->rowCount(QModelIndex()) - 1) {
                modsModel->moveRow(QModelIndex(), currentRow, QModelIndex(), currentRow + 2);
            }
        }
    });

    connect(modsOpenFolder, &QPushButton::clicked, []() {
        QDesktopServices::openUrl(QUrl::fromLocalFile(FS::getWeaveModsDirectory()));
    });

    QGridLayout* modsContainer = new QGridLayout();
    modsContainer->setSpacing(6);
    modsContainer->addWidget(modsView, 0, 0, 6, 1);
    modsContainer->addWidget(modsAdd, 0, 1);
    modsContainer->addWidget(modsRemove, 1, 1);
    modsContainer->addWidget(modsMoveUp, 2, 1);
    modsContainer->addWidget(modsMoveDown, 3, 1);
    modsContainer->addWidget(modsOpenFolder, 4, 1);
    modsLayout->addLayout(modsContainer);

    tabWidget->addTab(modsTab, QStringLiteral("Mods"));

    mainLayout->addWidget(tabWidget);
    setLayout(mainLayout);
}

QString ModificationsPage::title() {
    return QStringLiteral("Mods");
}

QIcon ModificationsPage::icon() {
    return QIcon();
}

void ModificationsPage::apply() {
    config.useWeave = useWeave->isChecked();
}

void ModificationsPage::load() {
    useWeave->setChecked(config.useWeave);
}

void ModificationsPage::onAgentsSelect(const QItemSelection& selected, const QItemSelection& deselected) {
    QModelIndexList selectedRows = qobject_cast<QItemSelectionModel*>(sender())->selectedRows();
    if (selectedRows.isEmpty()) {
        agentsRemove->setDisabled(true);
        agentsMoveUp->setDisabled(true);
        agentsMoveDown->setDisabled(true);
    } else {
        agentsRemove->setEnabled(true);
        agentsMoveUp->setEnabled(true);
        agentsMoveDown->setEnabled(true);
    }
}

void ModificationsPage::onModsSelect(const QItemSelection& selected, const QItemSelection& deselected) {
    QModelIndexList selectedRows = qobject_cast<QItemSelectionModel*>(sender())->selectedRows();
    if (selectedRows.isEmpty()) {
        modsRemove->setDisabled(true);
        modsMoveUp->setDisabled(true);
        modsMoveDown->setDisabled(true);
    } else {
        modsRemove->setEnabled(true);
        modsMoveUp->setEnabled(true);
        modsMoveDown->setEnabled(true);
    }
}

QString ModificationsPage::description() {
    return QStringLiteral("Manage agents and Weave mods.");
}
