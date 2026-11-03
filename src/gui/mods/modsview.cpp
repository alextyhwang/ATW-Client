#include "modsview.h"

#include <QHeaderView>
#include <QMouseEvent>

#include "gui/style/removeoutline.h"

ModsView::ModsView(QWidget *parent) : QTableView(parent) {
    setSelectionBehavior(QTableView::SelectRows);
    setSelectionMode(QTableView::SingleSelection);
    horizontalHeader()->setHighlightSections(false);
    setAlternatingRowColors(true);
    verticalHeader()->hide();
    auto style = new RemoveOutline;
    style->setParent(this);
    setStyle(style);
    horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);
    setAlternatingRowColors(true);

    // Figma list styling: 24 px rows, no grid, frameless (colours in the app stylesheet).
    verticalHeader()->setDefaultSectionSize(24);
    verticalHeader()->setMinimumSectionSize(20);
    setShowGrid(false);
    setFrameShape(QFrame::NoFrame);
    setWordWrap(false);
    horizontalHeader()->setDefaultAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    horizontalHeader()->hide();   // single "Name" column; the section title names it

    setAcceptDrops(true);
    setDropIndicatorShown(true);
    setDragDropMode(QAbstractItemView::DropOnly);
}

void ModsView::keyPressEvent(QKeyEvent *event) {
    QAbstractItemView::keyPressEvent(event);

    if(!event->isAccepted() && event->key() == Qt::Key_Delete){
        for(const QModelIndex& idx : selectionModel()->selectedRows()){
            model()->removeRow(idx.row());
        }
    }
}
