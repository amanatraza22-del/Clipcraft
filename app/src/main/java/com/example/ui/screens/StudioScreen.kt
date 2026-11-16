package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.ShortPresets
import com.example.data.model.ShortProject
import com.example.security.InputSanitizer
import com.example.ui.components.PreviewPlayerCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricCoral
import com.example.ui.theme.EmeraldShield
import com.example.ui.viewmodel.ExportUiState
import com.example.ui.viewmodel.GenerationUiState
import com.example.ui.viewmodel.PlaybackState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    draft: ShortProject,
    generationState: GenerationUiState,
    exportState: ExportUiState,
    playbackState: PlaybackState,
    attachedPhotos: List<Bitmap>,
    voiceoverStatus: String,
    threatsNeutralized: Int,
    onTopicChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onVoiceProfileChange: (String) -> Unit,
    onCaptionStyleChange: (String) -> Unit,
    onBackdropThemeChange: (String) -> Unit,
    onScriptChange: (String) -> Unit,
    onSceneCaptionChange: (Int, String) -> Unit,
    onPhotosPicked: (List<Bitmap>) -> Unit,
    onClearPhotos: () -> Unit,
    onGenerateClick: () -> Unit,
    onAutoCaptionScript: () -> Unit,
    onResynthesizeVoice: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSaveDraft: () -> Unit,
    onExportMp4: () -> Unit,
    onSaveToGallery: () -> Unit,
    onNewBlankShort: () -> Unit
) {
    val context = LocalContext.current

    // Zero-permission Android Photo Picker (up to 3 visual frames)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.mapNotNull { uri ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val src = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = false
                            val maxDim = 1440
                            val w = info.size.width
                            val h = info.size.height
                            if (w > maxDim || h > maxDim) {
                                val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                                decoder.setTargetSize(
                                    (w * scale).toInt().coerceAtLeast(1),
                                    (h * scale).toInt().coerceAtLeast(1)
                                )
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            onPhotosPicked(bitmaps)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Studio Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AI Shorts Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Free 20s verticals • Auto-captions • AI voiceover",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onNewBlankShort,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("new_short_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
            }

            // Prompt & Media Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Describe Your 20s Short",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = EmeraldShield.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Prompt Shield active",
                                    tint = EmeraldShield,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (threatsNeutralized > 0) "BLOCKED: $threatsNeutralized" else "PROMPT GUARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldShield
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draft.topic,
                        onValueChange = onTopicChange,
                        label = { Text("Topic, hook, or video idea") },
                        placeholder = { Text("e.g., 3 morning habits that double your focus") },
                        supportingText = {
                            Text("${draft.topic.length}/${InputSanitizer.MAX_TOPIC_CHARS} • Sanitized against prompt injection")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("topic_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Quick starter chips
                    val quickTopics = listOf(
                        "3 AI tools that save 5 hours weekly",
                        "Why 20 seconds of cold water resets focus",
                        "How to protect your phone from hackers"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTopics.forEach { idea ->
                            SuggestionChip(
                                onClick = { onTopicChange(idea) },
                                label = { Text(idea, maxLines = 1) }
                            )
                        }
                    }

                    // Duration & Style
                    Text("Duration & Creative Style", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShortPresets.durations.forEach { sec ->
                            val selected = draft.durationSec.toInt() == sec
                            FilterChip(
                                selected = selected,
                                onClick = { onDurationChange(sec) },
                                label = { Text("${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCoral.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCoral
                                )
                            )
                        }
                        ShortPresets.styles.forEach { style ->
                            FilterChip(
                                selected = draft.style == style,
                                onClick = { onStyleChange(style) },
                                label = { Text(style) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = CyberCyan
                                )
                            )
                        }
                    }

                    // Voiceover Profile
                    Text("AI Voiceover Profile", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShortPresets.voiceProfiles.forEach { profile ->
                            FilterChip(
                                selected = draft.voiceProfile == profile,
                                onClick = { onVoiceProfileChange(profile) },
                                label = { Text(profile) }
                            )
                        }
                    }

                    // Zero-Permission Photo Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("photo_picker_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (attachedPhotos.isEmpty()) "Attach Photos (Zero-Perm)"
                                else "${attachedPhotos.size} Photo(s) Attached"
                            )
                        }
                        if (attachedPhotos.isNotEmpty()) {
                            IconButton(
                                onClick = onClearPhotos,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove attached photos")
                            }
                        }
                    }

                    if (attachedPhotos.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            attachedPhotos.forEachIndexed { idx, bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Attached scene ${idx + 1}",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Generate CTA
                    val isGenerating = generationState is GenerationUiState.Loading
                    Button(
                        onClick = onGenerateClick,
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("generate_short_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCoral,
                            contentColor = Color.White
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text((generationState as GenerationUiState.Loading).stage)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate ${draft.durationSec}s AI Short",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    when (generationState) {
                        is GenerationUiState.Error -> {
                            Text(
                                text = generationState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is GenerationUiState.Ready -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldShield,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = generationState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldShield
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // 2. Live Interactive 9:16 Preview & Caption Style
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Live 9:16 Short Preview", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = draft.captionStyle,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }

                    PreviewPlayerCard(
                        project = draft,
                        playbackState = playbackState,
                        attachedPhotos = attachedPhotos,
                        voiceoverStatus = voiceoverStatus,
                        onTogglePlay = onTogglePlay,
                        onSeek = onSeekPreview
                    )

                    // Caption & Backdrop selectors
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Caption Preset", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShortPresets.captionStyles.forEach { preset ->
                                FilterChip(
                                    selected = draft.captionStyle == preset,
                                    onClick = { onCaptionStyleChange(preset) },
                                    label = { Text(preset) }
                                )
                            }
                        }
                        Text("Studio Backdrop", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShortPresets.backdropThemes.forEach { theme ->
                                FilterChip(
                                    selected = draft.backdropTheme == theme,
                                    onClick = { onBackdropThemeChange(theme) },
                                    label = { Text(theme) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Script & Automated Caption Beats Editor
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("3. Script & Automated Captions", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = onTitleChange,
                        label = { Text("Short Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = draft.voiceoverScript,
                        onValueChange = onScriptChange,
                        label = { Text("AI Voiceover Script") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAutoCaptionScript,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_captions_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Captions")
                        }
                        OutlinedButton(
                            onClick = onResynthesizeVoice,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("resynth_voice_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Update Voice")
                        }
                    }

                    draft.scenes.forEachIndexed { idx, scene ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = String.format(
                                    "BEAT %d • %.1fs – %.1fs",
                                    idx + 1,
                                    scene.startMs / 1000f,
                                    scene.endMs / 1000f
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                            OutlinedTextField(
                                value = scene.caption,
                                onValueChange = { onSceneCaptionChange(idx, it) },
                                label = { Text("On-Screen Caption") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 4. Cloud Save & Free MP4 Render / Export
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("4. Export & Cloud Sync", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSaveDraft,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("save_cloud_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Cloud")
                        }

                        Button(
                            onClick = onExportMp4,
                            enabled = exportState !is ExportUiState.Exporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("export_mp4_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Filled.MovieCreation, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.export_mp4_cta),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    when (exportState) {
                        is ExportUiState.Exporting -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { exportState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CyberCyan
                                )
                                Text(
                                    text = "Encoding 720x1280 MP4 (${(exportState.progress * 100).toInt()}%)...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                            }
                        }
                        is ExportUiState.Exported -> {
                            ExportedReadyCard(
                                file = exportState.file,
                                includesAudio = exportState.includesAudio,
                                durationSec = exportState.durationSec,
                                savedToGallery = exportState.galleryUri != null,
                                onSaveToGallery = onSaveToGallery,
                                onShare = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        exportState.file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share 20s Short"))
                                }
                            )
                        }
                        is ExportUiState.Error -> {
                            Text(
                                text = exportState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        ExportUiState.Idle -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExportedReadyCard(
    file: File,
    includesAudio: Boolean,
    durationSec: Int,
    savedToGallery: Boolean,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EmeraldShield.copy(alpha = 0.12f))
            .border(1.dp, EmeraldShield.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "MP4 Ready (${durationSec}s • ${file.length() / 1024} KB • ${if (includesAudio) "Video + Voiceover" else "Video + Captions"})",
            style = MaterialTheme.typography.titleMedium,
            color = EmeraldShield
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSaveToGallery,
                enabled = !savedToGallery,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_gallery_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldShield,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (savedToGallery) "Saved!" else stringResource(R.string.save_gallery_cta))
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("share_short_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share_short_cta))
            }
        }
    }
}
