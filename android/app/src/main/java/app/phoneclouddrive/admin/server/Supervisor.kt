package app.phoneclouddrive.admin.server

import android.os.SystemClock
import java.io.File
import java.io.FileWriter
import java.io.Writer

/**
 * One supervised program. [prepare] runs before every start; a non-null result is shown and
 * retried. [explain] turns a known output line (e.g. a port conflict) into a user-facing problem,
 * shown if the program then exits.
 */
class ProcessSpec(
    val name: String,
    val command: List<String>,
    val env: Map<String, String>,
    val workdir: File,
    val prepare: () -> String? = { null },
    val explain: (String) -> String? = ::explainCommon,
)

private val PORT_IN_USE = Regex("""listen tcp [^ ]*:(\d+): bind: address already in use""")

/** Problems every server program can hit, in words a user can act on. */
fun explainCommon(line: String): String? =
    PORT_IN_USE.find(line)?.let { "Port ${it.groupValues[1]} is already used by another app on this phone." }

/** cloudflared retries forever on these, so they have to be recognised from its log. */
fun explainTunnel(line: String): String? = when {
    "Unauthorized" in line -> "Cloudflare rejected the tunnel token. Set up the Cloudflare connection again."
    "Failed to get tunnel" in line -> "This tunnel doesn't exist in Cloudflare (deleted?). Set up the Cloudflare connection again."
    "no such host" in line || "network is unreachable" in line ->
        "Can't reach Cloudflare. Check the phone's internet connection."
    else -> explainCommon(line)
}

enum class ProcState { STARTING, RUNNING, WAITING, BACKOFF, STOPPED }

data class ProcStatus(val name: String, val state: ProcState, val restarts: Int, val message: String?)

/**
 * Runs each [ProcessSpec] on its own thread and restarts it with exponential backoff (1 s up to
 * 60 s) when it exits. Output goes to an in-memory tail for the UI and to a rotated log file.
 *
 * The programs are children of the app process, so Android stops them together with the app;
 * [killStale] covers the rare leftover from a crash so ports are free on the next start.
 */
class Supervisor(specs: List<ProcessSpec>, private val logDir: File, private val onChange: () -> Unit) {
    val daemons = specs.map { Daemon(it) }

    fun start() = daemons.forEach { it.start() }

    fun stop() {
        daemons.forEach { it.signalStop() }
        daemons.forEach { it.awaitStop(GRACE_MS) }
    }

    fun restart(name: String) = daemons.firstOrNull { it.spec.name == name }?.restart()

    fun statuses(): List<ProcStatus> = daemons.map { it.status }

    inner class Daemon(val spec: ProcessSpec) {
        @Volatile var status = ProcStatus(spec.name, ProcState.STOPPED, 0, null)
            private set
        val log = LogBuffer(400)

        @Volatile private var process: Process? = null
        @Volatile private var stopping = false
        @Volatile private var restartRequested = false
        @Volatile private var lastProblem: String? = null
        private var thread: Thread? = null

        fun start() {
            stopping = false
            thread = Thread(::loop, "drive-${spec.name}").apply { isDaemon = true; start() }
        }

        fun signalStop() {
            stopping = true
            process?.destroy()
            thread?.interrupt()
        }

        fun restart() {
            restartRequested = true
            log.add("— restart requested")
            process?.destroy() ?: thread?.interrupt()
        }

        fun awaitStop(ms: Long) {
            thread?.join(ms)
            process?.takeIf { it.isAlive }?.destroyForcibly()
            thread?.join(1_000)
        }

        private fun set(state: ProcState, message: String? = null) {
            status = status.copy(state = state, message = message)
            onChange()
        }

        private fun loop() {
            var backoff = MIN_BACKOFF_MS
            while (!stopping) {
                val problem = try {
                    spec.prepare()
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
                if (problem != null) {
                    log.add("! $problem")
                    set(ProcState.WAITING, problem)
                    if (!sleep(WAIT_RETRY_MS)) break
                    continue
                }
                restartRequested = false
                set(ProcState.STARTING)
                val started = SystemClock.elapsedRealtime()
                val rc = try {
                    runOnce()
                } catch (e: Exception) {
                    log.add("! ${e.message}")
                    -1
                }
                process = null
                if (stopping) break
                if (restartRequested) {
                    restartRequested = false
                    backoff = MIN_BACKOFF_MS
                    continue
                }
                if (SystemClock.elapsedRealtime() - started > STABLE_MS) backoff = MIN_BACKOFF_MS
                status = status.copy(restarts = status.restarts + 1)
                val reason = lastProblem ?: "Stopped unexpectedly (exit code $rc)."
                val msg = "$reason Retrying in ${backoff / 1000} s."
                log.add("! $msg")
                set(ProcState.BACKOFF, msg)
                if (!sleep(backoff)) break
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            set(ProcState.STOPPED)
        }

        private fun runOnce(): Int {
            lastProblem = null
            val pb = ProcessBuilder(spec.command).directory(spec.workdir).redirectErrorStream(true)
            pb.environment().putAll(spec.env)
            val p = pb.start()
            process = p
            if (stopping) p.destroy()
            p.outputStream.close()
            set(ProcState.RUNNING)
            LogFile(File(logDir, "${spec.name}.log")).use { file ->
                p.inputStream.bufferedReader().forEachLine { raw ->
                    val line = stripAnsi(raw)
                    spec.explain(line)?.let { problem ->
                        // Shown right away: some programs (cloudflared) keep retrying instead of exiting.
                        if (problem != lastProblem) {
                            lastProblem = problem
                            status = status.copy(message = problem)
                            onChange()
                        }
                    }
                    log.add(line)
                    file.write(line)
                }
            }
            return p.waitFor()
        }

        /** False if interrupted (stop requested). */
        private fun sleep(ms: Long): Boolean = try {
            Thread.sleep(ms)
            true
        } catch (_: InterruptedException) {
            !stopping
        }
    }

    companion object {
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val STABLE_MS = 60_000L
        private const val WAIT_RETRY_MS = 5_000L
        private const val GRACE_MS = 4_000L

        private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
        fun stripAnsi(s: String) = s.replace(ANSI, "")

        /**
         * Kills leftovers of [executables] from an earlier run of this app (same Linux user; other
         * apps' processes aren't visible or killable). Call only while nothing is supervised.
         */
        fun killStale(executables: List<File>) {
            val targets = executables.map { it.path }.toSet()
            val self = android.os.Process.myPid()
            File("/proc").listFiles { f -> f.name.all(Char::isDigit) }?.forEach { dir ->
                val pid = dir.name.toInt()
                if (pid == self) return@forEach
                val argv0 = try {
                    File(dir, "cmdline").readBytes().takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
                } catch (_: Exception) {
                    return@forEach
                }
                if (argv0 in targets) android.os.Process.killProcess(pid)
            }
        }
    }
}

/** Fixed-size tail of a process's output, for the logs screen. */
class LogBuffer(private val capacity: Int) {
    private val lines = ArrayDeque<String>(capacity)

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}

/** Appends lines to a log file, keeping it under [MAX_BYTES] with one rotated backup (.1). */
private class LogFile(private val file: File) : AutoCloseable {
    private var out: Writer = FileWriter(file, true)
    private var size = file.length()

    fun write(line: String) {
        if (size > MAX_BYTES) {
            out.close()
            file.renameTo(File(file.path + ".1"))
            out = FileWriter(file)
            size = 0
        }
        out.write(line)
        out.write("\n")
        out.flush()
        size += line.length + 1
    }

    override fun close() = out.close()

    companion object {
        const val MAX_BYTES = 1024 * 1024L
    }
}
