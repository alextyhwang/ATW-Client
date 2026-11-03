//
// Created by nils on 12/3/21.
//

#ifndef LUNAR_CLIENT_QT_WIDGETUTILS_H
#define LUNAR_CLIENT_QT_WIDGETUTILS_H

#include <QVBoxLayout>
#include <QCheckBox>

class QAbstractItemView;
class QHBoxLayout;
class QLabel;
class QPushButton;

namespace WidgetUtils {
    QVBoxLayout* createOptional(QCheckBox* checkBox, QWidget* item);

    QWidget* layoutToWidget(QLayout* layout);

    // Figma field label: 18 px #929292 in a 24 px block (15 px box + 9 px gap),
    // caps 2 px below the block top.
    QLabel* fieldLabel(const QString& text);
    // Right-aligned 16 px #BFBFBF value beside a field label.
    QLabel* fieldValue(const QString& text = QString());
    // Label whose caps are centred on a control of the given row height.
    QLabel* rowLabel(const QString& text, int rowHeight);
    // Field label above a control, with no gap (the label block holds it).
    QVBoxLayout* fieldGroup(QLabel* label, QWidget* control);
    // Row of pixel buttons; trailing buttons are pushed to the right.
    QHBoxLayout* buttonRow(const QList<QPushButton*>& leading, const QList<QPushButton*>& trailing = {});
    // Centred hint shown inside an item view while it has no rows.
    void installEmptyHint(QAbstractItemView* view, const QString& text);
}


#endif //LUNAR_CLIENT_QT_WIDGETUTILS_H
