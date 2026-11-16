package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ShortScene(
    val index: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 4000L,
    val caption: String = "",
    val emphasisWord: String = "",
    val visualCue: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "index" to index,
        "startMs" to startMs,
        "endMs" to endMs,
        "caption" to caption,
        "emphasisWord" to emphasisWord,
        "visualCue" to visualCue
    )
}

@IgnoreExtraProperties
data class ShortProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val topic: String = "",
    val style: String = "Viral Hook",
    val durationSec: Long = 20L,
    val voiceProfile: String = "Studio Pro",
    val captionStyle: String = "Neon Pop",
    val backdropTheme: String = "Cyber Aurora",
    val voiceoverScript: String = "",
    val scenes: List<ShortScene> = emptyList(),
    val securityVerified: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title,
        "topic" to topic,
        "style" to style,
        "durationSec" to durationSec,
        "voiceProfile" to voiceProfile,
        "captionStyle" to captionStyle,
        "backdropTheme" to backdropTheme,
        "voiceoverScript" to voiceoverScript,
        "scenes" to scenes.map { it.toMap() },
        "securityVerified" to securityVerified,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title,
        "topic" to topic,
        "style" to style,
        "durationSec" to durationSec,
        "voiceProfile" to voiceProfile,
        "captionStyle" to captionStyle,
        "backdropTheme" to backdropTheme,
        "voiceoverScript" to voiceoverScript,
        "scenes" to scenes.map { it.toMap() },
        "securityVerified" to securityVerified,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

object ShortPresets {
    val styles = listOf("Viral Hook", "Explainer", "Storytime", "Product Spotlight", "Motivation")
    val voiceProfiles = listOf("Studio Pro", "Energetic", "Calm Narrator", "Hype Host")
    val captionStyles = listOf("Neon Pop", "Cinema Box", "Bold Minimal", "Karaoke")
    val backdropThemes = listOf("Cyber Aurora", "Warm Liquid", "Obsidian Grid")
    val durations = listOf(15, 20, 30)
}
