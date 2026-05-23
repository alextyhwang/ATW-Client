#include <QApplication>
#include <QFontDatabase>

#include "gui/mainwindow.h"

namespace {
void installBundledFont(QApplication& app) {
    int fontId = QFontDatabase::addApplicationFont(QStringLiteral(":/res/fonts/Minecraft.ttf"));
    QString fontFamily = QStringLiteral("Minecraft");
    if (fontId < 0)
        return;

    QStringList families = QFontDatabase::applicationFontFamilies(fontId);
    if (families.isEmpty())
        return;

    for (const QString& family : families) {
        if (family.contains(QStringLiteral("Medium"), Qt::CaseInsensitive)) {
            fontFamily = family;
            break;
        }
    }
    if (fontFamily == QStringLiteral("Minecraft"))
        fontFamily = families.first();

    QFont mainFont(fontFamily, 14);
    mainFont.setStyleStrategy(QFont::PreferAntialias);
    mainFont.setWeight(QFont::Medium);
    mainFont.setFamily(fontFamily);
    app.setFont(mainFont);
}
}

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("atw-config"));
    app.setStyle(QStringLiteral("Fusion"));
    installBundledFont(app);

    MainWindow mainWindow;
    mainWindow.show();
    return QApplication::exec();
}

