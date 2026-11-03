//
// Figma slider. See pixelslider.h.
//

#include "pixelslider.h"

#include <QEvent>
#include <QFocusEvent>
#include <QHoverEvent>
#include <QMouseEvent>
#include <QPainter>
#include <QStyle>
#include <QWheelEvent>

#include "gui/style/atwtheme.h"

using namespace AtwTheme;

namespace {
constexpr int kThumbW = Metrics::ThumbWidth;
constexpr int kThumbH = Metrics::ThumbHeight;
}

PixelSlider::PixelSlider(QWidget* parent) : QSlider(Qt::Horizontal, parent) {
    setObjectName(QStringLiteral("pixelSlider"));
    setAttribute(Qt::WA_Hover);
    setMouseTracking(true);
    setFocusPolicy(Qt::StrongFocus);
    setFixedHeight(kThumbH);
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
}

QSize PixelSlider::sizeHint() const {
    return QSize(488, kThumbH);
}

QSize PixelSlider::minimumSizeHint() const {
    return QSize(kThumbW * 4, kThumbH);
}

QRectF PixelSlider::thumbRect() const {
    const int span = qMax(0, width() - kThumbW);
    const int x = QStyle::sliderPositionFromValue(minimum(), maximum(), sliderPosition(), span, false);
    return QRectF(x, 0, kThumbW, kThumbH);
}

int PixelSlider::valueAt(qreal thumbLeft) const {
    const int span = qMax(1, width() - kThumbW);
    return QStyle::sliderValueFromPosition(minimum(), maximum(), qRound(thumbLeft), span, false);
}

bool PixelSlider::event(QEvent* event) {
    if (event->type() == QEvent::HoverMove || event->type() == QEvent::HoverEnter) {
        const bool over = thumbRect().contains(static_cast<QHoverEvent*>(event)->position());
        if (over != hovered) {
            hovered = over;
            update();
        }
    } else if (event->type() == QEvent::HoverLeave) {
        hovered = false;
        update();
    }
    return QSlider::event(event);
}

void PixelSlider::focusInEvent(QFocusEvent* event) {
    keyboardFocus = event->reason() != Qt::MouseFocusReason;
    QSlider::focusInEvent(event);
}

void PixelSlider::focusOutEvent(QFocusEvent* event) {
    keyboardFocus = false;
    QSlider::focusOutEvent(event);
}

void PixelSlider::mousePressEvent(QMouseEvent* event) {
    if (event->button() != Qt::LeftButton || maximum() == minimum()) {
        event->ignore();
        return;
    }
    const QPointF position = event->position();
    const QRectF thumb = thumbRect();
    // Grab the thumb where it was pressed; a track click centres it there.
    grabOffset = thumb.contains(position) ? position.x() - thumb.left() : kThumbW / 2.0;
    dragging = true;
    setSliderDown(true);
    setSliderPosition(valueAt(position.x() - grabOffset));
    triggerAction(SliderMove);
    update();
    event->accept();
}

void PixelSlider::mouseMoveEvent(QMouseEvent* event) {
    if (!dragging) {
        event->ignore();
        return;
    }
    setSliderPosition(valueAt(event->position().x() - grabOffset));
    event->accept();
}

void PixelSlider::mouseReleaseEvent(QMouseEvent* event) {
    if (!dragging || event->button() != Qt::LeftButton) {
        event->ignore();
        return;
    }
    dragging = false;
    setSliderDown(false);
    update();
    event->accept();
}

void PixelSlider::wheelEvent(QWheelEvent* event) {
    // Let the settings page scroll unless the slider was focused on purpose.
    if (!hasFocus()) {
        event->ignore();
        return;
    }
    QSlider::wheelEvent(event);
}

void PixelSlider::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing, false);
    // Track: 12 px #343435 with a 3 px #2C2C2C edge, 5 px below the thumb top.
    const QRectF track(0, Metrics::TrackTop, width(), Metrics::TrackHeight);
    painter.fillRect(track, QColor(Color::Body));
    painter.fillRect(QRectF(track.left(), track.bottom() - 3, track.width(), 3), QColor(Color::Edge));

    paintThumb(painter, thumbRect().topLeft(), hovered || (keyboardFocus && hasFocus()), isSliderDown(), isEnabled());

    if (keyboardFocus && hasFocus()) {
        painter.setPen(QPen(QColor(0xE7, 0xED, 0xF9, 140), 1));
        painter.setBrush(Qt::NoBrush);
        painter.drawRect(thumbRect().adjusted(0.5, 0.5, -0.5, -0.5));
    }
}
