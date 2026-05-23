//
// Created by nils on 12/2/21.
//

#include "filechooser.h"
#include <QHBoxLayout>
#include <QFileDialog>
#include <QEvent>

FileChooser::FileChooser(QFileDialog::FileMode mode, QWidget *parent) : QWidget(parent){
    QHBoxLayout* layout = new QHBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);

    pathEdit = new QLineEdit();
    pathEdit->setCursor(Qt::PointingHandCursor);
    pathEdit->setReadOnly(true); // Make it act like a button

    // When clicked, open the file dialog
    // We'll use event filter to detect clicks on the line edit
    pathEdit->installEventFilter(this);

    // Save mode to use in event filter
    this->dialogMode = mode;

    layout->addWidget(pathEdit);
}

bool FileChooser::eventFilter(QObject* watched, QEvent* event) {
    if (watched == pathEdit && event->type() == QEvent::MouseButtonRelease) {
        QFileDialog fileDialog;
        fileDialog.setFileMode(dialogMode);
        if (dialogMode == QFileDialog::Directory)
            fileDialog.setOption(QFileDialog::ShowDirsOnly);
        if (fileDialog.exec() == QDialog::Accepted) {
            QStringList files = fileDialog.selectedFiles();
            if (files.length() > 0) {
                pathEdit->setText(files[0]);
            }
        }
        return true;
    }
    return QWidget::eventFilter(watched, event);
}

QString FileChooser::getPath() {
    return pathEdit->text();
}

void FileChooser::setPath(const QString &path) {
    pathEdit->setText(path);
}


