#include <QApplication>

#include "gui/mainwindow.h"
#include "gui/style/atwtheme.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("atw-config"));
    // Fusion, the bundled pixel font, palette and stylesheet (docs/UI_DESIGN.md).
    AtwTheme::installApplicationTheme(app);

    MainWindow mainWindow;
    mainWindow.show();
    return QApplication::exec();
}
