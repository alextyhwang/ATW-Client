//
// Custom tab bar with tab-specific SVGs
//

#include "customtabbar.h"

#include <QPainter>
#include <QStyle>
#include <QStyleOptionTab>

CustomTabBar::CustomTabBar(QWidget* parent) : QTabBar(parent) {
    setExpanding(false);
    setAutoFillBackground(false);
}

QSize CustomTabBar::tabSizeHint(int index) const {
    Q_UNUSED(index);
    return QSize(120, 48); // Match figma and stylesheet size
}

void CustomTabBar::setTabType(int index, const QString& type) {
    while (tabTypes.size() <= index)
        tabTypes.append("other");
    tabTypes[index] = type;
}

void CustomTabBar::paintEvent(QPaintEvent* event) {
    Q_UNUSED(event);
    QPainter p(this);
    for (int i = 0; i < count(); i++) {
        QRect rect = tabRect(i);
        bool selected = (currentIndex() == i);
        QString type = (i < tabTypes.size()) ? tabTypes[i] : QStringLiteral("other");
        QString svgPath = QStringLiteral(":/res/tabs/%1%2.png")
            .arg(type)
            .arg(selected ? "" : "_deselected");
        QPixmap pix(svgPath);
        if (!pix.isNull()) {
            p.drawPixmap(rect, pix.scaled(rect.size(), Qt::IgnoreAspectRatio, Qt::SmoothTransformation));
        }
        // Tab SVGs already contain the text - do not overlay
    }
}
