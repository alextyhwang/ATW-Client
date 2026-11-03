//
// Frameless window surface from the Figma reference: a 10 px rounded frame
// (#252526 header over a #343435 body), a soft drop shadow where the platform
// does not draw one, header dragging and edge resizing.
//

#ifndef ATW_GUI_WINDOWFRAME_H
#define ATW_GUI_WINDOWFRAME_H

#include <QPixmap>
#include <QWidget>

class WindowFrame : public QWidget {
    Q_OBJECT
public:
    explicit WindowFrame(QWidget* parent = nullptr);

    // Transparent margin around the visible frame that holds the shadow.
    static int shadowMargin();
    QRect frameRect() const;

protected:
    void paintEvent(QPaintEvent* event) override;
    void mousePressEvent(QMouseEvent* event) override;
    void mouseMoveEvent(QMouseEvent* event) override;
    void mouseReleaseEvent(QMouseEvent* event) override;
    void leaveEvent(QEvent* event) override;

private:
    Qt::Edges edgesAt(const QPoint& position) const;
    void updateCursor(const QPoint& position);
    void paintShadow(QPainter& painter, const QRect& frame);
    QPixmap shadowTile(qreal dpr);

    QPixmap tile;
    qreal tileDpr = 0;

    Qt::Edges resizeEdges;
    QRect startGeometry;
    QPoint startGlobal;
    bool manualResize = false;
    bool manualMove = false;
    QPoint moveOffset;
};

#endif // ATW_GUI_WINDOWFRAME_H
