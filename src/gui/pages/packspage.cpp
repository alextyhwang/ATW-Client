//
// Packs tab - placeholder until resource-pack management exists.
//

#include "packspage.h"

#include <QLabel>
#include <QPainter>
#include <QPolygonF>
#include <QVBoxLayout>

#include "gui/style/atwtheme.h"

namespace {
// Isometric pixel block in the thumb palette, echoing the raised controls.
class PackBlock : public QWidget {
public:
    explicit PackBlock(QWidget* parent = nullptr) : QWidget(parent) {
        setFixedSize(66, 66);
        setAttribute(Qt::WA_TransparentForMouseEvents);
    }

protected:
    void paintEvent(QPaintEvent*) override {
        QPainter painter(this);
        painter.setRenderHint(QPainter::Antialiasing, false);
        painter.setPen(Qt::NoPen);
        const QPolygonF top({QPointF(33, 6), QPointF(60, 19), QPointF(33, 32), QPointF(6, 19)});
        const QPolygonF left({QPointF(6, 19), QPointF(33, 32), QPointF(33, 60), QPointF(6, 47)});
        const QPolygonF right({QPointF(33, 32), QPointF(60, 19), QPointF(60, 47), QPointF(33, 60)});
        painter.setBrush(QColor(AtwTheme::Color::AccentLight));
        painter.drawPolygon(top);
        painter.setBrush(QColor(AtwTheme::Color::Accent));
        painter.drawPolygon(left);
        painter.setBrush(QColor(AtwTheme::Color::AccentDark));
        painter.drawPolygon(right);
        // Pixel texture on the faces.
        painter.setBrush(QColor(0x76, 0xA5, 0xFF));
        painter.drawRect(QRectF(30, 14, 6, 3));
        painter.drawRect(QRectF(42, 19, 6, 3));
        painter.setBrush(QColor(AtwTheme::Color::AccentDeep));
        painter.drawRect(QRectF(45, 33, 3, 9));
        painter.drawRect(QRectF(15, 33, 3, 6));
    }
};
}

PacksPage::PacksPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    QLabel* title = new QLabel(QStringLiteral("Resource packs"));
    title->setObjectName(QStringLiteral("emptyTitle"));
    title->setAlignment(Qt::AlignHCenter);
    QLabel* body = new QLabel(QStringLiteral("Pack management is coming soon."));
    body->setObjectName(QStringLiteral("emptyBody"));
    body->setAlignment(Qt::AlignHCenter);
    body->setWordWrap(true);

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);
    layout->addStretch(1);
    layout->addWidget(new PackBlock(this), 0, Qt::AlignHCenter);
    layout->addSpacing(18);
    layout->addWidget(title);
    layout->addSpacing(8);
    layout->addWidget(body);
    layout->addStretch(1);
}
