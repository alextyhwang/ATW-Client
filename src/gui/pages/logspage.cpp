//
// Logs tab - live view of Lunar Client launcher output.
//

#include "logspage.h"

#include <QVBoxLayout>
#include <QFile>
#include <QFont>
#include <QRegularExpression>

#include "util/fs.h"

LogHighlighter::LogHighlighter(QTextDocument* parent) : QSyntaxHighlighter(parent) {
    errorFormat.setForeground(Qt::red);
    errorFormat.setFontWeight(QFont::Bold);
}

void LogHighlighter::highlightBlock(const QString& text) {
    static const QRegularExpression errorPattern(
        QStringLiteral("\\bERROR\\b|\\bFATAL\\b|Exception|\\berror:|Caused by:"),
        QRegularExpression::CaseInsensitiveOption
    );

    QRegularExpressionMatchIterator it = errorPattern.globalMatch(text);
    while (it.hasNext()) {
        QRegularExpressionMatch match = it.next();
        setFormat(match.capturedStart(), match.capturedLength(), errorFormat);
    }
}

LogsPage::LogsPage(Config& config, QWidget* parent) : ConfigurationPage(config, parent) {
    tabWidget = new QTabWidget(this);
    mainLogEdit = new QPlainTextEdit(this);
    mainLogEdit->setReadOnly(true);
#ifdef Q_OS_WIN
    mainLogEdit->setFont(QFont(QStringLiteral("Consolas"), 9));
#else
    mainLogEdit->setFont(QFont(QStringLiteral("Monospace"), 9));
#endif
    new LogHighlighter(mainLogEdit->document());

    rendererLogEdit = new QPlainTextEdit(this);
    rendererLogEdit->setReadOnly(true);
#ifdef Q_OS_WIN
    rendererLogEdit->setFont(QFont(QStringLiteral("Consolas"), 9));
#else
    rendererLogEdit->setFont(QFont(QStringLiteral("Monospace"), 9));
#endif
    new LogHighlighter(rendererLogEdit->document());

    tabWidget->addTab(mainLogEdit, QStringLiteral("Main"));
    tabWidget->addTab(rendererLogEdit, QStringLiteral("Renderer"));

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->addWidget(tabWidget);

    pollTimer = new QTimer(this);
    connect(pollTimer, &QTimer::timeout, this, &LogsPage::pollLogs);
}

QString LogsPage::title() {
    return QStringLiteral("Logs");
}

QString LogsPage::description() {
    return QStringLiteral("Live launcher and game output.");
}

QIcon LogsPage::icon() {
    return QIcon(QStringLiteral(":/res/icons/log.svg"));
}

void LogsPage::apply() {}
void LogsPage::load() {}

void LogsPage::startPolling() {
    mainLogLastSize = 0;
    rendererLogLastSize = 0;
    pollLogs();
    pollTimer->start(500);
}

void LogsPage::stopPolling() {
    pollTimer->stop();
}

void LogsPage::appendToMainLog(const QString& text) {
    mainLogEdit->moveCursor(QTextCursor::End);
    mainLogEdit->insertPlainText(text + QChar('\n'));
    mainLogEdit->moveCursor(QTextCursor::End);
}

void LogsPage::pollLogs() {
    QString logsDir = FS::getLunarLogsPath();
    readLog(FS::combinePaths(logsDir, "main.log"), mainLogEdit, mainLogLastSize);
    readLog(FS::combinePaths(logsDir, "renderer.log"), rendererLogEdit, rendererLogLastSize);
}

void LogsPage::readLog(const QString& path, QPlainTextEdit* edit, qint64& lastSize) {
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    qint64 size = file.size();
    if (size < lastSize) {
        lastSize = 0;
    }
    if (size > lastSize) {
        file.seek(lastSize);
        QString newContent = QString::fromUtf8(file.readAll());
        if (!newContent.isEmpty()) {
            edit->moveCursor(QTextCursor::End);
            edit->insertPlainText(newContent);
            edit->moveCursor(QTextCursor::End);
        }
        lastSize = size;
    }
    file.close();
}
