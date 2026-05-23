//
// Custom tab bar with tab-specific SVGs (game, mods, packs, other)
//

#ifndef CUSTOMTABBAR_H
#define CUSTOMTABBAR_H

#include <QTabBar>

class CustomTabBar : public QTabBar {
    Q_OBJECT
public:
    explicit CustomTabBar(QWidget* parent = nullptr);

    // Tab type for SVG selection: "game", "mods", "packs", "other"
    void setTabType(int index, const QString& type);

    QSize tabSizeHint(int index) const override;

protected:
    void paintEvent(QPaintEvent* event) override;

private:
    QStringList tabTypes;  // SVG base name per tab index
};

#endif // CUSTOMTABBAR_H
