//
// ATW launcher/config visual system. Tokens and geometry come from the Figma
// reference (Frame 21, node 2427:642); see docs/UI_DESIGN.md. Shared by
// atw-config and by the sign-in dialog that atw-launch also shows.
//

#ifndef ATW_GUI_ATWTHEME_H
#define ATW_GUI_ATWTHEME_H

#include <QColor>
#include <QFont>
#include <QImage>
#include <QRectF>
#include <QString>

class QApplication;
class QPainter;
class QPainterPath;

namespace AtwTheme {

// ---- Colour tokens (Figma) -------------------------------------------------
namespace Color {
    inline constexpr QRgb Frame = 0x252526;        // header, tab tray, card
    inline constexpr QRgb Body = 0x343435;         // body, inputs, title tiles
    inline constexpr QRgb Edge = 0x2C2C2C;         // 3D edge under gray surfaces
    inline constexpr QRgb Label = 0x929292;
    inline constexpr QRgb Value = 0xBFBFBF;
    inline constexpr QRgb InputText = 0x6C6C6C;
    inline constexpr QRgb InactiveText = 0x5C5C60;
    inline constexpr QRgb ActiveText = 0xE7EDF9;
    inline constexpr QRgb Accent = 0x4779D8;
    inline constexpr QRgb AccentLight = 0x5487E4;
    inline constexpr QRgb AccentMid = 0x5283DE;
    inline constexpr QRgb AccentDark = 0x33599F;
    inline constexpr QRgb AccentDeep = 0x2A4F92;
    inline constexpr QRgb Glow = 0x88B0FF;         // inner glow on blue surfaces
    inline constexpr QRgb Danger = 0xD84747;
    inline constexpr QRgb Warning = 0xE8B65C;
}

// ---- Geometry (Figma frame 572 x 749) -------------------------------------
namespace Metrics {
    inline constexpr int FrameWidth = 572;
    inline constexpr int FrameHeight = 749;
    inline constexpr int FrameRadius = 10;
    inline constexpr int HeaderHeight = 160;       // header/body split
    inline constexpr int Margin = 22;              // outer horizontal margin
    inline constexpr int TitleTop = 22;
    inline constexpr int TitleRowHeight = 38;
    inline constexpr int LaunchTop = 107;
    inline constexpr int LaunchWidth = 322;
    inline constexpr int LaunchFaceHeight = 81;
    inline constexpr int LaunchEdge = 6;
    inline constexpr int TrayTop = 210;
    inline constexpr int TrayHeight = 66;
    inline constexpr int TrayPadding = 9;
    inline constexpr int TabGap = 9;
    inline constexpr int TabFaceHeight = 45;
    inline constexpr int TabEdge = 4;
    inline constexpr int CardTop = 288;
    inline constexpr int CardPadding = 18;
    inline constexpr int BottomMargin = 27;
    inline constexpr int GroupGap = 14;
    inline constexpr int LabelBlock = 24;          // 15 px label box + 9 px gap
    inline constexpr int FieldHeight = 21;
    inline constexpr int JvmHeight = 126;
    inline constexpr int ToggleWidth = 51;
    inline constexpr int ToggleHeight = 21;
    inline constexpr int ThumbWidth = 30;
    inline constexpr int ThumbHeight = 21;
    inline constexpr int TrackHeight = 12;
    inline constexpr int TrackTop = 5;             // track offset inside the 21 px slider
}

// A raised 3D surface: vertical gradient face, inner glow and a hard edge
// below the face (Figma: inset 0 0 7px 4px glow, 0 Npx 0 edge).
struct Surface {
    QColor top;
    QColor middle;          // invalid colour = two-stop gradient
    qreal middleAt = 0.543269;
    QColor bottom;
    QColor edge;
    QColor glow;            // includes alpha
};

Surface launchSurface();
Surface closeSurface();
Surface selectedTabSurface();
Surface tabSurface();

enum class Glyph { Launch, Close, Atw, CloseCross };

// Fonts. ensureFonts() registers the bundled pixel font once; it is safe to
// call from any executable before showing styled UI.
void ensureFonts();
QString fontFamily();
QString fixedFontFamily();
QFont font(qreal pixelSize);

// Application-wide look for atw-config (Fusion + palette + stylesheet).
void installApplicationTheme(QApplication& app);
QString applicationStyleSheet();
QString signInDialogStyleSheet();

// Painting primitives used by the custom widgets.
void paintSurface(QPainter& painter, const QRectF& face, const Surface& surface, qreal edgeHeight);
void paintInnerGlow(QPainter& painter, const QRectF& rect, const QColor& glow, qreal spread = 4, qreal blur = 3.5);
void paintThumb(QPainter& painter, const QPointF& topLeft, bool hovered, bool pressed, bool enabled = true);
QPainterPath notchedRect(const QRectF& rect, qreal notch);
// Draws a vector glyph from res/ui into target (scaled to fit, centred).
void paintGlyph(QPainter& painter, Glyph glyph, const QRectF& target, const QColor& color);
// Natural glyph bounds in its own coordinate space (used for proportional sizing).
QSizeF glyphSize(Glyph glyph);
QImage blurred(const QImage& image, int radius);
QColor mix(const QColor& a, const QColor& b, qreal t);

} // namespace AtwTheme

#endif // ATW_GUI_ATWTHEME_H
