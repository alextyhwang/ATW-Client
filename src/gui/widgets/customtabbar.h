//
// Main tab strip from the Figma reference: raised 3D tabs separated by 9 px
// gaps, painted in code so they stay crisp at any size or display scale.
//

#ifndef CUSTOMTABBAR_H
#define CUSTOMTABBAR_H

#include <QTabBar>

class CustomTabBar : public QTabBar {
    Q_OBJECT
public:
    explicit CustomTabBar(QWidget* parent = nullptr);

    // Face rectangle of a tab inside its slot (slots are adjacent; faces are
    // separated by the Figma gap).
    QRectF faceRect(int index) const;

    QSize sizeHint() const override;
    QSize minimumSizeHint() const override;

protected:
    QSize tabSizeHint(int index) const override;
    QSize minimumTabSizeHint(int index) const override;
    void paintEvent(QPaintEvent* event) override;
    bool event(QEvent* event) override;
    void mousePressEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;
    void focusInEvent(QFocusEvent* event) override;
    void focusOutEvent(QFocusEvent* event) override;

private:
    int hoveredIndex = -1;
    int pressedIndex = -1;
    bool keyboardFocus = false;
};

#endif // CUSTOMTABBAR_H
