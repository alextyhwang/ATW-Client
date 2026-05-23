//
// Custom title bar for frameless window
//

#include "customtitlebar.h"

#include <QHBoxLayout>
#include <QMouseEvent>
#include <QApplication>
#include <QWidget>

CustomTitleBar::CustomTitleBar(QWidget* parent) : QWidget(parent) {
    setFixedHeight(116);
    setObjectName("titleBar");
    setStyleSheet(
        "QWidget#titleBar { background-color: #1F1F20; }"
        "QLabel#atwTitle { color: #2D2D2F; background-color: #2B2B2D; font-size: 24px; padding: 7px 12px; }"
        "QPushButton#closeButton { background-color: #2B2B2D; border: none; color: #1F1F20; "
        "  min-width: 39px; min-height: 39px; max-width: 39px; max-height: 39px; font-size: 22px; padding: 0; }"
        "QPushButton#closeButton:hover { background-color: #3A3A3C; color: #929292; }"
    );

    QHBoxLayout* layout = new QHBoxLayout(this);
    layout->setContentsMargins(88, 72, 42, 0);
    layout->setSpacing(0);

    titleLabel = new QLabel(QStringLiteral("ATW"));
    titleLabel->setObjectName("atwTitle");
    layout->addWidget(titleLabel);

    layout->addStretch();

    closeButton = new QPushButton(QStringLiteral("×"));
    closeButton->setCursor(Qt::PointingHandCursor);
    closeButton->setObjectName("closeButton");
    connect(closeButton, &QPushButton::clicked, [this]() {
        if (QWidget* w = window())
            w->close();
    });
    layout->addWidget(closeButton);
}

void CustomTitleBar::mousePressEvent(QMouseEvent* event) {
    if (event->button() == Qt::LeftButton) {
        dragPosition = event->globalPosition().toPoint() - window()->frameGeometry().topLeft();
        event->accept();
    }
}

void CustomTitleBar::mouseMoveEvent(QMouseEvent* event) {
    if (event->buttons() & Qt::LeftButton) {
        window()->move(event->globalPosition().toPoint() - dragPosition);
        event->accept();
    }
}

void CustomTitleBar::mouseDoubleClickEvent(QMouseEvent* event) {
    Q_UNUSED(event);
    // Could toggle maximize if desired
}
