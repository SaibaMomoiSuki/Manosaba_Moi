package com.example.audio

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.util.concurrent.ConcurrentHashMap

/**
 * Hanzi → pinyin lookup, used to recognize the homophone mistakes the speech
 * model makes (see [SpeechPhraseMap]).
 *
 * A character may have several readings (多音字); all of them are returned and
 * a match on any one counts, which is why no attempt is made to disambiguate
 * by context.
 *
 * Readings are cached because [SpeechPhraseMap] runs on every partial result
 * (~10×/s while the player is speaking). The cache is concurrent: recognition
 * results are cleaned on the audio thread, and the final flush on a separate
 * shutdown thread.
 */
object Pinyin {

    /** One reading of a character. */
    data class Syllable(
        /** Toneless pinyin, e.g. `shang`. */
        val base: String,
        /** Tone 1-4, or 0 for the neutral tone / unknown. */
        val tone: Int,
        /** [base] with the commonly-confused sounds collapsed, e.g. `san`. */
        val fuzzy: String,
    )

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    private val cache = ConcurrentHashMap<Char, Set<Syllable>>()

    /** All readings of [c]; empty for non-Chinese characters. */
    fun readings(c: Char): Set<Syllable> = cache.computeIfAbsent(c) { compute(it) }

    /** True when [a] and [b] could be the same spoken syllable. */
    fun sounds(a: Syllable, b: Syllable, matchTone: Boolean): Boolean {
        if (matchTone && a.tone != b.tone && a.tone != 0 && b.tone != 0) return false
        return a.fuzzy == b.fuzzy
    }

    private fun compute(c: Char): Set<Syllable> {
        // pinyin4j returns null for anything that is not a Chinese character and
        // throws on odd format combinations; neither is worth propagating.
        val raw = try {
            PinyinHelper.toHanyuPinyinStringArray(c, format)
        } catch (_: Throwable) {
            null
        } ?: return emptySet()
        return raw.mapNotNullTo(LinkedHashSet()) { parse(it) }
    }

    private fun parse(reading: String): Syllable? {
        if (reading.isEmpty()) return null
        val last = reading.last()
        val hasTone = last.isDigit()
        val base = if (hasTone) reading.dropLast(1) else reading
        if (base.isEmpty()) return null
        return Syllable(base, if (hasTone) last - '0' else 0, fuzzify(base))
    }

    /**
     * Collapses the sound pairs speech models routinely confuse: the retroflex
     * initials zh/ch/sh against z/c/s, and the nasal finals -ang/-eng/-ing
     * against -an/-en/-in. So 上 (shang), 桑 (sang) and 三 (san) all reduce to
     * `san` and are treated as the same sound.
     *
     * n-/l- is deliberately NOT collapsed: Vosk's Mandarin models rarely confuse
     * the two, and doing so would make short names match far too much ordinary text.
     */
    private fun fuzzify(base: String): String {
        var s = base
        if (s.startsWith("zh") || s.startsWith("ch") || s.startsWith("sh")) {
            s = s.removeRange(1, 2)
        }
        // -ong is left alone: there is no -on final to confuse it with.
        if (s.endsWith("ang") || s.endsWith("eng") || s.endsWith("ing")) {
            s = s.dropLast(1)
        }
        return s
    }
}
