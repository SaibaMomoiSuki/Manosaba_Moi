package com.example.audio

import org.vosk.Model
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Caches one Vosk [Model] across recording sessions.
 *
 * Loading a Vosk model is expensive (the large Chinese model takes seconds and
 * hundreds of MB), so we load it once — on a background thread via [preload] —
 * and reuse it. Loading never blocks the caller: [getIfReady] returns null until
 * the background load completes, and [preload] accepts an optional callback that
 * runs once the model is available.
 *
 * The cache is keyed by model path, so switching models in the settings screen
 * really does switch models instead of silently reusing the old one. The model
 * being replaced is closed to give its (potentially gigabyte-sized) native
 * allocation back, but only once [markInUse] reports that no recognizer is
 * holding it — closing a model out from under a live recognizer crashes the JVM.
 */
object VoskModelCache {

    @Volatile
    private var model: Model? = null

    /** Path of the model currently in [model], or null when nothing is loaded. */
    @Volatile
    private var modelPath: String? = null

    /** Path of the load currently in flight, or null when idle. */
    @Volatile
    private var loadingPath: String? = null

    @Volatile
    private var inUse = false

    @Volatile
    private var statusText: String? = null

    private val lock = Any()

    private val readyCallbacks = CopyOnWriteArrayList<(Boolean) -> Unit>()

    init {
        // Vosk returns UTF-8 strings, but JNA defaults to the platform charset
        // (e.g. GBK on Chinese Windows). That would garble recognized Chinese,
        // so force JNA to decode native char* as UTF-8.
        System.setProperty("jna.encoding", "UTF-8")
    }

    /** Non-blocking; the cached model for [path], or null if it isn't loaded yet. */
    fun getIfReady(path: String): Model? = if (path == modelPath) model else null

    /** Text shown in the lower-left corner while loading, or after a load failure. */
    fun getStatusText(): String? = statusText

    /** Marks whether a recognizer is currently holding the cached model. */
    fun markInUse(value: Boolean) {
        inUse = value
    }

    /**
     * Ensures the model at [path] is loaded on a background thread. Never blocks
     * the caller. [onReady] runs exactly once when that model becomes available
     * (on the loading thread), or immediately if it is already cached. The
     * callback receives whether the model was loaded successfully.
     */
    fun preload(path: String, onReady: ((Boolean) -> Unit)? = null) {
        if (path == modelPath && model != null) {
            onReady?.invoke(true)
            return
        }
        synchronized(lock) {
            if (path == loadingPath) {
                // Same model already loading — piggyback on it.
                if (onReady != null) readyCallbacks.add(onReady)
                return
            }
            // A different model was requested; any in-flight load is now stale
            // and will discard its own result when it sees loadingPath changed.
            loadingPath = path
            statusText = "正在解析语音模型：${modelName(path)}"
        }
        Thread({ load(path, onReady) }, "chatlimit-vosk-model")
            .apply { isDaemon = true }
            .start()
    }

    private fun load(path: String, onReady: ((Boolean) -> Unit)?) {
        val loaded = try {
            Model(path)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }

        var stale = false
        var previous: Model? = null
        var callbacks: List<(Boolean) -> Unit> = emptyList()
        synchronized(lock) {
            if (loadingPath != path) {
                // Superseded by a newer request while we were loading.
                stale = true
            } else {
                previous = model
                model = loaded
                modelPath = if (loaded != null) path else null
                loadingPath = null
                statusText = if (loaded != null) null else "语音模型加载失败：${modelName(path)}"
                callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
            }
        }

        if (stale) {
            closeQuietly(loaded)
            return
        }
        closeQuietly(previous)
        val success = loaded != null
        onReady?.invoke(success)
        callbacks.forEach { it.invoke(success) }
    }

    private fun modelName(path: String): String = try {
        Path.of(path).fileName?.toString() ?: path
    } catch (_: Throwable) {
        path
    }

    /** Frees a model we are no longer using, unless a recognizer still holds it. */
    private fun closeQuietly(m: Model?) {
        if (m == null || inUse) return
        try {
            m.close()
        } catch (_: Throwable) {
            // Best-effort — a leaked model is better than a native crash.
        }
    }
}
