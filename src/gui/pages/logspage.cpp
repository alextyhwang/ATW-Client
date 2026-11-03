//
// Logs tab - live view of Lunar Client launcher output.
//

#include "logspage.h"

#include <QVBoxLayout>
#include <QFile>
#include <QFont>
#include <QFontDatabase>
#include <QRegularExpression>
#include <QTabBar>

#include "util/fs.h"

LogHighlighter::LogHighlighter(QTextDocument* parent) : QSyntaxHighlighter(parent) {
    errorFormat.setForeground(QColor(0xFF, 0x76, 0x76));   // Figma close-state red
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
    tabWidget->setObjectName(QStringLiteral("subTabs"));
    tabWidget->setDocumentMode(true);
    tabWidget->tabBar()->setUsesScrollButtons(false);
    tabWidget->tabBar()->setDrawBase(false);
    tabWidget->tabBar()->setCursor(Qt::PointingHandCursor);
    // Platform monospace font (Consolas, Menlo, ...); styled as a console.
    const QFont logFont = QFontDatabase::systemFont(QFontDatabase::FixedFont);
    mainLogEdit = new QPlainTextEdit(this);
    mainLogEdit->setObjectName(QStringLiteral("logView"));
    mainLogEdit->setReadOnly(true);
    mainLogEdit->setFont(logFont);
    mainLogEdit->setAccessibleName(QStringLiteral("Main log"));
    mainLogEdit->setPlaceholderText(QStringLiteral("Launcher output appears here while ATW Client runs."));
    new LogHighlighter(mainLogEdit->document());

    rendererLogEdit = new QPlainTextEdit(this);
    rendererLogEdit->setObjectName(QStringLiteral("logView"));
    rendererLogEdit->setReadOnly(true);
    rendererLogEdit->setFont(logFont);
    rendererLogEdit->setAccessibleName(QStringLiteral("Renderer log"));
    rendererLogEdit->setPlaceholderText(QStringLiteral("Game output appears here while ATW Client runs."));
    new LogHighlighter(rendererLogEdit->document());

    tabWidget->addTab(mainLogEdit, QStringLiteral("Main"));
    tabWidget->addTab(rendererLogEdit, QStringLiteral("Renderer"));

    QVBoxLayout* layout = new QVBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
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
