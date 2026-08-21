package de.mirkohaaser.claudechappe

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Notified whenever the set of known sessions or any of their states changes. */
fun interface ClaudeStatusListener {
    fun statusChanged(sessions: List<ClaudeSession>)
}

/**
 * Polls the status directory that the Claude Code hooks write into and keeps
 * the current set of sessions in memory.
 *
 * Polling rather than a file watcher on purpose: the directory holds a handful
 * of tiny files, a directory listing costs next to nothing, and a watcher would
 * add platform-specific failure modes for no gain.
 */
@Service(Service.Level.APP)
class ClaudeStatusStore : Disposable {

    companion object {
        @JvmField
        val TOPIC: Topic<ClaudeStatusListener> =
            Topic.create("Claude session status", ClaudeStatusListener::class.java)

        fun getInstance(): ClaudeStatusStore = service()
    }

    @Volatile
    var sessions: List<ClaudeSession> = emptyList()
        private set

    private var pollTask: ScheduledFuture<*>? = null

    @Synchronized
    fun ensureRunning() {
        if (pollTask?.isCancelled == false) return
        restart()
    }

    /** Reschedules the poll loop, e.g. after the interval was changed in the settings. */
    @Synchronized
    fun restart() {
        pollTask?.cancel(false)
        val interval = ChappeSettings.getInstance().pollIntervalMillis
        pollTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            ::poll, 0, interval, TimeUnit.MILLISECONDS,
        )
    }

    private fun poll() {
        val fresh = try {
            read(ChappeSettings.getInstance().statusDirectory)
        } catch (e: Exception) {
            thisLogger().debug("Could not read Claude status directory", e)
            return
        }
        if (fresh == sessions) return
        sessions = fresh
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed) return
        app.messageBus.syncPublisher(TOPIC).statusChanged(fresh)
    }

    private fun read(dir: Path): List<ClaudeSession> {
        if (!Files.isDirectory(dir)) return emptyList()
        val result = ArrayList<ClaudeSession>()
        Files.newDirectoryStream(dir, "*.json").use { entries ->
            for (file in entries) {
                readSession(file)?.let(result::add)
            }
        }
        result.sortBy { it.id }
        return result
    }

    private fun readSession(file: Path): ClaudeSession? = try {
        val fields = FlatJson.parse(Files.readString(file))
        val cwd = fields["cwd"].orEmpty()
        if (cwd.isEmpty()) {
            null
        } else {
            val session = ClaudeSession(
                id = fields["session"] ?: file.fileName.toString().removeSuffix(".json"),
                cwd = cwd,
                state = ClaudeState.parse(fields["state"]),
                timestamp = fields["ts"]?.toLongOrNull() ?: 0L,
                pid = fields["pid"]?.toLongOrNull() ?: 0L,
            )
            // A crashed session leaves its file behind. Drop it and clean up, so
            // a dead "working" state does not keep a window green forever.
            if (session.isAlive()) {
                session
            } else {
                runCatching { Files.deleteIfExists(file) }
                null
            }
        }
    } catch (e: Exception) {
        // A half-written file is normal - the hook writes to a temp file and
        // renames, but a reader can still lose the race on some filesystems.
        thisLogger().debug("Skipping unreadable status file $file", e)
        null
    }

    /** All live sessions whose working directory sits inside [basePath]. */
    fun sessionsUnder(basePath: String?): List<ClaudeSession> {
        val base = normalize(basePath) ?: return emptyList()
        return sessions.filter { session ->
            val cwd = normalize(session.cwd) ?: return@filter false
            cwd == base || cwd.startsWith("$base/")
        }
    }

    /**
     * Resolved paths, keyed by what was handed in. Resolving touches the file
     * system, and both sides of the comparison are asked for on every poll.
     */
    private val resolved = ConcurrentHashMap<String, String>()

    private fun normalize(path: String?): String? {
        if (path.isNullOrBlank()) return null
        // The IDE reports a project path with symlinks resolved, the hook writes
        // the working directory as the shell saw it. Comparing them as they come
        // leaves a project opened through a link without any colour.
        val real = resolved[path]
            ?: runCatching { Paths.get(path).toRealPath().toString() }.getOrNull()
                ?.also { resolved[path] = it }
            ?: path
        var p = real.replace('\\', '/').trimEnd('/')
        if (p.isEmpty()) p = "/"
        return if (ChappeSettings.pathsAreCaseInsensitive) p.lowercase() else p
    }

    override fun dispose() {
        pollTask?.cancel(false)
        pollTask = null
    }
}
