//
// Tab widget with custom tab bar for tab-specific SVGs
//

#ifndef CUSTOMTABWIDGET_H
#define CUSTOMTABWIDGET_H

#include <QTabWidget>
#include "customtabbar.h"

class CustomTabWidget : public QTabWidget {
    Q_OBJECT
public:
    explicit CustomTabWidget(QWidget* parent = nullptr);

    CustomTabBar* tabBar() const { return m_tabBar; }
    void setTabType(int index, const QString& type);

private:
    CustomTabBar* m_tabBar;
};

#endif // CUSTOMTABWIDGET_H
