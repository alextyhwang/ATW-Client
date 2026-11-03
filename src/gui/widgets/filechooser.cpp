//
// Created by nils on 12/2/21.
//

#include "filechooser.h"
#include <QHBoxLayout>
#include <QFileDialog>
#include <QEvent>
#include <QKeyEvent>
#include <QPainter>
#include <QtMath>
#include <QStyleOptionFrame>

#include "gui/style/atwtheme.h"

namespace {
// Shows the start of a long path with "..." (the pixel font has no ellipsis
// glyph), matching the Figma field. The full path stays the accessible value.
class PathField : public QLineEdit {
public:
    explicit PathField(QWidget* parent = nullptr) : QLineEdit(parent) {
        setAttribute(Qt::WA_Hover);
    }

protected:
    void paintEvent(QPaintEvent*) override {
        QPainter painter(this);
        QStyleOptionFrame panel;
        initStyleOption(&panel);
        style()->drawPrimitive(QStyle::PE_PanelLineEdit, &panel, &painter, this);

        const QFont textFont = AtwTheme::font(16);
        const QFontMetricsF metrics(textFont);
        const qreal left = 5;
        const qreal available = width() - left - 6;
        const bool empty = text().isEmpty();
        QString shown = empty ? placeholderText() : text();
        if (metrics.horizontalAdvance(shown) > available) {
            const QString dots = QStringLiteral("...");
            while (!shown.isEmpty() && metrics.horizontalAdvance(shown + dots) > available)
                shown.chop(1);
            shown += dots;
        }
        QColor color(AtwTheme::Color::InputText);
        if (!isEnabled())
            color = QColor(0x4A, 0x4A, 0x4E);
        else if (empty)
            color = QColor(AtwTheme::Color::InactiveText);
        if (isEnabled() && (underMouse() || hasFocus()))
            color = QColor(AtwTheme::Color::Value);
        painter.setFont(textFont);
        painter.setPen(color);
        // Cap-centred baseline: Figma places 12 px caps 4 px below a 21 px field top.
        const qreal baseline = qFloor((height() + metrics.ascent()) / 2);
        painter.drawText(QPointF(left, baseline), shown);
    }
};
}

FileChooser::FileChooser(QFileDialog::FileMode mode, QWidget *parent) : QWidget(parent){
    QHBoxLayout* layout = new QHBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);

    pathEdit = new PathField();
    pathEdit->setCursor(Qt::PointingHandCursor);
    pathEdit->setReadOnly(true); // Make it act like a button
    pathEdit->setFixedHeight(AtwTheme::Metrics::FieldHeight);
    pathEdit->setContextMenuPolicy(Qt::NoContextMenu);

    // Clicks (and Enter/Space) on the field open the file dialog.
    pathEdit->installEventFilter(this);

    // Save mode to use in event filter
    this->dialogMode = mode;

    layout->addWidget(pathEdit);
}

void FileChooser::choose() {
    QFileDialog fileDialog;
    fileDialog.setFileMode(dialogMode);
    if (dialogMode == QFileDialog::Directory)
        fileDialog.setOption(QFileDialog::ShowDirsOnly);
    if (fileDialog.exec() == QDialog::Accepted) {
        QStringList files = fileDialog.selectedFiles();
        if (files.length() > 0) {
            setPath(files[0]);
        }
    }
}

bool FileChooser::eventFilter(QObject* watched, QEvent* event) {
    if (watched == pathEdit) {
        if (event->type() == QEvent::MouseButtonRelease) {
            choose();
            return true;
        }
        if (event->type() == QEvent::KeyPress) {
            const int key = static_cast<QKeyEvent*>(event)->key();
            if (key == Qt::Key_Return || key == Qt::Key_Enter || key == Qt::Key_Space) {
                choose();
                return true;
            }
        }
    }
    return QWidget::eventFilter(watched, event);
}

QString FileChooser::getPath() {
    return pathEdit->text();
}

void FileChooser::setPath(const QString &path) {
    pathEdit->setText(path);
    pathEdit->setToolTip(path);
    pathEdit->setCursorPosition(0);
}

void FileChooser::setPlaceholderText(const QString& text) {
    pathEdit->setPlaceholderText(text);
}

void FileChooser::setAccessibleLabel(const QString& label) {
    pathEdit->setAccessibleName(label);
    setAccessibleName(label);
}
