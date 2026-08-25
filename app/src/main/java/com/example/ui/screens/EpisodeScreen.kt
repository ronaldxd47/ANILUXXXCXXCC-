package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.ui.components.*
import com.example.ui.ExoVideoPlayer

import com.example.data.*


import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeScreen(viewModel: AnimeViewModel) {
    val episodeDetailState by viewModel.episodeDetail.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        when (val state = episodeDetailState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            }
            is UiState.Success -> {
                val detail = state.data
                val animeDetailState by viewModel.animeDetail.collectAsState()

                val autoSelectedEmbed = remember(detail.streamEmbeds) {
                    val embeds = detail.streamEmbeds
                    // Mencari server dengan kualitas tertinggi secara otomatis (1080p -> 720p -> HD -> Max)
                    embeds.find { it.serverName.contains("1080p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("720p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("HD", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("Max", ignoreCase = true) }
                        ?: embeds.firstOrNull()
                }

                var activeEmbedServer by remember(autoSelectedEmbed) { mutableStateOf(autoSelectedEmbed?.serverName ?: "") }
                var activeIframeUrl by remember(autoSelectedEmbed) { mutableStateOf(autoSelectedEmbed?.iframeUrl ?: "") }
                var isPlaying by remember(activeIframeUrl) { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    Spacer(modifier = Modifier.height(16.dp))

                    // Back & Title Screen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.goBack() },
                            modifier = Modifier
                                .background(Color(0xFF1E1E24), RoundedCornerShape(8.dp))
                                .size(40.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Streaming Area",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Video Player Representation Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val animeDetail = (animeDetailState as? UiState.Success)?.data
                        val episodesList = animeDetail?.episodes ?: emptyList()
                        val currentEpisodeIndex = episodesList.indexOfFirst { 
                            it.link == viewModel.selectedEpisodeUrl.value || detail.title.contains(it.title) 
                        }.coerceAtLeast(0)
                        val totalEpisodes = episodesList.size
                          
                        ExoVideoPlayer(
                            title = detail.title,
                            streamUrl = activeIframeUrl,
                            streamEmbeds = detail.streamEmbeds,
                            currentEpisodeIndex = currentEpisodeIndex,
                            totalEpisodes = totalEpisodes,
                            onEpisodeChange = { index ->
                                val targetEpisode = episodesList.getOrNull(index)
                                if (targetEpisode != null) {
                                    viewModel.selectEpisode(targetEpisode.link)
                                }
                            },
                            onBack = { viewModel.goBack() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = FormatUtils.formatEpisodeTitle(detail.title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Servers Select Box List
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Pilih Server Putar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.streamEmbeds.forEach { server ->
                            val isSelected = activeEmbedServer == server.serverName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF1E1E24))
                                    .clickable {
                                        activeEmbedServer = server.serverName
                                        activeIframeUrl = server.iframeUrl
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = server.serverName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Menu Episode (Quick Episode Navigation)
                    Text(
                        text = "Menu Episode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val animeDetail = (animeDetailState as? UiState.Success)?.data
                    if (animeDetail != null && animeDetail.episodes.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(animeDetail.episodes) { epItem ->
                                val isCurrentEp = detail.title.contains(epItem.title) || epItem.link == viewModel.selectedEpisodeUrl.value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrentEp) Color(0xFFE50914).copy(alpha = 0.2f) else Color(0xFF1E1E24))
                                        .border(1.dp, if (isCurrentEp) Color(0xFFE50914) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!isCurrentEp) {
                                                viewModel.selectEpisode(epItem.link)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = FormatUtils.formatEpisodeShort(epItem.title),
                                        color = if (isCurrentEp) Color(0xFFE50914) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Memuat daftar episode lainnya...", color = Color.Gray, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Downloader Mirror Lists
                    Text(
                        text = "Download Links (Anichin Mirrors)", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (detail.downloads.isEmpty()) {
                        Text("Tidak ada link download yang ditemukan.", color = Color.Gray)
                    } else {
                        detail.downloads.forEach { group ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = Color(0xFF16161E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color(0xFFA0A0B0), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = group.resolution, 
                                                color = Color.White, 
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE50914).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFE50914).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = group.format, 
                                                color = Color(0xFFE50914), 
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        group.links.forEach { linkItem ->
                                            var showLinkOptionDialog by remember { mutableStateOf(false) }
                                            
                                            if (showLinkOptionDialog) {
                                                AlertDialog(
                                                    onDismissRequest = { showLinkOptionDialog = false },
                                                    containerColor = Color(0xFF1E1E24),
                                                    titleContentColor = Color.White,
                                                    textContentColor = Color.LightGray,
                                                    title = {
                                                        Text(
                                                            text = "Opsi Tautan Media",
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = "Server: ${linkItem.host} (${group.resolution})",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 14.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Text(
                                                                text = linkItem.url,
                                                                color = Color.Gray,
                                                                fontSize = 11.sp,
                                                                maxLines = 2,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showLinkOptionDialog = false
                                                                try {
                                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                    val clip = android.content.ClipData.newPlainText("Download Link", linkItem.url)
                                                                    clipboard.setPrimaryClip(clip)
                                                                } catch (e: Exception) {
                                                                    // Gagal
                                                                }
                                                            }
                                                        ) {
                                                            Text("Salin Link", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                                                        }
                                                    },

                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF2A2A35).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = LocalIndication.current,
                                                        onClick = {
                                                            showLinkOptionDialog = true
                                                        }
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = linkItem.host,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Gagal memuat server streaming.", color = Color.White)
                }
            }
            else -> {}
        }
    }
}
