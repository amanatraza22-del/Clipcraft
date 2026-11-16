package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiSceneVisualGenerator
import com.example.ai.AiShortGenerator
import com.example.ai.AiVideoGeneratorPipeline
import com.example.ai.VideoClipBuildRequest
import com.example.ai.VideoPipelineStage
import com.example.data.model.ShortProject
import com.example.data.model.ShortScene
import com.example.data.repository.ShortProjectRepository
import com.example.media.ShortVideoExporter
import com.example.media.VoiceoverEngine
import com.example.media.VoiceoverResult
import com.example.security.InputSanitizer
import com.example.security.SecurityAuditor
import com.example.security.SecurityPostureReport
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val projects: List<ShortProject>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Loading(val stage: String) : GenerationUiState
    data class Ready(val message: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progress: Float) : ExportUiState
    data class Exported(
        val file: File,
        val includesAudio: Boolean,
        val durationSec: Int,
        val galleryUri: Uri? = null
    ) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentMs: Long = 0L,
    val totalMs: Long = 20_000L
)

class StudioViewModel(
    private val repository: ShortProjectRepository,
    private val aiGenerator: AiShortGenerator,
    private val voiceoverEngine: VoiceoverEngine,
    private val videoExporter: ShortVideoExporter,
    private val currentUserId: String,
    appContext: Context,
    private val aiVisualGenerator: AiSceneVisualGenerator = AiSceneVisualGenerator()
) : ViewModel() {

    private val appCtx = appContext.applicationContext
    private val videoPipeline = AiVideoGeneratorPipeline(
        shortGenerator = aiGenerator,
        visualGenerator = aiVisualGenerator,
        voiceoverEngine = voiceoverEngine,
        videoExporter = videoExporter
    )

    val projectsState: StateFlow<ProjectsUiState> = repository.observeShorts(currentUserId)
        .map<List<ShortProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user shorts", rootError)
            emit(ProjectsUiState.Error(rootError.message ?: "Failed to load saved shorts."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    private val _activeDraft = MutableStateFlow(defaultDraft())
    val activeDraft: StateFlow<ShortProject> = _activeDraft.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _attachedPhotos = MutableStateFlow<List<Bitmap>>(emptyList())
    val attachedPhotos: StateFlow<List<Bitmap>> = _attachedPhotos.asStateFlow()

    private val _voiceoverFile = MutableStateFlow<File?>(null)
    val voiceoverFile: StateFlow<File?> = _voiceoverFile.asStateFlow()

    private val _voiceoverStatus = MutableStateFlow("Ready to synthesize AI voiceover")
    val voiceoverStatus: StateFlow<String> = _voiceoverStatus.asStateFlow()

    private val _screenShieldEnabled = MutableStateFlow(true)
    val screenShieldEnabled: StateFlow<Boolean> = _screenShieldEnabled.asStateFlow()

    private val _threatsNeutralized = MutableStateFlow(0)
    val threatsNeutralized: StateFlow<Int> = _threatsNeutralized.asStateFlow()

    private val _securityReport = MutableStateFlow(SecurityAuditor.evaluate(appCtx, true))
    val securityReport: StateFlow<SecurityPostureReport> = _securityReport.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _includeAiSceneVisuals = MutableStateFlow(false)
    val includeAiSceneVisuals: StateFlow<Boolean> = _includeAiSceneVisuals.asStateFlow()

    private var tickerJob: Job? = null

    fun updateTopic(topic: String) {
        _activeDraft.value = _activeDraft.value.copy(topic = topic.take(InputSanitizer.MAX_TOPIC_CHARS))
    }

    fun updateTitle(title: String) {
        _activeDraft.value = _activeDraft.value.copy(title = title.take(InputSanitizer.MAX_TITLE_CHARS))
    }

    fun updateStyle(style: String) {
        _activeDraft.value = _activeDraft.value.copy(style = style)
    }

    fun updateDuration(seconds: Int) {
        val safeSec = seconds.coerceIn(10, 30)
        val clamped = InputSanitizer.clampScenes(_activeDraft.value.scenes, safeSec)
        _activeDraft.value = _activeDraft.value.copy(durationSec = safeSec.toLong(), scenes = clamped)
        _playbackState.value = _playbackState.value.copy(
            totalMs = safeSec * 1000L,
            currentMs = _playbackState.value.currentMs.coerceAtMost(safeSec * 1000L)
        )
    }

    fun updateVoiceProfile(profile: String) {
        _activeDraft.value = _activeDraft.value.copy(voiceProfile = profile)
    }

    fun updateCaptionStyle(style: String) {
        _activeDraft.value = _activeDraft.value.copy(captionStyle = style)
    }

    fun updateBackdropTheme(theme: String) {
        _activeDraft.value = _activeDraft.value.copy(backdropTheme = theme)
    }

    fun updateScript(script: String) {
        _activeDraft.value = _activeDraft.value.copy(voiceoverScript = script.take(InputSanitizer.MAX_SCRIPT_CHARS))
    }

    fun updateSceneCaption(sceneIndex: Int, newCaption: String) {
        val updated = _activeDraft.value.scenes.mapIndexed { idx, scene ->
            if (idx == sceneIndex) {
                scene.copy(caption = newCaption.take(InputSanitizer.MAX_CAPTION_CHARS))
            } else {
                scene
            }
        }
        _activeDraft.value = _activeDraft.value.copy(scenes = updated)
    }

    fun setAttachedPhotos(bitmaps: List<Bitmap>) {
        _attachedPhotos.value = bitmaps.take(3)
    }

    fun clearAttachedPhotos() {
        _attachedPhotos.value = emptyList()
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun setIncludeAiSceneVisuals(enabled: Boolean) {
        _includeAiSceneVisuals.value = enabled
    }

    fun generateAiShort() {
        val draft = _activeDraft.value
        if (draft.topic.isBlank()) {
            _generationState.value = GenerationUiState.Error("Enter a topic or idea first.")
            return
        }
        stopPlayback()

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading("Writing ${draft.durationSec}s script & auto-captions with Gemini AI...")
            _exportState.value = ExportUiState.Idle
            try {
                val plan = aiGenerator.generateShortPlan(
                    rawTopic = draft.topic,
                    style = draft.style,
                    durationSec = draft.durationSec.toInt(),
                    voiceProfile = draft.voiceProfile,
                    visualBitmaps = _attachedPhotos.value
                )
                if (plan.threatsNeutralized > 0) {
                    _threatsNeutralized.value += plan.threatsNeutralized
                }

                val generatedDraft = draft.copy(
                    userId = currentUserId,
                    title = plan.title,
                    topic = InputSanitizer.sanitizeTopic(draft.topic).cleanText.ifBlank { plan.title },
                    voiceoverScript = plan.voiceoverScript,
                    scenes = plan.scenes,
                    securityVerified = true
                )
                _activeDraft.value = generatedDraft
                _playbackState.value = PlaybackState(
                    isPlaying = false,
                    currentMs = 0L,
                    totalMs = generatedDraft.durationSec * 1000L
                )

                if (_includeAiSceneVisuals.value) {
                    val generatedFrames = aiVisualGenerator.generateSceneKeyframes(
                        topic = generatedDraft.topic,
                        style = generatedDraft.style,
                        scenes = generatedDraft.scenes,
                        maxKeyframes = 3,
                        onFrameProgress = { done, total ->
                            _generationState.value = GenerationUiState.Loading(
                                "Generating AI scene visual ${done.coerceAtMost(total)}/$total (Nano Banana)..."
                            )
                        }
                    )
                    if (generatedFrames.isNotEmpty()) {
                        _attachedPhotos.value = generatedFrames
                    }
                }

                _generationState.value = GenerationUiState.Loading("Synthesizing ${generatedDraft.voiceProfile} voiceover...")
                synthesizeVoiceoverInternal(generatedDraft)

                // Persist to Cloud Firestore
                saveDraftInternal(generatedDraft)
                _generationState.value = GenerationUiState.Ready("Short ready! Tap Play to preview or Render Free MP4.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Short generation failed", e)
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "AI generation failed. Check connection and App Check status."
                )
            }
        }
    }

    fun generateFull20sVideoClip() {
        val draft = _activeDraft.value
        if (draft.topic.isBlank()) {
            _generationState.value = GenerationUiState.Error("Enter a topic or prompt first.")
            return
        }
        stopPlayback()

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading("Starting 20s AI video pipeline...")
            _exportState.value = ExportUiState.Exporting(0.03f)
            try {
                val buildResult = videoPipeline.generateAndExportClip(
                    request = VideoClipBuildRequest(
                        userId = currentUserId,
                        existingProject = draft,
                        promptTopic = draft.topic,
                        style = draft.style,
                        durationSec = draft.durationSec.toInt(),
                        voiceProfile = draft.voiceProfile,
                        attachedPhotos = _attachedPhotos.value,
                        generateAiVisuals = _includeAiSceneVisuals.value
                    ),
                    onStage = { stage ->
                        when (stage) {
                            is VideoPipelineStage.PlanningStoryboard -> {
                                _generationState.value = GenerationUiState.Loading(stage.message)
                            }
                            is VideoPipelineStage.GeneratingVisuals -> {
                                _generationState.value = GenerationUiState.Loading(
                                    "Generating AI scene frame ${stage.completed}/${stage.total}..."
                                )
                            }
                            is VideoPipelineStage.SynthesizingVoiceover -> {
                                _generationState.value = GenerationUiState.Loading(stage.message)
                            }
                            is VideoPipelineStage.RenderingVideo -> {
                                _generationState.value = GenerationUiState.Loading(
                                    "Encoding ${draft.durationSec}s MP4 clip (${(stage.progress * 100).toInt()}%)..."
                                )
                                _exportState.value = ExportUiState.Exporting(stage.progress)
                            }
                        }
                    },
                    onStoryboardReady = { project, threats ->
                        if (threats > 0) {
                            _threatsNeutralized.value += threats
                        }
                        _activeDraft.value = project
                        _playbackState.value = PlaybackState(
                            isPlaying = false,
                            currentMs = 0L,
                            totalMs = project.durationSec * 1000L
                        )
                    }
                )

                if (buildResult.sceneBitmaps.isNotEmpty()) {
                    _attachedPhotos.value = buildResult.sceneBitmaps
                }
                _voiceoverFile.value = buildResult.voiceoverFile
                _voiceoverStatus.value = buildResult.voiceoverStatus
                _exportState.value = ExportUiState.Exported(
                    file = buildResult.exportedVideo.file,
                    includesAudio = buildResult.exportedVideo.includesAudio,
                    durationSec = buildResult.exportedVideo.durationSec
                )

                saveDraftInternal(buildResult.project)
                _generationState.value = GenerationUiState.Ready(
                    "${buildResult.exportedVideo.durationSec}s AI MP4 clip generated! Ready to Save or Share."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Full 20s AI video pipeline failed", e)
                val msg = e.localizedMessage ?: "AI video clip generation failed."
                _generationState.value = GenerationUiState.Error(msg)
                _exportState.value = ExportUiState.Error(msg)
            }
        }
    }

    fun autoCaptionEditedScript() {
        val draft = _activeDraft.value
        if (draft.voiceoverScript.isBlank()) return
        viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading("Re-syncing automated captions to script...")
            try {
                val scenes = aiGenerator.autoGenerateCaptionsFromScript(
                    rawScript = draft.voiceoverScript,
                    durationSec = draft.durationSec.toInt()
                )
                val updated = draft.copy(scenes = scenes)
                _activeDraft.value = updated
                synthesizeVoiceoverInternal(updated)
                _generationState.value = GenerationUiState.Ready("Captions & voiceover synced.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _generationState.value = GenerationUiState.Error(e.localizedMessage ?: "Failed to re-sync captions.")
            }
        }
    }

    fun resynthesizeVoiceover() {
        val draft = _activeDraft.value
        viewModelScope.launch {
            _generationState.value = GenerationUiState.Loading("Synthesizing ${draft.voiceProfile} voiceover...")
            synthesizeVoiceoverInternal(draft)
            _generationState.value = GenerationUiState.Ready("Voiceover updated.")
        }
    }

    private suspend fun synthesizeVoiceoverInternal(draft: ShortProject) {
        when (val result = voiceoverEngine.synthesizeVoiceover(
            script = draft.voiceoverScript,
            voiceProfile = draft.voiceProfile,
            outputTag = draft.id.ifBlank { "active" }
        )) {
            is VoiceoverResult.Ready -> {
                _voiceoverFile.value = result.wavFile
                val sec = result.durationMs / 1000f
                _voiceoverStatus.value = "Voiceover ready (${String.format("%.1fs", sec)} • ${draft.voiceProfile})"
            }
            is VoiceoverResult.Unavailable -> {
                _voiceoverFile.value = null
                _voiceoverStatus.value = result.reason
            }
        }
    }

    fun togglePlayPreview() {
        val current = _playbackState.value
        if (current.isPlaying) {
            stopPlayback()
        } else {
            val startPos = if (current.currentMs >= current.totalMs - 100L) 0L else current.currentMs
            _playbackState.value = current.copy(isPlaying = true, currentMs = startPos)
            val wav = _voiceoverFile.value
            if (wav != null && wav.exists()) {
                if (startPos < 500L) {
                    voiceoverEngine.playWav(wav)
                }
            } else if (startPos < 500L) {
                val draft = _activeDraft.value
                voiceoverEngine.speakLive(draft.voiceoverScript, draft.voiceProfile)
            }
            tickerJob?.cancel()
            tickerJob = viewModelScope.launch {
                var pos = startPos
                val total = _activeDraft.value.durationSec * 1000L
                while (pos < total) {
                    delay(40L)
                    pos += 40L
                    _playbackState.value = PlaybackState(
                        isPlaying = true,
                        currentMs = pos.coerceAtMost(total),
                        totalMs = total
                    )
                }
                stopPlayback()
                _playbackState.value = PlaybackState(isPlaying = false, currentMs = 0L, totalMs = total)
            }
        }
    }

    fun seekPreview(ms: Long) {
        val total = _activeDraft.value.durationSec * 1000L
        _playbackState.value = _playbackState.value.copy(currentMs = ms.coerceIn(0L, total))
    }

    fun stopPlayback() {
        tickerJob?.cancel()
        tickerJob = null
        voiceoverEngine.stopPlayback()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun saveCurrentDraft() {
        viewModelScope.launch {
            val saved = saveDraftInternal(_activeDraft.value)
            if (saved) {
                _statusBanner.value = "Saved securely to your encrypted cloud library."
            }
        }
    }

    private suspend fun saveDraftInternal(draft: ShortProject): Boolean {
        val safeTitle = InputSanitizer.sanitizeTitle(draft.title)
        val safeTopic = InputSanitizer.sanitizeTopic(draft.topic).cleanText.ifBlank { safeTitle }
        val safeScript = InputSanitizer.sanitizeScript(draft.voiceoverScript).ifBlank { safeTopic }
        val safeScenes = InputSanitizer.clampScenes(draft.scenes, draft.durationSec.toInt())
        val prepared = draft.copy(
            userId = currentUserId,
            title = safeTitle,
            topic = safeTopic,
            voiceoverScript = safeScript,
            scenes = safeScenes,
            securityVerified = true
        )
        return if (prepared.id.isBlank()) {
            val res = repository.createShort(prepared)
            res.onSuccess { newId ->
                val fetched = repository.getShortById(newId).getOrNull()
                _activeDraft.value = fetched ?: prepared.copy(id = newId)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Cloud save failed."
            }
            res.isSuccess
        } else {
            val res = repository.updateShort(prepared)
            res.onSuccess {
                _activeDraft.value = prepared
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Cloud update failed."
            }
            res.isSuccess
        }
    }

    fun loadProject(project: ShortProject) {
        stopPlayback()
        _activeDraft.value = project
        _exportState.value = ExportUiState.Idle
        _playbackState.value = PlaybackState(
            isPlaying = false,
            currentMs = 0L,
            totalMs = project.durationSec * 1000L
        )
        viewModelScope.launch {
            synthesizeVoiceoverInternal(project)
        }
    }

    fun createBlankShort() {
        stopPlayback()
        _activeDraft.value = defaultDraft()
        _voiceoverFile.value = null
        _exportState.value = ExportUiState.Idle
        _generationState.value = GenerationUiState.Idle
        _playbackState.value = PlaybackState()
    }

    fun deleteProject(shortId: String) {
        viewModelScope.launch {
            repository.deleteShort(shortId).onSuccess {
                if (_activeDraft.value.id == shortId) {
                    createBlankShort()
                }
                _statusBanner.value = "Short deleted from cloud."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Delete failed."
            }
        }
    }

    fun exportVideoMp4() {
        val draft = _activeDraft.value
        if (draft.scenes.isEmpty() && draft.voiceoverScript.isBlank()) {
            _exportState.value = ExportUiState.Error("Generate or write captions before rendering MP4.")
            return
        }
        stopPlayback()
        viewModelScope.launch {
            _exportState.value = ExportUiState.Exporting(0.03f)
            try {
                if (_voiceoverFile.value == null && draft.voiceoverScript.isNotBlank()) {
                    synthesizeVoiceoverInternal(draft)
                }
                val res = videoExporter.exportMp4(
                    project = draft,
                    userPhotos = _attachedPhotos.value,
                    voiceWavFile = _voiceoverFile.value,
                    onProgress = { pct ->
                        _exportState.value = ExportUiState.Exporting(pct)
                    }
                )
                _exportState.value = ExportUiState.Exported(
                    file = res.file,
                    includesAudio = res.includesAudio,
                    durationSec = res.durationSec
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MP4 export failed", e)
                _exportState.value = ExportUiState.Error(
                    e.localizedMessage ?: "Video rendering failed on this device."
                )
            }
        }
    }

    fun saveExportedToGallery() {
        val current = _exportState.value as? ExportUiState.Exported ?: return
        viewModelScope.launch {
            val uri = videoExporter.saveToDeviceGallery(current.file, _activeDraft.value.title)
            if (uri != null) {
                _exportState.value = current.copy(galleryUri = uri)
                _statusBanner.value = "Saved MP4 to Movies/ClipCraft gallery!"
            } else {
                _statusBanner.value = "Could not save video to MediaStore gallery."
            }
        }
    }

    fun toggleScreenShield(enabled: Boolean) {
        _screenShieldEnabled.value = enabled
        refreshSecurityAudit()
    }

    fun refreshSecurityAudit() {
        _securityReport.value = SecurityAuditor.evaluate(appCtx, _screenShieldEnabled.value)
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        voiceoverEngine.release()
    }

    private fun defaultDraft(): ShortProject = ShortProject(
        userId = currentUserId,
        title = "20s AI Hook Short",
        topic = "",
        style = "Viral Hook",
        durationSec = 20L,
        voiceProfile = "Studio Pro",
        captionStyle = "Neon Pop",
        backdropTheme = "Cyber Aurora",
        voiceoverScript = "Stop scrolling. Here is how one 20-second AI workflow turns raw ideas into studio shorts.",
        scenes = listOf(
            ShortScene(0L, 0L, 4500L, "Stop scrolling right now.", "scrolling", "Fast zoom intro"),
            ShortScene(1L, 4500L, 10000L, "Turn any idea into a 20s short.", "20s short", "Kinetic caption beat"),
            ShortScene(2L, 10000L, 15500L, "Auto-captions + AI voiceover.", "AI voiceover", "Audio waveform pulse"),
            ShortScene(3L, 15500L, 20000L, "Export free in 1 tap.", "free", "End card CTA")
        ),
        securityVerified = true
    )

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
