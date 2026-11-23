package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.data.ScrapedAnime
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.ui.components.*
import com.example.ui.theme.AmberGold
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(viewModel: AnimeViewModel) {
    val animeDetailState by viewModel.animeDetail.collectAsState()
    val bookmarkedAnime by viewModel.bookmarkedAnime.collectAsState()
    val selectedUrl = viewModel.selectedAnimeUrl.collectAsState().value.orEmpty()

    var sortDescending by remember(selectedUrl) { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = animeDetailState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(0.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.65f).height(28.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        SkeletonBox(modifier = Modifier.fillMaxWidth(0.9f).height(18.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonBox(modifier = Modifier.weight(1f).height(48.dp))
                            SkeletonBox(modifier = Modifier.weight(1f).height(48.dp))
                        }
                    }
                }
            }

            is UiState.Success -> {
                val detail = state.data
                val isSaved = remember(bookmarkedAnime, selectedUrl) {
                    bookmarkedAnime.any { it.link == selectedUrl }
                }
                val isDonghua = remember(selectedUrl) { AnimeSource.isDonghuaUrl(selectedUrl) }
                val source = remember(selectedUrl) { AnimeSource.fromUrl(selectedUrl) }
                val lastProgress = remember(selectedUrl, detail.episodes) {
                    viewModel.getLastEpisodeProgress(selectedUrl)
                }

                val targetPlayEpisode = remember(detail.episodes, lastProgress) {
                    lastProgress?.let { prog ->
                        detail.episodes.find { it.link == prog.episodeUrl }
                    } ?: detail.episodes.lastOrNull() ?: detail.episodes.firstOrNull()
                }

                val sortedEpisodes = remember(detail.episodes, sortDescending) {
                    val indexed = detail.episodes.map { ep ->
                        val epNum = Regex("""(?:episode|ep|eps)\s*(\d+)""", RegexOption.IGNORE_CASE)
                            .find(ep.title)?.groupValues?.get(1)?.toIntOrNull()
                        ep to epNum
                    }
                    if (indexed.all { it.second != null }) {
                        if (sortDescending) indexed.sortedByDescending { it.second }.map { it.first }
                        else indexed.sortedBy { it.second }.map { it.first }
                    } else {
                        if (sortDescending) detail.episodes else detail.episodes.reversed()
                    }
                }

                val hasValidRating = remember(detail.rating) {
                    val r = detail.rating.trim()
                    r.isNotEmpty() && !r.equals("N/A", ignoreCase = true) && !r.equals("0", ignoreCase = true)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Editorial Poster Backdrop Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        ResolveAnimeImage(
                            url = detail.posterUrl,
                            contentDescription = detail.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.45f),
                                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                        1.0f to MaterialTheme.colorScheme.background
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(116.dp)
                                    .aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            ) {
                                ResolveAnimeImage(
                                    url = detail.posterUrl,
                                    contentDescription = detail.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MetaTagChip(
                                        text = if (isDonghua) "3D DONGHUA" else "ANIME",
                                        accent = true
                                    )
                                    if (hasValidRating) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .border(1.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = "Rating",
                                                tint = AmberGold,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = detail.rating,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AmberGold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = listOfNotNull(
                                        source.displayName,
                                        detail.status.takeIf { it.isNotBlank() },
                                        "${detail.episodes.size} Episode"
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Primary Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                targetPlayEpisode?.let { viewModel.selectEpisode(it.link) }
                            },
                            enabled = targetPlayEpisode != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1.35f)
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("detail_play_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val btnText = when {
                                lastProgress != null && targetPlayEpisode != null ->
                                    "Lanjut ${FormatUtils.formatEpisodeShort(targetPlayEpisode.title)}"
                                targetPlayEpisode != null ->
                                    "Tonton ${FormatUtils.formatEpisodeShort(targetPlayEpisode.title)}"
                                else -> "Belum Tersedia"
                            }
                            Text(
                                text = btnText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.toggleBookmark(
                                    ScrapedAnime(
                                        title = detail.title,
                                        link = selectedUrl,
                                        imageUrl = detail.posterUrl,
                                        status = detail.status,
                                        score = detail.rating,
                                        type = if (isDonghua) "Donghua" else "Anime"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSaved) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (isSaved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("detail_bookmark_btn")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) "Tersimpan" else "Simpan",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // Honest Metadata Row (strictly scraped fields)
                    val realMetaItems = remember(detail) {
                        listOfNotNull(
                            detail.released.takeIf { it.isNotBlank() && it != "?" }?.let { "Rilis: $it" },
                            detail.duration.takeIf { it.isNotBlank() && it != "?" }?.let { "Durasi: $it" },
                            detail.studio.takeIf { it.isNotBlank() && it != "?" }?.let { "Studio: $it" }
                        )
                    }
                    if (realMetaItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            realMetaItems.forEach { chipText ->
                                MetaTagChip(text = chipText)
                            }
                        }
                    }

                    // Clickable Genres
                    if (detail.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(detail.genres, key = { it }) { genre ->
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.performSearch(genre, immediateSaveHistory = true)
                                            viewModel.navigateTo(Screen.Search)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Synopsis
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Sinopsis",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = detail.synopsis.ifBlank { "Sinopsis belum tersedia untuk judul ini." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Episodes Header + Sort Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Episode (${detail.episodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sortDescending = !sortDescending }
                                .padding(horizontal = 8.dp)
                                .testTag("detail_sort_episodes")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Urutkan Episode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sortDescending) "Terbaru" else "Terlama",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedEpisodes.forEach { episode ->
                            val isLastWatched = lastProgress?.episodeUrl == episode.link
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLastWatched) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    1.dp,
                                    if (isLastWatched) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectEpisode(episode.link) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = FormatUtils.formatEpisodeTitle(episode.title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val subtitle = listOfNotNull(
                                            if (isLastWatched) "Terakhir ditonton" else null,
                                            episode.date.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        if (subtitle.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isLastWatched) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Putar Episode",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            is UiState.Error -> {
                ErrorStateView(
                    message = state.message,
                    onRetry = { viewModel.retryAnimeDetail() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {}
        }

        // Back button with 48dp accessible touch target
        IconButton(
            onClick = { viewModel.goBack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
                .testTag("detail_back_btn")
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
