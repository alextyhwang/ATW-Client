//
// ATW launcher/config visual system. See atwtheme.h and docs/UI_DESIGN.md.
//

#include "atwtheme.h"

#include <QApplication>
#include <QFontDatabase>
#include <QHash>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPalette>
#include <QPixmap>
#include <QPixmapCache>
#include <QStyleFactory>
#include <QSvgRenderer>

#include <cmath>
#include <cstring>
#include <QtMath>
#include <vector>

namespace AtwTheme {

namespace {

QString g_fontFamily = QStringLiteral("Minecraft");

QColor rgb(QRgb value) {
    return QColor(value);
}

QColor rgba(QRgb value, qreal alpha) {
    QColor color(value);
    color.setAlphaF(alpha);
    return color;
}

QString glyphResource(Glyph glyph) {
    switch (glyph) {
    case Glyph::Launch:
        return QStringLiteral(":/res/ui/launch-button/label-launch.svg");
    case Glyph::Close:
        return QStringLiteral(":/res/ui/launch-button/label-close.svg");
    case Glyph::Atw:
        return QStringLiteral(":/res/ui/window/label-atw.svg");
    case Glyph::CloseCross:
        return QStringLiteral(":/res/ui/window/glyph-close.svg");
    }
    return QString();
}

QSvgRenderer* glyphRenderer(Glyph glyph) {
    // Renderers live for the process; created lazily after QGuiApplication.
    static QHash<int, QSvgRenderer*> renderers;
    const int key = static_cast<int>(glyph);
    auto it = renderers.find(key);
    if (it == renderers.end())
        it = renderers.insert(key, new QSvgRenderer(glyphResource(glyph)));
    return it.value();
}

// Gaussian CDF for the Figma inner-glow falloff (eroded mask blurred by sigma).
qreal normalCdf(qreal x) {
    return 0.5 * (1.0 + std::erf(x / std::sqrt(2.0)));
}

void boxBlurPass(std::vector<quint32>& src, std::vector<quint32>& dst, int width, int height, int radius,
                 bool horizontal) {
    const int lines = horizontal ? height : width;
    const int length = horizontal ? width : height;
    const int window = radius * 2 + 1;
    for (int line = 0; line < lines; ++line) {
        auto at = [&](int i) -> quint32& {
            const int clamped = qBound(0, i, length - 1);
            return horizontal ? src[size_t(line) * width + clamped] : src[size_t(clamped) * width + line];
        };
        quint32 sums[4] = {0, 0, 0, 0};
        for (int i = -radius; i <= radius; ++i) {
            const quint32 p = at(i);
            sums[0] += (p >> 24) & 0xFF;
            sums[1] += (p >> 16) & 0xFF;
            sums[2] += (p >> 8) & 0xFF;
            sums[3] += p & 0xFF;
        }
        for (int i = 0; i < length; ++i) {
            quint32& out = horizontal ? dst[size_t(line) * width + i] : dst[size_t(i) * width + line];
            out = ((sums[0] / window) << 24) | ((sums[1] / window) << 16) | ((sums[2] / window) << 8)
                | (sums[3] / window);
            const quint32 add = at(i + radius + 1);
            const quint32 sub = at(i - radius);
            sums[0] += ((add >> 24) & 0xFF) - ((sub >> 24) & 0xFF);
            sums[1] += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
            sums[2] += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
            sums[3] += (add & 0xFF) - (sub & 0xFF);
        }
    }
}

} // namespace

// ---- Surfaces --------------------------------------------------------------

Surface launchSurface() {
    return {rgb(0x76A5FF), rgb(0x4779D8), 0.543269, rgb(0x2F4876), rgb(0x202F4A), rgba(Color::Glow, 0.30)};
}

Surface closeSurface() {
    return {rgb(0xFF7676), rgb(0xD84747), 0.543269, rgb(0x762F2F), rgb(0x4A2020), rgba(0xFF8888, 0.30)};
}

Surface selectedTabSurface() {
    return {rgb(0x44639D), QColor(), 0.5, rgb(0x284780), rgb(0x29354B), rgba(Color::Glow, 0.25)};
}

Surface tabSurface() {
    return {rgb(0x38383C), QColor(), 0.5, rgb(0x343435), rgb(Color::Edge), rgba(Color::Glow, 0.10)};
}

// ---- Fonts ----------------------------------------------------------------

void ensureFonts() {
    static bool loaded = false;
    if (loaded)
        return;
    loaded = true;
    const int id = QFontDatabase::addApplicationFont(QStringLiteral(":/res/fonts/Minecraft.ttf"));
    if (id < 0)
        return;
    const QStringList families = QFontDatabase::applicationFontFamilies(id);
    if (!families.isEmpty())
        g_fontFamily = families.first();
}

QString fontFamily() {
    ensureFonts();
    return g_fontFamily;
}

QString fixedFontFamily() {
    return QFontDatabase::systemFont(QFontDatabase::FixedFont).family();
}

QFont font(qreal pixelSize) {
    QFont result(fontFamily());
    result.setPixelSize(qMax(1, qRound(pixelSize)));
    result.setWeight(QFont::Medium);
    // Grayscale antialiasing like the Figma render; subpixel (ClearType) fringes
    // colour the pixel glyphs on the translucent frameless window.
    result.setStyleStrategy(QFont::StyleStrategy(QFont::PreferAntialias | QFont::NoSubpixelAntialias));
    return result;
}

// ---- Application theme -----------------------------------------------------

void installApplicationTheme(QApplication& app) {
    app.setStyle(QStyleFactory::create(QStringLiteral("Fusion")));
    ensureFonts();
    app.setFont(font(16));

    QPalette palette;
    palette.setColor(QPalette::Window, rgb(Color::Frame));
    palette.setColor(QPalette::WindowText, rgb(Color::Label));
    palette.setColor(QPalette::Base, rgb(Color::Body));
    palette.setColor(QPalette::AlternateBase, rgb(0x38383C));
    palette.setColor(QPalette::Text, rgb(Color::Value));
    palette.setColor(QPalette::PlaceholderText, rgb(Color::InactiveText));
    palette.setColor(QPalette::Button, rgb(Color::Body));
    palette.setColor(QPalette::ButtonText, rgb(Color::Value));
    palette.setColor(QPalette::BrightText, rgb(Color::ActiveText));
    palette.setColor(QPalette::Highlight, rgb(Color::Accent));
    palette.setColor(QPalette::HighlightedText, rgb(Color::ActiveText));
    palette.setColor(QPalette::ToolTipBase, rgb(Color::Frame));
    palette.setColor(QPalette::ToolTipText, rgb(Color::Value));
    palette.setColor(QPalette::Link, rgb(0x76A5FF));
    palette.setColor(QPalette::Light, rgb(0x45454B));
    palette.setColor(QPalette::Midlight, rgb(0x3C3C41));
    palette.setColor(QPalette::Mid, rgb(Color::Edge));
    palette.setColor(QPalette::Dark, rgb(0x202021));
    palette.setColor(QPalette::Shadow, rgb(0x141415));
    for (QPalette::ColorRole role : {QPalette::WindowText, QPalette::Text, QPalette::ButtonText})
        palette.setColor(QPalette::Disabled, role, rgb(Color::InactiveText));
    app.setPalette(palette);
    app.setStyleSheet(applicationStyleSheet());
}

QString applicationStyleSheet() {
    // Figma tokens: #252526 frame/cards, #343435 body/inputs, #2C2C2C 3D edges,
    // #929292 labels, #BFBFBF values, #6C6C6C input text, #4779D8 accent.
    static const QString css = QStringLiteral(R"(
QWidget {
    color: #929292;
    font-family: "%2";
    font-weight: 500;
    font-size: 16px;
}
QToolTip {
    background-color: #252526;
    color: #BFBFBF;
    border: 1px solid #3F3F42;
    padding: 4px 6px;
    font-size: 14px;
}
QDialog, QMessageBox {
    background-color: #252526;
}
QMessageBox QLabel {
    color: #BFBFBF;
    font-size: 16px;
}
QLabel {
    color: #929292;
    font-size: 18px;
    background: transparent;
}
QLabel#fieldValue {
    color: #BFBFBF;
    font-size: 16px;
}
QLabel#emptyTitle {
    color: #929292;
    font-size: 18px;
}
QLabel#emptyBody, QLabel#listHint {
    color: #6C6C6C;
    font-size: 16px;
}
QLabel#toast {
    background-color: #252526;
    color: #BFBFBF;
    font-size: 16px;
    padding: 2px 10px 1px 9px;
    border-left: 3px solid #4779D8;
}
QLabel#toast[tone="error"] {
    color: #F9E7E7;
    border-left-color: #D84747;
}
QFrame#card, QFrame#tray {
    background-color: #252526;
    border: none;
}
QScrollArea#pageScroll, QScrollArea#pageScroll > QWidget#qt_scrollarea_viewport,
QStackedWidget#pageStack {
    background: transparent;
    border: none;
}
QLineEdit, QPlainTextEdit, QTextEdit {
    background-color: #343435;
    color: #6C6C6C;
    border: none;
    border-radius: 0px;
    font-size: 16px;
    selection-background-color: #4779D8;
    selection-color: #E7EDF9;
}
QLineEdit {
    padding: 3px 3px 0px 3px;
}
QPlainTextEdit {
    padding: 0px 0px 0px 1px;
}
QLineEdit:hover, QPlainTextEdit:hover {
    color: #808080;
}
QLineEdit:read-only:hover {
    color: #BFBFBF;
}
QLineEdit:focus, QPlainTextEdit:focus, QTextEdit:focus {
    color: #BFBFBF;
}
QLineEdit:disabled, QPlainTextEdit:disabled {
    color: #4A4A4E;
}
QPlainTextEdit#logView {
    background-color: #1E1E1F;
    color: #BFBFBF;
    font-family: "%1";
    font-size: 12px;
    padding: 4px;
}
QPushButton {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #3D3D42, stop:1 #343435);
    background-clip: padding;
    color: #BFBFBF;
    border: none;
    border-top: 1px solid #47474D;
    border-bottom: 3px solid #2C2C2C;
    padding: 0px 10px;
    min-height: 20px;
    font-size: 16px;
}
QPushButton:hover {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #46464C, stop:1 #3B3B3E);
    color: #E7EDF9;
}
QPushButton:pressed {
    border-top: 3px solid transparent;
    border-bottom: 1px solid #2C2C2C;
}
QPushButton:focus {
    border-top-color: #5487E4;
}
QPushButton:disabled {
    background-color: #303031;
    color: #5C5C60;
    border-top-color: #333335;
    border-bottom-color: #2A2A2B;
}
QPushButton#primary {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #5B8DEE, stop:0.55 #4779D8, stop:1 #3A63B1);
    color: #E7EDF9;
    border-top-color: #76A5FF;
    border-bottom-color: #29354B;
}
QPushButton#primary:hover {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #6A98F3, stop:0.55 #5285E2, stop:1 #4069BA);
}
QPushButton#primary:disabled {
    background-color: #33415C;
    color: #7F8BA3;
    border-top-color: #3A4A68;
    border-bottom-color: #26304A;
}
AccountWidget QPushButton {
    min-height: 17px;
    padding: 0px 8px;
}
AccountWidget QLabel {
    padding-top: 4px;
    qproperty-indent: 0;
}
QScrollBar:vertical {
    background: #2C2C2C;
    width: 9px;
    margin: 0px;
    border: none;
}
QScrollBar::handle:vertical {
    background: #4A4A4E;
    border: none;
    border-bottom: 3px solid #3B3B3F;
    min-height: 28px;
}
QScrollBar:horizontal {
    background: #2C2C2C;
    height: 9px;
    margin: 0px;
    border: none;
}
QScrollBar::handle:horizontal {
    background: #4A4A4E;
    border: none;
    border-right: 3px solid #3B3B3F;
    min-width: 28px;
}
QScrollBar::handle:hover {
    background: #5C5C60;
}
QScrollBar::handle:pressed {
    background: #4779D8;
    border-color: #33599F;
}
QScrollBar::add-line, QScrollBar::sub-line {
    width: 0px;
    height: 0px;
    border: none;
    background: none;
}
QScrollBar::add-page, QScrollBar::sub-page {
    background: none;
}
QAbstractScrollArea::corner {
    background: #2C2C2C;
    border: none;
}
QAbstractItemView {
    background-color: #343435;
    alternate-background-color: #38383C;
    color: #BFBFBF;
    border: none;
    font-size: 16px;
    selection-background-color: #284780;
    selection-color: #E7EDF9;
}
QAbstractItemView::item {
    padding: 0px 4px;
    border: none;
}
QListWidget::item {
    padding: 3px 6px;
}
QAbstractItemView::item:hover {
    background-color: #3E3E43;
}
QAbstractItemView::item:selected {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #44639D, stop:1 #284780);
    color: #E7EDF9;
}
QAbstractItemView::indicator {
    width: 15px;
    height: 15px;
}
QAbstractItemView::indicator:unchecked {
    image: url(:/res/ui/inputs/check-off.svg);
}
QAbstractItemView::indicator:checked {
    image: url(:/res/ui/inputs/check-on.svg);
}
QHeaderView {
    background-color: #2C2C2C;
    border: none;
}
QHeaderView::section {
    background-color: #2C2C2C;
    color: #929292;
    border: none;
    padding: 2px 6px;
    font-size: 16px;
}
QTableView {
    gridline-color: #2C2C2C;
}
QTableCornerButton::section {
    background-color: #2C2C2C;
    border: none;
}
QTabWidget#subTabs::pane {
    border: none;
    background: transparent;
    top: 0px;
}
QTabWidget#subTabs::tab-bar {
    left: 0px;
}
QTabWidget#subTabs > QTabBar {
    background: transparent;
}
QTabWidget#subTabs > QTabBar::tab {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #38383C, stop:1 #343435);
    color: #5C5C60;
    border: none;
    border-bottom: 3px solid #2C2C2C;
    padding: 3px 14px 1px 14px;
    margin: 0px 6px 9px 0px;
    min-width: 48px;
    min-height: 20px;
    font-size: 16px;
}
QTabWidget#subTabs > QTabBar::tab:hover:!selected {
    color: #929292;
}
QTabWidget#subTabs > QTabBar::tab:selected {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #44639D, stop:1 #284780);
    color: #E7EDF9;
    border-bottom-color: #29354B;
}
QComboBox {
    background-color: #343435;
    color: #BFBFBF;
    border: none;
    border-bottom: 3px solid #2C2C2C;
    padding: 1px 6px 0px 6px;
    min-height: 17px;
    font-size: 16px;
}
QComboBox:hover {
    background-color: #3A3A3D;
}
QComboBox::drop-down {
    border: none;
    width: 22px;
}
QComboBox::down-arrow {
    image: url(:/res/ui/inputs/chevron-down.svg);
    width: 9px;
    height: 6px;
}
QComboBox QAbstractItemView {
    background-color: #343435;
    color: #BFBFBF;
    selection-background-color: #284780;
    border: 1px solid #2C2C2C;
}
QMenu {
    background-color: #252526;
    color: #BFBFBF;
    border: 1px solid #3F3F42;
    padding: 4px 0px;
    font-size: 16px;
}
QMenu::item {
    padding: 4px 18px;
}
QMenu::item:selected {
    background-color: #284780;
    color: #E7EDF9;
}
QMenu::item:disabled {
    color: #5C5C60;
}
QMenu::separator {
    height: 1px;
    background: #343435;
    margin: 4px 8px;
}
)");
    // %2: platform themes assign item views/headers their own fonts, which
    // QApplication::setFont does not override; the stylesheet does. The weight
    // is pinned to 500 because "Minecraft Ten" (the Figma LAUNCH face, weight
    // 400) also registers as typographic family "Minecraft" where installed.
    return css.arg(fixedFontFamily(), fontFamily());
}

QString signInDialogStyleSheet() {
    static const QString css = QStringLiteral(R"(
QDialog {
    background-color: #252526;
}
QDialog QWidget {
    font-family: "%1";
    font-weight: 500;
}
QLabel {
    color: #929292;
    font-size: 16px;
}
QLabel#title {
    color: #E7EDF9;
    font-size: 24px;
}
QLabel#notice {
    color: #E8B65C;
}
QLabel#code {
    color: #E7EDF9;
    background-color: #343435;
    border: none;
    border-bottom: 3px solid #2C2C2C;
    font-family: "%2";
    font-size: 34px;
    font-weight: bold;
    padding: 10px 0px 8px 0px;
}
QPushButton {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #3D3D42, stop:1 #343435);
    background-clip: padding;
    color: #BFBFBF;
    border: none;
    border-top: 1px solid #47474D;
    border-bottom: 3px solid #2C2C2C;
    padding: 0px 14px;
    min-height: 24px;
    font-size: 16px;
}
QPushButton:hover {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #46464C, stop:1 #3B3B3E);
    color: #E7EDF9;
}
QPushButton:pressed {
    border-top: 3px solid transparent;
    border-bottom: 1px solid #2C2C2C;
}
QPushButton:disabled {
    background-color: #303031;
    color: #5C5C60;
    border-top-color: #333335;
    border-bottom-color: #2A2A2B;
}
QPushButton#primary {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #5B8DEE, stop:0.55 #4779D8, stop:1 #3A63B1);
    color: #E7EDF9;
    border-top-color: #76A5FF;
    border-bottom-color: #29354B;
}
QPushButton#primary:hover {
    background-color: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #6A98F3, stop:0.55 #5285E2, stop:1 #4069BA);
}
QPushButton#primary:disabled {
    background-color: #33415C;
    color: #7F8BA3;
    border-top-color: #3A4A68;
    border-bottom-color: #26304A;
}
QToolTip {
    background-color: #252526;
    color: #BFBFBF;
    border: 1px solid #3F3F42;
    padding: 4px 6px;
}
)");
    return css.arg(fontFamily(), fixedFontFamily());
}

// ---- Painting --------------------------------------------------------------

QColor mix(const QColor& a, const QColor& b, qreal t) {
    t = qBound<qreal>(0, t, 1);
    return QColor::fromRgbF(a.redF() + (b.redF() - a.redF()) * t,
                            a.greenF() + (b.greenF() - a.greenF()) * t,
                            a.blueF() + (b.blueF() - a.blueF()) * t,
                            a.alphaF() + (b.alphaF() - a.alphaF()) * t);
}

void paintInnerGlow(QPainter& painter, const QRectF& rect, const QColor& glow, qreal spread, qreal blur) {
    if (glow.alpha() == 0 || rect.width() < 2 || rect.height() < 2)
        return;
    painter.save();
    painter.setRenderHint(QPainter::Antialiasing, false);
    const qreal baseAlpha = glow.alphaF();
    const int rings = int(std::ceil(spread + 3 * blur));
    for (int k = 0; k < rings; ++k) {
        const qreal w = rect.width() - 2 * k;
        const qreal h = rect.height() - 2 * k;
        if (w <= 0 || h <= 0)
            break;
        const qreal alpha = baseAlpha * (1.0 - normalCdf((k + 0.5 - spread) / blur));
        if (alpha < 0.004)
            break;
        QColor color = glow;
        color.setAlphaF(alpha);
        const qreal l = rect.left() + k;
        const qreal t = rect.top() + k;
        painter.fillRect(QRectF(l, t, w, 1), color);
        if (h > 1)
            painter.fillRect(QRectF(l, t + h - 1, w, 1), color);
        if (h > 2) {
            painter.fillRect(QRectF(l, t + 1, 1, h - 2), color);
            if (w > 1)
                painter.fillRect(QRectF(l + w - 1, t + 1, 1, h - 2), color);
        }
    }
    painter.restore();
}

void paintSurface(QPainter& painter, const QRectF& face, const Surface& surface, qreal edgeHeight) {
    painter.save();
    painter.setRenderHint(QPainter::Antialiasing, false);
    painter.setPen(Qt::NoPen);
    if (edgeHeight > 0)
        painter.fillRect(QRectF(face.left(), face.bottom(), face.width(), edgeHeight), surface.edge);
    QLinearGradient gradient(face.topLeft(), face.bottomLeft());
    gradient.setColorAt(0, surface.top);
    if (surface.middle.isValid())
        gradient.setColorAt(surface.middleAt, surface.middle);
    gradient.setColorAt(1, surface.bottom);
    painter.fillRect(face, gradient);
    paintInnerGlow(painter, face, surface.glow);
    painter.restore();
}

void paintThumb(QPainter& painter, const QPointF& topLeft, bool hovered, bool pressed, bool enabled) {
    // Figma thumb (30 x 21): base, 3 px bevel, inner row and two grip columns.
    QColor base = rgb(Color::Accent);
    QColor light = rgb(Color::AccentLight);
    QColor mid = rgb(Color::AccentMid);
    QColor dark = rgb(Color::AccentDark);
    QColor deep = rgb(Color::AccentDeep);
    if (!enabled) {
        base = rgb(0x4A4A4E);
        light = rgb(0x55555A);
        mid = rgb(0x515156);
        dark = rgb(0x3A3A3E);
        deep = rgb(0x333336);
    } else if (pressed) {
        for (QColor* c : {&base, &light, &mid, &dark, &deep})
            *c = mix(*c, Qt::black, 0.12);
    } else if (hovered) {
        for (QColor* c : {&base, &light, &mid, &dark, &deep})
            *c = mix(*c, Qt::white, 0.10);
    }
    painter.save();
    painter.setRenderHint(QPainter::Antialiasing, false);
    painter.translate(topLeft);
    painter.fillRect(QRectF(0, 0, 30, 21), base);
    painter.fillRect(QRectF(0, 18, 30, 3), dark);
    painter.fillRect(QRectF(0, 0, 30, 3), light);
    painter.fillRect(QRectF(27, 0, 3, 18), light);
    painter.fillRect(QRectF(0, 0, 3, 18), light);
    painter.fillRect(QRectF(0, 15, 30, 3), mid);
    for (qreal x : {18.0, 24.0}) {
        painter.fillRect(QRectF(x, 3, 3, 12), dark);
        painter.fillRect(QRectF(x, 9, 3, 6), deep);
    }
    painter.restore();
}

QPainterPath notchedRect(const QRectF& r, qreal n) {
    QPainterPath path;
    path.moveTo(r.left() + n, r.top());
    path.lineTo(r.right() - n, r.top());
    path.lineTo(r.right() - n, r.top() + n);
    path.lineTo(r.right(), r.top() + n);
    path.lineTo(r.right(), r.bottom() - n);
    path.lineTo(r.right() - n, r.bottom() - n);
    path.lineTo(r.right() - n, r.bottom());
    path.lineTo(r.left() + n, r.bottom());
    path.lineTo(r.left() + n, r.bottom() - n);
    path.lineTo(r.left(), r.bottom() - n);
    path.lineTo(r.left(), r.top() + n);
    path.lineTo(r.left() + n, r.top() + n);
    path.closeSubpath();
    return path;
}

QSizeF glyphSize(Glyph glyph) {
    return glyphRenderer(glyph)->viewBoxF().size();
}

void paintGlyph(QPainter& painter, Glyph glyph, const QRectF& target, const QColor& color) {
    QSvgRenderer* renderer = glyphRenderer(glyph);
    if (!renderer->isValid() || target.isEmpty())
        return;
    const QSizeF natural = renderer->viewBoxF().size();
    const qreal scale = qMin(target.width() / natural.width(), target.height() / natural.height());
    const QSizeF size = natural * scale;
    const QRectF drawRect(target.center() - QPointF(size.width() / 2, size.height() / 2), size);
    const qreal dpr = painter.device() ? painter.device()->devicePixelRatioF() : 1.0;
    const QSize pixels(qCeil(size.width() * dpr), qCeil(size.height() * dpr));
    const QString key = QStringLiteral("atw-glyph:%1:%2x%3:%4")
                            .arg(static_cast<int>(glyph)).arg(pixels.width()).arg(pixels.height())
                            .arg(color.rgba(), 8, 16, QLatin1Char('0'));
    QPixmap pixmap;
    if (!QPixmapCache::find(key, &pixmap)) {
        QImage image(pixels, QImage::Format_ARGB32_Premultiplied);
        image.fill(Qt::transparent);
        QPainter imagePainter(&image);
        imagePainter.setRenderHint(QPainter::Antialiasing);
        renderer->render(&imagePainter, QRectF(QPointF(0, 0), QSizeF(pixels)));
        imagePainter.setCompositionMode(QPainter::CompositionMode_SourceIn);
        imagePainter.fillRect(image.rect(), color);
        imagePainter.end();
        pixmap = QPixmap::fromImage(image);
        pixmap.setDevicePixelRatio(dpr);
        QPixmapCache::insert(key, pixmap);
    }
    painter.drawPixmap(drawRect.topLeft(), pixmap);
}

QImage blurred(const QImage& image, int radius) {
    if (radius <= 0 || image.isNull())
        return image;
    QImage source = image.convertToFormat(QImage::Format_ARGB32_Premultiplied);
    const int width = source.width();
    const int height = source.height();
    std::vector<quint32> a(size_t(width) * height);
    std::vector<quint32> b(size_t(width) * height);
    for (int y = 0; y < height; ++y)
        std::memcpy(&a[size_t(y) * width], source.constScanLine(y), size_t(width) * 4);
    // Three box passes approximate a Gaussian of sigma ~= radius.
    for (int pass = 0; pass < 3; ++pass) {
        boxBlurPass(a, b, width, height, radius, true);
        boxBlurPass(b, a, width, height, radius, false);
    }
    QImage result(width, height, QImage::Format_ARGB32_Premultiplied);
    for (int y = 0; y < height; ++y)
        std::memcpy(result.scanLine(y), &a[size_t(y) * width], size_t(width) * 4);
    result.setDevicePixelRatio(image.devicePixelRatio());
    return result;
}

} // namespace AtwTheme
