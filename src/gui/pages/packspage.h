//
// Packs tab - placeholder
//

#ifndef PACKSPAGE_H
#define PACKSPAGE_H

#include "configurationpage.h"

class PacksPage : public ConfigurationPage {
    Q_OBJECT
public:
    explicit PacksPage(Config& config, QWidget* parent = nullptr);

    QString title() override { return QStringLiteral("Packs"); }
    QString description() override { return QStringLiteral("Resource packs."); }
    QIcon icon() override { return QIcon(":/res/icons/cog.svg"); }
    void apply() override {}
    void load() override {}
};

#endif // PACKSPAGE_H
