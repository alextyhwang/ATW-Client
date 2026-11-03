//
// LAUNCH hero button. See launchbutton.h.
//

#include "launchbutton.h"

#include <QEvent>
#include <QFocusEvent>
#include <QPainter>
#include <QtMath>
#include <QVariantAnimation>

#include "gui/style/atwtheme.h"

using namespace AtwTheme;

namespace {
constexpr qreal kFaceWidth = Metrics::LaunchWidth;
constexpr qreal kFaceHeight = Metrics::LaunchFaceHeight;
constexpr qreal kEdge = Metrics::LaunchEdge;
constexpr qreal kPressDepth = 4;

AtwTheme::Surface brighten(AtwTheme::Surface s, qreal amount, qreal glowAlpha) {
    s.top = mix(s.top, Qt::white, amount);
    s.middle = mix(s.middle, Qt::white, amount);
    s.bottom = mix(s.bottom, Qt::white, amount);
    s.glow.setAlphaF(glowAlpha);
    return s;
}

AtwTheme::Surface quieten(AtwTheme::Surface s, qreal pulse) {
    const QColor slate(0x3A, 0x47, 0x60);
    s.top = mix(s.top, slate, 0.45);
    s.middle = mix(s.middle, slate, 0.45);
    s.bottom = mix(s.bottom, slate, 0.35);
    s.glow.setAlphaF(0.12 + 0.26 * pulse);
    return s;
}
}

LaunchButton::LaunchButton(QWidget* parent) : QAbstractButton(parent) {
    setObjectName(QStringLiteral("launchButton"));
    setAttribute(Qt::WA_Hover);
    setFocusPolicy(Qt::StrongFocus);
    setCursor(Qt::PointingHandCursor);
    setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
    setText(QStringLiteral("Launch"));
    setToolTip(QStringLiteral("Save settings and start ATW Client"));

    busyAnimation = new QVariantAnimation(this);
    busyAnimation->setStartValue(0.0);
    busyAnimation->setKeyValueAt(0.5, 1.0);
    busyAnimation->setEndValue(0.0);
    busyAnimation->setDuration(1600);
    busyAnimation->setEasingCurve(QEasingCurve::InOutSine);
    busyAnimation->setLoopCount(-1);
    connect(busyAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant& value) {
        pulse = value.toReal();
        update();
    });
}

QSize LaunchButton::sizeHint() const {
    return QSize(int(kFaceWidth), int(kFaceHeight + kEdge));
}

QSize LaunchButton::minimumSizeHint() const {
    return sizeHint();
}

void LaunchButton::setMode(Mode mode) {
    if (mode == currentMode)
        return;
    currentMode = mode;
    switch (mode) {
    case Mode::Launch:
        setText(QStringLiteral("Launch"));
        setToolTip(QStringLiteral("Save settings and start ATW Client"));
        setCursor(Qt::PointingHandCursor);
        busyAnimation->stop();
        break;
    case Mode::Close:
        setText(QStringLiteral("Close"));
        setToolTip(QStringLiteral("End the running game"));
        setCursor(Qt::PointingHandCursor);
        busyAnimation->stop();
        break;
    case Mode::Busy:
        setText(QStringLiteral("Running"));
        setToolTip(QStringLiteral("ATW Client is running. Close the game to launch again."));
        setCursor(Qt::ArrowCursor);
        busyAnimation->start();
        break;
    }
    update();
}

bool LaunchButton::event(QEvent* event) {
    switch (event->type()) {
    case QEvent::HoverEnter:
        hovered = true;
        update();
        break;
    case QEvent::HoverLeave:
        hovered = false;
        update();
        break;
    default:
        break;
    }
    return QAbstractButton::event(event);
}

void LaunchButton::focusInEvent(QFocusEvent* event) {
    keyboardFocus = event->reason() == Qt::TabFocusReason || event->reason() == Qt::BacktabFocusReason
        || event->reason() == Qt::ShortcutFocusReason;
    QAbstractButton::focusInEvent(event);
}

void LaunchButton::focusOutEvent(QFocusEvent* event) {
    keyboardFocus = false;
    QAbstractButton::focusOutEvent(event);
}

QImage LaunchButton::softShadow(qreal dpr) {
    // Figma's second text shadow: 0 8px 8px rgba(0,0,0,0.15).
    const int mode = currentMode == Mode::Close ? 1 : 0;
    if (!cachedShadow.isNull() && cachedShadowMode == mode && qFuzzyCompare(cachedShadowDpr, dpr))
        return cachedShadow;
    const int pad = 12;
    QImage mask(qCeil((kFaceWidth + 2 * pad) * dpr), qCeil((kFaceHeight + 2 * pad) * dpr),
                QImage::Format_ARGB32_Premultiplied);
    mask.fill(Qt::transparent);
    mask.setDevicePixelRatio(dpr);
    {
        QPainter painter(&mask);
        paintGlyph(painter, mode ? Glyph::Close : Glyph::Launch, QRectF(pad, pad, kFaceWidth, kFaceHeight),
                   QColor(0, 0, 0, int(255 * 0.15)));
    }
    cachedShadow = blurred(mask, qMax(1, qRound(3 * dpr)));
    cachedShadowMode = mode;
    cachedShadowDpr = dpr;
    return cachedShadow;
}

void LaunchButton::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    const bool running = currentMode == Mode::Close;
    const bool busy = currentMode == Mode::Busy;
    const bool pressed = isDown() && !busy;
    const qreal depth = pressed ? kPressDepth : (busy ? 2 : 0);

    // Centre horizontally if the widget is ever given more room.
    const qreal left = qRound((width() - kFaceWidth) / 2);
    const QRectF face(left, depth, kFaceWidth, kFaceHeight);

    Surface surface = running ? closeSurface() : launchSurface();
    if (busy)
        surface = quieten(surface, pulse);
    else if (pressed)
        surface.glow.setAlphaF(0.22);              // pressed in: dimmer inner glow
    else if (hovered && isEnabled())
        surface = brighten(surface, 0.07, 0.42);   // hover: lift the face slightly
    paintSurface(painter, face, surface, kEdge - depth);

    const Glyph glyph = running ? Glyph::Close : Glyph::Launch;
    const QColor textColor = running ? QColor(0xF9, 0xE7, 0xE7) : QColor(0xE7, 0xED, 0xF9);
    const QColor hardShadow = running ? QColor(0x3A, 0x32, 0x32) : QColor(0x32, 0x32, 0x3A);

    painter.save();
    painter.setClipRect(face);
    if (!busy) {
        const qreal dpr = devicePixelRatioF();
        painter.drawImage(QPointF(face.left() - 12, face.top() - 12 + 8), softShadow(dpr));
    }
    paintGlyph(painter, glyph, face.translated(0, 6), busy ? mix(hardShadow, QColor(0x2E, 0x36, 0x48), 0.5) : hardShadow);
    paintGlyph(painter, glyph, face, busy ? mix(textColor, QColor(0x9D, 0xAE, 0xCF), 0.55) : textColor);
    painter.restore();

    if (!isEnabled() && !busy)
        painter.fillRect(QRectF(face.left(), face.top(), kFaceWidth, kFaceHeight + kEdge - depth),
                         QColor(0x25, 0x25, 0x26, 140));

    if (keyboardFocus && hasFocus()) {
        painter.setPen(QPen(QColor(0xE7, 0xED, 0xF9, 150), 2));
        painter.setBrush(Qt::NoBrush);
        painter.drawRect(face.adjusted(4, 4, -4, -4));
    }
}
