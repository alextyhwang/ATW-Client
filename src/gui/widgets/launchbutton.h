//
// LAUNCH hero button from the Figma reference: 322 x 81 face with a 6 px edge,
// blue (Launch), red (Close, game running in-process) or busy states.
//

#ifndef ATW_GUI_LAUNCHBUTTON_H
#define ATW_GUI_LAUNCHBUTTON_H

#include <QAbstractButton>

class QVariantAnimation;

class LaunchButton : public QAbstractButton {
Q_OBJECT
public:
    enum class Mode {
        Launch,   // ready to start the game
        Close,    // in-process game running; clicking ends it (legacy launcher)
        Busy      // handed off to atw-launch; not clickable until it exits
    };

    explicit LaunchButton(QWidget* parent = nullptr);

    void setMode(Mode mode);
    Mode mode() const { return currentMode; }

    QSize sizeHint() const override;
    QSize minimumSizeHint() const override;

protected:
    void paintEvent(QPaintEvent* event) override;
    bool event(QEvent* event) override;
    void focusInEvent(QFocusEvent* event) override;
    void focusOutEvent(QFocusEvent* event) override;

private:
    QImage softShadow(qreal dpr);

    Mode currentMode = Mode::Launch;
    bool hovered = false;
    bool keyboardFocus = false;
    qreal pulse = 0;
    QVariantAnimation* busyAnimation = nullptr;
    QImage cachedShadow;
    int cachedShadowMode = -1;
    qreal cachedShadowDpr = 0;
};

#endif // ATW_GUI_LAUNCHBUTTON_H
