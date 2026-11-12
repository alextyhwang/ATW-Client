//
// Game settings - memory, JRE, Minecraft dir, JVM args.
//

#ifndef LUNAR_CLIENT_QT_GAMEPAGE_H
#define LUNAR_CLIENT_QT_GAMEPAGE_H

#include <QSlider>
#include <QPlainTextEdit>
#include <QCheckBox>
#include <QComboBox>

#include "configurationpage.h"
#include "gui/widgets/filechooser.h"

class AccountWidget;

class GamePage : public ConfigurationPage{
Q_OBJECT
public:
    explicit GamePage(Config& config, QWidget* parent = nullptr);

    QString title() override;
    QString description() override;
    QIcon icon() override;
    void apply() override;
    void load() override;

    // Re-reads the saved sign-in (for example after atw-launch signed in).
    void refreshAccount();
    // First run: asks for a Microsoft sign-in when no account is saved.
    void promptSignInIfSignedOut();

private:
    QSlider* memorySlider;
    FileChooser* jrePath;
    FileChooser* minecraftPathChooser;
    QPushButton* openDataFolder;
#ifdef ATW_TEST_PORTABLE
    QComboBox* javaProfile;
    QCheckBox* useLargePages;
    QCheckBox* showGpuReminder;
#endif
    QCheckBox* closeOnLaunch;
    QPlainTextEdit* jvmArgs;
    AccountWidget* account;
};

#endif // LUNAR_CLIENT_QT_GAMEPAGE_H
