package com.example.audio

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.function.Consumer

/**
 * Offline speech-to-text backed by the Vosk library.
 *
 * The heavy model is shared via [VoskModelCache]; only the lightweight
 * recognizer is created per session. Partial results are streamed so the
 * caller can update the input box live while the user is still speaking.
 *
 * The callback receives a [Recognition] carrying the stable [Recognition.committed]
 * text and the unstable [Recognition.partial] tail separately, so the caller can
 * append the former and replace the latter without clobbering user edits.
 *
 * Vosk's Chinese models emit inter-word spaces and no punctuation; spaces are
 * stripped here, and punctuation is left to the caller (which knows the input
 * box state).
 */
class VoskSpeechEngine(private val model: Model?) : SpeechEngine {

    private val gson = Gson()
    private var recognizer: Recognizer? = null
    private var onResult: Consumer<Recognition>? = null
    private val committed = StringBuilder()

    private var lastCommitted = ""
    private var lastPartial = ""
    private var lastTerminal = false

    override fun start(onResult: Consumer<Recognition>): Boolean {
        this.onResult = onResult
        committed.setLength(0)
        lastCommitted = ""
        lastPartial = ""
        lastTerminal = false
        return try {
            // model == null → recording-only mode (no text produced).
            recognizer = model?.let { Recognizer(it, 16000f) }
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            recognizer = null
            true // still allow recording without recognition
        }
    }

    override fun feed(bytes: ByteArray, length: Int) {
        val rec = recognizer ?: return
        try {
            val complete = rec.acceptWaveForm(bytes, length)
            if (complete) {
                val final = clean(parseField(rec.getResult(), "text"))
                if (final.isNotBlank()) committed.append(final)
            }
            val partial = if (complete) "" else clean(parseField(rec.getPartialResult(), "partial"))
            emit(committed.toString(), partial, false)
        } catch (_: Throwable) {
            // ignore transient recognition errors
        }
    }

    override fun stop() {
        val rec = recognizer
        if (rec != null) {
            try {
                rec.acceptWaveForm(ByteArray(3200), 3200) // flush with silence
                val final = clean(parseField(rec.getFinalResult(), "text"))
                if (final.isNotBlank()) committed.append(final)
                rec.close()
            } catch (_: Throwable) {
            }
        }
        recognizer = null
        emit(committed.toString(), "", true)
        onResult = null
        // The model is intentionally NOT closed — it is cached for reuse.
    }

    private fun emit(committedText: String, partialText: String, terminal: Boolean) {
        if (committedText == lastCommitted && partialText == lastPartial && terminal == lastTerminal) return
        lastCommitted = committedText
        lastPartial = partialText
        lastTerminal = terminal
        if (committedText.isBlank() && partialText.isBlank()) return
        onResult?.accept(Recognition(committedText, partialText, terminal))
    }

    /** Removes the inter-word spaces Vosk inserts between Chinese tokens, then applies the phrase map. */
    private fun clean(s: String): String = SpeechPhraseMap.apply(s.filterNot { it.isWhitespace() })

    private fun parseField(json: String, field: String): String {
        return try {
            gson.fromJson(json, JsonObject::class.java)
                .get(field)?.takeIf { !it.isJsonNull }?.asString ?: ""
        } catch (_: Throwable) {
            ""
        }
    }
}
