//
// Saved Microsoft sign-in. config/account.dat (version 2) holds the session
// encrypted with a key derived from the user's master password, so the same
// file unlocks on any PC. Version 1 files (Windows DPAPI or a Mac Keychain
// reference) are still read once and rewritten as version 2.
// Lunar's accounts.json receives only the short-lived Minecraft token.
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
        Locked,        // password-protected; call again with the master password
        WrongPassword, // the master password does not open this file
        Unavailable,   // OS secret store or account file cannot currently be read
        Unreadable     // legacy file from another PC or Windows user, or damaged
    };

    QString accountFilePath();
    // Version 2 files need the master password; version 1 files ignore it.
    // localId, clientId and the profile name are filled in even when Locked.
    LoadStatus load(SavedAccount& saved, const QString& password = QString(), QString* error = nullptr);
    // True when account.dat is a version 2 (master password) file.
    bool isPasswordProtected();
    // Non-secret display metadata only. Never decrypts or touches Keychain,
    // and does not establish that the saved sign-in can be used.
    bool savedAccountMetadata(QString* name = nullptr);
    // Encrypts the session with the master password into account.dat, then
    // writes the Minecraft token into accounts.json.
    bool save(const SavedAccount& saved, const QString& password, QString* error = nullptr);
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

    // OS-bound encryption (DPAPI / Keychain), used only for version 1 files.
    QByteArray protect(const QByteArray& plain, QString* error = nullptr);
    QByteArray unprotect(const QByteArray& encrypted, QString* error = nullptr, bool* unavailable = nullptr);

    // Appends to logs/launcher/main.log. Never pass token values.
    void log(const QString& message);
}

#endif // ATW_AUTH_ACCOUNTSTORE_H
