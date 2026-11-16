package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ShortScene
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Generates bespoke visual keyframes for short-form video scenes using Firebase AI Logic
 * (`gemini-nano-banana-2.1`). Requires a Firebase Blaze plan when enabled.
 */
class AiSceneVisualGenerator {

    private val imageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateSceneKeyframes(
        topic: String,
        style: String,
        scenes: List<ShortScene>,
        maxKeyframes: Int = 3,
        onFrameProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Bitmap> {
        if (scenes.isEmpty()) return emptyList()
        val selectedScenes = selectRepresentativeScenes(scenes, maxKeyframes.coerceIn(1, 3))
        val total = selectedScenes.size
        val generated = mutableListOf<Bitmap>()

        selectedScenes.forEachIndexed { idx, scene ->
            onFrameProgress(idx, total)
            val prompt = buildVisualPrompt(topic, style, scene, idx + 1, total)
            val bitmap = generateSingleFrame(prompt)
                ?: throw IllegalStateException(
                    "AI visual model returned no image for scene ${idx + 1}."
                )
            generated.add(bitmap)
            onFrameProgress(idx + 1, total)
        }
        return generated
    }

    private suspend fun generateSingleFrame(prompt: String): Bitmap? {
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(content { text(prompt) })
            }
            response.candidates.firstOrNull()
                ?.content
                ?.parts
                ?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
                ?.image
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI scene visual generation timed out", e)
            throw IllegalStateException("AI scene visual generation timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI scene visual generation failed", e)
            throw e
        }
    }

    private fun selectRepresentativeScenes(scenes: List<ShortScene>, count: Int): List<ShortScene> {
        if (scenes.size <= count) return scenes
        return when (count) {
            1 -> listOf(scenes.first())
            2 -> listOf(scenes.first(), scenes.last())
            else -> listOf(scenes.first(), scenes[scenes.size / 2], scenes.last())
        }
    }

    private fun buildVisualPrompt(
        topic: String,
        style: String,
        scene: ShortScene,
        beatIndex: Int,
        totalBeats: Int
    ): String {
        val cue = scene.visualCue.ifBlank { scene.caption }
        return "Vertical 9:16 cinematic short-form video still frame ($beatIndex of $totalBeats). " +
            "Topic: $topic. Style: $style. Visual direction: $cue. " +
            "High contrast, vivid studio lighting, clean composition with clear space in the lower third for captions, no embedded text or watermarks."
    }

    private companion object {
        const val TAG = "AiSceneVisualGenerator"
    }
}
