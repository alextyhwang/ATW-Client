//
// Created by nils on 11/4/21.
//

#ifndef LUNAR_CLIENT_QT_OFFLINELAUNCHER_H
#define LUNAR_CLIENT_QT_OFFLINELAUNCHER_H

#include <QObject>
#include <QProcess>

#include "launcher.h"


class OfflineLauncher : public Launcher{
Q_OBJECT
public:
    explicit OfflineLauncher(const Config& config, const bool useCustomAssetIndex, const QString& customAssetIndex, QObject* parent = nullptr);

    bool launch();
    bool isProcessRunning() const;
    void endProcess();

signals:
    void error(const QString& message);
    void processStarted();
    void processFinished();

private slots:
    void onProcessFinished(int exitCode, QProcess::ExitStatus status);

private:
    static QString resolveJavaExecutable(const QString& path);
    static QString findJavaExecutable(const QString& gameVersion);
    static QStringList sanitizeJvmArgs(const QStringList& args, QStringList* removedArgs = nullptr);
    static void sanitizeMinecraftOptions(const QString& gameDir);
    static void HelperLaunch(const QString& helper);
    static bool isPidRunning(qint64 pid);
    static void scheduleMinecraftWindowIcon(qint64 pid);

    QProcess* process = nullptr;
    qint64 launchedPid = 0;
};


#endif //LUNAR_CLIENT_QT_OFFLINELAUNCHER_H
