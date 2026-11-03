//
// Figma toggle (51 x 21): pixel thumb on a recessed track, sliding between
// off (left) and on (right). Keeps the QCheckBox API and accessibility role.
//

#ifndef ATW_GUI_PIXELTOGGLE_H
#define ATW_GUI_PIXELTOGGLE_H

#include <QCheckBox>

class QVariantAnimation;

class PixelToggle : public QCheckBox {
    Q_OBJECT
public:
    explicit PixelToggle(QWidget* parent = nullptr);

    QSize sizeHint() const override;
    QSize minimumSizeHint() const override;

protected:
    void paintEvent(QPaintEvent* event) override;
    bool hitButton(const QPoint& position) const override;
    bool event(QEvent* event) override;
    void checkStateSet() override;
    void nextCheckState() override;
    void focusInEvent(QFocusEvent* event) override;
    void focusOutEvent(QFocusEvent* event) override;

private:
    void animateTo(bool on);

    qreal position = 0;            // 0 = off (thumb left), 1 = on (thumb right)
    bool hovered = false;
    bool keyboardFocus = false;
    QVariantAnimation* animation = nullptr;
};

#endif // ATW_GUI_PIXELTOGGLE_H
