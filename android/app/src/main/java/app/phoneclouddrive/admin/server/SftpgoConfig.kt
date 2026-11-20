package app.phoneclouddrive.admin.server

/**
 * SFTPGo's configuration, as SFTPGO_SECTION__KEY environment variables (no sftpgo.json needed).
 * Mirrors server/sftpgo/drive.env.example, with one deliberate difference: the admin side
 * lives on its own loopback-only binding, so it can never be reached from outside the phone.
 *
 *  - binding 0 (users):  web client only, 127.0.0.1:28080 (or 0.0.0.0 with LAN access)
 *  - binding 1 (admin):  REST API only, 127.0.0.1:28081. The web admin UI is disabled everywhere.
 *  - WebDAV:             127.0.0.1:28082 (or 0.0.0.0 with LAN access)
 *  - SFTP / FTP:         off
 *
 * Kept free of Android types so it can be unit-tested.
 */
object SftpgoConfig {
    // Uncommon ports on purpose: 8080 & co. are often taken by other server apps on the phone
    // (web servers, other tunnels). Below the ephemeral range so outgoing connections never hold them.
    const val WEB_PORT = 28080
    const val ADMIN_PORT = 28081
    const val WEBDAV_PORT = 28082
    /** cloudflared's metrics/readiness endpoint. */
    const val TUNNEL_METRICS_PORT = 28083
    const val LOOPBACK = "127.0.0.1"

    data class Input(
        val dataDir: String,        // database + signing key
        val uiDir: String,          // templates/ and static/ (extracted by UiInstaller)
        val storageBase: String,    // one folder per user
        val lanAccess: Boolean,
        val adminUser: String,
        val adminPassword: String,
    )

    fun env(i: Input): Map<String, String> {
        val listen = if (i.lanAccess) "" else LOOPBACK // "" = all interfaces
        return linkedMapOf(
            // ---- storage / database ----
            "SFTPGO_DATA_PROVIDER__DRIVER" to "sqlite",
            "SFTPGO_DATA_PROVIDER__NAME" to "${i.dataDir}/sftpgo.db",
            "SFTPGO_DATA_PROVIDER__USERS_BASE_DIR" to i.storageBase,
            // Count storage usage for every user, not only those with a quota.
            "SFTPGO_DATA_PROVIDER__TRACK_QUOTA" to "1",
            // First start (or after an admin reset): create the app's admin account.
            "SFTPGO_DATA_PROVIDER__CREATE_DEFAULT_ADMIN" to "true",
            "SFTPGO_DEFAULT_ADMIN_USERNAME" to i.adminUser,
            "SFTPGO_DEFAULT_ADMIN_PASSWORD" to i.adminPassword,

            // ---- protocols ----
            "SFTPGO_SFTPD__BINDINGS__0__PORT" to "0",
            "SFTPGO_FTPD__BINDINGS__0__PORT" to "0",
            "SFTPGO_WEBDAVD__BINDINGS__0__PORT" to "$WEBDAV_PORT",
            "SFTPGO_WEBDAVD__BINDINGS__0__ADDRESS" to listen,
            "SFTPGO_WEBDAVD__BINDINGS__0__PROXY_ALLOWED" to "127.0.0.1,::1",
            "SFTPGO_WEBDAVD__BINDINGS__0__CLIENT_IP_PROXY_HEADER" to "CF-Connecting-IP",

            // ---- web UI files and session signing ----
            "SFTPGO_HTTPD__TEMPLATES_PATH" to "${i.uiDir}/templates",
            "SFTPGO_HTTPD__STATIC_FILES_PATH" to "${i.uiDir}/static",
            // Email templates are loaded at startup even without SMTP; the default path is
            // relative to the config dir, where they don't exist.
            "SFTPGO_SMTP__TEMPLATES_PATH" to "${i.uiDir}/templates",
            // Fixed key => web logins survive restarts and reboots. Replaced by "Sign out everywhere".
            "SFTPGO_HTTPD__SIGNING_PASSPHRASE_FILE" to "${i.dataDir}/signing.key",
            // 720 min (12 h) is the most SFTPGo accepts; renewed while the drive is in use.
            "SFTPGO_HTTPD__COOKIE_LIFETIME" to "720",
            // Don't tie a login to one IP (phones move between Wi-Fi and mobile data).
            "SFTPGO_HTTPD__TOKEN_VALIDATION" to "1",

            // ---- binding 0: the web drive for users ----
            "SFTPGO_HTTPD__BINDINGS__0__PORT" to "$WEB_PORT",
            "SFTPGO_HTTPD__BINDINGS__0__ADDRESS" to listen,
            "SFTPGO_HTTPD__BINDINGS__0__ENABLE_WEB_CLIENT" to "true",
            "SFTPGO_HTTPD__BINDINGS__0__ENABLE_WEB_ADMIN" to "false",
            "SFTPGO_HTTPD__BINDINGS__0__ENABLE_REST_API" to "false",
            "SFTPGO_HTTPD__BINDINGS__0__RENDER_OPENAPI" to "false",
            // Real visitor IPs from the tunnel (trusted only from this phone).
            "SFTPGO_HTTPD__BINDINGS__0__PROXY_ALLOWED" to "127.0.0.1,::1",
            "SFTPGO_HTTPD__BINDINGS__0__CLIENT_IP_PROXY_HEADER" to "CF-Connecting-IP",
            // Security headers; also marks the session cookie Secure behind HTTPS.
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__ENABLED" to "true",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__HTTPS_PROXY_HEADERS__0__KEY" to "X-Forwarded-Proto",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__HTTPS_PROXY_HEADERS__0__VALUE" to "https",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__STS_SECONDS" to "31536000",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__CONTENT_TYPE_NOSNIFF" to "true",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__REFERRER_POLICY" to "same-origin",
            "SFTPGO_HTTPD__BINDINGS__0__SECURITY__PERMISSIONS_POLICY" to "camera=(), microphone=(), geolocation=()",
            // Branding (files in static/drive, added by native/build.sh)
            "SFTPGO_HTTPD__BINDINGS__0__BRANDING__WEB_CLIENT__NAME" to "Drive",
            "SFTPGO_HTTPD__BINDINGS__0__BRANDING__WEB_CLIENT__SHORT_NAME" to "Drive",
            "SFTPGO_HTTPD__BINDINGS__0__BRANDING__WEB_CLIENT__LOGO_PATH" to "/drive/logo.svg",
            "SFTPGO_HTTPD__BINDINGS__0__BRANDING__WEB_CLIENT__FAVICON_PATH" to "/drive/favicon.png",
            "SFTPGO_HTTPD__BINDINGS__0__BRANDING__WEB_CLIENT__EXTRA_CSS" to "/drive/drive.css",

            // ---- binding 1: admin REST API for this app only ----
            "SFTPGO_HTTPD__BINDINGS__1__PORT" to "$ADMIN_PORT",
            "SFTPGO_HTTPD__BINDINGS__1__ADDRESS" to LOOPBACK,
            "SFTPGO_HTTPD__BINDINGS__1__ENABLE_WEB_CLIENT" to "false",
            "SFTPGO_HTTPD__BINDINGS__1__ENABLE_WEB_ADMIN" to "false",
            "SFTPGO_HTTPD__BINDINGS__1__ENABLE_REST_API" to "true",
            "SFTPGO_HTTPD__BINDINGS__1__RENDER_OPENAPI" to "false",

            // ---- brute-force protection (bans an IP after repeated failed logins) ----
            "SFTPGO_COMMON__DEFENDER__ENABLED" to "true",
            "SFTPGO_COMMON__DEFENDER__BAN_TIME" to "30",
            "SFTPGO_COMMON__DEFENDER__THRESHOLD" to "15",
        )
    }
}
