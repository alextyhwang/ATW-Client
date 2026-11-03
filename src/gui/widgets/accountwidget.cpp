//
// Account row for atw-config. See accountwidget.h.
//

#include "accountwidget.h"

#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#ifdef Q_OS_MACOS
#include <QDir>
#include <QFileInfo>
#include <QMessageBox>
#include <QProcess>
#include "util/fs.h"
#endif

#include "auth/accountstore.h"
#include "auth/signindialog.h"
#include "config/config.h"

AccountWidget::AccountWidget(const Config& config, QWidget* parent) : QWidget(parent), config(config) {
    statusLabel = new QLabel();
    statusLabel->setStyleSheet("color: #BFBFBF; font-size: 16px;");
    button = new QPushButton();
    button->setCursor(Qt::PointingHandCursor);
    connect(button, &QPushButton::clicked, this, &AccountWidget::onButtonClicked);

    QHBoxLayout* layout = new QHBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(new QLabel(QStringLiteral("Account")));
    layout->addStretch();
    layout->addWidget(statusLabel);
    layout->addWidget(button);
    refresh();
}

void AccountWidget::refresh() {
#ifdef Q_OS_MACOS
    if (accountProcess)
        return;
    // Only atw-launch touches the Mac Keychain. Reading config status must
    // never produce a security prompt from a second executable identity.
    QString accountName;
    signedIn = AccountStore::savedAccountMetadata(&accountName)
        || AccountStore::hasUsableLunarAccount(&accountName);
    storageUnavailable = false;
    statusLabel->setText(signedIn ? accountName : QStringLiteral("Not signed in"));
    button->setText(signedIn ? QStringLiteral("Sign out") : QStringLiteral("Sign in"));
    button->setEnabled(true);
    button->setToolTip(QString());
#else
    AccountStore::SavedAccount saved;
    QString lunarName;
    QString error;
    storageUnavailable = false;
    button->setToolTip(QString());
    switch (AccountStore::load(saved, &error)) {
    case AccountStore::LoadStatus::Ok:
        signedIn = true;
        statusLabel->setText(saved.account.name);
        break;
    case AccountStore::LoadStatus::Unreadable:
        // Reading settings must never remove an existing sign-in.
        signedIn = AccountStore::hasUsableLunarAccount(&lunarName);
        statusLabel->setText(signedIn ? lunarName : QStringLiteral("Saved sign-in needs attention"));
        break;
    case AccountStore::LoadStatus::Unavailable:
        signedIn = false;
        storageUnavailable = true;
        statusLabel->setText(QStringLiteral("Saved sign-in unavailable"));
        button->setToolTip(error);
        break;
    case AccountStore::LoadStatus::Missing:
        signedIn = AccountStore::hasUsableLunarAccount(&lunarName);
        statusLabel->setText(signedIn ? lunarName : QStringLiteral("Not signed in"));
        break;
    }
    button->setText(storageUnavailable ? QStringLiteral("Try again")
                    : signedIn ? QStringLiteral("Sign out") : QStringLiteral("Sign in"));
#endif
}

void AccountWidget::onButtonClicked() {
#ifdef Q_OS_MACOS
    launchAccountAction();
#else
    if (storageUnavailable)
        return refresh();
    if (signedIn) {
        AccountStore::signOut();
        AccountStore::log(QStringLiteral("Signed out from ATW Config."));
    } else {
        SignInDialog dialog(config.msaClientId, false, QString(), window());
        dialog.exec();
    }
    refresh();
#endif
}

#ifdef Q_OS_MACOS
void AccountWidget::launchAccountAction() {
    if (accountProcess)
        return;
    const QString root = QFileInfo(FS::getPackageRoot()).canonicalFilePath();
    const QString path = QDir(root).filePath(QStringLiteral("atw-launch.app/Contents/MacOS/atw-launch"));
    const QFileInfo executable(path);
    if (root.isEmpty() || !executable.isFile() || !executable.isExecutable()
            || executable.isSymLink() || executable.canonicalFilePath() != path) {
        QMessageBox::warning(window(), QStringLiteral("ATW Client"),
            QStringLiteral("The packaged account launcher is missing or invalid. Repair the ATW Client installation."));
        return;
    }
    accountProcess = new QProcess(this);
    QProcess* process = accountProcess;
    connect(process, &QProcess::errorOccurred, this, [this, process](QProcess::ProcessError error) {
        if (error != QProcess::FailedToStart)
            return;
        accountProcess = nullptr;
        QMessageBox::warning(window(), QStringLiteral("ATW Client"),
            QStringLiteral("Could not open the account launcher. ") + process->errorString());
        process->deleteLater();
        refresh();
    });
    connect(process, qOverload<int, QProcess::ExitStatus>(&QProcess::finished), this,
            [this, process](int code, QProcess::ExitStatus status) {
        accountProcess = nullptr;
        process->deleteLater();
        refresh();
        if (status != QProcess::NormalExit || code != 0)
            QMessageBox::warning(window(), QStringLiteral("ATW Client"),
                QStringLiteral("The account action did not finish. Your saved sign-in has been preserved; try again."));
    });
    process->setProgram(path);
    process->setArguments({signedIn ? QStringLiteral("--sign-out") : QStringLiteral("--sign-in")});
    process->setWorkingDirectory(root);
    button->setEnabled(false);
    statusLabel->setText(signedIn ? QStringLiteral("Signing out...") : QStringLiteral("Signing in..."));
    process->start();
}
#endif
