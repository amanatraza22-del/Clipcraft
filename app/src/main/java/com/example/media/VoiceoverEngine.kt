package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

sealed interface VoiceoverResult {
    data class Ready(val wavFile: File, val durationMs: Long) : VoiceoverResult
    data class Unavailable(val reason: String) : VoiceoverResult
}

class VoiceoverEngine(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val initSignal = CompletableDeferred<Boolean>()
    private var mediaPlayer: MediaPlayer? = null

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                val ok = status == TextToSpeech.SUCCESS
                if (ok) {
                    val engine = tts
                    val preferredLocales = listOf(Locale.US, Locale.ENGLISH, Locale.getDefault())
                    for (loc in preferredLocales) {
                        val res = engine?.setLanguage(loc)
                        if (res != null && res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                            break
                        }
                    }
                }
                if (!initSignal.isCompleted) {
                    initSignal.complete(ok)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech engine unavailable on this device", e)
            if (!initSignal.isCompleted) {
                initSignal.complete(false)
            }
        }
    }

    suspend fun synthesizeVoiceover(
        script: String,
        voiceProfile: String,
        outputTag: String
    ): VoiceoverResult = withContext(Dispatchers.IO) {
        if (script.isBlank()) {
            return@withContext VoiceoverResult.Unavailable("Voiceover script is empty.")
        }
        val initialized = withTimeoutOrNull(4000L) { initSignal.await() } ?: false
        val engine = tts
        if (!initialized || engine == null) {
            return@withContext VoiceoverResult.Unavailable(
                "System speech synthesizer is unavailable on this device/emulator; visual captions remain synced."
            )
        }

        val (pitch, rate) = profileParams(voiceProfile)
        engine.setPitch(pitch)
        engine.setSpeechRate(rate)

        val exportsDir = File(appContext.filesDir, "exports").apply { mkdirs() }
        val safeTag = outputTag.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "short" }
        val outFile = File(exportsDir, "voice_${safeTag}.wav")
        if (outFile.exists()) outFile.delete()

        val utteranceId = UUID.randomUUID().toString()
        val doneSignal = CompletableDeferred<Boolean>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && !doneSignal.isCompleted) {
                    doneSignal.complete(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !doneSignal.isCompleted) {
                    doneSignal.complete(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !doneSignal.isCompleted) {
                    doneSignal.complete(false)
                }
            }
        })

        val params = Bundle()
        val result = engine.synthesizeToFile(script, params, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            return@withContext VoiceoverResult.Unavailable("Voice synthesis could not start.")
        }

        val completed = withTimeoutOrNull(18_000L) { doneSignal.await() } ?: false
        if (!completed || !outFile.exists() || outFile.length() < 64L) {
            return@withContext VoiceoverResult.Unavailable(
                "No offline speech voice data installed on this emulator; previewing synced captions."
            )
        }

        val durationMs = estimateWavDurationMs(outFile)
        VoiceoverResult.Ready(wavFile = outFile, durationMs = durationMs)
    }

    fun speakLive(script: String, voiceProfile: String) {
        val engine = tts ?: return
        if (script.isBlank()) return
        try {
            val (pitch, rate) = profileParams(voiceProfile)
            engine.setPitch(pitch)
            engine.setSpeechRate(rate)
            engine.speak(script, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (e: Exception) {
            Log.w(TAG, "Live TTS speak skipped", e)
        }
    }

    fun playWav(wavFile: File, onCompletion: () -> Unit = {}) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onCompletion()
                }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play synthesized voiceover", e)
        }
    }

    fun stopPlayback() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stopPlayback()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun profileParams(profile: String): Pair<Float, Float> = when (profile) {
        "Energetic" -> 1.14f to 1.16f
        "Calm Narrator" -> 0.88f to 0.94f
        "Hype Host" -> 1.22f to 1.25f
        else -> 1.0f to 1.06f // Studio Pro
    }

    private fun estimateWavDurationMs(wavFile: File): Long {
        if (wavFile.length() <= 44L) return 0L
        return try {
            val bytes = wavFile.inputStream().use { input ->
                val header = ByteArray(44)
                val read = input.read(header)
                if (read < 44) return 0L
                header
            }
            val byteRate = (bytes[28].toInt() and 0xFF) or
                ((bytes[29].toInt() and 0xFF) shl 8) or
                ((bytes[30].toInt() and 0xFF) shl 16) or
                ((bytes[31].toInt() and 0xFF) shl 24)
            if (byteRate <= 0) return 0L
            ((wavFile.length() - 44L) * 1000L) / byteRate
        } catch (_: Exception) {
            0L
        }
    }

    private companion object {
        const val TAG = "VoiceoverEngine"
    }
}
