//
// Master password prompt. See passworddialog.h.
//

#include "passworddialog.h"

#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QVBoxLayout>

#include "gui/style/atwtheme.h"

namespace {
constexpr int kMinimumLength = 4;
}

PasswordDialog::PasswordDialog(Mode mode, const QString& accountName, const QString& notice,
                               bool allowOffline, QWidget* parent)
    : QDialog(parent), mode(mode) {
    const bool create = mode == Mode::Create;
    setWindowTitle(create ? QStringLiteral("ATW Client - Master password")
                          : QStringLiteral("ATW Client - Unlock"));
    setWindowFlag(Qt::WindowContextHelpButtonHint, false);
    setMinimumWidth(440);
    AtwTheme::ensureFonts();
    setStyleSheet(AtwTheme::signInDialogStyleSheet());

    QLabel* title = new QLabel(create ? QStringLiteral("Choose a master password")
                                      : QStringLiteral("Enter your master password"));
    title->setObjectName(QStringLiteral("title"));

    const QString who = accountName.isEmpty() ? QStringLiteral("your Minecraft account")
                                              : accountName;
    QLabel* instructions = new QLabel(create
        ? QStringLiteral("ATW saves the sign-in for %1 encrypted with this password. "
                         "You type it each time you start the game, on this PC or any other. "
                         "If you forget it, sign in with Microsoft again.").arg(who)
        : QStringLiteral("Unlock the saved sign-in for %1.").arg(who));
    instructions->setWordWrap(true);

    passwordEdit = new QLineEdit();
    passwordEdit->setEchoMode(QLineEdit::Password);
    passwordEdit->setPlaceholderText(QStringLiteral("Master password"));
    if (create) {
        confirmEdit = new QLineEdit();
        confirmEdit->setEchoMode(QLineEdit::Password);
        confirmEdit->setPlaceholderText(QStringLiteral("Type it again"));
    }

    errorLabel = new QLabel(notice);
    errorLabel->setObjectName(QStringLiteral("notice"));
    errorLabel->setWordWrap(true);
    errorLabel->setVisible(!notice.isEmpty());

    QPushButton* okButton = new QPushButton(create ? QStringLiteral("Save") : QStringLiteral("Unlock"));
    okButton->setObjectName(QStringLiteral("primary"));
    okButton->setDefault(true);
    connect(okButton, &QPushButton::clicked, this, &PasswordDialog::submit);

    QHBoxLayout* buttons = new QHBoxLayout();
    if (!create) {
        QPushButton* signIn = new QPushButton(QStringLiteral("Forgot? Sign in again"));
        signIn->setToolTip(QStringLiteral("Sign in with Microsoft and choose a new master password."));
        connect(signIn, &QPushButton::clicked, this, [this]() {
            picked = Choice::SignInAgain;
            accept();
        });
        buttons->addWidget(signIn);
    }
    buttons->addStretch();
    if (allowOffline) {
        QPushButton* offline = new QPushButton(QStringLiteral("Play offline"));
        offline->setToolTip(QStringLiteral("Start without an account. Online servers will reject you."));
        connect(offline, &QPushButton::clicked, this, [this]() {
            picked = Choice::Offline;
            accept();
        });
        buttons->addWidget(offline);
    }
    QPushButton* cancel = new QPushButton(QStringLiteral("Cancel"));
    connect(cancel, &QPushButton::clicked, this, &QDialog::reject);
    buttons->addWidget(cancel);

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->setContentsMargins(22, 18, 22, 18);
    layout->setSpacing(12);
    layout->addWidget(title);
    layout->addWidget(instructions);
    layout->addWidget(passwordEdit);
    if (confirmEdit)
        layout->addWidget(confirmEdit);
    layout->addWidget(errorLabel);
    layout->addWidget(okButton);
    layout->addLayout(buttons);

    passwordEdit->setFocus();
}

QString PasswordDialog::password() const {
    return passwordEdit->text();
}

void PasswordDialog::submit() {
    auto fail = [this](const QString& message) {
        errorLabel->setText(message);
        errorLabel->show();
        passwordEdit->setFocus();
        passwordEdit->selectAll();
    };
    const QString text = passwordEdit->text();
    if (text.isEmpty())
        return fail(QStringLiteral("Enter a password."));
    if (mode == Mode::Create) {
        if (text.size() < kMinimumLength)
            return fail(QStringLiteral("Use at least %1 characters.").arg(kMinimumLength));
        if (confirmEdit->text() != text)
            return fail(QStringLiteral("The two passwords don't match."));
    }
    picked = Choice::Password;
    accept();
}
