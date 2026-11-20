package app.phoneclouddrive.admin.server

import org.junit.Assert.assertEquals
import org.junit.Test

class SupervisorTest {
    @Test
    fun stripsAnsiColours() {
        assertEquals(
            "2026-11-20T04:42:44.329 INF Data provider initialized",
            Supervisor.stripAnsi("\u001B[90m2026-11-20T04:42:44.329\u001B[0m \u001B[32mINF\u001B[0m \u001B[1mData provider initialized\u001B[0m")
        )
    }

    @Test
    fun explainsPortConflicts() {
        val line = """{"level":"error","message":"could not start HTTP server: listen tcp 127.0.0.1:28080: bind: address already in use"}"""
        assertEquals("Port 28080 is already used by another app on this phone.", explainCommon(line))
        assertEquals(null, explainCommon("INF all good"))
    }

    @Test
    fun explainsTunnelProblems() {
        val line = """2026-11-20T05:13:52Z ERR Register tunnel error from server side error="Unauthorized: Invalid tunnel secret" connIndex=0"""
        assertEquals("Cloudflare rejected the tunnel token. Set up the Cloudflare connection again.", explainTunnel(line))
        assertEquals(null, explainTunnel("INF Registered tunnel connection connIndex=0"))
        assertEquals(
            "This tunnel doesn't exist in Cloudflare (deleted?). Set up the Cloudflare connection again.",
            explainTunnel("""ERR Register tunnel error from server side error="Failed to get tunnel" connIndex=0""")
        )
        // DNS fallback noise at startup is not a problem worth showing.
        assertEquals(null, explainTunnel("""ERR Failed to fetch features, default to disable error="lookup cfd-features.argotunnel.com on [::1]:53: read: connection refused""""))
    }

    @Test
    fun logBufferKeepsTheTail() {
        val b = LogBuffer(3)
        (1..5).forEach { b.add("line $it") }
        assertEquals(listOf("line 3", "line 4", "line 5"), b.snapshot())
    }
}
