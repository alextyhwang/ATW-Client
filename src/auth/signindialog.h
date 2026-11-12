//
// Microsoft device-code sign-in dialog shared by atw-launch and atw-config.
// Accepting means an account was saved, or "Play offline" was chosen.
//

#ifndef ATW_AUTH_SIGNINDIALOG_H
#define ATW_AUTH_SIGNINDIALOG_H

#include <QDialog>
#include <QUrl>

class QLabel;
class QPushButton;
class MsaAuth;
struct MinecraftAccount;

class SignInDialog : public QDialog {
Q_OBJECT
public:
    SignInDialog(const QString& clientId, bool allowOffline, const QString& notice = QString(), QWidget* parent = nullptr);

    bool offlineChosen() const { return offline; }
    QString accountName() const { return signedInName; }
    // The master password chosen for the saved sign-in (empty if offline).
    QString password() const { return masterPassword; }

private:
    void start();
    void onSucceeded(const MinecraftAccount& account);
    void onFailed(const QString& message);

    MsaAuth* auth;
    QString clientId;
    QLabel* codeLabel;
    QLabel* statusLabel;
    QPushButton* openButton;
    QPushButton* retryButton;
    QString userCode;
    QUrl verificationUri;
    QString signedInName;
    QString masterPassword;
    bool offline = false;
};

#endif // ATW_AUTH_SIGNINDIALOG_H
