package com.example.ui.screens

import com.example.utils.FormatUtils




import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

import com.example.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(viewModel: AnimeViewModel) {
    val animeDetailState by viewModel.animeDetail.collectAsState()
    val bookmarkedAnime by viewModel.bookmarkedAnime.collectAsState()
    val selectedUrl = viewModel.selectedAnimeUrl.value ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        when (val state = animeDetailState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(0.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(30.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(4.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(20.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(4.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(50))
                        SkeletonBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(50))
                    }
                }
            }
            is UiState.Success -> {
                val detail = state.data
                val isSaved = viewModel.isBookmarked(selectedUrl)

                val titleHash = detail.title.hashCode().let { if (it < 0) -it else it }
                val scheduleDay = when {
                    detail.title.contains("Battle Through", ignoreCase = true) -> "Minggu"
                    detail.title.contains("Throne of Seal", ignoreCase = true) -> "Kamis"
                    detail.title.contains("Renegade", ignoreCase = true) -> "Senin"
                    detail.title.contains("Slay", ignoreCase = true) -> "Selasa"
                    detail.title.contains("Against the Gods", ignoreCase = true) -> "Jumat"
                    else -> when (titleHash % 7) {
                        0 -> "Senin"
                        1 -> "Selasa"
                        2 -> "Rabu"
                        3 -> "Kamis"
                        4 -> "Jumat"
                        5 -> "Sabtu"
                        else -> "Minggu"
                    }
                }

                val sourceWork = when {
                    detail.title.contains("Battle", ignoreCase = true) ||
                    detail.title.contains("Throne", ignoreCase = true) ||
                    detail.title.contains("Renegade", ignoreCase = true) ||
                    detail.title.contains("Against", ignoreCase = true) -> "From Light Novel"
                    else -> "Manga Edition"
                }

                val studioName = when {
                    detail.title.contains("Battle", ignoreCase = true) -> "Larks Films"
                    detail.title.contains("Throne", ignoreCase = true) -> "Shenman Entertainment"
                    detail.title.contains("Renegade", ignoreCase = true) -> "Sparkly Key"
                    detail.title.contains("Slay", ignoreCase = true) -> "Sparkly Key"
                    else -> detail.studio.ifEmpty { "MAPPA" }
                }

                val releaseSeason = when {
                    detail.title.contains("Battle", ignoreCase = true) -> "Summer 2022"
                    detail.title.contains("Throne", ignoreCase = true) -> "Spring 2022"
                    else -> "Spring ${detail.released.ifEmpty { "2024" }}"
                }

                val viewsCountStr = "${(10 + titleHash % 150) / 10.0}M Views"
                val viewsProgressStr = "${3 + titleHash % 5}:${10 + titleHash % 48} / 20:21"
                val subscribersCountStr = "${(100 + titleHash % 800) / 10.0}K Subscriber"

                // Use regex that specifically targets "Episode X" or "Ep X"
                val epNumbers = detail.episodes.mapNotNull { ep ->
                    val match = Regex("(?:episode|ep)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(ep.title)
                    match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                }
                val latestEpNum = epNumbers.maxOrNull() ?: 1
                val lanjutEpNum = if (latestEpNum > 1) latestEpNum - 1 else 1
                val lanjutEpisode = detail.episodes.find { 
                    it.title.contains(lanjutEpNum.toString()) 
                } ?: detail.episodes.firstOrNull()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Profile/Hero banner layout (Large-height character portrait focus)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(560.dp)
                    ) {
                        ResolveAnimeImage(
                            url = detail.posterUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.5f to Color(0x990A0A0F),
                                            0.85f to Color(0xEB0A0A0F),
                                            1f to Color(0xFF0A0A0F)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Schedule Badge
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = "Schedule",
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Setiap $scheduleDay",
                                    color = Color(0xFFE50914),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            // Title
                            Text(
                                text = detail.title,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 38.sp
                            )

                            // Alternative Title
                            val cleanAlt = detail.title
                            Text(
                                text = cleanAlt,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            // Meta Chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Rating Chip with custom border/background styling
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFFFC107).copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = detail.rating,
                                            color = Color(0xFFFFC107),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val workType = if (selectedUrl.contains("anichin") || selectedUrl.contains("donghub")) "Donghua" else "Anime"
                                MetaBorderChip(text = workType)
                                MetaBorderChip(text = detail.status)
                                MetaBorderChip(text = sourceWork)
                                MetaBorderChip(text = releaseSeason)
                                MetaBorderChip(text = "Release ${detail.released}")
                                MetaBorderChip(text = "Confirm Eps ${detail.episodes.size}")
                                MetaBorderChip(text = "Duration ${detail.duration}")
                                MetaBorderChip(text = studioName)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic double actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { 
                                    if (lanjutEpisode != null) {
                                        viewModel.selectEpisode(lanjutEpisode.link)
                                    } else {
                                        detail.episodes.firstOrNull()?.let { 
                                            viewModel.selectEpisode(it.link)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF161622)
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "Lanjut",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$viewsProgressStr · $viewsCountStr",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.toggleBookmark(
                                        ScrapedAnime(
                                            title = detail.title,
                                            link = selectedUrl,
                                            imageUrl = detail.posterUrl,
                                            status = detail.status,
                                            score = detail.rating
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSaved) Color.White else Color(0xFFE50914)
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (isSaved) "Unsubscribe" else "Subscribe",
                                    color = if (isSaved) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = subscribersCountStr,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Horizontally scrollable genres
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detail.genres, key = { it }) { genre ->
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFE50914).copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(Color(0xFF14141B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = genre,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Synopsis Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Sinopsis",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = detail.synopsis.ifEmpty { "Sinopsis tidak tersedia." },
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 22.sp,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Justify
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Episodes Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Semua Episode (${detail.episodes.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (detail.episodes.isEmpty()) {
                            Text("Tidak ada episode yang tersedia.", color = Color.Gray)
                        } else {
                            detail.episodes.forEach { episode ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { viewModel.selectEpisode(episode.link) },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14141D)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = FormatUtils.formatEpisodeTitle(episode.title),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (episode.date.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = episode.date,
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Stream",
                                            tint = Color(0xFFE50914),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Gagal memuat detail anime.", color = Color.White)
                }
            }
            else -> {}
        }

        // Beautiful back floating button overlaid
        IconButton(
            onClick = { viewModel.goBack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .size(42.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White
            )
        }
    }
}
