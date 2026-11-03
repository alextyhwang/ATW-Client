//
// Main tab strip. See customtabbar.h.
//

#include "customtabbar.h"

#include <QEvent>
#include <QFocusEvent>
#include <QHoverEvent>
#include <QMouseEvent>
#include <QPainter>

#include "gui/style/atwtheme.h"

using namespace AtwTheme;

namespace {
constexpr int kMinFaceWidth = 92;
constexpr int kBarHeight = Metrics::TabFaceHeight + Metrics::TabEdge;   // 45 face + 4 edge
constexpr int kCaptionPx = 24;
}

CustomTabBar::CustomTabBar(QWidget* parent) : QTabBar(parent) {
    setObjectName(QStringLiteral("mainTabBar"));
    setExpanding(true);
    setDrawBase(false);
    setUsesScrollButtons(false);
    setElideMode(Qt::ElideNone);
    setDocumentMode(true);
    setAutoFillBackground(false);
    setAttribute(Qt::WA_Hover);
    setMouseTracking(true);
    setFocusPolicy(Qt::TabFocus);
    setCursor(Qt::PointingHandCursor);
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
}

QSize CustomTabBar::tabSizeHint(int index) const {
    Q_UNUSED(index);
    // Equal slots: QTabBar expands them to the bar width (Figma: 120.75 faces).
    return QSize(kMinFaceWidth + Metrics::TabGap, kBarHeight);
}

QSize CustomTabBar::minimumTabSizeHint(int index) const {
    return tabSizeHint(index);
}

QSize CustomTabBar::sizeHint() const {
    const int n = qMax(1, count());
    return QSize(n * 121 + (n - 1) * Metrics::TabGap, kBarHeight);
}

QSize CustomTabBar::minimumSizeHint() const {
    const int n = qMax(1, count());
    return QSize(n * kMinFaceWidth + (n - 1) * Metrics::TabGap, kBarHeight);
}

QRectF CustomTabBar::faceRect(int index) const {
    const int n = count();
    if (index < 0 || index >= n)
        return QRectF();
    const qreal faceWidth = (width() - Metrics::TabGap * (n - 1)) / qreal(n);
    const qreal x = index * (faceWidth + Metrics::TabGap);
    return QRectF(qRound(x), 0, qRound(x + faceWidth) - qRound(x), Metrics::TabFaceHeight);
}

bool CustomTabBar::event(QEvent* event) {
    switch (event->type()) {
    case QEvent::HoverMove:
    case QEvent::HoverEnter: {
        const int index = tabAt(static_cast<QHoverEvent*>(event)->position().toPoint());
        if (index != hoveredIndex) {
            hoveredIndex = index;
            update();
        }
        break;
    }
    case QEvent::HoverLeave:
        hoveredIndex = -1;
        update();
        break;
    default:
        break;
    }
    return QTabBar::event(event);
}

void CustomTabBar::mousePressEvent(QMouseEvent* event) {
    if (event->button() == Qt::LeftButton) {
        pressedIndex = tabAt(event->position().toPoint());
        update();
    }
    QTabBar::mousePressEvent(event);
}

void CustomTabBar::mouseReleaseEvent(QMouseEvent* event) {
    pressedIndex = -1;
    update();
    QTabBar::mouseReleaseEvent(event);
}

void CustomTabBar::focusInEvent(QFocusEvent* event) {
    keyboardFocus = event->reason() != Qt::MouseFocusReason;
    QTabBar::focusInEvent(event);
}

void CustomTabBar::focusOutEvent(QFocusEvent* event) {
    keyboardFocus = false;
    QTabBar::focusOutEvent(event);
}

void CustomTabBar::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    painter.setFont(AtwTheme::font(kCaptionPx));
    const QFontMetricsF metrics(painter.font());
    for (int i = 0; i < count(); ++i) {
        const bool selected = i == currentIndex();
        const bool pressed = i == pressedIndex && !selected;
        const bool hovered = i == hoveredIndex && isTabEnabled(i);
        const qreal depth = pressed ? 2 : 0;
        QRectF face = faceRect(i).translated(0, depth);

        Surface surface = selected ? selectedTabSurface() : tabSurface();
        if (hovered && !selected) {
            surface.top = mix(surface.top, Qt::white, 0.05);
            surface.bottom = mix(surface.bottom, Qt::white, 0.04);
            surface.glow.setAlphaF(0.16);
        }
        paintSurface(painter, face, surface, Metrics::TabEdge - depth);

        // Caption: caps centred in the face, 3 px hard shadow (Figma).
        const QString caption = tabText(i);
        const qreal textWidth = metrics.horizontalAdvance(caption);
        const qreal capHeight = metrics.ascent();
        const QPointF baseline(face.left() + (face.width() - textWidth) / 2,
                               face.top() + (face.height() + capHeight) / 2);
        const QColor color = selected ? QColor(Color::ActiveText)
                             : hovered ? QColor(0x8E, 0x8E, 0x94)
                                       : QColor(Color::InactiveText);
        painter.setPen(QColor(0, 0, 0, 64));
        painter.drawText(baseline + QPointF(0, 3), caption);
        painter.setPen(isTabEnabled(i) ? color : QColor(0x45, 0x45, 0x48));
        painter.drawText(baseline, caption);

        if (selected && keyboardFocus && hasFocus()) {
            painter.save();
            painter.setPen(QPen(QColor(0xE7, 0xED, 0xF9, 140), 2));
            painter.setBrush(Qt::NoBrush);
            painter.drawRect(face.adjusted(3, 3, -3, -3));
            painter.restore();
        }
    }
}
