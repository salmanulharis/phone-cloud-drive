package app.phoneclouddrive.admin

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Minimal client for SFTPGo's admin REST API (/api/v2).
 * The admin password is kept in memory only, so an expired token can be renewed silently.
 */
class SftpgoApi(private val baseUrl: String) {
    private var token: String? = null
    private var adminUser = ""
    private var adminPass = ""

    suspend fun login(user: String, pass: String) = io {
        adminUser = user
        adminPass = pass
        fetchToken()
    }

    suspend fun users(): List<JSONObject> = io {
        JSONArray(request("GET", "/api/v2/users?limit=500")).objects()
    }

    suspend fun user(name: String): JSONObject = io {
        JSONObject(request("GET", "/api/v2/users/${enc(name)}"))
    }

    suspend fun createUser(user: JSONObject) = io {
        request("POST", "/api/v2/users", user.toString())
    }

    suspend fun updateUser(name: String, user: JSONObject) = io {
        request("PUT", "/api/v2/users/${enc(name)}", user.toString())
    }

    suspend fun deleteUser(name: String) = io {
        request("DELETE", "/api/v2/users/${enc(name)}")
    }

    suspend fun scanQuota(name: String) = io {
        request("POST", "/api/v2/quotas/users/${enc(name)}/scan")
    }

    suspend fun connections(): List<JSONObject> = io {
        JSONArray(request("GET", "/api/v2/connections")).objects()
    }

    suspend fun disconnect(id: String) = io {
        request("DELETE", "/api/v2/connections/${enc(id)}")
    }

    suspend fun status(): JSONObject = io {
        JSONObject(request("GET", "/api/v2/status"))
    }

    // ---- internals ----

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "Can't reach the drive server at $baseUrl. Is Termux running?")
        }
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }

    private fun fetchToken() {
        val c = open("/api/v2/token", "GET")
        val basic = Base64.encodeToString("$adminUser:$adminPass".toByteArray(), Base64.NO_WRAP)
        c.setRequestProperty("Authorization", "Basic $basic")
        val code = c.responseCode
        val body = read(c)
        if (code == 401) throw ApiException(401, "Wrong admin username or password")
        if (code !in 200..299) throw ApiException(code, errorText(code, body))
        token = JSONObject(body).getString("access_token")
    }

    private fun request(method: String, path: String, body: String? = null, retry: Boolean = true): String {
        if (token == null) fetchToken()
        val c = open(path, method)
        c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = read(c)
        if (code == 401 && retry) {
            token = null
            return request(method, path, body, retry = false)
        }
        if (code !in 200..299) throw ApiException(code, errorText(code, text))
        return text
    }

    private fun read(c: HttpURLConnection): String {
        val stream = if (c.responseCode >= 400) c.errorStream else c.inputStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun errorText(code: Int, body: String): String {
        val detail = try {
            val o = JSONObject(body)
            listOf(o.optString("message"), o.optString("error")).filter { it.isNotBlank() }.joinToString(": ")
        } catch (e: Exception) {
            body.take(200)
        }
        return if (detail.isBlank()) "Server error ($code)" else detail
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
