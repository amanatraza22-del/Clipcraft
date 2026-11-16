package com.example.security

import com.example.data.model.ShortScene

object InputSanitizer {
    const val MAX_TOPIC_CHARS = 500
    const val MAX_TITLE_CHARS = 100
    const val MAX_SCRIPT_CHARS = 1200
    const val MAX_CAPTION_CHARS = 140

    private val injectionPatterns = listOf(
        Regex("(?i)ignore\\s+(all\\s+)?previous\\s+instructions?"),
        Regex("(?i)system\\s+prompt"),
        Regex("(?i)reveal\\s+(your|the)\\s+(api|key|secret|instructions)"),
        Regex("(?i)act\\s+as\\s+root"),
        Regex("<\\s*/?\\s*system\\s*>", RegexOption.IGNORE_CASE),
        Regex("```[a-zA-Z]*")
    )

    data class SanitizationResult(
        val cleanText: String,
        val threatsNeutralized: Int
    )

    fun sanitizeTopic(raw: String): SanitizationResult {
        var text = stripControlChars(raw).trim()
        var threats = 0
        for (pattern in injectionPatterns) {
            if (pattern.containsMatchIn(text)) {
                threats++
                text = pattern.replace(text, " ")
            }
        }
        val collapsed = text.replace(Regex("\\s+"), " ").trim().take(MAX_TOPIC_CHARS)
        return SanitizationResult(cleanText = collapsed, threatsNeutralized = threats)
    }

    fun sanitizeTitle(raw: String): String {
        val cleaned = stripControlChars(raw).replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { "Untitled 20s Short" }.take(MAX_TITLE_CHARS)
    }

    fun sanitizeScript(raw: String): String {
        val cleaned = stripControlChars(raw).replace(Regex("[ \\t]+"), " ").trim()
        return cleaned.take(MAX_SCRIPT_CHARS)
    }

    fun clampScenes(scenes: List<ShortScene>, totalDurationSec: Int): List<ShortScene> {
        val totalMs = (totalDurationSec.coerceIn(10, 30) * 1000L)
        if (scenes.isEmpty()) return emptyList()
        val bounded = scenes.take(8)
        return bounded.mapIndexed { idx, scene ->
            val safeStart = scene.startMs.coerceIn(0L, totalMs - 500L)
            val defaultEnd = ((idx + 1) * totalMs / bounded.size).coerceAtLeast(safeStart + 500L)
            val safeEnd = scene.endMs.coerceIn(safeStart + 400L, totalMs)
            ShortScene(
                index = idx.toLong(),
                startMs = safeStart,
                endMs = if (safeEnd > safeStart) safeEnd else defaultEnd.coerceAtMost(totalMs),
                caption = stripControlChars(scene.caption).trim().ifBlank { "Scene ${idx + 1}" }.take(MAX_CAPTION_CHARS),
                emphasisWord = stripControlChars(scene.emphasisWord).trim().take(40),
                visualCue = stripControlChars(scene.visualCue).trim().take(140)
            )
        }
    }

    private fun stripControlChars(input: String): String {
        return input.filter { ch ->
            ch == '\n' || ch == '\t' || (ch.code >= 32 && ch.code != 127)
        }
    }
}
