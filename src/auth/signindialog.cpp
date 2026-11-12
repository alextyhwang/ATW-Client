//
// Microsoft device-code sign-in dialog. See signindialog.h.
//

#include "signindialog.h"

#include <QClipboard>
#include <QDesktopServices>
#include <QGuiApplication>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QUuid>
#include <QVBoxLayout>

#include "auth/accountstore.h"
#include "auth/msaauth.h"
#include "auth/passworddialog.h"
#include "gui/style/atwtheme.h"

SignInDialog::SignInDialog(const QString& clientId, bool allowOffline, const QString& notice, QWidget* parent)
    : QDialog(parent), auth(new MsaAuth(clientId, this)), clientId(MsaAuth::resolveClientId(clientId)) {
    setWindowTitle(QStringLiteral("ATW Client - Sign in"));
    setWindowFlag(Qt::WindowContextHelpButtonHint, false);
    setMinimumWidth(440);
    // Shared ATW look (pixel font, raised buttons); atw-launch shows this too.
    AtwTheme::ensureFonts();
    setStyleSheet(AtwTheme::signInDialogStyleSheet());

    QLabel* title = new QLabel(QStringLiteral("Sign in with Microsoft"));
    title->setObjectName(QStringLiteral("title"));
    QLabel* instructions = new QLabel(QStringLiteral(
        "Open the Microsoft link page, enter this code, and sign in with the account that owns Minecraft."));
    instructions->setWordWrap(true);

    codeLabel = new QLabel(QStringLiteral("--------"));
    codeLabel->setObjectName(QStringLiteral("code"));
    codeLabel->setAlignment(Qt::AlignCenter);
    codeLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);

    openButton = new QPushButton(QStringLiteral("Copy code && open browser"));
    openButton->setObjectName(QStringLiteral("primary"));
    openButton->setEnabled(false);
    connect(openButton, &QPushButton::clicked, this, [this]() {
        QGuiApplication::clipboard()->setText(userCode);
        QDesktopServices::openUrl(verificationUri);
        statusLabel->setText(QStringLiteral("Code copied. Paste it on the Microsoft page, then sign in."));
    });

    statusLabel = new QLabel();
    statusLabel->setWordWrap(true);

    retryButton = new QPushButton(QStringLiteral("Try again"));
    retryButton->hide();
    connect(retryButton, &QPushButton::clicked, this, &SignInDialog::start);

    QHBoxLayout* buttons = new QHBoxLayout();
    buttons->addWidget(retryButton);
    buttons->addStretch();
    if (allowOffline) {
        QPushButton* offlineButton = new QPushButton(QStringLiteral("Play offline"));
        offlineButton->setToolTip(QStringLiteral("Start without an account. Online servers will reject you."));
        connect(offlineButton, &QPushButton::clicked, this, [this]() {
            offline = true;
            accept();
        });
        buttons->addWidget(offlineButton);
    }
    QPushButton* cancelButton = new QPushButton(QStringLiteral("Cancel"));
    connect(cancelButton, &QPushButton::clicked, this, &QDialog::reject);
    buttons->addWidget(cancelButton);

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->setContentsMargins(22, 18, 22, 18);
    layout->setSpacing(12);
    layout->addWidget(title);
    if (!notice.isEmpty()) {
        QLabel* noticeLabel = new QLabel(notice);
        noticeLabel->setObjectName(QStringLiteral("notice"));
        noticeLabel->setWordWrap(true);
        layout->addWidget(noticeLabel);
    }
    layout->addWidget(instructions);
    layout->addWidget(codeLabel);
    layout->addWidget(openButton);
    layout->addWidget(statusLabel);
    layout->addLayout(buttons);

    connect(auth, &MsaAuth::deviceCode, this, [this](const QString& code, const QUrl& uri) {
        userCode = code;
        verificationUri = uri;
        codeLabel->setText(code);
        openButton->setEnabled(true);
        openButton->setToolTip(uri.toString());
    });
    connect(auth, &MsaAuth::progress, statusLabel, &QLabel::setText);
    connect(auth, &MsaAuth::succeeded, this, &SignInDialog::onSucceeded);
    connect(auth, &MsaAuth::failed, this, [this](const QString& message, bool) { onFailed(message); });
    connect(this, &QDialog::finished, auth, &MsaAuth::cancel);

    start();
}

void SignInDialog::start() {
    retryButton->hide();
    openButton->setEnabled(false);
    codeLabel->setText(QStringLiteral("--------"));
    auth->startDeviceLogin();
}

void SignInDialog::onSucceeded(const MinecraftAccount& account) {
    // The session is saved only under a master password the user chooses now.
    PasswordDialog passwordDialog(PasswordDialog::Mode::Create, account.name, QString(), false, this);
    if (passwordDialog.exec() != QDialog::Accepted) {
        AccountStore::log(QStringLiteral("Sign-in not saved: no master password was chosen."));
        return onFailed(QStringLiteral("Signed in, but not saved: ATW needs a master password to keep the sign-in. "
                                       "Try again and choose one."));
    }
    // Save atomically before replacing the previous sign-in. A failed file
    // write must never sign out the existing account.
    AccountStore::SavedAccount saved{QUuid::createUuid().toString(QUuid::WithoutBraces), clientId, account};
    QString error;
    if (!AccountStore::save(saved, passwordDialog.password(), &error)) {
        AccountStore::log(QStringLiteral("Sign-in could not be saved: ") + error);
        return onFailed(QStringLiteral("Signed in, but the account could not be saved. ") + error);
    }
    AccountStore::log(QStringLiteral("Signed in as %1.").arg(account.name));
    signedInName = account.name;
    masterPassword = passwordDialog.password();
    accept();
}

void SignInDialog::onFailed(const QString& message) {
    AccountStore::log(QStringLiteral("Sign-in failed: ") + message);
    statusLabel->setText(message);
    openButton->setEnabled(false);
    codeLabel->setText(QStringLiteral("--------"));
    retryButton->show();
}
