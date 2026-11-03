//
// Frameless title row. See customtitlebar.h.
//

#include "customtitlebar.h"

#include <QEvent>
#include <QHBoxLayout>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QWindow>

#include "buildconfig.h"
#include "gui/style/atwtheme.h"

using namespace AtwTheme;

namespace {
constexpr qreal kTile = 34;      // Figma tile 34.43-34.83 px, snapped for crisp edges
constexpr qreal kNotch = 2;      // stepped pixel corners
}

bool startWindowMove(QWidget* widget) {
    QWindow* handle = widget->window()->windowHandle();
    return handle && handle->startSystemMove();
}

// ---- Badge -----------------------------------------------------------------

TitleBadge::TitleBadge(QWidget* parent) : QWidget(parent) {
    setFixedSize(sizeHint());
    setAttribute(Qt::WA_TransparentForMouseEvents);   // dragging passes through
    setAccessibleName(QStringLiteral("ATW Client v") + BuildConfig::VERSION);
}

void TitleBadge::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing, false);
    const QRectF tile(0, 0, width(), height());
    painter.fillPath(notchedRect(tile, kNotch), QColor(Color::Body));
    // "ATW" lettering, debossed in the frame colour (Figma: #252526 on #343435).
    const QSizeF natural = glyphSize(Glyph::Atw);
    const qreal glyphHeight = 17;
    const QSizeF size(natural.width() * glyphHeight / natural.height(), glyphHeight);
    const QRectF target(qRound((width() - size.width()) / 2), qRound((height() - size.height()) / 2),
                        size.width(), size.height());
    paintGlyph(painter, Glyph::Atw, target, QColor(Color::Frame));
}

// ---- Close tile ------------------------------------------------------------

TitleCloseButton::TitleCloseButton(QWidget* parent) : QAbstractButton(parent) {
    setObjectName(QStringLiteral("closeButton"));
    setFixedSize(sizeHint());
    setAttribute(Qt::WA_Hover);
    setCursor(Qt::PointingHandCursor);
    setFocusPolicy(Qt::TabFocus);
    setText(QStringLiteral("Close"));
    setToolTip(QStringLiteral("Close"));
    setAccessibleName(QStringLiteral("Close"));
}

bool TitleCloseButton::event(QEvent* event) {
    if (event->type() == QEvent::HoverEnter || event->type() == QEvent::HoverLeave) {
        hovered = event->type() == QEvent::HoverEnter;
        update();
    }
    return QAbstractButton::event(event);
}

void TitleCloseButton::paintEvent(QPaintEvent*) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing, false);
    const qreal inset = (width() - kTile) / 2;
    const QRectF tile(inset, inset, kTile, kTile);
    QColor fill(Color::Body);
    QColor cross(Color::Frame);
    if (isDown()) {
        fill = QColor(0x8F, 0x37, 0x37);
        cross = QColor(0xF9, 0xE7, 0xE7);
    } else if (hovered) {
        fill = QColor(Color::Danger);
        cross = QColor(0xF9, 0xE7, 0xE7);
    } else if (hasFocus()) {
        cross = QColor(Color::Label);
    }
    painter.fillPath(notchedRect(tile, kNotch), fill);
    const qreal glyph = 21;   // 7 x 7 pixel grid at 3 px per cell
    const QRectF target(qRound(tile.center().x() - glyph / 2), qRound(tile.center().y() - glyph / 2), glyph, glyph);
    paintGlyph(painter, Glyph::CloseCross, target, cross);
}

// ---- Title row -------------------------------------------------------------

CustomTitleBar::CustomTitleBar(QWidget* parent) : QWidget(parent) {
    setObjectName(QStringLiteral("titleBar"));
    setFixedHeight(Metrics::TitleRowHeight);

    badge = new TitleBadge(this);
    closeButton = new TitleCloseButton(this);
    connect(closeButton, &QAbstractButton::clicked, this, [this]() {
        if (QWidget* w = window())
            w->close();
    });

    QHBoxLayout* layout = new QHBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);
    layout->addWidget(badge, 0, Qt::AlignVCenter);
    layout->addStretch();
    layout->addWidget(closeButton, 0, Qt::AlignVCenter);
}

void CustomTitleBar::mousePressEvent(QMouseEvent* event) {
    if (event->button() != Qt::LeftButton)
        return QWidget::mousePressEvent(event);
    manualDrag = !startWindowMove(this);
    if (manualDrag)
        dragOffset = event->globalPosition().toPoint() - window()->frameGeometry().topLeft();
    event->accept();
}

void CustomTitleBar::mouseMoveEvent(QMouseEvent* event) {
    if (manualDrag && (event->buttons() & Qt::LeftButton)) {
        window()->move(event->globalPosition().toPoint() - dragOffset);
        event->accept();
        return;
    }
    QWidget::mouseMoveEvent(event);
}

void CustomTitleBar::mouseReleaseEvent(QMouseEvent* event) {
    manualDrag = false;
    QWidget::mouseReleaseEvent(event);
}
