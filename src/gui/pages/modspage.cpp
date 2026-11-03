#include "modspage.h"
#include "util/fs.h"
#include "gui/style/atwtheme.h"
#include "gui/widgets/pixeltoggle.h"
#include "gui/widgets/widgetutils.h"

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
    mainLayout->setSpacing(0);
    mainLayout->setContentsMargins(0, 0, 0, 0);

    // --- Weave Mods Section ---
    const int headerHeight = AtwTheme::Metrics::LabelBlock;
    QHBoxLayout* weaveHeaderLayout = new QHBoxLayout();
    weaveHeaderLayout->setSpacing(9);
    QLabel* weaveLabel = WidgetUtils::rowLabel(QStringLiteral("Weave Mods"), headerHeight);
    QLabel* weaveToggleLabel = WidgetUtils::fieldValue(QStringLiteral("Enable Weave"));
    weaveToggleLabel->setFixedHeight(headerHeight);
    weaveToggleLabel->setContentsMargins(0, (headerHeight - 12) / 2, 0, 0);
    useWeave = new PixelToggle();
    useWeave->setAccessibleName(QStringLiteral("Enable Weave"));
    weaveHeaderLayout->addWidget(weaveLabel);
    weaveHeaderLayout->addStretch();
    weaveHeaderLayout->addWidget(weaveToggleLabel);
    weaveHeaderLayout->addWidget(useWeave, 0, Qt::AlignVCenter);

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

    mods->setAccessibleName(QStringLiteral("Weave Mods"));
    WidgetUtils::installEmptyHint(mods, QStringLiteral("No Weave mods. Drop a .jar here or choose Add."));
    QHBoxLayout* modsButtons = WidgetUtils::buttonRow({modsAdd, modsRemove, modsMoveUp, modsMoveDown}, {openFolder});

    // --- Java Agents Section ---
    QLabel* agentsLabel = WidgetUtils::rowLabel(QStringLiteral("Java Agents"), headerHeight);

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

    agents->setAccessibleName(QStringLiteral("Java Agents"));
    WidgetUtils::installEmptyHint(agents, QStringLiteral("No Java agents. Drop a .jar here or choose Add."));
    QHBoxLayout* agentsButtons = WidgetUtils::buttonRow({agentsAdd, agentsRemove, agentsMoveUp, agentsMoveDown});

    mods->setMinimumHeight(72);
    agents->setMinimumHeight(72);
    mainLayout->addLayout(weaveHeaderLayout);
    mainLayout->addSpacing(4);
    mainLayout->addWidget(mods, 3);
    mainLayout->addSpacing(6);
    mainLayout->addLayout(modsButtons);
    mainLayout->addSpacing(AtwTheme::Metrics::GroupGap);
    mainLayout->addWidget(agentsLabel);
    mainLayout->addSpacing(4);
    mainLayout->addWidget(agents, 2);
    mainLayout->addSpacing(6);
    mainLayout->addLayout(agentsButtons);

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
