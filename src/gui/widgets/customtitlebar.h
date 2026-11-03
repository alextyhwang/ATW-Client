//
// Frameless title row from the Figma reference: debossed ATW badge on the
// left, close tile on the right. Dragging the row moves the window.
//

#ifndef CUSTOMTITLEBAR_H
#define CUSTOMTITLEBAR_H

#include <QAbstractButton>
#include <QPoint>
#include <QWidget>

class TitleBadge : public QWidget {
    Q_OBJECT
public:
    explicit TitleBadge(QWidget* parent = nullptr);
    QSize sizeHint() const override { return QSize(65, 34); }

protected:
    void paintEvent(QPaintEvent* event) override;
};

class TitleCloseButton : public QAbstractButton {
    Q_OBJECT
public:
    explicit TitleCloseButton(QWidget* parent = nullptr);
    QSize sizeHint() const override { return QSize(38, 38); }

protected:
    void paintEvent(QPaintEvent* event) override;
    bool event(QEvent* event) override;

private:
    bool hovered = false;
};

class CustomTitleBar : public QWidget {
    Q_OBJECT
public:
    explicit CustomTitleBar(QWidget* parent = nullptr);

protected:
    void mousePressEvent(QMouseEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;

private:
    TitleBadge* badge;
    TitleCloseButton* closeButton;
    QPoint dragOffset;
    bool manualDrag = false;
};

// Starts a native window move (Aero Snap on Windows, native drag on macOS).
// Returns false when the platform cannot, so callers can move manually.
bool startWindowMove(QWidget* widget);

#endif // CUSTOMTITLEBAR_H
