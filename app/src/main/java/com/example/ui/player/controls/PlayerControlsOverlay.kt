package com.example.ui.player.controls

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CrimsonPrimaryPressed
import com.example.ui.theme.SkyAccent
import com.example.utils.FormatUtils

/**
 * Overlay kontrol video player (Top Bar, Center Playback Buttons, Bottom Progress Bar).
 * Semua tombol memenuhi target sentuh aksesibilitas minimum 48.dp.
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
    hasPrevEpisode: Boolean = currentEpisodeIndex > 0,
    hasNextEpisode: Boolean = currentEpisodeIndex in 0 until (totalEpisodes - 1),
    showSkipIntro: Boolean = false,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSkipIntro: () -> Unit = {},
    onPrevEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
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
                        Color.Black.copy(alpha = 0.78f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismissControls() })
            }
    ) {
        if (isLocked) {
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(48.dp)
                    .testTag("player_unlock_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Buka Kunci Layar",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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

                PlayerCenterControls(
                    isPlaying = isPlaying,
                    isFullscreen = isFullscreen,
                    hasPrevEpisode = hasPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onRewind10 = onRewind10,
                    onForward10 = onForward10,
                    onPrevEpisode = {
                        if (hasPrevEpisode) {
                            onPrevEpisode()
                        } else if (currentEpisodeIndex > 0) {
                            onEpisodeChange(currentEpisodeIndex - 1)
                        }
                    },
                    onNextEpisode = {
                        if (hasNextEpisode) {
                            onNextEpisode()
                        } else if (currentEpisodeIndex < totalEpisodes - 1) {
                            onEpisodeChange(currentEpisodeIndex + 1)
                        }
                    }
                )

                PlayerBottomBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    playbackSpeed = playbackSpeed,
                    currentServerName = currentServerName,
                    resizeMode = resizeMode,
                    isFullscreen = isFullscreen,
                    showSkipIntro = showSkipIntro,
                    onSeek = onSeek,
                    onSkipIntro = onSkipIntro,
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
                horizontal = if (isFullscreen) 12.dp else 4.dp,
                vertical = if (isFullscreen) 6.dp else 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFullscreen) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_back_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        val formattedEpTitle = remember(title) { FormatUtils.formatEpisodeTitle(title) }
        Text(
            text = formattedEpTitle,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (isFullscreen) 14.sp else 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isFullscreen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (batteryLevel in 0..20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                    contentDescription = "Status Baterai",
                    tint = if (batteryLevel in 0..20) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(12.dp)
                )
                if (batteryLevel >= 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (currentTimeString.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentTimeString,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.5.dp, SkyAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = bandwidthSpeedText.ifEmpty { "Auto" },
                    color = SkyAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        // Switch to WebView Sandbox button with accessible touch area
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onSwitchToWeb)
                .padding(horizontal = 4.dp)
                .testTag("player_switch_web_btn"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Web", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_settings_btn")
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Pengaturan Putar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (isFullscreen) {
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_lock_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Kunci Layar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    isFullscreen: Boolean,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFullscreen && hasPrevEpisode) {
            IconButton(
                onClick = onPrevEpisode,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_prev_episode_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Episode Sebelumnya",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(
            onClick = onRewind10,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_rewind_btn")
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFullscreen) 36.dp else 32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Mundur 10 Detik",
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 18.dp else 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 8.dp))

        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .size(52.dp)
                .testTag("player_play_pause_btn")
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFullscreen) 46.dp else 40.dp)
                    .background(
                        Brush.radialGradient(listOf(CrimsonPrimary, CrimsonPrimaryPressed)),
                        CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 24.dp else 20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isFullscreen) 14.dp else 8.dp))

        IconButton(
            onClick = onForward10,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_forward_btn")
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFullscreen) 36.dp else 32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Maju 10 Detik",
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 18.dp else 16.dp)
                )
            }
        }

        if (isFullscreen && hasNextEpisode) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onNextEpisode,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("player_next_episode_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Episode Selanjutnya",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    showSkipIntro: Boolean,
    onSeek: (Long) -> Unit,
    onSkipIntro: () -> Unit,
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
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                )
            )
            .padding(
                horizontal = if (isFullscreen) 14.dp else 8.dp,
                vertical = if (isFullscreen) 6.dp else 2.dp
            )
    ) {
        Slider(
            value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                val target = (frac * duration).toLong()
                onSeek(target)
            },
            colors = SliderDefaults.colors(
                thumbColor = CrimsonPrimary,
                activeTrackColor = CrimsonPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 24.dp else 20.dp)
                .testTag("player_seek_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${FormatUtils.formatTime(currentPosition)} / ${FormatUtils.formatTime(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isFullscreen) 11.sp else 10.sp
                )
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSkipIntro) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = onSkipIntro)
                            .padding(horizontal = 4.dp)
                            .testTag("player_skip_intro_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberGold.copy(alpha = 0.22f))
                                .border(0.75.dp, AmberGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = "Lewati Intro 85 detik",
                                    tint = AmberGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "+85d",
                                    color = AmberGold,
                                    fontSize = if (isFullscreen) 10.sp else 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClick = onPlaybackSpeedCycle)
                        .padding(horizontal = 4.dp)
                        .testTag("player_speed_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color.White,
                            fontSize = if (isFullscreen) 10.sp else 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = onServerSelectorClick)
                            .padding(horizontal = 4.dp)
                            .testTag("player_server_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Dns,
                                    contentDescription = "Pilih Server",
                                    tint = AmberGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentServerName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleAspectRatio,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_aspect_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.White.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Filled.ZoomOutMap
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.FitScreen
                                    else -> Icons.Filled.AspectRatio
                                },
                                contentDescription = "Aspek Rasio",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_fullscreen_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (isFullscreen) "Keluar Layar Penuh" else "Layar Penuh",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
