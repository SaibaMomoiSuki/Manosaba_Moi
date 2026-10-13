package com.example.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Detects and records from the system microphone using the JDK's
 * `javax.sound.sampled` API (no external dependency).
 *
 * Audio is captured as 16 kHz / 16-bit / mono PCM to match the speech engine.
 * A specific microphone (mixer) can be selected by name; `null` means system default.
 */
object MicrophoneManager {

    private val format = AudioFormat(16000f, 16, 1, true, false)
    private val lineInfo = DataLine.Info(TargetDataLine::class.java, format)

    @Volatile
    private var line: TargetDataLine? = null

    @Volatile
    private var running = false

    private var thread: Thread? = null

    @Volatile
    private var cachedMicrophones: List<String>? = null

    /** True if at least one recording device (mixer) is available. */
    fun isMicrophoneAvailable(): Boolean = getMicrophones().isNotEmpty()

    /**
     * Names of all mixers that can record at the required format.
     *
     * The result is cached: enumerating mixers asks the OS about every audio
     * device on the machine and routinely takes hundreds of milliseconds, which
     * is far too slow for the render thread to do each time chat is opened.
     * [refreshMicrophones] rebuilds it.
     */
    fun getMicrophones(): List<String> = cachedMicrophones ?: refreshMicrophones()

    /** Re-enumerates recording devices. Slow — keep this off the render thread. */
    fun refreshMicrophones(): List<String> {
        val names = mutableListOf<String>()
        try {
            for (info in AudioSystem.getMixerInfo()) {
                try {
                    if (AudioSystem.getMixer(info).isLineSupported(lineInfo)) {
                        names.add(info.name)
                    }
                } catch (_: Exception) {
                    // ignore mixers that fail to open
                }
            }
        } catch (_: Exception) {
        }
        val result = names.toList()
        cachedMicrophones = result
        return result
    }

    /**
     * Starts recording from the mixer named [mixerName] (or the system default when null).
     * Delivers PCM chunks to [onData]. Returns false if no microphone is available.
     */
    fun start(onData: (ByteArray, Int) -> Unit, mixerName: String? = null): Boolean {
        if (running) return true
        return try {
            val targetLine = openLine(mixerName) ?: return false
            targetLine.open(format)
            targetLine.start()
            line = targetLine
            running = true
            thread = Thread({
                val buffer = ByteArray(3200) // ~100 ms at 16 kHz / 16-bit
                while (running) {
                    val n = targetLine.read(buffer, 0, buffer.size)
                    if (n > 0) onData(buffer, n)
                }
            }, "chatlimit-voice-input")
            thread!!.isDaemon = true
            thread!!.start()
            true
        } catch (e: Exception) {
            running = false
            false
        }
    }

    private fun openLine(mixerName: String?): TargetDataLine? {
        return try {
            if (mixerName != null) {
                val mixer = AudioSystem.getMixerInfo()
                    .firstOrNull { it.name == mixerName }
                    ?.let { AudioSystem.getMixer(it) }
                if (mixer != null && mixer.isLineSupported(lineInfo)) {
                    mixer.getLine(lineInfo) as TargetDataLine
                } else {
                    // Selected device disappeared — fall back to the default.
                    AudioSystem.getLine(lineInfo) as TargetDataLine
                }
            } else {
                AudioSystem.getLine(lineInfo) as TargetDataLine
            }
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        running = false
        // Stop the line first so a pending blocking read() returns immediately,
        // then the recording thread exits quickly without a long join.
        try { line?.stop() } catch (_: Exception) {}
        try { thread?.join(300) } catch (_: InterruptedException) {}
        thread = null
        try { line?.close() } catch (_: Exception) {}
        line = null
    }
}
