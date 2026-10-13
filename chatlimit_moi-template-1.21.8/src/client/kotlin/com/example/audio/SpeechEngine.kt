package com.example.audio

import java.util.function.Consumer

/**
 * A snapshot of recognized speech.
 *
 * [committed] is the stable, final text — it only ever grows as utterances are
 * finalized. [partial] is the tentative tail that may be revised or reverted by
 * the recognizer. [terminal] is true on the final snapshot emitted when
 * recording stops (used to terminate the sentence with punctuation).
 */
data class Recognition(
    val committed: String,
    val partial: String,
    val terminal: Boolean = false,
)

/**
 * Pluggable speech-to-text backend.
 *
 * Implementations feed on raw 16 kHz / 16-bit / mono PCM and emit recognized
 * text via the [Consumer] callback.
 */
interface SpeechEngine {

    /** Initializes the engine; returns false if unavailable (e.g. missing model/native lib). */
    fun start(onResult: Consumer<Recognition>): Boolean

    /** Feeds a chunk of raw PCM audio. */
    fun feed(bytes: ByteArray, length: Int)

    /** Flushes and releases resources. */
    fun stop()
}
