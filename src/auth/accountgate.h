//
// Makes sure atw-launch has a usable Minecraft account before starting the
// game: unlocks the saved sign-in with the master password, reuses a fresh
// token or refreshes it silently, or asks the user to sign in.
//

#ifndef ATW_AUTH_ACCOUNTGATE_H
#define ATW_AUTH_ACCOUNTGATE_H

#include <QObject>

#include "auth/accountstore.h"

class AccountGate : public QObject {
Q_OBJECT
public:
    explicit AccountGate(const QString& configuredClientId, QObject* parent = nullptr);

    void start();

signals:
    void ready(bool offline);
    void cancelled();

private:
    void continueWithSaved();
    void refreshSaved();
    void onRefreshFailed(const QString& message, bool needsSignIn);
    void useSaved();
    void askPassword(const QString& notice);
    bool upgradeLegacy();
    void askSignIn(const QString& notice);

    QString configuredClientId;
    AccountStore::SavedAccount saved;
    QString password;  // master password typed during this launch only
    MsaAuth* auth = nullptr;
};

#endif // ATW_AUTH_ACCOUNTGATE_H
