package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.ui.AnimeViewModel
import com.example.ui.ExoVideoPlayer
import com.example.ui.UiState
import com.example.ui.components.ErrorStateView
import com.example.ui.components.MetaTagChip
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeScreen(viewModel: AnimeViewModel) {
    val episodeDetailState by viewModel.episodeDetail.collectAsState()
    val animeDetailState by viewModel.animeDetail.collectAsState()
    val selectedEpisodeUrl by viewModel.selectedEpisodeUrl.collectAsState()
    val playbackPrefs by viewModel.playbackPreferences.collectAsState()
    val context = LocalContext.current

    val activeEpisodeUrl = selectedEpisodeUrl.orEmpty()
    val source = remember(activeEpisodeUrl) { AnimeSource.fromUrl(activeEpisodeUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = episodeDetailState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Memuat server streaming...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is UiState.Success -> {
                val detail = state.data
                val autoSelectedEmbed = remember(detail.streamEmbeds, playbackPrefs.preferredServer) {
                    val embeds = detail.streamEmbeds
                    val prefToken = playbackPrefs.preferredServer.substringBefore(" ").trim()
                    embeds.find {
                        prefToken.length > 2 &&
                            !prefToken.equals("Auto", ignoreCase = true) &&
                            it.serverName.contains(prefToken, ignoreCase = true)
                    }
                        ?: embeds.find { it.serverName.contains("1080p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("720p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("HD", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("Max", ignoreCase = true) }
                        ?: embeds.firstOrNull()
                }

                var activeEmbedServer by remember(autoSelectedEmbed) {
                    mutableStateOf(autoSelectedEmbed?.serverName.orEmpty())
                }
                var activeIframeUrl by remember(autoSelectedEmbed) {
                    mutableStateOf(autoSelectedEmbed?.iframeUrl.orEmpty())
                }

                val animeDetail = (animeDetailState as? UiState.Success)?.data
                val episodesList = animeDetail?.episodes ?: emptyList()
                val currentEpisodeIndex = remember(episodesList, activeEpisodeUrl, detail.title) {
                    episodesList.indexOfFirst {
                        it.link == activeEpisodeUrl || detail.title.contains(it.title, ignoreCase = true)
                    }.coerceAtLeast(0)
                }

                // Tentukan indeks episode sebelumnya & berikutnya secara cerdas (terlepas urutan ASC/DESC di situs sumber)
                val isNewestFirst = remember(episodesList) {
                    if (episodesList.size <= 1) false
                    else {
                        val firstNum = Regex("""(\d+)""").find(episodesList.first().title)?.groupValues?.get(1)?.toIntOrNull()
                        val lastNum = Regex("""(\d+)""").find(episodesList.last().title)?.groupValues?.get(1)?.toIntOrNull()
                        firstNum != null && lastNum != null && firstNum > lastNum
                    }
                }
                val prevEpisodeIndex = remember(episodesList, currentEpisodeIndex, isNewestFirst) {
                    if (episodesList.size <= 1) null
                    else {
                        val candidate = if (isNewestFirst) currentEpisodeIndex + 1 else currentEpisodeIndex - 1
                        candidate.takeIf { it in episodesList.indices }
                    }
                }
                val nextEpisodeIndex = remember(episodesList, currentEpisodeIndex, isNewestFirst) {
                    if (episodesList.size <= 1) null
                    else {
                        val candidate = if (isNewestFirst) currentEpisodeIndex - 1 else currentEpisodeIndex + 1
                        candidate.takeIf { it in episodesList.indices }
                    }
                }
                val nextEpisodeItem = nextEpisodeIndex?.let { episodesList.getOrNull(it) }

                val savedResumePos = remember(activeEpisodeUrl) {
                    viewModel.getPlaybackPosition(activeEpisodeUrl)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.statusBarsPadding())

                    // Minimal Top Theater Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.goBack() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("episode_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = animeDetail?.title ?: source.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = FormatUtils.formatEpisodeTitle(detail.title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MetaTagChip(text = source.displayName)
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Video Player Stage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        ExoVideoPlayer(
                            title = detail.title,
                            streamUrl = activeIframeUrl,
                            streamEmbeds = detail.streamEmbeds,
                            currentEpisodeIndex = currentEpisodeIndex,
                            totalEpisodes = episodesList.size,
                            hasPrevEpisode = prevEpisodeIndex != null,
                            hasNextEpisode = nextEpisodeIndex != null,
                            nextEpisodeTitle = nextEpisodeItem?.let { FormatUtils.formatEpisodeShort(it.title) },
                            onPrevEpisode = {
                                prevEpisodeIndex?.let { idx ->
                                    episodesList.getOrNull(idx)?.let { viewModel.selectEpisode(it.link) }
                                }
                            },
                            onNextEpisode = {
                                nextEpisodeItem?.let { viewModel.selectEpisode(it.link) }
                            },
                            onEpisodeChange = { index ->
                                episodesList.getOrNull(index)?.let { viewModel.selectEpisode(it.link) }
                            },
                            onBack = { viewModel.goBack() },
                            initialPositionMs = savedResumePos,
                            hardwareAccel = playbackPrefs.hardwareAccel,
                            dataSaver = playbackPrefs.dataSaver,
                            autoPlayNext = playbackPrefs.autoPlayNext,
                            defaultQuality = playbackPrefs.defaultQuality,
                            onSaveProgress = { posMs, durMs ->
                                viewModel.saveEpisodeProgress(activeEpisodeUrl, detail.title, posMs, durMs)
                            },
                            onPlaybackEnded = {
                                nextEpisodeItem?.let { nextEp ->
                                    Toast.makeText(
                                        context,
                                        "Melanjutkan ke ${FormatUtils.formatEpisodeShort(nextEp.title)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.selectEpisode(nextEp.link)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Server Switcher
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "Pilih Server Putar",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            detail.streamEmbeds.forEachIndexed { idx, server ->
                                val isSelected = activeEmbedServer == server.serverName
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            activeEmbedServer = server.serverName
                                            activeIframeUrl = server.iframeUrl
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("episode_server_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = server.serverName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Episode Switcher Strip
                    if (episodesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Daftar Episode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(episodesList, key = { idx, it -> "${idx}_${it.link}" }) { _, epItem ->
                                val isCurrentEp = epItem.link == activeEpisodeUrl || detail.title.contains(epItem.title, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isCurrentEp) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .border(
                                            1.dp,
                                            if (isCurrentEp) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (!isCurrentEp) viewModel.selectEpisode(epItem.link)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = FormatUtils.formatEpisodeShort(epItem.title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isCurrentEp) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Download Mirrors
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "Tautan Unduh • ${source.displayName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (detail.downloads.isEmpty()) {
                            Text(
                                text = "Tautan unduh tidak tersedia untuk episode ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            detail.downloads.forEach { group ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Download,
                                                    contentDescription = "Unduh",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = group.resolution,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            MetaTagChip(text = group.format, accent = true)
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            group.links.forEach { linkItem ->
                                                var showLinkDialog by remember { mutableStateOf(false) }

                                                if (showLinkDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showLinkDialog = false },
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        title = {
                                                            Text(
                                                                text = "${linkItem.host} (${group.resolution})",
                                                                style = MaterialTheme.typography.titleMedium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        },
                                                        text = {
                                                            Text(
                                                                text = linkItem.url,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 3,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        },
                                                        confirmButton = {
                                                            Button(
                                                                onClick = {
                                                                    showLinkDialog = false
                                                                    try {
                                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Download Link", linkItem.url))
                                                                        Toast.makeText(context, "Tautan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                                                    } catch (_: Exception) {
                                                                    }
                                                                },
                                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                                            ) {
                                                                Text("Salin Tautan", fontWeight = FontWeight.SemiBold)
                                                            }
                                                        },
                                                        dismissButton = {
                                                            TextButton(
                                                                onClick = { showLinkDialog = false },
                                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                                            ) {
                                                                Text("Tutup")
                                                            }
                                                        }
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .defaultMinSize(minHeight = 44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                                        .clickable { showLinkDialog = true }
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = linkItem.host,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            is UiState.Error -> {
                ErrorStateView(
                    message = state.message,
                    onRetry = { viewModel.retryEpisodeDetail() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {}
        }
    }
}
