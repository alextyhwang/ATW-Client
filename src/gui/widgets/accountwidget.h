//
// Account row for atw-config: shows who is signed in, with sign in/out.
//

#ifndef ATW_GUI_ACCOUNTWIDGET_H
#define ATW_GUI_ACCOUNTWIDGET_H

#include <QWidget>

class QLabel;
class QPushButton;
class QProcess;
class Config;

class AccountWidget : public QWidget {
Q_OBJECT
public:
    explicit AccountWidget(const Config& config, QWidget* parent = nullptr);

    void refresh();

private:
    void onButtonClicked();
#ifdef Q_OS_MACOS
    void launchAccountAction();
    QProcess* accountProcess = nullptr;
#endif

    const Config& config;
    QLabel* statusLabel;
    QPushButton* button;
    bool signedIn = false;
    bool storageUnavailable = false;
};

#endif // ATW_GUI_ACCOUNTWIDGET_H
