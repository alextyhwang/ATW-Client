#include "javaagents.h"

bool Atw::Core::JavaAgents::containsPath(const QList<Agent>& agents, const QString& path) {
    for (const Agent& agent : agents) {
        if (agent.path == path)
            return true;
    }

    return false;
}

bool Atw::Core::JavaAgents::add(QList<Agent>& agents, const QString& path, const QString& option) {
    if (containsPath(agents, path))
        return false;

    agents.append({path, option});
    return true;
}

void Atw::Core::JavaAgents::setEnabled(Agent& agent, bool enabled) {
    agent.enabled = enabled;
}

void Atw::Core::JavaAgents::setOption(Agent& agent, const QString& option) {
    agent.option = option;
}

