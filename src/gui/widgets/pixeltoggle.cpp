//
// Figma toggle. See pixeltoggle.h.
//

#include "pixeltoggle.h"

#include <QEvent>
#include <QFocusEvent>
#include <QPainter>
#include <QVariantAnimation>

#include "gui/style/atwtheme.h"

using namespace AtwTheme;

namespace {
constexpr int kWidth = Metrics::ToggleWidth;
constexpr int kHeight = Metrics::ToggleHeight;
constexpr int kTravel = Metrics::ToggleWidth - Metrics::ThumbWidth;   // 21 px
}

PixelToggle::PixelToggle(QWidget* parent) : QCheckBox(parent) {
    setObjectName(QStringLiteral("pixelToggle"));
    setAttribute(Qt::WA_Hover);
    setCursor(Qt::PointingHandCursor);
    setFocusPolicy(Qt::StrongFocus);
    setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
    setFixedSize(kWidth, kHeight);
    animation = new QVariantAnimation(this);
    animation->setDuration(140);
    animation->setEasingCurve(QEasingCurve::OutCubic);
    connect(animation, &QVariantAnimation::valueChanged, this, [this](const QVariant& value) {
        position = value.toReal();
        update();
    });
}

QSize PixelToggle::sizeHint() const {
    return QSize(kWidth, kHeight);
}

QSize PixelToggle::minimumSizeHint() const {
    return sizeHint();
}

bool PixelToggle::hitButton(const QPoint& position) const {
    return rect().contains(position);
}

bool PixelToggle::event(QEvent* event) {
    if (event->type() == QEvent::HoverEnter || event->type() == QEvent::HoverLeave) {
        hovered = event->type() == QEvent::HoverEnter;
        update();
    }
    return QCheckBox::event(event);
}

void PixelToggle::focusInEvent(QFocusEvent* event) {
    keyboardFocus = event->reason() != Qt::MouseFocusReason;
    QCheckBox::focusInEvent(event);
}

void PixelToggle::focusOutEvent(QFocusEvent* event) {
    keyboardFocus = false;
    QCheckBox::focusOutEvent(event);
}

void PixelToggle::checkStateSet() {
    // Programmatic changes (settings load) jump; user toggles animate.
    QCheckBox::checkStateSet();
    animation->stop();
    position = isChecked() ? 1 : 0;
    update();
}

void PixelToggle::nextCheckState() {
    QCheckBox::nextCheckState();
    animateTo(isChecked());
}

void PixelToggle::animateTo(bool on) {
    animation->stop();
    animation->setStartValue(position);
    animation->setEndValue(on ? 1.0 : 0.0);
    animation->start();
}

void PixelToggle::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing, false);
    const bool enabled = isEnabled();
    const qreal thumbX = qRound(position * kTravel);

    // Track (#343435) with the 3 px edge under the part the thumb leaves bare.
    painter.fillRect(QRectF(0, 0, kWidth, kHeight), QColor(Color::Body));
    const QColor edge(Color::Edge);
    if (thumbX > 0)
        painter.fillRect(QRectF(0, kHeight - 3, thumbX, 3), edge);
    if (thumbX < kTravel)
        painter.fillRect(QRectF(thumbX + Metrics::ThumbWidth, kHeight - 3, kTravel - thumbX, 3), edge);

    paintThumb(painter, QPointF(thumbX, 0), hovered && enabled, isDown(), enabled);

    if (keyboardFocus && hasFocus()) {
        painter.setPen(QPen(QColor(0xE7, 0xED, 0xF9, 140), 1));
        painter.setBrush(Qt::NoBrush);
        painter.drawRect(QRectF(0.5, 0.5, kWidth - 1, kHeight - 1));
    }
}
