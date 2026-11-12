//
// Master password prompt for the saved sign-in (config/account.dat).
// Create: choose a password after a Microsoft sign-in (typed twice).
// Unlock: enter it before ATW can use the saved session on any PC.
//

#ifndef ATW_AUTH_PASSWORDDIALOG_H
#define ATW_AUTH_PASSWORDDIALOG_H

#include <QDialog>

class QLabel;
class QLineEdit;

class PasswordDialog : public QDialog {
Q_OBJECT
public:
    enum class Mode { Create, Unlock };
    // What the user picked when the dialog was accepted.
    enum class Choice { Password, SignInAgain, Offline };

    PasswordDialog(Mode mode, const QString& accountName, const QString& notice = QString(),
                   bool allowOffline = false, QWidget* parent = nullptr);

    QString password() const;
    Choice choice() const { return picked; }

private:
    void submit();

    Mode mode;
    QLineEdit* passwordEdit;
    QLineEdit* confirmEdit = nullptr;
    QLabel* errorLabel;
    Choice picked = Choice::Password;
};

#endif // ATW_AUTH_PASSWORDDIALOG_H
