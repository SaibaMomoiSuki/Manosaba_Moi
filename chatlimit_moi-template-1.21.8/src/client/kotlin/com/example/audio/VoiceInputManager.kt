package com.example.audio

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.vosk.Model
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Unified voice-input entry point used by the chat screen mixin and settings screens.
 *
 * Exposes a Java-friendly API: [isMicrophoneAvailable], [isRecording], [start],
 * [stop], plus microphone selection ([getMicrophones], [getSelectedMicrophone],
 * [setSelectedMicrophone]).
 *
 * The Vosk Java binding bundles its native library and auto-loads it via JNA, so
 * the only thing the user must provide is the speech model directory (see
 * [getModelDir]). The model is loaded asynchronously and never blocks the render
 * thread; when it is still loading the first time the user clicks the mic, we
 * show a brief notice and start recording automatically once it is ready. When
 * the model is missing, recording still works but no text is produced.
 */
object VoiceInputManager {

    /** Download URLs offered in the "no model" chat message. */
    const val SMALL_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    const val LARGE_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip"

    /** Model id used when a model is extracted straight into the model directory. */
    const val ROOT_MODEL_ID = "."

    /** A speech model found on disk: [id] is what gets persisted, [label] what the player sees. */
    data class SpeechModel(val id: String, val label: String)

    /** Where the user places the extracted Vosk model (e.g. `vosk-model-small-cn-0.22`). */
    private val modelDir: Path =
        FabricLoader.getInstance().configDir.resolve("chatlimit_moi/vosk-model")

    @Volatile
    private var recording = false

    /** True while the background thread is opening the recognizer and audio line. */
    @Volatile
    private var starting = false

    private var engine: SpeechEngine? = null

    /** Marshalled callback held while the model is still loading. */
    private var pendingCallback: Consumer<Recognition>? = null

    /** Incremented each session so late callbacks from a stale engine are ignored. */
    @Volatile
    private var generation = 0

    init {
        // Vosk returns UTF-8; JNA defaults to the platform charset (GBK on
        // Chinese Windows), which would garble recognized Chinese text.
        System.setProperty("jna.encoding", "UTF-8")
    }

    // ==================== Microphone availability & selection ====================

    fun isMicrophoneAvailable(): Boolean = MicrophoneManager.isMicrophoneAvailable()

    /** Names of all recording devices available to the system. */
    fun getMicrophones(): List<String> = MicrophoneManager.getMicrophones()

    /** The microphone chosen in settings, or null for the system default. */
    fun getSelectedMicrophone(): String? = MicrophoneSettings.getSelected()

    /** Persists the chosen microphone (null = system default). */
    fun setSelectedMicrophone(name: String?) = MicrophoneSettings.setSelected(name)

    /** Absolute path of the directory where the Vosk model must be extracted. */
    fun getModelDir(): Path = modelDir

    /**
     * Creates the `chatlimit_moi` and `vosk-model` folders on first run so the
     * player has an obvious place to drop an extracted model into.
     */
    fun ensureDirectories() {
        try {
            Files.createDirectories(modelDir.parent) // config/chatlimit_moi
            Files.createDirectories(modelDir)        // config/chatlimit_moi/vosk-model
        } catch (_: Throwable) {
            // Best-effort.
        }
    }

    /** The speech models currently present in the model directory. */
    fun listModels(): List<SpeechModel> {
        val found = mutableListOf<SpeechModel>()
        try {
            if (!Files.isDirectory(modelDir)) return found
            // Model extracted directly into the model directory (am/graph at top).
            if (looksLikeModel(modelDir)) {
                found.add(SpeechModel(ROOT_MODEL_ID, modelDir.fileName.toString() + "（根目录）"))
            }
            Files.list(modelDir).use { stream ->
                stream.filter { looksLikeModel(it) }
                    .sorted()
                    .forEach {
                        val name = it.fileName.toString()
                        found.add(SpeechModel(name, name))
                    }
            }
        } catch (_: Throwable) {
        }
        return found
    }

    /** The speech model chosen in settings, or null when it is picked automatically. */
    fun getSelectedModel(): String? = MicrophoneSettings.getSelectedModel()

    /**
     * Persists the chosen speech model (null = pick automatically) and starts
     * loading it. Recording is stopped first: the previously loaded model is
     * freed as part of the switch, which is only safe once no recognizer holds it.
     */
    fun setSelectedModel(id: String?) {
        if (id == MicrophoneSettings.getSelectedModel()) return
        stop()
        MicrophoneSettings.setSelectedModel(id)
        preloadModel()
    }

    /** Opens the model directory in the OS file manager (creates it first if needed). */
    fun openModelFolder() {
        val dir = modelDir.toFile()
        try {
            if (!dir.exists()) dir.mkdirs()
            Util.getPlatform().openFile(dir)
        } catch (_: Throwable) {
            // Best-effort — nothing sensible to do if the OS refuses to open it.
        }
    }

    /** Whether [url] is one of the model download links (opened without confirmation). */
    fun isModelDownloadUrl(url: String): Boolean = url == SMALL_MODEL_URL || url == LARGE_MODEL_URL

    /** True when a Vosk model appears to be present (heuristic, no load). */
    fun isModelAvailable(): Boolean = resolveModelDir() != null

    /** Loads the speech model on a background thread so the first mic click is instant. */
    fun preloadModel() {
        val dir = resolveModelDir() ?: return
        VoskModelCache.preload(dir.toString())
    }

    fun getModelStatusText(): String? = VoskModelCache.getStatusText()

    /**
     * Finds the directory that actually holds the model to use.
     *
     * Prefers the model chosen in the settings screen; when nothing is chosen —
     * or the chosen one has since been deleted — falls back to the first model
     * found. Accepts both a model extracted directly into [modelDir]
     * (am/conf/... at the top) and one inside a wrapper folder
     * (e.g. `vosk-model/vosk-model-cn-0.22/am/...`). Returns null when there is
     * no model at all.
     */
    fun resolveModelDir(): Path? {
        val chosen = MicrophoneSettings.getSelectedModel()
        if (chosen != null) {
            val dir = resolveModelId(chosen)
            if (dir != null) return dir
            // Chosen model is gone — fall through and auto-pick one instead.
        }
        return firstModelDir()
    }

    /** Maps a persisted model id to its directory, or null if it is not a usable model. */
    private fun resolveModelId(id: String): Path? {
        if (id == ROOT_MODEL_ID) return modelDir.takeIf { looksLikeModel(it) }
        // The id comes from a config file the player can edit, so keep it to a
        // plain folder name — no separators, no walking out of the model dir.
        if (id.isBlank() || id.contains('/') || id.contains('\\') || id == "..") return null
        return modelDir.resolve(id).takeIf { looksLikeModel(it) }
    }

    private fun firstModelDir(): Path? {
        if (looksLikeModel(modelDir)) return modelDir
        val smallModel = modelDir.resolve(ModelSources.SMALL.name)
        if (looksLikeModel(smallModel)) return smallModel
        return try {
            Files.list(modelDir).use { stream ->
                stream.filter { looksLikeModel(it) }
                    .sorted()
                    .findFirst()
                    .orElse(null)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Whether [dir] looks like an extracted Vosk model (has an `am` or `graph`
     * subfolder). Half-written installs are staged as `<name>.partial` and are
     * excluded, so a model being unpacked never gets picked up mid-flight.
     */
    private fun looksLikeModel(dir: Path): Boolean = try {
        !dir.fileName.toString().endsWith(PARTIAL_SUFFIX) &&
            Files.isDirectory(dir) &&
            (Files.isDirectory(dir.resolve("am")) || Files.isDirectory(dir.resolve("graph")))
    } catch (_: Throwable) {
        false
    }

    /** Suffix used by [ModelInstaller] for staging directories. */
    const val PARTIAL_SUFFIX = ".partial"

    /** True when a model with this folder name is installed and usable. */
    fun isModelInstalled(name: String): Boolean = looksLikeModel(modelDir.resolve(name))

    /** True while recording, or while a requested recording is still starting up. */
    fun isRecording(): Boolean = recording || starting || pendingCallback != null

    /**
     * Does the expensive one-time setup — enumerating audio devices, building
     * the pinyin tables and loading the speech model — on a background thread at
     * client start, so that opening chat and clicking the microphone later stay
     * responsive.
     */
    fun warmUp() {
        // Do not load Vosk during game startup. Its native decoder can reserve
        // hundreds of MB and some Windows audio/driver combinations crash the
        // JVM while the render window is still being created. The model is
        // loaded lazily when the chat microphone is first used.
        Thread({
            try {
                MicrophoneManager.refreshMicrophones()
            } catch (_: Throwable) {
            }
            try {
                SpeechPhraseMap.warmUp()
            } catch (_: Throwable) {
            }
            try {
                // build2 embeds the models; unpack one on first run so voice
                // input works without the player downloading anything.
                ModelInstaller.autoInstallBundledIfNeeded()
            } catch (_: Throwable) {
            }
        }, "chatlimit-voice-warmup").apply { isDaemon = true }.start()
    }

    // ==================== Recording lifecycle ====================

    /**
     * Starts voice input. Returns false if no microphone is available.
     * Recognized text is delivered to [onResult] on the render thread.
     */
    fun start(onResult: Consumer<Recognition>): Boolean {
        if (isRecording()) return true
        if (!MicrophoneManager.isMicrophoneAvailable()) return false

        generation++
        val gen = generation

        // Marshal recognized text back to the render thread so it can touch the chat input,
        // and drop callbacks that arrive after a newer session has started.
        val marshalled = Consumer<Recognition> { r ->
            if (gen == generation) {
                Minecraft.getInstance().execute {
                    if (gen == generation) onResult.accept(r)
                }
            }
        }

        val dir = resolveModelDir()
        val path = dir?.toString()
        val model = path?.let { VoskModelCache.getIfReady(it) }

        if (path != null && model == null) {
            // Model present but not loaded yet — load it in the background and
            // begin recording automatically once it is ready, without ever
            // blocking the render thread.
            pendingCallback = marshalled
            notifyModelLoading()
            VoskModelCache.preload(path) { loaded ->
                Minecraft.getInstance().execute {
                    val cb = pendingCallback
                    if (cb != null && gen == generation) {
                        pendingCallback = null
                        val readyModel = VoskModelCache.getIfReady(path)
                        if (loaded && readyModel != null) {
                            beginRecording(readyModel, cb, gen)
                        } else {
                            notifyModelLoadFailed()
                        }
                    }
                }
            }
            return true
        }

        beginRecording(model, marshalled, gen)
        if (dir == null) notifyModelMissing()
        return true
    }

    /**
     * Opens the recognizer and the audio line on a background thread.
     *
     * Both steps are slow — building a recognizer allocates decoder state from
     * the (up to gigabyte-sized) model, and opening a capture line initializes
     * the device — so doing them inline visibly stutters the game the first few
     * times. [starting] keeps the microphone button lit meanwhile, and [gen]
     * cancels the whole thing if the player toggles recording back off before
     * it finishes.
     */
    private fun beginRecording(model: Model?, onResult: Consumer<Recognition>, gen: Int) {
        starting = true
        Thread({
            var pinned = false
            var speech: SpeechEngine? = null
            var ok = false
            try {
                if (gen != generation) return@Thread
                val s = VoskSpeechEngine(model)
                speech = s
                // Pin the cached model for as long as a recognizer references
                // it, so a model switch cannot free it out from under us.
                VoskModelCache.markInUse(true)
                pinned = true
                s.start(onResult)
                val started = MicrophoneManager.start({ bytes, len -> s.feed(bytes, len) },
                    MicrophoneSettings.getSelected())
                if (!started) return@Thread
                if (gen != generation) {
                    // Toggled off while we were starting up — undo it.
                    MicrophoneManager.stop()
                    return@Thread
                }
                engine = s
                recording = true
                ok = true
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                if (!ok) {
                    speech?.stop()
                    if (pinned) VoskModelCache.markInUse(false)
                }
                starting = false
            }
        }, "chatlimit-voice-start").apply { isDaemon = true }.start()
    }

    fun stop() {
        pendingCallback = null
        // Invalidate any start still in flight so it tears itself down instead
        // of leaving the microphone open.
        generation++
        if (!recording) return
        recording = false
        MicrophoneManager.stop()
        val e = engine
        engine = null
        if (e != null) {
            // Recognizer close frees native resources and can be slow — run it
            // off the render thread. Final text is still marshalled to the UI.
            Thread({
                try {
                    e.stop()
                } finally {
                    // Only now is the cached model safe to replace.
                    VoskModelCache.markInUse(false)
                }
            }, "chatlimit-vosk-stop").apply { isDaemon = true }.start()
        }
    }

    private fun notifyModelLoading() {
        val player = Minecraft.getInstance().player ?: return
        player.displayClientMessage(
            Component.literal("[语音输入] 正在加载语音模型，请稍候…").withStyle(ChatFormatting.YELLOW),
            true
        )
    }

    private fun notifyModelMissing() {
        val player = Minecraft.getInstance().player ?: return
        // Action-bar prompt (non-intrusive).
        player.displayClientMessage(
            Component.literal("[语音输入] 未找到语音模型，仅录音。").withStyle(ChatFormatting.GOLD),
            true
        )
        // Chat lines with one-click download links (opened without confirmation).
        player.displayClientMessage(
            Component.literal("如果不知道哪里可以下载模型在这里跳转：").withStyle(ChatFormatting.YELLOW),
            false
        )
        player.displayClientMessage(
            Component.literal("轻量模型").withStyle(ChatFormatting.WHITE)
                .append(linkButton("42MB 语音识别可能较粗略", SMALL_MODEL_URL)),
            false
        )
        player.displayClientMessage(
            Component.literal("高精度模型").withStyle(ChatFormatting.WHITE)
                .append(linkButton("1.2GB 服务器所用语音识别精细", LARGE_MODEL_URL)),
            false
        )
    }

    private fun notifyModelLoadFailed() {
        val player = Minecraft.getInstance().player ?: return
        player.displayClientMessage(
            Component.literal("[语音输入] 语音模型加载失败，请检查模型文件或重新选择模型。")
                .withStyle(ChatFormatting.RED),
            true
        )
    }

    /** A bracketed, underlined, clickable label that opens [url] in the system browser. */
    private fun linkButton(label: String, url: String): MutableComponent {
        val style = Style.EMPTY
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
        return Component.literal("[$label]").withStyle(style)
    }
}
