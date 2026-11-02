//
// Microsoft account sign-in for Minecraft. See msaauth.h.
//

#include "msaauth.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QTimer>
#include <QUuid>

namespace {
// Minecraft for Nintendo Switch title. Like prismarine-auth, it uses the
// consumer live.com device-code flow, which needs no Azure app approval.
const QString builtInClientId = QStringLiteral("00000000441cc96b");
const QString liveScope = QStringLiteral("service::user.auth.xboxlive.com::MBI_SSL");
const QString azureScope = QStringLiteral("XboxLive.signin offline_access");
const int requestTimeoutMs = 20000;

QString describeOAuthError(const QString& prefix, const QJsonObject& body) {
    QString detail = body["error_description"].toString();
    if (detail.isEmpty())
        detail = body["error"].toString();
    // Microsoft appends trace and correlation lines to some descriptions.
    detail = detail.section(QStringLiteral("\r\n"), 0, 0).trimmed();
    return detail.isEmpty() ? prefix + QStringLiteral(".") : prefix + QStringLiteral(": ") + detail;
}

QString describeXstsError(qint64 code) {
    switch (code) {
    case 2148916233:
        return QStringLiteral("This Microsoft account has no Xbox profile yet. Sign in once at xbox.com to create one, then try again.");
    case 2148916235:
        return QStringLiteral("Xbox Live is not available in this account's country or region.");
    case 2148916236:
    case 2148916237:
        return QStringLiteral("This account needs adult verification on xbox.com before it can play.");
    case 2148916238:
        return QStringLiteral("This is a child account. An adult must add it to a Microsoft family before it can play.");
    default:
        return QStringLiteral("Xbox Live refused this account (error %1).").arg(code);
    }
}

// The Minecraft token is a JWT whose payload carries the Xbox user ID.
QString xuidFromMinecraftToken(const QString& token) {
    const QString payload = token.section(QLatin1Char('.'), 1, 1);
    const QByteArray json = QByteArray::fromBase64(payload.toLatin1(),
        QByteArray::Base64UrlEncoding | QByteArray::OmitTrailingEquals);
    return QJsonDocument::fromJson(json).object()["xuid"].toString();
}
}

MsaAuth::MsaAuth(const QString& clientId, QObject* parent)
    : QObject(parent),
      network(new QNetworkAccessManager(this)),
      pollTimer(new QTimer(this)),
      clientId(resolveClientId(clientId)) {
    pollTimer->setSingleShot(true);
    connect(pollTimer, &QTimer::timeout, this, &MsaAuth::pollToken);
}

QString MsaAuth::defaultClientId() {
    return builtInClientId;
}

QString MsaAuth::resolveClientId(const QString& configured) {
    const QString trimmed = configured.trimmed();
    return trimmed.isEmpty() ? builtInClientId : trimmed;
}

// Azure app registrations use GUIDs; first-party title IDs are 16 hex digits.
bool MsaAuth::usesAzureEndpoints() const {
    return !QUuid::fromString(clientId).isNull();
}

QString MsaAuth::scope() const {
    return usesAzureEndpoints() ? azureScope : liveScope;
}

QUrl MsaAuth::oauthUrl(bool deviceCodeEndpoint) const {
    if (usesAzureEndpoints()) {
        return QUrl(deviceCodeEndpoint
            ? QStringLiteral("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
            : QStringLiteral("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"));
    }
    return QUrl(deviceCodeEndpoint
        ? QStringLiteral("https://login.live.com/oauth20_connect.srf")
        : QStringLiteral("https://login.live.com/oauth20_token.srf"));
}

void MsaAuth::startDeviceLogin() {
    cancel();
    account = {};
    emit progress(QStringLiteral("Requesting a sign-in code..."));

    QUrlQuery form;
    form.addQueryItem(QStringLiteral("client_id"), clientId);
    form.addQueryItem(QStringLiteral("scope"), scope());
    if (!usesAzureEndpoints())
        form.addQueryItem(QStringLiteral("response_type"), QStringLiteral("device_code"));

    postForm(oauthUrl(true), form, [this](int status, const QJsonObject& body) {
        deviceCodeValue = body["device_code"].toString();
        const QString userCode = body["user_code"].toString();
        if (status != 200 || deviceCodeValue.isEmpty() || userCode.isEmpty())
            return fail(describeOAuthError(QStringLiteral("Microsoft did not issue a sign-in code"), body));

        pollIntervalSeconds = qMax(1, body["interval"].toInt(5));
        deviceCodeExpiry = QDateTime::currentDateTimeUtc().addSecs(body["expires_in"].toInt(900));
        const QUrl uri(body["verification_uri"].toString(QStringLiteral("https://www.microsoft.com/link")));
        emit deviceCode(userCode, uri);
        emit progress(QStringLiteral("Waiting for you to finish signing in..."));
        pollTimer->start(pollIntervalSeconds * 1000);
    });
}

void MsaAuth::pollToken() {
    if (QDateTime::currentDateTimeUtc() >= deviceCodeExpiry)
        return fail(QStringLiteral("The sign-in code expired. Click Try again for a new code."));

    QUrlQuery form;
    form.addQueryItem(QStringLiteral("client_id"), clientId);
    form.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("urn:ietf:params:oauth:grant-type:device_code"));
    form.addQueryItem(QStringLiteral("device_code"), deviceCodeValue);

    postForm(oauthUrl(false), form, [this](int status, const QJsonObject& body) {
        if (status == 200)
            return onMicrosoftTokens(body, false);

        const QString error = body["error"].toString();
        if (error == QLatin1String("authorization_pending")) {
            pollTimer->start(pollIntervalSeconds * 1000);
        } else if (error == QLatin1String("slow_down")) {
            pollIntervalSeconds += 5;
            pollTimer->start(pollIntervalSeconds * 1000);
        } else if (error == QLatin1String("expired_token") || error == QLatin1String("code_expired")) {
            fail(QStringLiteral("The sign-in code expired. Click Try again for a new code."));
        } else if (error == QLatin1String("authorization_declined") || error == QLatin1String("access_denied")) {
            fail(QStringLiteral("Sign-in was declined in the browser."));
        } else {
            fail(describeOAuthError(QStringLiteral("Microsoft sign-in failed"), body));
        }
    });
}

void MsaAuth::refresh(const QString& refreshToken) {
    cancel();
    account = {};
    if (refreshToken.isEmpty())
        return fail(QStringLiteral("Sign in with Microsoft to play online."), true);

    emit progress(QStringLiteral("Refreshing your Minecraft session..."));
    QUrlQuery form;
    form.addQueryItem(QStringLiteral("client_id"), clientId);
    form.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("refresh_token"));
    form.addQueryItem(QStringLiteral("refresh_token"), refreshToken);
    form.addQueryItem(QStringLiteral("scope"), scope());

    postForm(oauthUrl(false), form, [this, refreshToken](int status, const QJsonObject& body) {
        if (status == 200) {
            account.refreshToken = refreshToken; // kept if Microsoft does not rotate it
            return onMicrosoftTokens(body, true);
        }
        // 4xx means Microsoft rejected the saved sign-in; 5xx is worth retrying.
        const bool rejected = status >= 400 && status < 500;
        fail(rejected ? QStringLiteral("Your saved Microsoft sign-in has expired. Sign in again.")
                      : describeOAuthError(QStringLiteral("Microsoft sign-in is unavailable"), body),
             rejected);
    });
}

void MsaAuth::onMicrosoftTokens(const QJsonObject& body, bool fromRefresh) {
    const QString accessToken = body["access_token"].toString();
    if (accessToken.isEmpty())
        return fail(QStringLiteral("Microsoft returned no access token."), fromRefresh);
    const QString refreshToken = body["refresh_token"].toString();
    if (!refreshToken.isEmpty())
        account.refreshToken = refreshToken;
    authenticateXbox(accessToken, fromRefresh);
}

void MsaAuth::authenticateXbox(const QString& msaAccessToken, bool fromRefresh) {
    emit progress(QStringLiteral("Signing in to Xbox Live..."));
    // Azure tokens need the "d=" prefix; live.com tokens are sent raw.
    const QString ticket = usesAzureEndpoints() ? QStringLiteral("d=") + msaAccessToken : msaAccessToken;
    const QJsonObject request{
        {"Properties", QJsonObject{
            {"AuthMethod", "RPS"},
            {"SiteName", "user.auth.xboxlive.com"},
            {"RpsTicket", ticket}}},
        {"RelyingParty", "http://auth.xboxlive.com"},
        {"TokenType", "JWT"}};

    postJson(QUrl(QStringLiteral("https://user.auth.xboxlive.com/user/authenticate")), request,
             [this, fromRefresh](int status, const QJsonObject& body) {
        const QString token = body["Token"].toString();
        if (status != 200 || token.isEmpty()) {
            return fail(QStringLiteral("Xbox Live sign-in failed (HTTP %1).").arg(status),
                        fromRefresh && (status == 400 || status == 401));
        }
        authorizeXsts(token);
    });
}

void MsaAuth::authorizeXsts(const QString& xblToken) {
    const QJsonObject request{
        {"Properties", QJsonObject{
            {"SandboxId", "RETAIL"},
            {"UserTokens", QJsonArray{xblToken}}}},
        {"RelyingParty", "rp://api.minecraftservices.com/"},
        {"TokenType", "JWT"}};

    postJson(QUrl(QStringLiteral("https://xsts.auth.xboxlive.com/xsts/authorize")), request,
             [this](int status, const QJsonObject& body) {
        if (status == 401)
            return fail(describeXstsError(qint64(body["XErr"].toDouble())), true);
        const QString token = body["Token"].toString();
        const QString userHash = body["DisplayClaims"].toObject()["xui"].toArray()
            .at(0).toObject()["uhs"].toString();
        if (status != 200 || token.isEmpty() || userHash.isEmpty())
            return fail(QStringLiteral("Xbox Live authorization failed (HTTP %1).").arg(status));
        loginMinecraft(userHash, token);
    });
}

void MsaAuth::loginMinecraft(const QString& userHash, const QString& xstsToken) {
    emit progress(QStringLiteral("Signing in to Minecraft..."));
    const QJsonObject request{
        {"identityToken", QStringLiteral("XBL3.0 x=%1;%2").arg(userHash, xstsToken)}};

    postJson(QUrl(QStringLiteral("https://api.minecraftservices.com/authentication/login_with_xbox")), request,
             [this](int status, const QJsonObject& body) {
        const QString token = body["access_token"].toString();
        if (status == 200 && !token.isEmpty()) {
            account.accessToken = token;
            account.expiresAt = QDateTime::currentDateTimeUtc().addSecs(body["expires_in"].toInt(86400));
            account.xuid = xuidFromMinecraftToken(token);
            return fetchProfile();
        }
        if (status == 429)
            return fail(QStringLiteral("Minecraft is rate limiting sign-ins. Wait a minute and try again."));
        if (status == 403 && usesAzureEndpoints())
            return fail(QStringLiteral("Minecraft rejected the custom msaClientId (not an approved app). "
                                       "Clear msaClientId in settings.json to use the built-in sign-in."), true);
        fail(QStringLiteral("Minecraft login failed (HTTP %1).").arg(status), status == 401 || status == 403);
    });
}

void MsaAuth::fetchProfile() {
    QNetworkRequest request(QUrl(QStringLiteral("https://api.minecraftservices.com/minecraft/profile")));
    request.setTransferTimeout(requestTimeoutMs);
    request.setRawHeader("Accept", "application/json");
    request.setRawHeader("Authorization", "Bearer " + account.accessToken.toUtf8());

    send(network->get(request), [this](int status, const QJsonObject& body) {
        if (status == 404) {
            return fail(QStringLiteral("This Microsoft account does not own Minecraft: Java Edition, "
                                       "or has not chosen a Java profile name yet. Set it up at minecraft.net, "
                                       "then sign in again."), true);
        }
        account.uuid = body["id"].toString();
        account.name = body["name"].toString();
        if (status != 200 || account.uuid.isEmpty() || account.name.isEmpty())
            return fail(QStringLiteral("Could not read your Minecraft profile (HTTP %1).").arg(status));
        emit progress(QStringLiteral("Signed in as %1.").arg(account.name));
        emit succeeded(account);
    });
}

void MsaAuth::postForm(const QUrl& url, const QUrlQuery& form, Handler handler) {
    QNetworkRequest request(url);
    request.setTransferTimeout(requestTimeoutMs);
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/x-www-form-urlencoded"));
    request.setRawHeader("Accept", "application/json");
    // QUrlQuery leaves '+' and other reserved characters alone, so encode fully.
    QByteArray encoded;
    for (const auto& item : form.queryItems(QUrl::FullyDecoded)) {
        if (!encoded.isEmpty())
            encoded += '&';
        encoded += QUrl::toPercentEncoding(item.first) + '=' + QUrl::toPercentEncoding(item.second);
    }
    send(network->post(request, encoded), std::move(handler));
}

void MsaAuth::postJson(const QUrl& url, const QJsonObject& body, Handler handler) {
    QNetworkRequest request(url);
    request.setTransferTimeout(requestTimeoutMs);
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    request.setRawHeader("Accept", "application/json");
    send(network->post(request, QJsonDocument(body).toJson(QJsonDocument::Compact)), std::move(handler));
}

void MsaAuth::send(QNetworkReply* reply, Handler handler) {
    activeReply = reply;
    const quint64 requestGeneration = generation;
    connect(reply, &QNetworkReply::finished, this, [this, reply, handler, requestGeneration]() {
        reply->deleteLater();
        if (requestGeneration != generation)
            return; // cancelled
        activeReply = nullptr;

        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        if (status == 0) {
            const QString reason = reply->error() == QNetworkReply::OperationCanceledError
                ? QStringLiteral("the request timed out")
                : reply->errorString();
            return fail(QStringLiteral("Could not reach %1 (%2). Check your internet connection.")
                            .arg(reply->url().host(), reason));
        }
        handler(status, QJsonDocument::fromJson(reply->readAll()).object());
    });
}

void MsaAuth::cancel() {
    ++generation;
    pollTimer->stop();
    if (activeReply)
        activeReply->abort();
    activeReply = nullptr;
}

void MsaAuth::fail(const QString& message, bool needsSignIn) {
    cancel();
    account = {};
    emit failed(message, needsSignIn);
}
