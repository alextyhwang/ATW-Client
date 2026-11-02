//
// Account row for atw-config. See accountwidget.h.
//

#include "accountwidget.h"

#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>

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
    AccountStore::SavedAccount saved;
    QString lunarName;
    switch (AccountStore::load(saved)) {
    case AccountStore::LoadStatus::Ok:
        signedIn = true;
        statusLabel->setText(saved.account.name);
        break;
    case AccountStore::LoadStatus::Unreadable:
        // Made on another PC or Windows user: it cannot be used here anyway.
        AccountStore::signOut();
        signedIn = false;
        statusLabel->setText(QStringLiteral("Not signed in"));
        break;
    case AccountStore::LoadStatus::Missing:
        signedIn = AccountStore::hasUsableLunarAccount(&lunarName);
        statusLabel->setText(signedIn ? lunarName : QStringLiteral("Not signed in"));
        break;
    }
    button->setText(signedIn ? QStringLiteral("Sign out") : QStringLiteral("Sign in"));
}

void AccountWidget::onButtonClicked() {
    if (signedIn) {
        AccountStore::signOut();
        AccountStore::log(QStringLiteral("Signed out from ATW Config."));
    } else {
        SignInDialog dialog(config.msaClientId, false, QString(), window());
        dialog.exec();
    }
    refresh();
}
