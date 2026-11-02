//
// Microsoft account sign-in for Minecraft: device code -> Xbox Live -> XSTS ->
// Minecraft services. Tokens never leave this module except through
// MinecraftAccount, and none of them are written to logs.
//

#ifndef ATW_AUTH_MSAAUTH_H
#define ATW_AUTH_MSAAUTH_H

#include <functional>

#include <QDateTime>
#include <QJsonObject>
#include <QObject>
#include <QPointer>
#include <QUrl>
#include <QUrlQuery>

class QNetworkAccessManager;
class QNetworkReply;
class QNetworkRequest;
class QTimer;

struct MinecraftAccount {
    QString name;          // Minecraft profile name
    QString uuid;          // 32 hex characters, no dashes
    QString xuid;
    QString accessToken;   // short-lived Minecraft services token
    QDateTime expiresAt;   // UTC expiry of accessToken
    QString refreshToken;  // long-lived Microsoft refresh token
};

class MsaAuth : public QObject {
Q_OBJECT
public:
    // An empty clientId selects the built-in Minecraft title ID.
    explicit MsaAuth(const QString& clientId, QObject* parent = nullptr);

    static QString defaultClientId();
    static QString resolveClientId(const QString& configured);

    void startDeviceLogin();
    void refresh(const QString& refreshToken);
    void cancel();

signals:
    void deviceCode(const QString& userCode, const QUrl& verificationUri);
    void progress(const QString& message);
    void succeeded(const MinecraftAccount& account);
    // needsSignIn is true when only a fresh interactive sign-in can help.
    void failed(const QString& message, bool needsSignIn);

private:
    using Handler = std::function<void(int status, const QJsonObject& body)>;

    bool usesAzureEndpoints() const;
    QString scope() const;
    QUrl oauthUrl(bool deviceCodeEndpoint) const;

    void postForm(const QUrl& url, const QUrlQuery& form, Handler handler);
    void postJson(const QUrl& url, const QJsonObject& body, Handler handler);
    void send(QNetworkReply* reply, Handler handler);

    void pollToken();
    void onMicrosoftTokens(const QJsonObject& body, bool fromRefresh);
    void authenticateXbox(const QString& msaAccessToken, bool fromRefresh);
    void authorizeXsts(const QString& xblToken);
    void loginMinecraft(const QString& userHash, const QString& xstsToken);
    void fetchProfile();
    void fail(const QString& message, bool needsSignIn = false);

    QNetworkAccessManager* network;
    QTimer* pollTimer;
    QPointer<QNetworkReply> activeReply;
    quint64 generation = 0;

    QString clientId;
    QString deviceCodeValue;
    int pollIntervalSeconds = 5;
    QDateTime deviceCodeExpiry;
    MinecraftAccount account;
};

#endif // ATW_AUTH_MSAAUTH_H
