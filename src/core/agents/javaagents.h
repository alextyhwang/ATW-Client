#ifndef ATW_CLIENT_CORE_JAVAAGENTS_H
#define ATW_CLIENT_CORE_JAVAAGENTS_H

#include "config/config.h"

#include <QList>
#include <QString>

namespace Atw::Core::JavaAgents {
    bool containsPath(const QList<Agent>& agents, const QString& path);
    bool add(QList<Agent>& agents, const QString& path, const QString& option);
    void setEnabled(Agent& agent, bool enabled);
    void setOption(Agent& agent, const QString& option);
}

#endif

