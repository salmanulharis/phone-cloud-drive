package app.phoneclouddrive.admin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

class CloudflareException(message: String) : Exception(message)

/** A domain (zone) in the user's Cloudflare account. */
data class Zone(val id: String, val name: String, val accountId: String)

/** What guided setup produced: the token cloudflared runs with, and the published hostnames. */
data class TunnelSetup(val tunnelId: String, val token: String, val driveHost: String, val davHost: String)

/**
 * Sets up a remotely-managed Cloudflare Tunnel with a user-supplied API token, so no browser login
 * or config files are needed on the phone. The API token is used only during setup and never
 * stored; the app keeps just the tunnel token.
 *
 * Token permissions needed: Account > Cloudflare Tunnel > Edit, Zone > DNS > Edit, Zone > Zone > Read.
 */
class Cloudflare(private val apiToken: String, private val baseUrl: String = "https://api.cloudflare.com/client/v4") {

    suspend fun zones(): List<Zone> = io {
        val res = call("GET", "/zones?per_page=50&status=active")
        val arr = res.getJSONArray("result")
        (0 until arr.length()).map { i ->
            val z = arr.getJSONObject(i)
            Zone(z.getString("id"), z.getString("name"), z.getJSONObject("account").getString("id"))
        }.sortedBy { it.name }
    }

    /**
     * Creates (or reuses) the tunnel [name], points its public hostnames at the local server and
     * creates the DNS records. Safe to run again, e.g. to change hostnames.
     */
    suspend fun setUp(zone: Zone, name: String, driveHost: String, davHost: String): TunnelSetup = io {
        require(driveHost.isNotBlank()) { "Enter the web drive address" }
        listOf(driveHost, davHost).filter { it.isNotBlank() }.forEach { host ->
            if (!host.endsWith(".${zone.name}") && host != zone.name) {
                throw CloudflareException("$host is not part of ${zone.name}")
            }
        }
        val acc = zone.accountId
        val id = findTunnel(acc, name) ?: createTunnel(acc, name)
        call("PUT", "/accounts/$acc/cfd_tunnel/$id/configurations", JSONObject().put("config", ingress(driveHost, davHost)))
        upsertCname(zone.id, driveHost, "$id.cfargotunnel.com")
        if (davHost.isNotBlank()) upsertCname(zone.id, davHost, "$id.cfargotunnel.com")
        val token = call("GET", "/accounts/$acc/cfd_tunnel/$id/token").getString("result")
        TunnelSetup(id, token, driveHost, davHost)
    }

    private fun findTunnel(acc: String, name: String): String? {
        val res = call("GET", "/accounts/$acc/cfd_tunnel?is_deleted=false&name=${enc(name)}")
        val arr = res.getJSONArray("result")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .firstOrNull { it.optString("name") == name }?.getString("id")
    }

    private fun createTunnel(acc: String, name: String): String =
        call("POST", "/accounts/$acc/cfd_tunnel", JSONObject().put("name", name).put("config_src", "cloudflare"))
            .getJSONObject("result").getString("id")

    private fun upsertCname(zoneId: String, host: String, target: String) {
        val existing = call("GET", "/zones/$zoneId/dns_records?name=${enc(host)}").getJSONArray("result")
        val record = JSONObject()
            .put("type", "CNAME").put("name", host).put("content", target).put("proxied", true)
            .put("comment", "phone-cloud-drive tunnel")
        if (existing.length() == 0) {
            call("POST", "/zones/$zoneId/dns_records", record)
            return
        }
        val current = existing.getJSONObject(0)
        if (current.getString("type") != "CNAME") {
            throw CloudflareException(
                "$host already has a ${current.getString("type")} record. Delete it in the Cloudflare dashboard, or pick another address."
            )
        }
        call("PUT", "/zones/$zoneId/dns_records/${current.getString("id")}", record)
    }

    // ---- HTTP ----

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: IOException) {
            throw CloudflareException("Can't reach Cloudflare: ${e.message}")
        }
    }

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $apiToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return parse(code, text)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val TUNNEL_NAME = "phone-cloud-drive"
        const val TOKEN_HELP_URL = "https://dash.cloudflare.com/profile/api-tokens"
        const val GUIDE_URL = "https://github.com/salmanulharis/phone-cloud-drive/blob/main/docs/cloudflare-app.md"

        /** Malformed/invalid/expired token or missing permission (6003 = "Invalid request headers"). */
        private val AUTH_ERROR_CODES = setOf(6003, 6100, 6101, 6102, 6103, 6111, 9103, 9106, 9109, 10000, 10001)

        /**
         * Ingress for the tunnel. Origins use 127.0.0.1 (not "localhost", which may resolve to ::1
         * where nothing listens). The admin paths get a 404 at Cloudflare as a second safeguard;
         * they're already disabled on the user-facing binding.
         */
        fun ingress(driveHost: String, davHost: String): JSONObject {
            val rules = JSONArray()
                .put(JSONObject().put("hostname", driveHost).put("path", "^/(web/admin|api/)").put("service", "http_status:404"))
                .put(JSONObject().put("hostname", driveHost).put("service", "http://127.0.0.1:${SftpgoConfig.WEB_PORT}"))
            if (davHost.isNotBlank()) {
                rules.put(JSONObject().put("hostname", davHost).put("service", "http://127.0.0.1:${SftpgoConfig.WEBDAV_PORT}"))
            }
            rules.put(JSONObject().put("service", "http_status:404"))
            return JSONObject().put("ingress", rules)
        }

        /** Cloudflare's {success, errors, result} envelope; throws a readable error on failure. */
        fun parse(code: Int, text: String): JSONObject {
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                throw CloudflareException("Cloudflare returned an unexpected response ($code)")
            }
            if (code in 200..299 && json.optBoolean("success", false)) return json
            val errors = json.optJSONArray("errors")
            val list = (0 until (errors?.length() ?: 0)).map { errors!!.getJSONObject(it) }
            val msg = list.joinToString("; ") { it.optString("message") }
            val authProblem = code == 401 || code == 403 || list.any { it.optInt("code") in AUTH_ERROR_CODES }
            throw CloudflareException(
                when {
                    authProblem -> "Cloudflare rejected the API token. Check that it was copied completely and has the three permissions listed above."
                    msg.isNotBlank() -> msg
                    else -> "Cloudflare request failed ($code)"
                }
            )
        }

        /**
         * Checks a pasted tunnel token (base64 JSON with account "a", tunnel "t" and secret "s")
         * and returns the tunnel ID, or null if it isn't a tunnel token.
         */
        /**
         * Pulls the token out of whatever was pasted: the bare token, or a whole install command
         * from the dashboard ("cloudflared.exe service install eyJ…", "… --token eyJ…").
         */
        fun extractToken(pasted: String): String =
            TOKEN_IN_TEXT.find(pasted)?.value ?: pasted.trim()

        private val TOKEN_IN_TEXT = Regex("""eyJ[A-Za-z0-9+/_=-]{20,}""")

        fun tunnelIdFromToken(token: String): String? = try {
            val json = JSONObject(String(Base64.getDecoder().decode(token.trim())))
            json.getString("t").takeIf { json.optString("a").isNotBlank() && json.optString("s").isNotBlank() }
        } catch (_: Exception) {
            null
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
