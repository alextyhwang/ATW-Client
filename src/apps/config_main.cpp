#include <QApplication>
#include <QIcon>

#include "gui/mainwindow.h"
#include "gui/style/atwtheme.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("atw-config"));
    QApplication::setApplicationDisplayName(QStringLiteral("ATW Client Settings"));
    QApplication::setWindowIcon(QIcon(QStringLiteral(":/res/branding/atw.svg")));
    // Fusion, the bundled pixel font, palette and stylesheet (docs/UI_DESIGN.md).
    AtwTheme::installApplicationTheme(app);

    MainWindow mainWindow;
    mainWindow.show();
    return QApplication::exec();
}
