package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.R
import com.example.data.model.ShortProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ExportedVideoResult(
    val file: File,
    val includesAudio: Boolean,
    val durationSec: Int
)

class ShortVideoExporter(private val context: Context) {

    suspend fun exportMp4(
        project: ShortProject,
        userPhotos: List<Bitmap>,
        voiceWavFile: File?,
        onProgress: (Float) -> Unit
    ): ExportedVideoResult = withContext(Dispatchers.Default) {
        val width = 720
        val height = 1280
        val fps = 24
        val durationSec = project.durationSec.toInt().coerceIn(10, 30)
        val totalFrames = durationSec * fps

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeId = project.id.ifBlank { "draft_${System.currentTimeMillis()}" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(exportsDir, "clipcraft_${safeId}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val backdropBitmap = if (project.backdropTheme == "Obsidian Grid") {
            null
        } else {
            loadBackdropBitmap(project.backdropTheme, width, height)
        }
        val scaledPhotos = userPhotos.map { scaleCenterCrop(it, width, height) }

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / fps

        // Paints for rendering frames
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
        }
        val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4D6D")
        }
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 16, 19, 34)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 14, 17, 30)
        }
        val captionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#00E5FF")
        }
        val captionTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(8f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4D6D")
        }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val gridCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 0, 229, 255)
            strokeWidth = 2f
        }
        val gridCoralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(24, 255, 77, 109)
            strokeWidth = 2f
        }
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            for (frameIndex in 0 until totalFrames) {
                val timeMs = (frameIndex * 1000L) / fps
                val progress = (frameIndex + 1).toFloat() / totalFrames.toFloat()

                val canvas = inputSurface.lockCanvas(null)
                try {
                    drawVideoFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        timeMs = timeMs,
                        progress = progress,
                        project = project,
                        backdropBitmap = backdropBitmap,
                        photos = scaledPhotos,
                        scrimPaint = scrimPaint,
                        progressBgPaint = progressBgPaint,
                        progressFillPaint = progressFillPaint,
                        badgeBgPaint = badgeBgPaint,
                        badgeTextPaint = badgeTextPaint,
                        titleTextPaint = titleTextPaint,
                        captionBgPaint = captionBgPaint,
                        captionStrokePaint = captionStrokePaint,
                        captionTextPaint = captionTextPaint,
                        pillPaint = pillPaint,
                        pillTextPaint = pillTextPaint,
                        gridCyanPaint = gridCyanPaint,
                        gridCoralPaint = gridCoralPaint,
                        fadePaint = fadePaint
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain encoded video buffers
                drainEncoder(
                    encoder = videoEncoder,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    onOutputFormat = { format ->
                        videoTrackIndex = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    },
                    onBufferReady = { data, info ->
                        if (muxerStarted && videoTrackIndex >= 0) {
                            info.presentationTimeUs = frameIndex * frameDurationUs
                            muxer.writeSampleData(videoTrackIndex, data, info)
                        }
                    }
                )

                if (frameIndex % 8 == 0) {
                    onProgress((progress * 0.85f).coerceIn(0.02f, 0.85f))
                }
            }

            videoEncoder.signalEndOfInputStream()
            drainEncoder(
                encoder = videoEncoder,
                bufferInfo = bufferInfo,
                endOfStream = true,
                onOutputFormat = { format ->
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    }
                },
                onBufferReady = { data, info ->
                    if (muxerStarted && videoTrackIndex >= 0 && info.size > 0) {
                        muxer.writeSampleData(videoTrackIndex, data, info)
                    }
                }
            )
        } finally {
            try {
                videoEncoder.stop()
                videoEncoder.release()
            } catch (_: Exception) {
            }
            try {
                inputSurface.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {
            }
            if (backdropBitmap != null && !backdropBitmap.isRecycled) {
                backdropBitmap.recycle()
            }
            scaledPhotos.forEach { photo ->
                if (userPhotos.none { it === photo } && !photo.isRecycled) {
                    photo.recycle()
                }
            }
        }

        // Optional AAC voiceover mux pass into final MP4
        var audioIncluded = false
        if (voiceWavFile != null && voiceWavFile.exists() && voiceWavFile.length() > 44L) {
            val muxedWithAudio = File(exportsDir, "clipcraft_${safeId}_av.mp4")
            audioIncluded = tryMuxAudioWithVideo(outputFile, voiceWavFile, muxedWithAudio, durationSec * 1000L)
            if (audioIncluded && muxedWithAudio.exists() && muxedWithAudio.length() > 1024L) {
                outputFile.delete()
                muxedWithAudio.renameTo(outputFile)
            } else {
                muxedWithAudio.delete()
            }
        }

        onProgress(1f)
        ExportedVideoResult(file = outputFile, includesAudio = audioIncluded, durationSec = durationSec)
    }

    fun saveToDeviceGallery(videoFile: File, title: String): Uri? {
        if (!videoFile.exists()) return null
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifBlank { "ClipCraft_Short" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safeTitle}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipCraft")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                videoFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving exported video to MediaStore", e)
            null
        }
    }

    private fun drawVideoFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeMs: Long,
        progress: Float,
        project: ShortProject,
        backdropBitmap: Bitmap?,
        photos: List<Bitmap>,
        scrimPaint: Paint,
        progressBgPaint: Paint,
        progressFillPaint: Paint,
        badgeBgPaint: Paint,
        badgeTextPaint: Paint,
        titleTextPaint: Paint,
        captionBgPaint: Paint,
        captionStrokePaint: Paint,
        captionTextPaint: TextPaint,
        pillPaint: Paint,
        pillTextPaint: TextPaint,
        gridCyanPaint: Paint,
        gridCoralPaint: Paint,
        fadePaint: Paint
    ) {
        canvas.drawColor(Color.parseColor("#0A0B10"))

        val activeSceneIndex = project.scenes.indexOfFirst { timeMs in it.startMs..it.endMs }
            .takeIf { it >= 0 } ?: 0
        val activeScene = project.scenes.getOrNull(activeSceneIndex)
        val sceneStart = activeScene?.startMs ?: 0L
        val sceneEnd = activeScene?.endMs ?: (project.durationSec * 1000L)
        val sceneSpan = (sceneEnd - sceneStart).coerceAtLeast(1000L)
        val elapsedInScene = (timeMs - sceneStart).coerceAtLeast(0L)
        val sceneProgress = (elapsedInScene.toFloat() / sceneSpan.toFloat()).coerceIn(0f, 1f)

        // Draw user/AI scene photos with alternating Ken Burns motion & 350ms crossfade
        if (photos.isNotEmpty()) {
            val currentIdx = activeSceneIndex % photos.size
            val photo = photos[currentIdx]
            if (activeSceneIndex > 0 && photos.size > 1 && elapsedInScene < 350L) {
                val prevPhoto = photos[(activeSceneIndex - 1) % photos.size]
                fadePaint.alpha = 255
                canvas.save()
                canvas.scale(1.06f, 1.06f, width / 2f, height / 2f)
                canvas.drawBitmap(prevPhoto, 0f, 0f, fadePaint)
                canvas.restore()
                fadePaint.alpha = ((elapsedInScene / 350f) * 255f).toInt().coerceIn(0, 255)
            } else {
                fadePaint.alpha = 255
            }

            val zoom = when (activeSceneIndex % 3) {
                0 -> 1.0f + 0.08f * sceneProgress
                1 -> 1.08f - 0.06f * sceneProgress
                else -> 1.02f + 0.07f * sceneProgress
            }
            val panX = when (activeSceneIndex % 3) {
                0 -> -14f * sceneProgress
                1 -> 14f * sceneProgress
                else -> 0f
            }
            canvas.save()
            canvas.translate(panX, 0f)
            canvas.scale(zoom, zoom, width / 2f, height / 2f)
            canvas.drawBitmap(photo, 0f, 0f, fadePaint)
            canvas.restore()
        } else if (project.backdropTheme == "Obsidian Grid") {
            scrimPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.parseColor("#0E1120"),
                    Color.parseColor("#191D33"),
                    Color.parseColor("#0A0B10")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            scrimPaint.shader = null
            val step = 90f
            var x = 0f
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), gridCyanPaint)
                x += step
            }
            var y = 0f
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, gridCoralPaint)
                y += step
            }
        } else if (backdropBitmap != null) {
            val zoom = 1f + 0.06f * progress
            canvas.save()
            canvas.scale(zoom, zoom, width / 2f, height / 2f)
            canvas.drawBitmap(backdropBitmap, 0f, 0f, null)
            canvas.restore()
        }

        // Top & bottom contrast scrim
        scrimPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.argb(170, 10, 11, 16),
                Color.argb(40, 10, 11, 16),
                Color.argb(215, 10, 11, 16)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // Top progress bar
        val barRect = RectF(36f, 44f, width - 36f, 54f)
        canvas.drawRoundRect(barRect, 6f, 6f, progressBgPaint)
        val fillRight = 36f + (width - 72f) * progress
        canvas.drawRoundRect(RectF(36f, 44f, fillRight, 54f), 6f, 6f, progressFillPaint)

        // Top badge
        val badgeRect = RectF(36f, 76f, 340f, 128f)
        canvas.drawRoundRect(badgeRect, 20f, 20f, badgeBgPaint)
        val secDisplay = String.format("%.1fs / %ds", timeMs / 1000f, project.durationSec)
        canvas.drawText("CLIPCRAFT • $secDisplay", 56f, 111f, badgeTextPaint)

        // Title header
        canvas.drawText(project.title.take(32), 36f, 180f, titleTextPaint)

        // Automated caption block
        val captionText = (activeScene?.caption ?: project.voiceoverScript.take(80))
            .ifBlank { project.title.ifBlank { "ClipCraft Short" } }
        val textWidth = width - 128
        captionTextPaint.color = when (project.captionStyle) {
            "Neon Pop" -> Color.parseColor("#00E5FF")
            "Karaoke" -> Color.parseColor("#FFB703")
            else -> Color.WHITE
        }
        val layout = StaticLayout.Builder
            .obtain(captionText, 0, captionText.length, captionTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(6f, 1.05f)
            .build()

        val boxTop = height * 0.66f
        val boxBottom = boxTop + layout.height + 56f
        val captionBox = RectF(44f, boxTop, width - 44f, boxBottom)
        if (project.captionStyle != "Bold Minimal") {
            canvas.drawRoundRect(captionBox, 26f, 26f, captionBgPaint)
            if (project.captionStyle == "Neon Pop") {
                canvas.drawRoundRect(captionBox, 26f, 26f, captionStrokePaint)
            }
        }
        canvas.save()
        canvas.translate(64f, boxTop + 28f)
        layout.draw(canvas)
        canvas.restore()

        // Emphasis pill below caption
        val emphasis = activeScene?.emphasisWord.orEmpty()
        if (emphasis.isNotBlank()) {
            val pillWidth = (pillTextPaint.measureText(emphasis.uppercase()) + 48f).coerceAtMost(width - 120f)
            val left = (width - pillWidth) / 2f
            val pillTop = boxBottom + 20f
            canvas.drawRoundRect(RectF(left, pillTop, left + pillWidth, pillTop + 52f), 26f, 26f, pillPaint)
            canvas.drawText(emphasis.uppercase(), width / 2f, pillTop + 35f, pillTextPaint)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onOutputFormat: (MediaFormat) -> Unit,
        onBufferReady: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var tryAgainCount = 0
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    tryAgainCount++
                    if (tryAgainCount >= 200) break
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    tryAgainCount = 0
                    onOutputFormat(encoder.outputFormat)
                }
                status >= 0 -> {
                    tryAgainCount = 0
                    val encodedData = encoder.getOutputBuffer(status)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            onBufferReady(encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun tryMuxAudioWithVideo(
        videoOnlyFile: File,
        wavFile: File,
        outputFile: File,
        maxDurationMs: Long
    ): Boolean {
        var audioEncoder: MediaCodec? = null
        var videoExtractor: android.media.MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxStarted = false
        return try {
            val wavBytes = wavFile.readBytes()
            if (wavBytes.size <= 44) return false

            val sampleRate = ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val channels = ByteBuffer.wrap(wavBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            if (sampleRate <= 0 || channels <= 0) return false

            val pcmOffset = 44
            val maxPcmBytes = ((maxDurationMs * sampleRate * channels * 2L) / 1000L).toInt()
            val pcmLength = (wavBytes.size - pcmOffset).coerceAtMost(maxPcmBytes)
            if (pcmLength <= 0) return false

            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder = encoder
            encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val extractor = android.media.MediaExtractor().apply {
                setDataSource(videoOnlyFile.absolutePath)
            }
            videoExtractor = extractor
            var srcVideoTrack = -1
            var srcVideoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    srcVideoTrack = i
                    srcVideoFormat = fmt
                    break
                }
            }
            if (srcVideoTrack < 0 || srcVideoFormat == null) {
                return false
            }

            val localMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = localMuxer
            val muxVideoTrack = localMuxer.addTrack(srcVideoFormat)

            val audioBufferInfo = MediaCodec.BufferInfo()
            val encodedAudioSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
            var outputAudioFormat: MediaFormat? = null

            var readPos = pcmOffset
            val pcmEnd = pcmOffset + pcmLength
            var inputDone = false
            var outputDone = false
            var totalSamplesWritten = 0L
            var stallIterations = 0

            while (!outputDone && stallIterations < 400) {
                var madeProgress = false
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        madeProgress = true
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remaining = pcmEnd - readPos
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                (totalSamplesWritten * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val toCopy = remaining.coerceAtMost(inBuf.capacity())
                            inBuf.put(wavBytes, readPos, toCopy)
                            val ptsUs = (totalSamplesWritten * 1_000_000L) / sampleRate
                            totalSamplesWritten += toCopy / (2 * channels)
                            readPos += toCopy
                            encoder.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(audioBufferInfo, 5000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        madeProgress = true
                        outputAudioFormat = encoder.outputFormat
                    }
                    outIdx >= 0 -> {
                        madeProgress = true
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufferInfo.size > 0) {
                            outBuf.position(audioBufferInfo.offset)
                            val bytes = ByteArray(audioBufferInfo.size)
                            outBuf.get(bytes)
                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, bytes.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                            }
                            encodedAudioSamples.add(bytes to copyInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
                stallIterations = if (madeProgress) 0 else stallIterations + 1
            }

            if (!outputDone || outputAudioFormat == null) {
                return false
            }

            val muxAudioTrack = localMuxer.addTrack(outputAudioFormat)
            localMuxer.start()
            muxStarted = true

            // Write video samples
            extractor.selectTrack(srcVideoTrack)
            val videoBuf = ByteBuffer.allocate(256 * 1024)
            val vInfo = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(videoBuf, 0)
                if (sampleSize < 0) break
                vInfo.offset = 0
                vInfo.size = sampleSize
                vInfo.presentationTimeUs = extractor.sampleTime
                vInfo.flags = extractor.sampleFlags
                localMuxer.writeSampleData(muxVideoTrack, videoBuf, vInfo)
                extractor.advance()
            }

            // Write audio samples
            for ((bytes, info) in encodedAudioSamples) {
                localMuxer.writeSampleData(muxAudioTrack, ByteBuffer.wrap(bytes), info)
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Audio mux skipped cleanly: ${e.message}")
            false
        } finally {
            try {
                audioEncoder?.stop()
                audioEncoder?.release()
            } catch (_: Exception) {
            }
            try {
                videoExtractor?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadBackdropBitmap(theme: String, width: Int, height: Int): Bitmap? {
        val resId = when (theme) {
            "Warm Liquid" -> R.drawable.img_backdrop_warm
            else -> R.drawable.img_backdrop_cyber
        }
        val raw = BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val scaled = scaleCenterCrop(raw, width, height)
        if (scaled != raw) raw.recycle()
        return scaled
    }

    private fun scaleCenterCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = maxOf(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height
        )
        val scaledW = (scale * source.width).toInt().coerceAtLeast(1)
        val scaledH = (scale * source.height).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val xOffset = ((scaledW - targetWidth) / 2).coerceAtLeast(0)
        val yOffset = ((scaledH - targetHeight) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(
            resized,
            xOffset,
            yOffset,
            targetWidth.coerceAtMost(scaledW - xOffset),
            targetHeight.coerceAtMost(scaledH - yOffset)
        )
        if (cropped !== resized && resized !== source && !resized.isRecycled) {
            resized.recycle()
        }
        return cropped
    }

    private companion object {
        const val TAG = "ShortVideoExporter"
    }
}
