//
// Created by nils on 12/3/21.
//

#include "widgetutils.h"

#include <QAbstractItemView>
#include <QEvent>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>

#include "gui/style/atwtheme.h"

namespace {
// Keeps an empty-state hint centred over a view's viewport.
class EmptyHint : public QLabel {
public:
    EmptyHint(const QString& text, QAbstractItemView* view) : QLabel(text, view->viewport()), view(view) {
        setObjectName(QStringLiteral("listHint"));
        setAlignment(Qt::AlignCenter);
        setWordWrap(true);
        setAttribute(Qt::WA_TransparentForMouseEvents);
        view->viewport()->installEventFilter(this);
        if (QAbstractItemModel* model = view->model()) {
            for (auto signal : {&QAbstractItemModel::rowsInserted, &QAbstractItemModel::rowsRemoved})
                connect(model, signal, this, [this]() { refresh(); });
            connect(model, &QAbstractItemModel::modelReset, this, [this]() { refresh(); });
            connect(model, &QAbstractItemModel::layoutChanged, this, [this]() { refresh(); });
        }
        refresh();
    }

protected:
    bool eventFilter(QObject* watched, QEvent* event) override {
        if (watched == view->viewport() && event->type() == QEvent::Resize)
            refresh();
        return QLabel::eventFilter(watched, event);
    }

private:
    void refresh() {
        setGeometry(view->viewport()->rect().adjusted(16, 8, -16, -8));
        setVisible(view->model() && view->model()->rowCount() == 0);
    }

    QAbstractItemView* view;
};
}

QVBoxLayout *WidgetUtils::createOptional(QCheckBox *checkBox, QWidget *item) {
    QVBoxLayout* layout = new QVBoxLayout();
    layout->setSpacing(6);

    QObject::connect(checkBox, &QCheckBox::toggled, item, &QWidget::setEnabled);
    item->setDisabled(true);

    layout->addWidget(checkBox, 0, Qt::AlignHCenter);
    layout->addWidget(item);

    return layout;
}

QWidget *WidgetUtils::layoutToWidget(QLayout *layout) {
    QWidget* widget = new QWidget;
    widget->setLayout(layout);
    return widget;
}

QLabel* WidgetUtils::fieldLabel(const QString& text) {
    QLabel* label = new QLabel(text);
    label->setObjectName(QStringLiteral("fieldLabel"));
    label->setAlignment(Qt::AlignLeft | Qt::AlignTop);
    label->setContentsMargins(0, 2, 0, 0);
    label->setFixedHeight(AtwTheme::Metrics::LabelBlock);
    return label;
}

QLabel* WidgetUtils::fieldValue(const QString& text) {
    QLabel* label = new QLabel(text);
    label->setObjectName(QStringLiteral("fieldValue"));
    label->setAlignment(Qt::AlignRight | Qt::AlignTop);
    label->setContentsMargins(0, 1, 0, 0);
    label->setFixedHeight(AtwTheme::Metrics::LabelBlock);
    return label;
}

QLabel* WidgetUtils::rowLabel(const QString& text, int rowHeight) {
    QLabel* label = new QLabel(text);
    label->setObjectName(QStringLiteral("rowLabel"));
    label->setAlignment(Qt::AlignLeft | Qt::AlignTop);
    // Caps are the full ascent of this font, so the cap top is the line top.
    const int capHeight = qRound(18 * 0.75);
    label->setContentsMargins(0, qMax(0, qMin((rowHeight - capHeight) / 2, rowHeight - 18)), 0, 0);
    label->setFixedHeight(rowHeight);
    return label;
}

QVBoxLayout* WidgetUtils::fieldGroup(QLabel* label, QWidget* control) {
    QVBoxLayout* layout = new QVBoxLayout();
    layout->setSpacing(0);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(label);
    layout->addWidget(control);
    return layout;
}

QHBoxLayout* WidgetUtils::buttonRow(const QList<QPushButton*>& leading, const QList<QPushButton*>& trailing) {
    QHBoxLayout* layout = new QHBoxLayout();
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(6);
    for (QPushButton* button : leading) {
        button->setCursor(Qt::PointingHandCursor);
        layout->addWidget(button);
    }
    layout->addStretch(1);
    for (QPushButton* button : trailing) {
        button->setCursor(Qt::PointingHandCursor);
        layout->addWidget(button);
    }
    return layout;
}

void WidgetUtils::installEmptyHint(QAbstractItemView* view, const QString& text) {
    new EmptyHint(text, view);
}
