package com.example.audio

import net.fabricmc.loader.api.FabricLoader
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Installs Vosk speech models into `config/chatlimit_moi/vosk-model/`, either by
 * downloading them or by unpacking a copy embedded in the mod jar.
 *
 * The `build2` jar ships the models under `chatlimit_moi-models/`; the plain
 * `build` jar ships none and relies on downloading. Either way the model has to
 * end up as real files on disk, because Vosk's native loader takes a filesystem
 * path and cannot read from inside a jar.
 *
 * One install runs at a time, always on a background thread. Progress is polled
 * by the settings screen rather than pushed, which keeps this class free of any
 * UI dependency.
 */
object ModelInstaller {

    /** Directory inside the mod jar holding the embedded models (build2 only). */
    private const val BUNDLE_DIR = "chatlimit_moi-models"

    private const val MOD_ID = "chatlimit_moi"

    /** A snapshot of the install in progress. */
    data class Progress(
        val label: String,
        val phase: String,
        val done: Long,
        val total: Long,
    ) {
        val fraction: Float
            get() = if (total <= 0L) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

        /** e.g. `23.4 / 42.0 MB`. */
        val sizeText: String
            get() {
                val mb = 1024.0 * 1024.0
                return if (total <= 0L) String.format("%.1f MB", done / mb)
                else String.format("%.1f / %.1f MB", done / mb, total / mb)
            }
    }

    @Volatile
    private var progress: Progress? = null

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var busy = false

    fun getProgress(): Progress? = progress

    fun getLastError(): String? = lastError

    fun isBusy(): Boolean = busy

    fun clearError() {
        lastError = null
    }

    // ==================== Embedded models (build2) ====================

    /** Root of the embedded models inside the mod jar, or null in a plain build. */
    private fun bundleRoot(): Path? = try {
        FabricLoader.getInstance().getModContainer(MOD_ID)
            .flatMap { it.findPath(BUNDLE_DIR) }
            .orElse(null)
    } catch (_: Throwable) {
        null
    }

    /** Names of the models embedded in this jar; empty for the plain build. */
    fun listBundled(): List<String> {
        val root = bundleRoot() ?: return emptyList()
        return try {
            Files.list(root).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .map { it.fileName.toString().trim('/', '\\') }
                    .sorted()
                    .toList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Unpacks an embedded model to the config directory, in the background.
     * Does nothing when another install is already running.
     */
    fun installBundled(name: String) {
        startJob("释放内置模型 $name") {
            val root = bundleRoot() ?: throw IOException("此版本未内置模型")
            val source = root.resolve(name)
            if (!Files.isDirectory(source)) throw IOException("内置模型不存在: $name")
            copyTree(source, stagingDir(name), name)
            commit(name)
        }
    }

    /**
     * Unpacks the smallest embedded model on first run, so the `build2` jar
     * works out of the box without making the player wait on the 2 GB one.
     * Does nothing if any model is already installed, or if none is embedded.
     */
    fun autoInstallBundledIfNeeded() {
        if (busy) return
        if (VoiceInputManager.resolveModelDir() != null) return
        val bundled = listBundled()
        if (bundled.isEmpty()) return
        // Prefer the small model — it is the one that unpacks in seconds.
        val pick = bundled.firstOrNull { it.contains("small") } ?: bundled.first()
        installBundled(pick)
    }

    // ==================== Downloads ====================

    /**
     * Downloads and unpacks [source], in the background. [ModelSource.urls] are
     * tried in order so a mirror can be preferred over the upstream site.
     */
    fun download(source: ModelSource) {
        startJob("下载 ${source.label}") {
            val zip = Files.createTempFile("chatlimit-model-", ".zip")
            try {
                var lastFailure: Throwable? = null
                var fetched = false
                for ((index, url) in source.urls.withIndex()) {
                    try {
                        val via = if (index == 0) "镜像" else "官方源"
                        fetchTo(url, zip, source.downloadBytes, "下载中（$via）")
                        fetched = true
                        break
                    } catch (t: Throwable) {
                        lastFailure = t
                    }
                }
                if (!fetched) {
                    throw IOException("所有下载源均失败: ${lastFailure?.message ?: "未知错误"}")
                }
                unzip(zip, stagingDir(source.name), source.name)
                commit(source.name)
            } finally {
                try {
                    Files.deleteIfExists(zip)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun fetchTo(url: String, dest: Path, expected: Long, phase: String) {
        openStream(url).use { (input, length) ->
            val total = if (length > 0) length else expected
            Files.newOutputStream(dest).use { out ->
                val buffer = ByteArray(1 shl 16)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    publish(phase, done, total)
                }
            }
        }
    }

    /** A response body plus its declared length. */
    private class Body(val stream: InputStream, val length: Long) : AutoCloseable {
        operator fun component1() = stream
        operator fun component2() = length
        override fun close() = stream.close()
    }

    /**
     * Opens [url], following redirects manually — HttpURLConnection refuses to
     * follow them across protocols, and the mirror redirects to a CDN host.
     */
    private fun openStream(url: String): Body {
        var current = url
        var lastCode = -1
        for (hop in 0 until 5) {
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "chatlimit_moi")
            lastCode = conn.responseCode
            if (lastCode in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) break
                current = URI(current).resolve(location).toString()
                continue
            }
            if (lastCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw IOException("HTTP $lastCode")
            }
            return Body(conn.inputStream, conn.contentLengthLong)
        }
        throw IOException("重定向过多或响应异常 (HTTP $lastCode)")
    }

    // ==================== Unpacking ====================

    private fun unzip(zip: Path, target: Path, name: String) {
        ZipFile(zip.toFile()).use { archive ->
            val total = archive.entries().asSequence()
                .sumOf { if (it.size > 0) it.size else 0L }
            var done = 0L
            // The archives wrap everything in a top-level folder named after the
            // model; drop it so the model lands directly in `target`.
            val entries = archive.entries().toList()
            for (entry in entries) {
                val relative = stripLeadingFolder(entry.name, name)
                if (relative.isEmpty()) continue
                val dest = resolveSafely(target, relative)
                if (entry.isDirectory) {
                    Files.createDirectories(dest)
                    continue
                }
                Files.createDirectories(dest.parent)
                archive.getInputStream(entry).use { input ->
                    Files.newOutputStream(dest).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            publish("解压中", done, total)
                        }
                    }
                }
            }
        }
    }

    private fun copyTree(source: Path, target: Path, name: String) {
        val files = Files.walk(source).use { it.filter { p -> Files.isRegularFile(p) }.toList() }
        val total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        var done = 0L
        for (file in files) {
            // Paths inside a jar belong to a different FileSystem, so the
            // relative path has to be rebuilt from its string form.
            val relative = source.relativize(file).joinToString("/")
            val dest = resolveSafely(target, relative)
            Files.createDirectories(dest.parent)
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
            done += runCatching { Files.size(dest) }.getOrDefault(0L)
            publish("释放中", done, total)
        }
        if (files.isEmpty()) throw IOException("内置模型为空: $name")
    }

    /** Drops the archive's top-level wrapper folder, if it has one. */
    private fun stripLeadingFolder(entryName: String, modelName: String): String {
        val clean = entryName.replace('\\', '/').trimStart('/')
        val prefix = "$modelName/"
        return if (clean.startsWith(prefix)) clean.removePrefix(prefix) else clean
    }

    /** Joins [relative] onto [base], refusing anything that escapes it (zip slip). */
    private fun resolveSafely(base: Path, relative: String): Path {
        val resolved = base.resolve(relative).normalize()
        if (!resolved.startsWith(base)) throw IOException("压缩包条目非法: $relative")
        return resolved
    }

    // ==================== Job plumbing ====================

    private fun stagingDir(name: String): Path {
        val dir = VoiceInputManager.getModelDir().resolve("$name.partial")
        deleteTree(dir)
        Files.createDirectories(dir)
        return dir
    }

    /** Swaps the finished staging directory into place. */
    private fun commit(name: String) {
        val target = VoiceInputManager.getModelDir().resolve(name)
        val staging = VoiceInputManager.getModelDir().resolve("$name.partial")
        deleteTree(target)
        Files.move(staging, target)
    }

    private fun deleteTree(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach {
                try {
                    Files.delete(it)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun publish(phase: String, done: Long, total: Long) {
        val current = progress ?: return
        progress = current.copy(phase = phase, done = done, total = total)
    }

    private fun startJob(label: String, body: () -> Unit) {
        synchronized(this) {
            if (busy) return
            busy = true
        }
        lastError = null
        progress = Progress(label, "准备中", 0, 0)
        Thread({
            try {
                body()
                progress = null
                // Pick the new model up without waiting for a restart.
                VoiceInputManager.preloadModel()
            } catch (t: Throwable) {
                t.printStackTrace()
                lastError = "$label 失败: ${t.message ?: t.javaClass.simpleName}"
                progress = null
            } finally {
                busy = false
            }
        }, "chatlimit-model-install").apply { isDaemon = true }.start()
    }
}
