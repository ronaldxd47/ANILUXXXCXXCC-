package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.ui.components.*

@Composable
fun ScheduleScreen(viewModel: AnimeViewModel) {
    val currentDayName = remember {
        val calendar = java.util.Calendar.getInstance()
        when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "Senin"
            java.util.Calendar.TUESDAY -> "Selasa"
            java.util.Calendar.WEDNESDAY -> "Rabu"
            java.util.Calendar.THURSDAY -> "Kamis"
            java.util.Calendar.FRIDAY -> "Jumat"
            java.util.Calendar.SATURDAY -> "Sabtu"
            java.util.Calendar.SUNDAY -> "Minggu"
            else -> "Senin"
        }
    }
    var selectedDay by remember { mutableStateOf(currentDayName) }
    val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu", "Acak")
    
    val scheduleState by viewModel.scheduleState.collectAsState()
    val trendingAnime by viewModel.trendingAnime.collectAsState()
    val latestUpdatesState by viewModel.latestUpdates.collectAsState()
    val latestUpdates = latestUpdatesState
    
    // 1. Old reliable deterministic fallback list
    val displayedFallbackAnime = remember(selectedDay, trendingAnime, latestUpdates) {
        val dayIndex = days.indexOf(selectedDay)
        val sourceList = if (trendingAnime.isNotEmpty()) {
            trendingAnime
        } else if (latestUpdates is UiState.Success) {
            latestUpdates.data
        } else {
            emptyList()
        }
        
        if (sourceList.isEmpty()) {
            emptyList()
        } else {
            // Filter deterministically so every day has a different set of real anime/donghua
            sourceList.filterIndexed { index, _ -> 
                (index + dayIndex) % 3 == 0 
            }
        }
    }
    
    // 2. Resolve displayed list based on schedule state
    val displayedAnime = remember(scheduleState, selectedDay, trendingAnime, latestUpdates, displayedFallbackAnime) {
        var list: List<com.example.data.ScrapedAnime> = emptyList()
        
        if (scheduleState is UiState.Success) {
            val allDays = (scheduleState as UiState.Success<List<com.example.data.ScheduleDay>>).data
            val dayData = allDays.find { 
                it.day.replace("'", "").equals(selectedDay, ignoreCase = true) 
            }
            if (dayData != null && dayData.animeList.isNotEmpty()) {
                list = dayData.animeList.map { anime ->
                    val titleClean = anime.title.replace("[SVIP]", "").trim()
                    var imgUrl = anime.imageUrl
                    
                    // Fuzzy matching to find actual image URL from other sources if missing
                    if (imgUrl.isEmpty()) {
                        val matchTrending = trendingAnime.find { 
                            it.link == anime.link || 
                            it.title.contains(titleClean, ignoreCase = true) || 
                            titleClean.contains(it.title, ignoreCase = true) 
                        }
                        if (matchTrending != null) {
                            imgUrl = matchTrending.imageUrl
                        } else if (latestUpdates is UiState.Success) {
                            val matchLatest = latestUpdates.data.find { 
                                it.link == anime.link || 
                                it.title.contains(titleClean, ignoreCase = true) || 
                                titleClean.contains(it.title, ignoreCase = true) 
                            }
                            if (matchLatest != null) {
                                imgUrl = matchLatest.imageUrl
                            }
                        }
                    }
                    
                    // Smart handpicked high-quality poster fallback
                    if (imgUrl.isEmpty()) {
                        val lowerTitle = titleClean.lowercase()
                        imgUrl = when {
                            lowerTitle.contains("renegade immortal") || lowerTitle.contains("renegade") -> "https://anichin.moe/wp-content/uploads/2023/09/Renegade-Immortal-Poster.jpg"
                            lowerTitle.contains("btth") || lowerTitle.contains("battle through the heavens") -> "https://anichin.moe/wp-content/uploads/2022/07/Battle-Through-The-Heavens-Season-5-Poster.jpg"
                            lowerTitle.contains("perfect world") -> "https://anichin.moe/wp-content/uploads/2021/04/Perfect-World-Poster.jpg"
                            lowerTitle.contains("soul land") -> "https://anichin.moe/wp-content/uploads/2023/06/Soul-Land-2-Poster.jpg"
                            lowerTitle.contains("swallowed star") -> "https://anichin.moe/wp-content/uploads/2020/11/Swallowed-Star-Poster.jpg"
                            lowerTitle.contains("apotheosis") -> "https://anichin.moe/wp-content/uploads/2022/11/Apotheosis-Poster.jpg"
                            lowerTitle.contains("shrouding the heavens") -> "https://anichin.moe/wp-content/uploads/2023/05/Shrouding-the-Heavens-Poster.jpg"
                            lowerTitle.contains("martial master") -> "https://anichin.moe/wp-content/uploads/2020/03/Martial-Master-Poster.jpg"
                            lowerTitle.contains("100.000 years") || lowerTitle.contains("refined qi") || lowerTitle.contains("refining qi") -> "https://anichin.moe/wp-content/uploads/2023/02/100000-Years-Refining-Qi-Poster.jpg"
                            lowerTitle.contains("against the gods") -> "https://anichin.moe/wp-content/uploads/2023/09/Against-the-Gods-Poster.jpg"
                            lowerTitle.contains("great ruler") -> "https://anichin.moe/wp-content/uploads/2023/07/The-Great-Ruler-Poster.jpg"
                            lowerTitle.contains("supreme god emperor") -> "https://anichin.moe/wp-content/uploads/2020/09/Supreme-God-Emperor-Poster.jpg"
                            lowerTitle.contains("against sky supreme") || lowerTitle.contains("against the sky") -> "https://anichin.moe/wp-content/uploads/2021/06/Against-the-Sky-Supreme-Poster.jpg"
                            lowerTitle.contains("legend of martial immortal") -> "https://anichin.moe/wp-content/uploads/2023/11/Legend-of-Martial-Immortal-Poster.jpg"
                            else -> "https://anichin.moe/wp-content/uploads/2023/09/Renegade-Immortal-Poster.jpg"
                        }
                    }

                    // Extract exact episode number if present, or find from latest update match
                    var episodeStr = if (anime.episode.isNotBlank() && anime.episode != "Episode ?") {
                        anime.episode
                    } else {
                        val matchLatest = if (latestUpdates is UiState.Success) {
                            latestUpdates.data.find { it.link == anime.link || it.title.contains(titleClean, ignoreCase = true) || titleClean.contains(it.title, ignoreCase = true) }
                        } else null
                        val matchTrending = trendingAnime.find { it.link == anime.link || it.title.contains(titleClean, ignoreCase = true) || titleClean.contains(it.title, ignoreCase = true) }
                        
                        matchLatest?.episode?.takeIf { it.isNotBlank() && it != "Episode ?" }
                            ?: matchTrending?.episode?.takeIf { it.isNotBlank() && it != "Episode ?" }
                            ?: "Update $selectedDay"
                    }
                    if (!episodeStr.contains("Episode", ignoreCase = true) && !episodeStr.contains("Ep", ignoreCase = true) && episodeStr.contains(Regex("^\\d+$"))) {
                        episodeStr = "Episode $episodeStr"
                    }

                    val isToday = selectedDay.equals(currentDayName, ignoreCase = true)
                    val statusTag = if (isToday) "HARI INI" else "RILIS $selectedDay"
                    
                    com.example.data.ScrapedAnime(
                        title = titleClean,
                        link = anime.link,
                        imageUrl = imgUrl,
                        status = statusTag,
                        episode = episodeStr,
                        score = "N/A",
                        type = if (anime.source.isNotBlank()) anime.source.uppercase() else "DONGHUA"
                    )
                }
            }
        }
        
        // If the live schedule list is empty, seamlessly use fallback recommendation list
        if (list.isEmpty()) {
            displayedFallbackAnime
        } else {
            list
        }
    }
    
    val isUsingLiveSchedule = remember(scheduleState, selectedDay) {
        if (scheduleState is UiState.Success) {
            val allDays = (scheduleState as UiState.Success<List<com.example.data.ScheduleDay>>).data
            val dayData = allDays.find { 
                it.day.replace("'", "").equals(selectedDay, ignoreCase = true) 
            }
            dayData != null && dayData.animeList.isNotEmpty()
        } else {
            false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Jadwal Rilis",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.fetchScheduleData() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Jadwal",
                        tint = Color(0xFFE53935)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.navigateTo(Screen.Search) }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Cari Anime",
                        tint = Color(0xFFE53935)
                    )
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxSize()) {
            // Left content area (Schedule List)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDay,
                            color = Color(0xFFE53935),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${displayedAnime.size} Anime)",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Status badge (Official vs Fallback Recommendation)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isUsingLiveSchedule) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFE53935).copy(alpha = 0.15f))
                                .border(1.dp, if (isUsingLiveSchedule) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isUsingLiveSchedule) "JADWAL RESMI" else "REKOMENDASI",
                                color = if (isUsingLiveSchedule) Color(0xFF4CAF50) else Color(0xFFE53935),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (displayedAnime.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = "No Schedule",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tidak ada jadwal tayang untuk hari $selectedDay",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp)
                        ) {
                            items(displayedAnime.size, key = { index -> displayedAnime[index].title + displayedAnime[index].link }) { index ->
                                val anime = displayedAnime[index]
                                CompactAnimeCard(
                                    anime = anime,
                                    onClick = {
                                        if (anime.link.contains("myanimelist.net")) {
                                            viewModel.performSearch(anime.title)
                                            viewModel.navigateTo(Screen.Search)
                                        } else {
                                            viewModel.selectAnime(anime.link)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Right Sidebar (Days list selector)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 100.dp)
            ) {
                days.forEach { day ->
                    val isSelected = selectedDay == day
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF7000FF) else Color(0xFF1B1E2E))
                            .clickable { selectedDay = day }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
