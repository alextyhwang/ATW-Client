#include "modsmodel.h"

#include "core/mods/weavemods.h"

#include <QFileInfo>
#include <QMimeData>


ModsModel::ModsModel(QList<Mod> &mods, QObject *parent) : mods(mods), QAbstractTableModel(parent) {

}

int ModsModel::rowCount(const QModelIndex &parent) const {
    return mods.size();
}

int ModsModel::columnCount(const QModelIndex &parent) const {
    return Column::NUM_COLS;
}

QVariant ModsModel::data(const QModelIndex &index, int role) const {
    Mod mod = mods[index.row()];

    if (index.column() == Column::NAME) {
        switch (role) {
            case Qt::DisplayRole:
            case Qt::EditRole:
                return mod.name;
            case Qt::ToolTipRole:
                return mod.name;
            case Qt::CheckStateRole:
                return mod.enabled ? Qt::Checked : Qt::Unchecked;
        }
    }
    return {};
}

bool ModsModel::setData(const QModelIndex &index, const QVariant &value, int role) {
    Mod& mod = mods[index.row()];

    if(index.column() == Column::NAME) {
        if(role == Qt::CheckStateRole) {
            return Atw::Core::WeaveMods::setEnabled(mod, !mod.enabled);
        }
        if (role == Qt::EditRole) {
            if (value.toString() != mod.name){
            return Atw::Core::WeaveMods::rename(mod, value.toString());
            }
        }
    }
    return false;
}

QVariant ModsModel::headerData(int section, Qt::Orientation orientation, int role) const {
    if (role == Qt::DisplayRole && orientation == Qt::Horizontal) {
        switch (section) {
            case Column::NAME:
                return QString("Name");
        }
    }
    return {};
}

Qt::ItemFlags ModsModel::flags(const QModelIndex &index) const {
    auto flags = QAbstractTableModel::flags(index);

    if(index.column() == Column::NAME) {
        flags |= Qt::ItemIsUserCheckable; 
        flags |= Qt::ItemIsEditable;
    }

    flags |= Qt::ItemIsDropEnabled;

    return flags;
}

bool ModsModel::removeRows(int row, int count, const QModelIndex &parent) {
    beginRemoveRows(QModelIndex(), row, row + count - 1);

    for (int i = 0; i < count; i++) {
        Atw::Core::WeaveMods::remove(mods.at(row));
        mods.removeAt(row);
    }

    endRemoveRows();
    return true;
}


// Stole this from QStringListModel
bool ModsModel::moveRows(const QModelIndex &sourceParent, int sourceRow, int count, const QModelIndex &destinationParent,
                      int destinationChild) {
    beginMoveRows(QModelIndex(), sourceRow, sourceRow + count - 1, QModelIndex(), destinationChild);

    /*
    QList::move assumes that the second argument is the index where the item will end up to
    i.e. the valid range for that argument is from 0 to QList::size()-1
    QAbstractItemModel::moveRows when source and destinations have the same parent assumes that
    the item will end up being in the row BEFORE the one indicated by destinationChild
    i.e. the valid range for that argument is from 1 to QList::size()
    For this reason we remove 1 from destinationChild when using it inside QList
    */
    destinationChild--;
    const int fromRow = destinationChild < sourceRow ? (sourceRow + count - 1) : sourceRow;
    while (count--)
        mods.move(fromRow, destinationChild);

    endMoveRows();
    return true;
}

void ModsModel::addMod(const QFileInfo &modFileInfo) {
    if(containsMod(modFileInfo.fileName().remove(".jar.disabled").remove(".jar"))) return;

    Mod installed(QStringLiteral(""));
    if (!Atw::Core::WeaveMods::install(modFileInfo, &installed))
        return;

    const int row = mods.size();
    beginInsertRows(QModelIndex(), row, row);
    mods.append(installed);
    endInsertRows();
}

Qt::DropActions ModsModel::supportedDropActions() const {
    return Qt::CopyAction;
}

bool ModsModel::canDropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column,
                                  const QModelIndex &parent) const {
    return data->hasUrls();
}

bool ModsModel::dropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column,
                               const QModelIndex &parent) {
    for(const QUrl& url : data->urls()){
        if(!url.isLocalFile()){
            continue;
        }

        QFileInfo fileInfo = QFileInfo(url.toLocalFile());

        if(fileInfo.completeSuffix().endsWith(".jar") || fileInfo.completeSuffix().endsWith(".jar.disabled")) {
            addMod(fileInfo);
        }
    }

    return true;
}

bool ModsModel::containsMod(const QString &name) const {
    return Atw::Core::WeaveMods::contains(mods, name);
}
