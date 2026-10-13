package com.example.audio

/**
 * Repairs recognized speech for game-specific terms, character names and
 * honorific suffixes.
 *
 * Vosk's Mandarin models only ever emit words from their own lexicon, and most
 * of the names used here (樱羽, 黑部, 蕾雅, 奈叶香 …) are simply not in it. What
 * comes back is a homophone: 樱羽 arrives as 英语, 艾玛桑 as 爱玛三. Literal
 * string replacement can never fix that, so every phrase below is matched
 * *phonetically* — the recognized text is converted to pinyin via [Pinyin] and
 * compared syllable by syllable, since one Hanzi is always one syllable.
 *
 * How aggressively a phrase matches depends on what it is:
 *
 *  - [Kind.NAME] — matched ignoring tone. Names are distinctive enough that
 *    a tone-blind match is safe, and it is what lets 雷亚 become 蕾雅.
 *  - [Kind.FEATURE] — three syllables or more are matched ignoring tone; one or
 *    two syllables additionally require the tones to line up, so ordinary words
 *    survive untouched (至于 zhi4-yu2 is not turned into 治愈 zhi4-yu4, 呼唤
 *    hu1-huan4 not into 互换 hu4-huan4, 环视 huan2-shi4 not into 幻视 huan4-shi4).
 *  - [Kind.HONORIFIC] — 桑/君/酱 sound like the very common 三/军/将, so they are
 *    only applied directly after a name has just been matched. 艾玛三 becomes
 *    艾玛桑, while 我有三个 is left alone.
 *
 * A few entries rewrite to a different string than they match (瓦达西 → watashi,
 * 爹丝袜 → desuwa, 哎哎哦 → AAO); the rest are canonical spellings that the
 * phonetic match exists to restore.
 */
object SpeechPhraseMap {

    private enum class Kind { FEATURE, NAME, HONORIFIC }

    private class Phrase(
        /** Canonical spelling, and the pattern whose sound is matched against. */
        val text: String,
        /** What gets written out; differs from [text] only for the romanizations. */
        val output: String,
        val kind: Kind,
        /** Whether tones must line up as well as the syllables. */
        val strict: Boolean,
        /** Only match at the very end of an utterance. */
        val tailOnly: Boolean = false,
    ) {
        val readings: List<Set<Pinyin.Syllable>> = text.map { Pinyin.readings(it) }
    }

    private fun feature(
        text: String,
        output: String = text,
        strict: Boolean? = null,
        tailOnly: Boolean = false,
    ) = Phrase(text, output, Kind.FEATURE, strict ?: (text.length <= 2), tailOnly)

    private fun name(text: String, strict: Boolean = false) =
        Phrase(text, text, Kind.NAME, strict)

    private fun honorific(text: String) = Phrase(text, text, Kind.HONORIFIC, false)

    private val phrases: List<Phrase> = listOf(
        // ===== 特征词 =====
        feature("本小姐"),
        feature("本侦探"),
        feature("哎哎哦", "AAO"),
        // What the model actually returns for AAO when the vowel comes out as
        // ei rather than ai — too far from 哎哎哦 for the phonetic match to bridge.
        feature("诶诶噢", "AAO"),
        feature("啊啦"),
        feature("何意味"),
        feature("瓦达西", "watashi"),
        feature("吾辈"),
        feature("视线诱导"),
        feature("感知"),
        feature("液体操控"),
        feature("液体操纵"),
        feature("互换"),
        feature("飘浮"),
        feature("怪力"),
        feature("死亡回溯"),
        feature("介入过去"),
        feature("窃密"),
        feature("穿墙"),
        feature("魔女杀手"),
        feature("治愈"),
        feature("幻视"),
        feature("千里眼"),
        feature("洗脑"),
        feature("重力操控"),
        feature("模仿"),
        feature("点火"),
        feature("爹丝袜", "desuwa"),
        // ですわ is a sentence-final tic, and 对死亡 (dui-si-wang) is what the
        // model returns for it. It is also a real Chinese phrase, but only ever
        // mid-sentence (对死亡的恐惧), so matching just at the end of an utterance
        // separates the two: 好可怕对死亡 -> 好可怕desuwa, 我对死亡的看法 untouched.
        feature("对死亡", "desuwa", tailOnly = true),
        feature("大魔女"),
        feature("幕后黑手"),
        feature("魔女岛"),

        // ===== 人名（全名） =====
        name("樱羽艾玛"),
        name("二阶堂希罗"),
        name("橘雪莉"),
        name("远野汉娜"),
        name("黑部奈叶香"),
        name("宝生玛格"),
        name("泽渡可可"),
        name("夏目安安"),
        name("城崎诺亚"),
        name("莲见蕾雅"),
        name("紫藤亚里沙"),
        name("冰上梅露露"),

        // ===== 人名（名） =====
        name("艾玛"),
        name("希罗"),
        name("月代雪"),
        // 小雪 sounds exactly like 小学 apart from the tone, so it is the one
        // name that has to check tones.
        name("小雪", strict = true),
        name("雪莉"),
        name("汉娜"),
        name("奈叶香"),
        name("玛格"),
        name("可可"),
        name("安安"),
        name("诺亚"),
        name("蕾雅"),
        name("亚里沙"),
        name("梅露露"),

        // ===== 人名（姓） =====
        name("樱羽"),
        name("二阶堂"),
        name("远野"),
        name("黑部"),
        name("宝生"),
        name("泽渡"),
        name("夏目"),
        name("城崎"),
        name("莲见"),
        name("紫藤"),
        name("冰上"),
        name("月代"),
        name("典狱长"),
        name("看守"),

        // ===== 人名后缀 =====
        honorific("君"),
        honorific("酱"),
        honorific("桑"),
        honorific("亲"),
    )

    /** Longest first, so compound names are matched before their parts. */
    private val ordered: List<Phrase> = phrases.sortedByDescending { it.text.length }

    /**
     * Builds the pinyin tables now instead of during the first recognition.
     * pinyin4j loads its character database lazily on first use, which is slow
     * enough to be felt if it happens while the player is already speaking.
     */
    fun warmUp() {
        apply("樱羽艾玛桑")
    }

    /**
     * Rewrites [text], replacing anything that *sounds like* a known phrase with
     * its canonical spelling. Scans left to right and always takes the longest
     * match, so 樱羽艾玛 is never split into 樱羽 + 艾玛.
     */
    fun apply(text: String): String {
        if (text.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        var afterName = false
        while (i < text.length) {
            val match = matchAt(text, i, afterName)
            if (match != null) {
                out.append(match.output)
                i += match.text.length
                afterName = match.kind == Kind.NAME
            } else {
                out.append(text[i])
                i++
                afterName = false
            }
        }
        return out.toString()
    }

    private fun matchAt(text: String, start: Int, afterName: Boolean): Phrase? {
        for (p in ordered) {
            if (p.kind == Kind.HONORIFIC && !afterName) continue
            val end = start + p.text.length
            if (end > text.length) continue
            if (p.tailOnly && !isUtteranceEnd(text, end)) continue
            if (matches(p, text, start)) return p
        }
        return null
    }

    /** True when nothing but punctuation follows [from]. */
    private fun isUtteranceEnd(text: String, from: Int): Boolean {
        for (i in from until text.length) {
            if (text[i] !in TRAILING) return false
        }
        return true
    }

    private const val TRAILING = "，。！？；、：,.!?;: \t"

    private fun matches(p: Phrase, text: String, start: Int): Boolean {
        for (k in p.text.indices) {
            val actual = text[start + k]
            if (actual == p.text[k]) continue // already correct
            val expected = p.readings[k]
            if (expected.isEmpty()) return false
            val heard = Pinyin.readings(actual)
            if (heard.isEmpty()) return false
            if (!soundsAlike(heard, expected, p.strict)) return false
        }
        return true
    }

    /** True when any reading of the recognized character matches any expected reading. */
    private fun soundsAlike(
        heard: Set<Pinyin.Syllable>,
        expected: Set<Pinyin.Syllable>,
        strict: Boolean,
    ): Boolean {
        for (h in heard) {
            for (e in expected) {
                if (Pinyin.sounds(h, e, strict)) return true
            }
        }
        return false
    }
}
