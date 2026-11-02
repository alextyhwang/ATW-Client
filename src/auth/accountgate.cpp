//
// Pre-launch account check for atw-launch. See accountgate.h.
//

#include "accountgate.h"

#include <QMessageBox>
#include <QPushButton>

#include "auth/signindialog.h"

AccountGate::AccountGate(const QString& configuredClientId, QObject* parent)
    : QObject(parent), configuredClientId(configuredClientId) {}

void AccountGate::start() {
    switch (AccountStore::load(saved)) {
    case AccountStore::LoadStatus::Ok:
        if (AccountStore::tokenIsFresh(saved.account))
            return useSaved();
        return refreshSaved();
    case AccountStore::LoadStatus::Unreadable:
        AccountStore::log(QStringLiteral("Saved sign-in is from another PC or Windows user; removed it."));
        AccountStore::signOut();
        return askSignIn(QStringLiteral(
            "The saved sign-in was made on another PC or Windows user, so it was removed. Sign in again to play."));
    case AccountStore::LoadStatus::Missing:
        // Packages made from a copied Lunar profile keep working until expiry.
        if (AccountStore::hasUsableLunarAccount())
            return emit ready(false);
        return askSignIn(QString());
    }
}

void AccountGate::refreshSaved() {
    if (!auth) {
        auth = new MsaAuth(saved.clientId.isEmpty() ? configuredClientId : saved.clientId, this);
        connect(auth, &MsaAuth::succeeded, this, [this](const MinecraftAccount& account) {
            saved.account = account;
            QString error;
            if (!AccountStore::save(saved, &error))
                AccountStore::log(QStringLiteral("Refreshed sign-in could not be saved: ") + error);
            AccountStore::log(QStringLiteral("Refreshed Minecraft session for %1.").arg(account.name));
            emit ready(false);
        });
        connect(auth, &MsaAuth::failed, this, &AccountGate::onRefreshFailed);
    }
    auth->refresh(saved.account.refreshToken);
}

void AccountGate::onRefreshFailed(const QString& message, bool needsSignIn) {
    AccountStore::log(QStringLiteral("Session refresh failed: ") + message);
    if (needsSignIn) {
        AccountStore::signOut();
        return askSignIn(message);
    }
    // A short network outage should not block a token that still works.
    if (AccountStore::tokenIsFresh(saved.account, 60))
        return useSaved();

    QMessageBox box(QMessageBox::Warning, QStringLiteral("ATW Client"),
                    QStringLiteral("Could not refresh your Minecraft sign-in.\n\n") + message);
    QPushButton* retry = box.addButton(QStringLiteral("Try again"), QMessageBox::AcceptRole);
    QPushButton* offline = box.addButton(QStringLiteral("Play offline"), QMessageBox::DestructiveRole);
    box.addButton(QMessageBox::Cancel);
    box.exec();
    if (box.clickedButton() == retry)
        return refreshSaved();
    if (box.clickedButton() == offline)
        return emit ready(true);
    emit cancelled();
}

void AccountGate::useSaved() {
    QString error;
    if (!AccountStore::writeLunarAccount(saved, &error))
        AccountStore::log(QStringLiteral("Could not update accounts.json: ") + error);
    emit ready(false);
}

void AccountGate::askSignIn(const QString& notice) {
    SignInDialog dialog(configuredClientId, true, notice);
    if (dialog.exec() != QDialog::Accepted)
        return emit cancelled();
    emit ready(dialog.offlineChosen());
}
