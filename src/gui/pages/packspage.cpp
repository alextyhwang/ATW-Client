//
// Packs tab - placeholder
//

#include "packspage.h"

#include <QVBoxLayout>
#include <QLabel>

PacksPage::PacksPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->addWidget(new QLabel(QStringLiteral("Packs - Coming soon")));
}
