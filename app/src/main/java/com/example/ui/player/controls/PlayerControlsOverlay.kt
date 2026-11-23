package com.example.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.utils.FormatUtils

/**
 * Overlay kontrol video player (Top Bar, Center Playback Buttons, Bottom Progress Bar).
 * Mendukung status terkunci (screen lock) dan penyesuaian otomatis untuk orientasi Fullscreen.
 */
@Composable
fun PlayerControlsOverlay(
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    bandwidthSpeedText: String,
    currentServerName: String,
    resizeMode: Int,
    isLocked: Boolean,
    isFullscreen: Boolean,
    batteryLevel: Int,
    currentTimeString: String,
    currentEpisodeIndex: Int,
    totalEpisodes: Int,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onEpisodeChange: (Int) -> Unit,
    onPlaybackSpeedCycle: () -> Unit,
    onLockToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onServerSelectorClick: () -> Unit,
    onSwitchToWeb: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onDismissControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.75f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismissControls() }
                )
            }
    ) {
        if (isLocked) {
            // Mode Terkunci: Hanya tampilkan tombol unlock di pojok kiri atas
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Buka Kunci Layar",
                    tint = Color.White
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Controls Bar
                PlayerTopBar(
                    title = title,
                    isFullscreen = isFullscreen,
                    batteryLevel = batteryLevel,
                    currentTimeString = currentTimeString,
                    bandwidthSpeedText = bandwidthSpeedText,
                    onBack = onBack,
                    onSwitchToWeb = onSwitchToWeb,
                    onSettingsClick = onSettingsClick,
                    onLockToggle = onLockToggle
                )

                // 2. Center Playback Controls (Prev, Rewind 10s, Play/Pause, Forward 10s, Next)
                PlayerCenterControls(
                    isPlaying = isPlaying,
                    isFullscreen = isFullscreen,
                    currentEpisodeIndex = currentEpisodeIndex,
                    totalEpisodes = totalEpisodes,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onRewind10 = onRewind10,
                    onForward10 = onForward10,
                    onEpisodeChange = onEpisodeChange
                )

                // 3. Bottom Controls Bar (Slider, Timestamp, Speed, Server, Aspect, Fullscreen)
                PlayerBottomBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    playbackSpeed = playbackSpeed,
                    currentServerName = currentServerName,
                    resizeMode = resizeMode,
                    isFullscreen = isFullscreen,
                    onSeek = onSeek,
                    onPlaybackSpeedCycle = onPlaybackSpeedCycle,
                    onServerSelectorClick = onServerSelectorClick,
                    onToggleAspectRatio = onToggleAspectRatio,
                    onToggleFullscreen = onToggleFullscreen
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    isFullscreen: Boolean,
    batteryLevel: Int,
    currentTimeString: String,
    bandwidthSpeedText: String,
    onBack: () -> Unit,
    onSwitchToWeb: () -> Unit,
    onSettingsClick: () -> Unit,
    onLockToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                )
            )
            .padding(
                horizontal = if (isFullscreen) 14.dp else 10.dp,
                vertical = if (isFullscreen) 8.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFullscreen) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val formattedEpTitle = remember(title) { FormatUtils.formatEpisodeTitle(title) }
        Text(
            text = formattedEpTitle,
            color = Color.White,
            fontSize = if (isFullscreen) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isFullscreen) {
            // Indikator Baterai dan Jam
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Icon(
                    imageVector = if (batteryLevel in 0..20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                    contentDescription = "Baterai",
                    tint = if (batteryLevel in 0..20) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(12.dp)
                )
                if (batteryLevel >= 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (currentTimeString.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentTimeString,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Indikator Bitrate / Resolusi HLS
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = bandwidthSpeedText.ifEmpty { "HD" },
                    color = Color(0xFF00F2FE),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
        }

        // Tombol Switch ke Web Engine Sandbox
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .clickable { onSwitchToWeb() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Web", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Tombol Pengaturan (Settings)
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .size(if (isFullscreen) 28.dp else 24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Pengaturan",
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 15.dp else 13.dp)
            )
        }

        if (isFullscreen) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = "Kunci Layar",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    isFullscreen: Boolean,
    currentEpisodeIndex: Int,
    totalEpisodes: Int,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onEpisodeChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Sebelumnya (Fullscreen saja)
        if (isFullscreen && totalEpisodes > 1 && currentEpisodeIndex > 0) {
            IconButton(
                onClick = { onEpisodeChange(currentEpisodeIndex - 1) },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Episode Sebelumnya",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 10.dp))
        }

        // Mundur 10 Detik
        IconButton(
            onClick = onRewind10,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .size(if (isFullscreen) 32.dp else 26.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = "Mundur 10 Detik",
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 16.dp else 14.dp)
            )
        }

        Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 10.dp))

        // Play / Pause Button dengan Radiant Red Accent
        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFE50914), Color(0xFFB00610))
                    ),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .size(if (isFullscreen) 40.dp else 34.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Jeda" else "Putar",
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 22.dp else 18.dp)
            )
        }

        Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 10.dp))

        // Maju 10 Detik
        IconButton(
            onClick = onForward10,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .size(if (isFullscreen) 32.dp else 26.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = "Maju 10 Detik",
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 16.dp else 14.dp)
            )
        }

        // Episode Selanjutnya (Fullscreen saja)
        if (isFullscreen && totalEpisodes > 1 && currentEpisodeIndex < totalEpisodes - 1) {
            Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 10.dp))
            IconButton(
                onClick = { onEpisodeChange(currentEpisodeIndex + 1) },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Episode Selanjutnya",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomBar(
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    currentServerName: String,
    resizeMode: Int,
    isFullscreen: Boolean,
    onSeek: (Long) -> Unit,
    onPlaybackSpeedCycle: () -> Unit,
    onServerSelectorClick: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                )
            )
            .padding(
                horizontal = if (isFullscreen) 14.dp else 8.dp,
                vertical = if (isFullscreen) 8.dp else 4.dp
            )
    ) {
        // Slider Scrubbing
        Slider(
            value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                val target = (frac * duration).toLong()
                onSeek(target)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFE50914),
                activeTrackColor = Color(0xFFE50914),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 16.dp else 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label Posisi / Durasi
            Text(
                text = "${FormatUtils.formatTime(currentPosition)} / ${FormatUtils.formatTime(duration)}",
                color = Color.White,
                fontSize = if (isFullscreen) 11.sp else 9.sp,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Quick Playback Speed Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .clickable { onPlaybackSpeedCycle() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = if (isFullscreen) 10.sp else 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isFullscreen) {
                    Spacer(modifier = Modifier.width(6.dp))

                    // Server Selector Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .clickable { onServerSelectorClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = currentServerName,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Toggle Aspect Ratio (Fit, Zoom, Fill)
                    IconButton(
                        onClick = onToggleAspectRatio,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .size(26.dp)
                    ) {
                        Icon(
                            imageVector = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Filled.ZoomOutMap
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.FitScreen
                                else -> Icons.Filled.AspectRatio
                            },
                            contentDescription = "Aspek Rasio",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Toggle Fullscreen
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .size(if (isFullscreen) 26.dp else 22.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "Layar Penuh",
                        tint = Color.White,
                        modifier = Modifier.size(if (isFullscreen) 14.dp else 13.dp)
                    )
                }
            }
        }
    }
}
