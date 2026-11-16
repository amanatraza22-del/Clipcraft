package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ShortProject
import com.example.ui.theme.AmberSpotlight
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.PlaybackState

@Composable
fun PreviewPlayerCard(
    project: ShortProject,
    playbackState: PlaybackState,
    attachedPhotos: List<Bitmap>,
    voiceoverStatus: String,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = playbackState.totalMs.coerceAtLeast(1000L)
    val progress = (playbackState.currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val activeSceneIndex = project.scenes.indexOfFirst {
        playbackState.currentMs in it.startMs..it.endMs
    }.takeIf { it >= 0 } ?: 0
    val activeScene = project.scenes.getOrNull(activeSceneIndex)
    val kenBurnsScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1.07f else 1.0f,
        label = "ken_burns"
    )

    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(26.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
                .background(ObsidianBg)
        ) {
            if (attachedPhotos.isNotEmpty()) {
                val photo = attachedPhotos[activeSceneIndex % attachedPhotos.size]
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Short scene photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = kenBurnsScale
                            scaleY = kenBurnsScale
                        },
                    contentScale = ContentScale.Crop
                )
            } else if (project.backdropTheme == "Obsidian Grid") {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0E1120), Color(0xFF191D33), Color(0xFF0A0B10))
                        )
                    )
                    val step = 52.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = CyberCyan.copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = ElectricCoral.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }
            } else {
                val resId = if (project.backdropTheme == "Warm Liquid") {
                    R.drawable.img_backdrop_warm
                } else {
                    R.drawable.img_backdrop_cyber
                }
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = "Short backdrop artwork",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = kenBurnsScale
                            scaleY = kenBurnsScale
                        },
                    contentScale = ContentScale.Crop
                )
            }

            // Contrast scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.65f),
                                Color.Transparent,
                                ObsidianBg.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Top progress bar & timecode
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(ElectricCoral, CyberCyan)))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${project.style.uppercase()} • ${project.durationSec}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        color = ObsidianBg.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = String.format(
                                "%04.1fs / %02ds",
                                playbackState.currentMs / 1000f,
                                project.durationSec
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Center Play/Pause button
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ElectricCoral.copy(alpha = 0.88f))
                    .testTag("play_preview_button")
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause preview" else "Play preview",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Live Automated Caption Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val captionText = activeScene?.caption ?: project.voiceoverScript.take(80)
                val emphasis = activeScene?.emphasisWord.orEmpty()

                AnimatedContent(targetState = captionText, label = "caption_anim") { text ->
                    val styled = buildAnnotatedString {
                        if (emphasis.isNotBlank() && text.contains(emphasis, ignoreCase = true)) {
                            val startIdx = text.indexOf(emphasis, ignoreCase = true)
                            val endIdx = startIdx + emphasis.length
                            append(text.substring(0, startIdx))
                            withStyle(
                                SpanStyle(
                                    color = if (project.captionStyle == "Karaoke") AmberSpotlight else ElectricCoral,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) {
                                append(text.substring(startIdx, endIdx))
                            }
                            append(text.substring(endIdx))
                        } else {
                            append(text)
                        }
                    }

                    val boxColor = when (project.captionStyle) {
                        "Bold Minimal" -> Color.Transparent
                        "Cinema Box" -> Color.Black.copy(alpha = 0.82f)
                        else -> ObsidianBg.copy(alpha = 0.82f)
                    }
                    val borderMod = if (project.captionStyle == "Neon Pop") {
                        Modifier.border(1.5.dp, CyberCyan.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    }

                    Surface(
                        color = boxColor,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(borderMod)
                    ) {
                        Text(
                            text = styled,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            color = if (project.captionStyle == "Neon Pop") CyberCyan else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                if (!activeScene?.visualCue.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cue: ${activeScene?.visualCue}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Timeline scrubber + voiceover status
        Slider(
            value = playbackState.currentMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..totalMs.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = ElectricCoral,
                activeTrackColor = ElectricCoral,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("preview_scrubber")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = "Voiceover status",
                tint = CyberCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = voiceoverStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
