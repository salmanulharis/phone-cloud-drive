package app.phoneclouddrive.admin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SftpgoConfigTest {
    private fun env(lan: Boolean = false) = SftpgoConfig.env(
        SftpgoConfig.Input(
            dataDir = "/data/sftpgo", uiDir = "/data/ui", storageBase = "/sdcard/.CloudDrive",
            lanAccess = lan, adminUser = "admin", adminPassword = "secret",
        )
    )

    @Test
    fun adminSideIsLoopbackOnlyAndWebAdminIsOff() {
        val e = env(lan = true)
        assertEquals("127.0.0.1", e["SFTPGO_HTTPD__BINDINGS__1__ADDRESS"])
        assertEquals("true", e["SFTPGO_HTTPD__BINDINGS__1__ENABLE_REST_API"])
        assertEquals("false", e["SFTPGO_HTTPD__BINDINGS__0__ENABLE_REST_API"])
        assertEquals("false", e["SFTPGO_HTTPD__BINDINGS__0__ENABLE_WEB_ADMIN"])
        assertEquals("false", e["SFTPGO_HTTPD__BINDINGS__1__ENABLE_WEB_ADMIN"])
        assertEquals("false", e["SFTPGO_HTTPD__BINDINGS__1__ENABLE_WEB_CLIENT"])
    }

    @Test
    fun usersOnlyThroughLoopbackUnlessLanAccess() {
        assertEquals("127.0.0.1", env()["SFTPGO_HTTPD__BINDINGS__0__ADDRESS"])
        assertEquals("127.0.0.1", env()["SFTPGO_WEBDAVD__BINDINGS__0__ADDRESS"])
        assertEquals("", env(lan = true)["SFTPGO_HTTPD__BINDINGS__0__ADDRESS"])
        assertEquals("", env(lan = true)["SFTPGO_WEBDAVD__BINDINGS__0__ADDRESS"])
    }

    @Test
    fun sftpAndFtpAreDisabled() {
        assertEquals("0", env()["SFTPGO_SFTPD__BINDINGS__0__PORT"])
        assertEquals("0", env()["SFTPGO_FTPD__BINDINGS__0__PORT"])
    }

    @Test
    fun proxyHeadersTrustedOnlyFromLoopback() {
        val e = env()
        assertEquals("127.0.0.1,::1", e["SFTPGO_HTTPD__BINDINGS__0__PROXY_ALLOWED"])
        assertEquals("127.0.0.1,::1", e["SFTPGO_WEBDAVD__BINDINGS__0__PROXY_ALLOWED"])
        assertFalse(e.containsKey("SFTPGO_HTTPD__BINDINGS__1__PROXY_ALLOWED"))
    }

    @Test
    fun pathsAndSecrets() {
        val e = env()
        assertEquals("/data/sftpgo/sftpgo.db", e["SFTPGO_DATA_PROVIDER__NAME"])
        assertEquals("/data/sftpgo/signing.key", e["SFTPGO_HTTPD__SIGNING_PASSPHRASE_FILE"])
        assertEquals("/data/ui/templates", e["SFTPGO_HTTPD__TEMPLATES_PATH"])
        assertEquals("/data/ui/templates", e["SFTPGO_SMTP__TEMPLATES_PATH"])
        assertEquals("/sdcard/.CloudDrive", e["SFTPGO_DATA_PROVIDER__USERS_BASE_DIR"])
        assertEquals("secret", e["SFTPGO_DEFAULT_ADMIN_PASSWORD"])
    }
}
