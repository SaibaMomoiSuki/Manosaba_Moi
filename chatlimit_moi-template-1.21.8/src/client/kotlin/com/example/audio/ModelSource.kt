package com.example.audio

/**
 * A Vosk speech model the mod knows how to fetch.
 *
 * [urls] are tried in order, so a China-reachable mirror can be listed ahead of
 * the upstream site and the upstream used only as a fallback.
 */
data class ModelSource(
    /** Folder name once extracted, e.g. `vosk-model-small-cn-0.22`. */
    val name: String,
    /** Shown in the settings screen. */
    val label: String,
    /** Compressed download size, for the progress display. */
    val downloadBytes: Long,
    val urls: List<String>,
) {
    /** e.g. `42 MB`. */
    val sizeText: String
        get() = when {
            downloadBytes >= 1024L * 1024 * 1024 ->
                String.format("%.2f GB", downloadBytes / 1024.0 / 1024.0 / 1024.0)
            else -> "${downloadBytes / 1024 / 1024} MB"
        }
}

object ModelSources {

    /**
     * hf-mirror.com is the China-facing HuggingFace mirror; rhasspy/vosk-models
     * republishes the upstream Vosk archives there byte-for-byte. It only
     * carries the small Chinese model — the 1.36 GB one is upstream-only.
     */
    private const val MIRROR = "https://hf-mirror.com/rhasspy/vosk-models/resolve/main/zh"
    private const val OFFICIAL = "https://alphacephei.com/vosk/models"

    val SMALL = ModelSource(
        name = "vosk-model-small-cn-0.22",
        label = "轻量模型 · 识别较粗略",
        downloadBytes = 43_898_754L,
        urls = listOf(
            "$MIRROR/vosk-model-small-cn-0.22.zip",
            "$OFFICIAL/vosk-model-small-cn-0.22.zip",
        ),
    )

    val LARGE = ModelSource(
        name = "vosk-model-cn-0.22",
        label = "高精度模型 · 识别精细",
        downloadBytes = 1_358_736_686L,
        urls = listOf("$OFFICIAL/vosk-model-cn-0.22.zip"),
    )

    val ALL = listOf(SMALL, LARGE)
}
