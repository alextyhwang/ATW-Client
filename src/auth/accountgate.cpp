//
// Pre-launch account check for atw-launch. See accountgate.h.
//

#include "accountgate.h"

#include <QMessageBox>
#include <QPushButton>

#include "auth/passworddialog.h"
#include "auth/signindialog.h"

AccountGate::AccountGate(const QString& configuredClientId, QObject* parent)
    : QObject(parent), configuredClientId(configuredClientId) {}

void AccountGate::start() {
    QString error;
    switch (AccountStore::load(saved, password, &error)) {
    case AccountStore::LoadStatus::Ok:
        if (!AccountStore::isPasswordProtected() && !upgradeLegacy())
            return emit cancelled();
        return continueWithSaved();
    case AccountStore::LoadStatus::Locked:
        return askPassword(QString());
    case AccountStore::LoadStatus::WrongPassword:
        AccountStore::log(QStringLiteral("Master password did not unlock the saved sign-in."));
        password.clear();
        return askPassword(QStringLiteral("That password didn't unlock the saved sign-in. Try again."));
    case AccountStore::LoadStatus::Unreadable:
        AccountStore::log(QStringLiteral("Saved sign-in could not be read; preserved it."));
        if (AccountStore::hasUsableLunarAccount())
            return emit ready(false);
        return askSignIn(QStringLiteral(
            "The saved sign-in could not be read. Sign in again to replace it."));
    case AccountStore::LoadStatus::Unavailable: {
        AccountStore::log(QStringLiteral("Saved sign-in is temporarily unavailable: ") + error);
        if (AccountStore::hasUsableLunarAccount())
            return emit ready(false);
        QMessageBox box(QMessageBox::Warning, QStringLiteral("ATW Client"),
                        QStringLiteral("Your saved sign-in is temporarily unavailable. It has been preserved.\n\n") + error);
        QPushButton* retry = box.addButton(QStringLiteral("Try again"), QMessageBox::AcceptRole);
        QPushButton* offline = box.addButton(QStringLiteral("Play offline"), QMessageBox::DestructiveRole);
        box.addButton(QMessageBox::Cancel);
        box.exec();
        if (box.clickedButton() == retry)
            return start();
        if (box.clickedButton() == offline)
            return emit ready(true);
        return emit cancelled();
    }
    case AccountStore::LoadStatus::Missing:
        // Packages made from a copied Lunar profile keep working until expiry.
        if (AccountStore::hasUsableLunarAccount())
            return emit ready(false);
        return askSignIn(QString());
    }
}

void AccountGate::continueWithSaved() {
    if (AccountStore::tokenIsFresh(saved.account))
        return useSaved();
    refreshSaved();
}

void AccountGate::askPassword(const QString& notice) {
    PasswordDialog dialog(PasswordDialog::Mode::Unlock, saved.account.name, notice, true);
    if (dialog.exec() != QDialog::Accepted)
        return emit cancelled();
    switch (dialog.choice()) {
    case PasswordDialog::Choice::SignInAgain:
        return askSignIn(QString());
    case PasswordDialog::Choice::Offline:
        return emit ready(true);
    case PasswordDialog::Choice::Password:
        password = dialog.password();
        return start();
    }
}

// A version 1 file (DPAPI or Keychain) opened on this computer: ask for a
// master password once and rewrite it as a portable version 2 file.
bool AccountGate::upgradeLegacy() {
    PasswordDialog dialog(PasswordDialog::Mode::Create, saved.account.name, QStringLiteral(
        "ATW now protects your saved sign-in with a master password instead of locking it to this computer."));
    if (dialog.exec() != QDialog::Accepted)
        return false;
    password = dialog.password();
    QString error;
    if (!AccountStore::save(saved, password, &error)) {
        AccountStore::log(QStringLiteral("Could not protect the saved sign-in with a master password: ") + error);
        QMessageBox::warning(nullptr, QStringLiteral("ATW Client"),
            QStringLiteral("The master password could not be saved. The previous sign-in was kept.\n\n") + error);
        return true;  // the decrypted session is still usable for this launch
    }
    AccountStore::log(QStringLiteral("Saved sign-in is now protected by a master password."));
    return true;
}

void AccountGate::refreshSaved() {
    if (!auth) {
        auth = new MsaAuth(saved.clientId.isEmpty() ? configuredClientId : saved.clientId, this);
        connect(auth, &MsaAuth::succeeded, this, [this](const MinecraftAccount& account) {
            saved.account = account;
            QString error;
            if (!AccountStore::save(saved, password, &error)) {
                AccountStore::log(QStringLiteral("Refreshed sign-in could not be saved: ") + error);
                QMessageBox::warning(nullptr, QStringLiteral("ATW Client"),
                    QStringLiteral("Your Minecraft session was refreshed, but the saved sign-in could not be updated. "
                                   "You may need to sign in again next time.\n\n") + error);
            }
            AccountStore::log(QStringLiteral("Refreshed Minecraft session for %1.").arg(account.name));
            // Saving can fail before writing Lunar's token. Always publish the
            // refreshed session and confirm that write before starting Minecraft.
            useSaved();
        });
        connect(auth, &MsaAuth::failed, this, &AccountGate::onRefreshFailed);
    }
    auth->refresh(saved.account.refreshToken);
}

void AccountGate::onRefreshFailed(const QString& message, bool needsSignIn) {
    AccountStore::log(QStringLiteral("Session refresh failed: ") + message);
    if (needsSignIn) {
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
    if (!AccountStore::writeLunarAccount(saved, &error)) {
        AccountStore::log(QStringLiteral("Could not update accounts.json: ") + error);
        QMessageBox box(QMessageBox::Warning, QStringLiteral("ATW Client"),
                        QStringLiteral("Could not give Minecraft your signed-in session.\n\n") + error);
        QPushButton* retry = box.addButton(QStringLiteral("Try again"), QMessageBox::AcceptRole);
        box.addButton(QMessageBox::Cancel);
        box.exec();
        if (box.clickedButton() == retry)
            return useSaved();
        return emit cancelled();
    }
    emit ready(false);
}

void AccountGate::askSignIn(const QString& notice) {
    SignInDialog dialog(configuredClientId, true, notice);
    if (dialog.exec() != QDialog::Accepted)
        return emit cancelled();
    password = dialog.password();
    emit ready(dialog.offlineChosen());
}
