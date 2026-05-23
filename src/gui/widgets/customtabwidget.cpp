//
// Tab widget with custom tab bar - dark gray container
//

#include "customtabwidget.h"

CustomTabWidget::CustomTabWidget(QWidget* parent) : QTabWidget(parent) {
    m_tabBar = new CustomTabBar(this);
    m_tabBar->setObjectName("mainTabBar");
    setTabBar(m_tabBar);
}

void CustomTabWidget::setTabType(int index, const QString& type) {
    m_tabBar->setTabType(index, type);
}
