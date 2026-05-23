#ifndef ATW_CLIENT_CORE_WEAVEMODS_H
#define ATW_CLIENT_CORE_WEAVEMODS_H

#include "config/config.h"

#include <QFileInfo>
#include <QList>
#include <QString>

namespace Atw::Core::WeaveMods {
    QList<Mod> list();
    bool contains(const QList<Mod>& mods, const QString& name);
    bool install(const QFileInfo& modFileInfo, Mod* installed = nullptr);
    bool remove(const Mod& mod);
    bool setEnabled(Mod& mod, bool enabled);
    bool rename(Mod& mod, const QString& newName);
}

#endif
