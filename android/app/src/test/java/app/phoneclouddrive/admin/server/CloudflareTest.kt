package app.phoneclouddrive.admin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CloudflareTest {
    @Test
    fun tunnelIdFromValidToken() {
        val token = Base64.getEncoder().encodeToString("""{"a":"acc","t":"tunnel-id","s":"c2VjcmV0"}""".toByteArray())
        assertEquals("tunnel-id", Cloudflare.tunnelIdFromToken(token))
        assertEquals("tunnel-id", Cloudflare.tunnelIdFromToken("  $token\n"))
    }

    @Test
    fun extractsTokenFromPastedInstallCommands() {
        val token = Base64.getEncoder().encodeToString("""{"a":"acc","t":"tunnel-id","s":"c2VjcmV0"}""".toByteArray())
        assertEquals(token, Cloudflare.extractToken("cloudflared.exe service install $token"))
        assertEquals(token, Cloudflare.extractToken("sudo cloudflared service install $token\n"))
        assertEquals(token, Cloudflare.extractToken("cloudflared tunnel run --token $token"))
        assertEquals(token, Cloudflare.extractToken("  $token "))
    }

    @Test
    fun rejectsThingsThatAreNotTunnelTokens() {
        assertNull(Cloudflare.tunnelIdFromToken(""))
        assertNull(Cloudflare.tunnelIdFromToken("not base64 !!"))
        assertNull(Cloudflare.tunnelIdFromToken(Base64.getEncoder().encodeToString("""{"t":"x"}""".toByteArray())))
    }

    @Test
    fun ingressBlocksAdminPathsAndUsesIpv4Loopback() {
        val rules = Cloudflare.ingress("drive.example.com", "dav.example.com").getJSONArray("ingress")
        assertEquals(4, rules.length())
        assertEquals("http_status:404", rules.getJSONObject(0).getString("service"))
        assertEquals("^/(web/admin|api/)", rules.getJSONObject(0).getString("path"))
        assertEquals("http://127.0.0.1:28080", rules.getJSONObject(1).getString("service"))
        assertEquals("http://127.0.0.1:28082", rules.getJSONObject(2).getString("service"))
        assertEquals("http_status:404", rules.getJSONObject(3).getString("service"))
        assertTrue(!rules.getJSONObject(3).has("hostname"))
    }

    @Test
    fun ingressWithoutWebdav() {
        assertEquals(3, Cloudflare.ingress("drive.example.com", "").getJSONArray("ingress").length())
    }

    @Test
    fun parseReturnsBodyOnSuccessAndReadableErrors() {
        assertEquals("x", Cloudflare.parse(200, """{"success":true,"result":"x"}""").getString("result"))
        val e = assertThrows(CloudflareException::class.java) {
            Cloudflare.parse(400, """{"success":false,"errors":[{"code":1003,"message":"Invalid name"}]}""")
        }
        assertEquals("Invalid name", e.message)
        val auth = assertThrows(CloudflareException::class.java) { Cloudflare.parse(403, """{"success":false,"errors":[]}""") }
        assertTrue(auth.message!!.contains("rejected the API token"))
        // A malformed token comes back as 400 "Invalid request headers" (code 6003).
        val malformed = assertThrows(CloudflareException::class.java) {
            Cloudflare.parse(400, """{"success":false,"errors":[{"code":6003,"message":"Invalid request headers"}]}""")
        }
        assertTrue(malformed.message!!.contains("rejected the API token"))
        assertThrows(CloudflareException::class.java) { Cloudflare.parse(502, "<html>bad gateway</html>") }
    }
}
