//
// Figma slider: 12 px recessed track with a 3 px edge and the 30 x 21 pixel
// thumb centred on it. Drag the thumb, click the track to jump, or use keys.
//

#ifndef ATW_GUI_PIXELSLIDER_H
#define ATW_GUI_PIXELSLIDER_H

#include <QSlider>

class PixelSlider : public QSlider {
    Q_OBJECT
public:
    explicit PixelSlider(QWidget* parent = nullptr);

    QSize sizeHint() const override;
    QSize minimumSizeHint() const override;

protected:
    void paintEvent(QPaintEvent* event) override;
    void mousePressEvent(QMouseEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;
    void wheelEvent(QWheelEvent* event) override;
    bool event(QEvent* event) override;
    void focusInEvent(QFocusEvent* event) override;
    void focusOutEvent(QFocusEvent* event) override;

private:
    QRectF thumbRect() const;
    int valueAt(qreal thumbLeft) const;

    bool hovered = false;
    bool dragging = false;
    bool keyboardFocus = false;
    qreal grabOffset = 0;
};

#endif // ATW_GUI_PIXELSLIDER_H
