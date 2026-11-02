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

SignInDialog::SignInDialog(const QString& clientId, bool allowOffline, const QString& notice, QWidget* parent)
    : QDialog(parent), auth(new MsaAuth(clientId, this)), clientId(MsaAuth::resolveClientId(clientId)) {
    setWindowTitle(QStringLiteral("ATW Client - Sign in"));
    setWindowFlag(Qt::WindowContextHelpButtonHint, false);
    setMinimumWidth(440);
    setStyleSheet(QStringLiteral(
        "QDialog { background-color: #303031; }"
        "QLabel { color: #CFCFCF; font-size: 14px; }"
        "QLabel#title { color: #E7EDF9; font-size: 20px; }"
        "QLabel#notice { color: #E8B65C; }"
        "QLabel#code { color: #E7EDF9; font-family: Consolas, monospace; font-size: 34px; font-weight: bold; }"
        "QPushButton { background-color: #343435; color: #CFCFCF; border: 1px solid #4A4A4C;"
        " border-radius: 4px; padding: 7px 14px; font-size: 14px; }"
        "QPushButton:hover { background-color: #3d3d3e; }"
        "QPushButton:disabled { color: #6C6C6C; }"
        "QPushButton#primary { background-color: #4779D8; color: white; border: none; }"
        "QPushButton#primary:hover { background-color: #5687E2; }"));

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
    // Replace any previous account so only this one can be used here.
    AccountStore::signOut();
    AccountStore::SavedAccount saved{QUuid::createUuid().toString(QUuid::WithoutBraces), clientId, account};
    QString error;
    if (!AccountStore::save(saved, &error)) {
        AccountStore::signOut();
        AccountStore::log(QStringLiteral("Sign-in could not be saved: ") + error);
        return onFailed(QStringLiteral("Signed in, but the account could not be saved. ") + error);
    }
    AccountStore::log(QStringLiteral("Signed in as %1.").arg(account.name));
    signedInName = account.name;
    accept();
}

void SignInDialog::onFailed(const QString& message) {
    AccountStore::log(QStringLiteral("Sign-in failed: ") + message);
    statusLabel->setText(message);
    openButton->setEnabled(false);
    codeLabel->setText(QStringLiteral("--------"));
    retryButton->show();
}
