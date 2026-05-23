//
// Logs tab - live view of Lunar Client launcher output.
//

#ifndef LUNAR_CLIENT_QT_LOGSPAGE_H
#define LUNAR_CLIENT_QT_LOGSPAGE_H

#include <QWidget>
#include <QTabWidget>
#include <QPlainTextEdit>
#include <QTimer>
#include <QSyntaxHighlighter>

#include "configurationpage.h"

class LogHighlighter : public QSyntaxHighlighter {
    Q_OBJECT
public:
    explicit LogHighlighter(QTextDocument* parent = nullptr);

protected:
    void highlightBlock(const QString& text) override;

private:
    QTextCharFormat errorFormat;
};

class LogsPage : public ConfigurationPage {
Q_OBJECT
public:
    explicit LogsPage(Config& config, QWidget* parent = nullptr);

    QString title() override;
    QString description() override;
    QIcon icon() override;
    void apply() override;
    void load() override;

    void startPolling();
    void stopPolling();
    void appendToMainLog(const QString& text);

private slots:
    void pollLogs();

private:
    void readLog(const QString& path, QPlainTextEdit* edit, qint64& lastSize);

    QTabWidget* tabWidget;
    QPlainTextEdit* mainLogEdit;
    QPlainTextEdit* rendererLogEdit;
    QTimer* pollTimer;
    qint64 mainLogLastSize = 0;
    qint64 rendererLogLastSize = 0;
};

#endif // LUNAR_CLIENT_QT_LOGSPAGE_H
