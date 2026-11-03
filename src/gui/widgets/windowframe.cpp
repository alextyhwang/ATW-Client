//
// Frameless window surface. See windowframe.h.
//

#include "windowframe.h"

#include <QMouseEvent>
#include <QPainter>
#include <QtMath>
#include <QPainterPath>
#include <QWindow>

#include "gui/style/atwtheme.h"
#include "gui/widgets/customtitlebar.h"

using namespace AtwTheme;

namespace {
constexpr int kRadius = Metrics::FrameRadius;
constexpr int kShadowOffset = 5;
constexpr int kShadowBlur = 5;          // box radius; ~sigma 5.5 px after three passes
constexpr qreal kShadowAlpha = 0.55;
constexpr int kInsideGrip = 5;          // resize zone inside the visible frame
constexpr int kOutsideGrip = 9;         // and inside the shadow margin
constexpr int kCorner = kRadius + 18;   // 9-slice corner extent (radius + blur reach)
}

WindowFrame::WindowFrame(QWidget* parent) : QWidget(parent) {
    setObjectName(QStringLiteral("windowFrame"));
    setAttribute(Qt::WA_TranslucentBackground);
    setMouseTracking(true);
}

int WindowFrame::shadowMargin() {
#ifdef Q_OS_MACOS
    return 0;   // macOS draws a native shadow that follows the rounded alpha mask
#else
    return 18;
#endif
}

QRect WindowFrame::frameRect() const {
    const int m = shadowMargin();
    return rect().adjusted(m, m, -m, -m);
}

QPixmap WindowFrame::shadowTile(qreal dpr) {
    if (!tile.isNull() && qFuzzyCompare(tileDpr, dpr))
        return tile;
    const int m = shadowMargin();
    const int shape = 2 * kCorner + 2;
    const int logical = shape + 2 * m;
    QImage image(qCeil(logical * dpr), qCeil(logical * dpr), QImage::Format_ARGB32_Premultiplied);
    image.fill(Qt::transparent);
    image.setDevicePixelRatio(dpr);
    {
        QPainter painter(&image);
        painter.setRenderHint(QPainter::Antialiasing);
        QPainterPath path;
        path.addRoundedRect(QRectF(m, m + kShadowOffset, shape, shape), kRadius, kRadius);
        painter.fillPath(path, QColor(0, 0, 0, int(255 * kShadowAlpha)));
    }
    tile = QPixmap::fromImage(blurred(image, qMax(1, qRound(kShadowBlur * dpr))));
    tile.setDevicePixelRatio(dpr);
    tileDpr = dpr;
    return tile;
}

void WindowFrame::paintShadow(QPainter& painter, const QRect& frame) {
    const int m = shadowMargin();
    if (m <= 0)
        return;
    const qreal dpr = devicePixelRatioF();
    const QPixmap source = shadowTile(dpr);
    const int shape = 2 * kCorner + 2;
    const int c = m + kCorner;                  // slice size along each axis
    const QRect outer = frame.adjusted(-m, -m, m, m);
    const int midW = outer.width() - 2 * c;
    const int midH = outer.height() - 2 * c;
    if (midW < 0 || midH < 0)
        return;
    auto src = [&](int x, int y, int w, int h) {
        return QRectF(x * dpr, y * dpr, w * dpr, h * dpr);
    };
    const int s = shape + 2 * m;
    // Corners.
    painter.drawPixmap(QRectF(outer.left(), outer.top(), c, c), source, src(0, 0, c, c));
    painter.drawPixmap(QRectF(outer.right() + 1 - c, outer.top(), c, c), source, src(s - c, 0, c, c));
    painter.drawPixmap(QRectF(outer.left(), outer.bottom() + 1 - c, c, c), source, src(0, s - c, c, c));
    painter.drawPixmap(QRectF(outer.right() + 1 - c, outer.bottom() + 1 - c, c, c), source, src(s - c, s - c, c, c));
    // Edges: stretch the 2 px middle of the tile.
    painter.drawPixmap(QRectF(outer.left() + c, outer.top(), midW, c), source, src(c, 0, 2, c));
    painter.drawPixmap(QRectF(outer.left() + c, outer.bottom() + 1 - c, midW, c), source, src(c, s - c, 2, c));
    painter.drawPixmap(QRectF(outer.left(), outer.top() + c, c, midH), source, src(0, c, c, 2));
    painter.drawPixmap(QRectF(outer.right() + 1 - c, outer.top() + c, c, midH), source, src(s - c, c, c, 2));
}

void WindowFrame::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    const QRect frame = frameRect();
    paintShadow(painter, frame);

    painter.setRenderHint(QPainter::Antialiasing);
    QPainterPath path;
    path.addRoundedRect(QRectF(frame), kRadius, kRadius);
    painter.fillPath(path, QColor(Color::Frame));
    painter.save();
    painter.setClipPath(path);
    painter.fillRect(QRect(frame.left(), frame.top() + Metrics::HeaderHeight,
                           frame.width(), frame.height() - Metrics::HeaderHeight),
                     QColor(Color::Body));
    painter.restore();
}

Qt::Edges WindowFrame::edgesAt(const QPoint& p) const {
    QWidget* w = window();
    if (!w || w->isMaximized() || w->isFullScreen() || w->minimumSize() == w->maximumSize())
        return {};
    const QRect frame = frameRect();
    const int outside = qMin(kOutsideGrip, shadowMargin());
    const QRect grip = frame.adjusted(-outside, -outside, outside, outside);
    if (!grip.contains(p))
        return {};
    Qt::Edges edges;
    if (p.x() < frame.left() + kInsideGrip)
        edges |= Qt::LeftEdge;
    else if (p.x() > frame.right() - kInsideGrip)
        edges |= Qt::RightEdge;
    if (p.y() < frame.top() + kInsideGrip)
        edges |= Qt::TopEdge;
    else if (p.y() > frame.bottom() - kInsideGrip)
        edges |= Qt::BottomEdge;
    return edges;
}

void WindowFrame::updateCursor(const QPoint& position) {
    const Qt::Edges edges = edgesAt(position);
    if (edges == (Qt::LeftEdge | Qt::TopEdge) || edges == (Qt::RightEdge | Qt::BottomEdge))
        setCursor(Qt::SizeFDiagCursor);
    else if (edges == (Qt::RightEdge | Qt::TopEdge) || edges == (Qt::LeftEdge | Qt::BottomEdge))
        setCursor(Qt::SizeBDiagCursor);
    else if (edges & (Qt::LeftEdge | Qt::RightEdge))
        setCursor(Qt::SizeHorCursor);
    else if (edges & (Qt::TopEdge | Qt::BottomEdge))
        setCursor(Qt::SizeVerCursor);
    else
        unsetCursor();
}

void WindowFrame::mousePressEvent(QMouseEvent* event) {
    if (event->button() != Qt::LeftButton)
        return QWidget::mousePressEvent(event);
    const QPoint position = event->position().toPoint();
    const Qt::Edges edges = edgesAt(position);
    QWindow* handle = window()->windowHandle();
    if (edges) {
        if (!handle || !handle->startSystemResize(edges)) {
            manualResize = true;
            resizeEdges = edges;
            startGeometry = window()->geometry();
            startGlobal = event->globalPosition().toPoint();
        }
        event->accept();
        return;
    }
    if (frameRect().contains(position) && position.y() < frameRect().top() + Metrics::HeaderHeight) {
        manualMove = !startWindowMove(this);
        if (manualMove)
            moveOffset = event->globalPosition().toPoint() - window()->frameGeometry().topLeft();
        event->accept();
        return;
    }
    QWidget::mousePressEvent(event);
}

void WindowFrame::mouseMoveEvent(QMouseEvent* event) {
    if (manualResize && (event->buttons() & Qt::LeftButton)) {
        const QPoint delta = event->globalPosition().toPoint() - startGlobal;
        QRect g = startGeometry;
        const QSize minimum = window()->minimumSize();
        const QSize maximum = window()->maximumSize();
        if (resizeEdges & Qt::LeftEdge)
            g.setLeft(qBound(g.right() + 1 - maximum.width(), g.left() + delta.x(), g.right() + 1 - minimum.width()));
        if (resizeEdges & Qt::RightEdge)
            g.setRight(qBound(g.left() - 1 + minimum.width(), g.right() + delta.x(), g.left() - 1 + maximum.width()));
        if (resizeEdges & Qt::TopEdge)
            g.setTop(qBound(g.bottom() + 1 - maximum.height(), g.top() + delta.y(), g.bottom() + 1 - minimum.height()));
        if (resizeEdges & Qt::BottomEdge)
            g.setBottom(qBound(g.top() - 1 + minimum.height(), g.bottom() + delta.y(), g.top() - 1 + maximum.height()));
        window()->setGeometry(g);
        event->accept();
        return;
    }
    if (manualMove && (event->buttons() & Qt::LeftButton)) {
        window()->move(event->globalPosition().toPoint() - moveOffset);
        event->accept();
        return;
    }
    if (!(event->buttons() & Qt::LeftButton))
        updateCursor(event->position().toPoint());
    QWidget::mouseMoveEvent(event);
}

void WindowFrame::mouseReleaseEvent(QMouseEvent* event) {
    manualResize = false;
    manualMove = false;
    QWidget::mouseReleaseEvent(event);
}

void WindowFrame::leaveEvent(QEvent* event) {
    if (!manualResize)
        unsetCursor();
    QWidget::leaveEvent(event);
}
