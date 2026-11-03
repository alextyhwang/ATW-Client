//
// Redacted online-readiness report. See onlinereadiness.h.
//

#include "onlinereadiness.h"

#include <QEventLoop>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRandomGenerator>
#include <QRegularExpression>
#include <QTextStream>
#include <QUrlQuery>

#include "auth/accountstore.h"
#include "util/fs.h"

namespace {
const int timeoutMs = 15000;

struct Reply {
    int status = 0;
    QByteArray body;
    QString error;
};

Reply request(QNetworkAccessManager& network, QNetworkRequest req, const QByteArray* postBody = nullptr) {
    req.setTransferTimeout(timeoutMs);
    req.setRawHeader("Accept", "application/json");
    QNetworkReply* reply = postBody ? network.post(req, *postBody) : network.get(req);
    QEventLoop loop;
    QObject::connect(reply, &QNetworkReply::finished, &loop, &QEventLoop::quit);
    loop.exec();
    Reply result;
    result.status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    result.body = reply->readAll();
    if (reply->error() != QNetworkReply::NoError && result.status == 0)
        result.error = reply->errorString();
    reply->deleteLater();
    return result;
}

QString describe(const Reply& reply) {
    return reply.status ? QStringLiteral("HTTP %1").arg(reply.status) : QStringLiteral("network error: ") + reply.error;
}

QString lastGameLog() {
    QFile file(FS::combinePaths(FS::getLunarDirectory(), QStringLiteral("offline/multiver/logs/latest.log")));
    return file.open(QIODevice::ReadOnly) ? QString::fromUtf8(file.readAll()) : QString();
}
}

int runOnlineReadiness() {
    QTextStream out(stdout);
    int failures = 0;
    auto report = [&](const char* level, const QString& message) {
        out << level << ' ' << message << Qt::endl;
        if (qstrcmp(level, "FAIL") == 0)
            ++failures;
    };

    // 1-2. Saved sign-in.
    AccountStore::SavedAccount saved;
    QString storageError;
    switch (AccountStore::load(saved, &storageError)) {
    case AccountStore::LoadStatus::Missing:
        report("FAIL", QStringLiteral("account.dat: missing. Start ATW and sign in with Microsoft."));
        return 1;
    case AccountStore::LoadStatus::Unreadable:
        report("FAIL", QStringLiteral("account.dat: cannot be read on this computer. Saved account files were preserved."));
        return 1;
    case AccountStore::LoadStatus::Unavailable:
        report("FAIL", QStringLiteral("account.dat: operating-system secret store is unavailable; saved account files were preserved. ") + storageError);
        return 1;
    case AccountStore::LoadStatus::Ok:
        report("PASS", QStringLiteral("account.dat: read from the operating-system secret store for %1.").arg(saved.account.name));
        break;
    }
    const qint64 minutesLeft = QDateTime::currentDateTimeUtc().secsTo(saved.account.expiresAt) / 60;
    if (!AccountStore::tokenIsFresh(saved.account)) {
        report("WARN", QStringLiteral("saved Minecraft token: %1 minutes left. The next normal start refreshes it "
                                      "before the game starts; this check does not refresh it.").arg(minutesLeft));
        return 3;
    }
    report("PASS", QStringLiteral("saved Minecraft token: %1 minutes left.").arg(minutesLeft));

    // 3. Minecraft services profile.
    QNetworkAccessManager network;
    QNetworkRequest profileRequest(QUrl(QStringLiteral("https://api.minecraftservices.com/minecraft/profile")));
    profileRequest.setRawHeader("Authorization", "Bearer " + saved.account.accessToken.toUtf8());
    const Reply profile = request(network, profileRequest);
    const QJsonObject profileBody = QJsonDocument::fromJson(profile.body).object();
    const bool profileMatches = profileBody["id"].toString() == saved.account.uuid
        && profileBody["name"].toString() == saved.account.name;
    if (profile.status == 200 && profileMatches)
        report("PASS", QStringLiteral("Minecraft profile: HTTP 200, matches the saved profile."));
    else
        report("FAIL", QStringLiteral("Minecraft profile: %1%2.").arg(describe(profile),
               profile.status == 200 ? QStringLiteral(", but a different profile") : QString()));

    // 4. Session-server join, the check every online-mode server performs.
    const QString serverId = QString::number(QRandomGenerator::global()->generate64(), 16);
    QNetworkRequest joinRequest(QUrl(QStringLiteral("https://sessionserver.mojang.com/session/minecraft/join")));
    joinRequest.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    const QByteArray joinBody = QJsonDocument(QJsonObject{
        {"accessToken", saved.account.accessToken},
        {"selectedProfile", saved.account.uuid},
        {"serverId", serverId}}).toJson(QJsonDocument::Compact);
    const Reply join = request(network, joinRequest, &joinBody);
    if (join.status == 204) {
        QUrl hasJoinedUrl(QStringLiteral("https://sessionserver.mojang.com/session/minecraft/hasJoined"));
        hasJoinedUrl.setQuery(QUrlQuery{{QStringLiteral("username"), saved.account.name},
                                        {QStringLiteral("serverId"), serverId}});
        const Reply hasJoined = request(network, QNetworkRequest(hasJoinedUrl));
        if (hasJoined.status == 200 && QJsonDocument::fromJson(hasJoined.body).object()["id"].toString() == saved.account.uuid)
            report("PASS", QStringLiteral("server authentication: session join accepted and confirmed by hasJoined."));
        else
            report("FAIL", QStringLiteral("server authentication: join accepted, hasJoined returned %1.").arg(describe(hasJoined)));
    } else {
        report("FAIL", QStringLiteral("server authentication: session join returned %1.").arg(describe(join)));
    }

    // 5. Lunar's accounts.json.
    QFile lunarFile(FS::getLunarAccountsPath());
    const QJsonObject lunar = lunarFile.open(QIODevice::ReadOnly)
        ? QJsonDocument::fromJson(lunarFile.readAll()).object() : QJsonObject();
    const QJsonObject entry = lunar["accounts"].toObject()[saved.localId].toObject();
    if (entry.isEmpty()) {
        report("FAIL", QStringLiteral("accounts.json: no entry for the saved account. The next normal start writes it."));
    } else {
        const bool active = lunar["activeAccountLocalId"].toString() == saved.localId;
        const bool tokenMatches = entry["accessToken"].toString() == saved.account.accessToken;
        const bool noRefreshKey = !entry.contains(QStringLiteral("refreshToken"));
        if (active && tokenMatches && noRefreshKey)
            report("PASS", QStringLiteral("accounts.json: active entry holds the saved token and no refreshToken key."));
        else
            report("FAIL", QStringLiteral("accounts.json: active=%1, token matches=%2, no refreshToken key=%3. "
                                          "The next normal start rewrites the entry.")
                               .arg(active ? "yes" : "no", tokenMatches ? "yes" : "no", noRefreshKey ? "yes" : "no"));
    }

    // 6. Last game run, from the most recent account load onwards.
    QString log = lastGameLog();
    const int start = log.lastIndexOf(QStringLiteral("[LC Accounts] Attempting to load account"));
    if (start < 0) {
        report("INFO", QStringLiteral("last game log: no Lunar account load found."));
    } else {
        log = log.mid(start);
        const bool sessionSet = log.contains(QStringLiteral("[LC Accounts] Setting account '%1' as the current session.")
                                                 .arg(saved.account.name));
        const bool invalidated = log.contains(QStringLiteral("Going to invalidate this account"))
            || log.contains(QStringLiteral("Current account is null?!"));
        static const QRegularExpression authenticator403(QStringLiteral("\\[LC Auth\\] Connection closed[^\\n]*403"));
        const int auth403 = int(log.count(authenticator403));
        static const QRegularExpression connecting(QStringLiteral("Connecting to ([^,\\s]+), (\\d+)"));
        QStringList servers;
        for (auto it = connecting.globalMatch(log); it.hasNext();) {
            const auto match = it.next();
            servers << match.captured(1) + ':' + match.captured(2);
        }
        if (invalidated)
            report("FAIL", QStringLiteral("last game log: Lunar invalidated the account during startup."));
        else
            report(sessionSet ? "PASS" : "INFO", sessionSet
                ? QStringLiteral("last game log: Lunar set %1 as the session and did not invalidate it.").arg(saved.account.name)
                : QStringLiteral("last game log: Lunar did not report setting the session."));
        report("INFO", QStringLiteral("last game log: Lunar assets authenticator 403 x%1. This service serves Lunar "
                                      "cosmetics only and does not change accounts or server sessions.").arg(auth403));
        if (!servers.isEmpty())
            report("INFO", QStringLiteral("last game log: connection attempts: %1. Whether a join succeeded is visible "
                                          "in game only.").arg(servers.join(QStringLiteral(", "))));
    }

    out << (failures ? "NOT READY" : "READY") << ": " << failures << " failed check(s)." << Qt::endl;
    return failures ? 1 : 0;
}
