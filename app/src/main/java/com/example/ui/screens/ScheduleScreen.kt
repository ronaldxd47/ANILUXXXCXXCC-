package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.data.ScheduleDay
import com.example.data.ScrapedAnime
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.ui.components.*
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.JadeContainer
import com.example.ui.theme.JadeVerified
import java.util.Calendar

@Composable
fun ScheduleScreen(viewModel: AnimeViewModel) {
    val currentDayName = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Senin"
            Calendar.TUESDAY -> "Selasa"
            Calendar.WEDNESDAY -> "Rabu"
            Calendar.THURSDAY -> "Kamis"
            Calendar.FRIDAY -> "Jumat"
            Calendar.SATURDAY -> "Sabtu"
            Calendar.SUNDAY -> "Minggu"
            else -> "Senin"
        }
    }
    var selectedDay by remember { mutableStateOf(currentDayName) }
    val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu", "Acak")

    val scheduleState by viewModel.scheduleState.collectAsState()
    val trendingAnime by viewModel.trendingAnime.collectAsState()
    val latestUpdates by viewModel.latestUpdates.collectAsState()

    val isUsingLiveSchedule = remember(scheduleState, selectedDay) {
        val allDays = (scheduleState as? UiState.Success<List<ScheduleDay>>)?.data.orEmpty()
        val dayData = allDays.find { it.day.replace("'", "").equals(selectedDay, ignoreCase = true) }
        dayData != null && dayData.animeList.isNotEmpty()
    }

    val displayedAnime = remember(scheduleState, selectedDay, trendingAnime, latestUpdates) {
        val allDays = (scheduleState as? UiState.Success<List<ScheduleDay>>)?.data.orEmpty()
        val dayData = allDays.find { it.day.replace("'", "").equals(selectedDay, ignoreCase = true) }

        val liveList = if (dayData != null && dayData.animeList.isNotEmpty()) {
            val latestList = (latestUpdates as? UiState.Success)?.data.orEmpty()
            dayData.animeList.map { anime ->
                val titleClean = anime.title.replace("[SVIP]", "").trim()
                val matchedItem = (trendingAnime + latestList).firstOrNull {
                    it.link == anime.link ||
                        it.title.equals(titleClean, ignoreCase = true) ||
                        (titleClean.length > 4 && it.title.contains(titleClean, ignoreCase = true))
                }
                val imgUrl = anime.imageUrl.ifBlank { matchedItem?.imageUrl.orEmpty() }
                val epLabel = anime.episode.takeIf { it.isNotBlank() && it != "Episode ?" }
                    ?: matchedItem?.episode?.takeIf { it.isNotBlank() && it != "Episode ?" }
                    ?: "Jadwal $selectedDay"

                ScrapedAnime(
                    title = titleClean,
                    link = anime.link,
                    imageUrl = imgUrl,
                    status = if (selectedDay.equals(currentDayName, ignoreCase = true)) "Hari Ini" else selectedDay,
                    episode = epLabel,
                    score = "",
                    type = if (AnimeSource.isDonghuaUrl(anime.link) || anime.source.equals("anichin", true) || anime.source.equals("donghub", true)) "Donghua" else "Anime"
                )
            }
        } else {
            emptyList()
        }

        if (liveList.isNotEmpty()) {
            liveList
        } else {
            val dayIndex = days.indexOf(selectedDay).coerceAtLeast(0)
            val fallbackPool = trendingAnime.ifEmpty { (latestUpdates as? UiState.Success)?.data.orEmpty() }
            fallbackPool.filterIndexed { index, _ -> (index + dayIndex) % 3 == 0 }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val gridColumns = responsiveGridColumns(maxWidth)

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.statusBarsPadding())

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Jadwal Rilis",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$selectedDay • ${displayedAnime.size} judul",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Honest Source Verification Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUsingLiveSchedule) JadeContainer else AmberContainer)
                            .border(
                                1.dp,
                                if (isUsingLiveSchedule) JadeVerified.copy(alpha = 0.5f) else AmberGold.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isUsingLiveSchedule) "RESMI" else "REKOMENDASI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUsingLiveSchedule) JadeVerified else AmberGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.fetchScheduleData(forceRefresh = true) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("schedule_refresh_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Muat Ulang Jadwal",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Search) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("schedule_search_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Horizontal Top Day Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(days, key = { it }) { day ->
                    val selected = selectedDay == day
                    val isToday = day.equals(currentDayName, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else if (isToday) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedDay = day }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("schedule_day_$day"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isToday) "$day • Hari Ini" else day,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Content
            when {
                scheduleState is UiState.Loading && displayedAnime.isEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(6) {
                            SkeletonBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.68f)
                            )
                        }
                    }
                }

                displayedAnime.isEmpty() -> {
                    EmptyStateView(
                        title = "Belum ada jadwal untuk $selectedDay",
                        subtitle = "Coba muat ulang jadwal atau pilih hari lain.",
                        icon = Icons.Filled.DateRange,
                        actionLabel = "Muat Ulang",
                        onAction = { viewModel.fetchScheduleData(forceRefresh = true) }
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedAnime, key = { it.link + it.title }) { anime ->
                            MediaPosterCard(
                                anime = anime,
                                onClick = { viewModel.selectAnime(anime.link) }
                            )
                        }
                    }
                }
            }
        }
    }
}
