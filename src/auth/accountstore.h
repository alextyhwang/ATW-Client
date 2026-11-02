//
// Saved Microsoft sign-in. The refresh token lives only in config/account.dat,
// encrypted with Windows DPAPI for the current Windows user. Lunar's
// accounts.json receives only the short-lived Minecraft token.
//

#ifndef ATW_AUTH_ACCOUNTSTORE_H
#define ATW_AUTH_ACCOUNTSTORE_H

#include <QByteArray>
#include <QJsonObject>
#include <QString>

#include "auth/msaauth.h"

namespace AccountStore {
    struct SavedAccount {
        QString localId;   // key of this account in Lunar's accounts.json
        QString clientId;  // the refresh token only works with this client ID
        MinecraftAccount account;
    };

    enum class LoadStatus {
        Missing,
        Ok,
        Unreadable  // copied from another PC or Windows user, or damaged
    };

    QString accountFilePath();
    LoadStatus load(SavedAccount& saved);
    // Writes account.dat, then the Minecraft token into accounts.json.
    bool save(const SavedAccount& saved, QString* error = nullptr);
    bool writeLunarAccount(const SavedAccount& saved, QString* error = nullptr);
    // The accounts.json entry for a saved account. It has no refreshToken key:
    // Lunar refreshes any account whose refreshToken is non-null, even "", and
    // invalidates the account when that refresh fails.
    QJsonObject lunarAccountEntry(const SavedAccount& saved);
    // Deletes account.dat and every account in accounts.json.
    void signOut();

    // True when accounts.json already holds a token that has not expired, for
    // packages built from a copied Lunar profile rather than ATW sign-in.
    bool hasUsableLunarAccount(QString* name = nullptr);
    bool tokenIsFresh(const MinecraftAccount& account, int marginSeconds = 600);

    QByteArray protect(const QByteArray& plain);
    QByteArray unprotect(const QByteArray& encrypted);

    // Appends to logs/launcher/main.log. Never pass token values.
    void log(const QString& message);
}

#endif // ATW_AUTH_ACCOUNTSTORE_H
