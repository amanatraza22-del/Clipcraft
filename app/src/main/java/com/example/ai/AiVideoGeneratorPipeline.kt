package com.example.ai

import android.graphics.Bitmap
import com.example.data.model.ShortProject
import com.example.media.ExportedVideoResult
import com.example.media.ShortVideoExporter
import com.example.media.VoiceoverEngine
import com.example.media.VoiceoverResult
import com.example.security.InputSanitizer
import java.io.File

sealed interface VideoPipelineStage {
    data class PlanningStoryboard(val message: String) : VideoPipelineStage
    data class GeneratingVisuals(val completed: Int, val total: Int) : VideoPipelineStage
    data class SynthesizingVoiceover(val message: String) : VideoPipelineStage
    data class RenderingVideo(val progress: Float) : VideoPipelineStage
}

data class VideoClipBuildRequest(
    val userId: String,
    val existingProject: ShortProject,
    val promptTopic: String,
    val style: String,
    val durationSec: Int,
    val voiceProfile: String,
    val attachedPhotos: List<Bitmap>,
    val generateAiVisuals: Boolean
)

data class VideoClipBuildResult(
    val project: ShortProject,
    val sceneBitmaps: List<Bitmap>,
    val voiceoverFile: File?,
    val voiceoverStatus: String,
    val exportedVideo: ExportedVideoResult,
    val threatsNeutralized: Int
)

/**
 * End-to-end 20-second AI video clip pipeline:
 * 1. Gemini structured storyboard & timed caption beats (`AiShortGenerator`)
 * 2. Optional AI visual scene keyframes (`AiSceneVisualGenerator` via `gemini-nano-banana-2.1`)
 * 3. Spoken voiceover synthesis (`VoiceoverEngine`)
 * 4. 720x1280 24fps H.264/AAC MP4 video rendering (`ShortVideoExporter`)
 */
class AiVideoGeneratorPipeline(
    private val shortGenerator: AiShortGenerator,
    private val visualGenerator: AiSceneVisualGenerator,
    private val voiceoverEngine: VoiceoverEngine,
    private val videoExporter: ShortVideoExporter
) {

    suspend fun generateAndExportClip(
        request: VideoClipBuildRequest,
        onStage: (VideoPipelineStage) -> Unit,
        onStoryboardReady: (project: ShortProject, threatsNeutralized: Int) -> Unit = { _, _ -> }
    ): VideoClipBuildResult {
        val safeDuration = request.durationSec.coerceIn(10, 30)
        onStage(
            VideoPipelineStage.PlanningStoryboard(
                "Writing ${safeDuration}s script & timed beats with Gemini AI..."
            )
        )

        val plan = shortGenerator.generateShortPlan(
            rawTopic = request.promptTopic,
            style = request.style,
            durationSec = safeDuration,
            voiceProfile = request.voiceProfile,
            visualBitmaps = request.attachedPhotos
        )

        val updatedProject = request.existingProject.copy(
            userId = request.userId,
            title = plan.title,
            topic = InputSanitizer.sanitizeTopic(request.promptTopic).cleanText.ifBlank { plan.title },
            style = request.style,
            durationSec = safeDuration.toLong(),
            voiceProfile = request.voiceProfile,
            voiceoverScript = plan.voiceoverScript,
            scenes = plan.scenes,
            securityVerified = true
        )
        onStoryboardReady(updatedProject, plan.threatsNeutralized)

        val sceneBitmaps: List<Bitmap> = if (request.generateAiVisuals) {
            visualGenerator.generateSceneKeyframes(
                topic = updatedProject.topic,
                style = updatedProject.style,
                scenes = updatedProject.scenes,
                maxKeyframes = 3,
                onFrameProgress = { completed, total ->
                    onStage(VideoPipelineStage.GeneratingVisuals(completed, total))
                }
            )
        } else {
            request.attachedPhotos
        }

        onStage(
            VideoPipelineStage.SynthesizingVoiceover(
                "Synthesizing ${updatedProject.voiceProfile} voiceover..."
            )
        )
        val voiceResult = voiceoverEngine.synthesizeVoiceover(
            script = updatedProject.voiceoverScript,
            voiceProfile = updatedProject.voiceProfile,
            outputTag = updatedProject.id.ifBlank { "clip_${System.currentTimeMillis()}" }
        )

        val (voiceFile, voiceStatus) = when (voiceResult) {
            is VoiceoverResult.Ready -> {
                val sec = voiceResult.durationMs / 1000f
                voiceResult.wavFile to "Voiceover ready (${String.format("%.1fs", sec)} • ${updatedProject.voiceProfile})"
            }
            is VoiceoverResult.Unavailable -> {
                null to voiceResult.reason
            }
        }

        onStage(VideoPipelineStage.RenderingVideo(0.05f))
        val exported = videoExporter.exportMp4(
            project = updatedProject,
            userPhotos = sceneBitmaps,
            voiceWavFile = voiceFile,
            onProgress = { pct ->
                onStage(VideoPipelineStage.RenderingVideo(pct))
            }
        )

        return VideoClipBuildResult(
            project = updatedProject,
            sceneBitmaps = sceneBitmaps,
            voiceoverFile = voiceFile,
            voiceoverStatus = voiceStatus,
            exportedVideo = exported,
            threatsNeutralized = plan.threatsNeutralized
        )
    }
}
