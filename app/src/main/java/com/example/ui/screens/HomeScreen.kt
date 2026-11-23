package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.ui.components.*
import java.util.Calendar

@Composable
fun HomeScreen(viewModel: AnimeViewModel) {
    val latestUpdates by viewModel.latestUpdates.collectAsState()
    val trendingAnime by viewModel.trendingAnime.collectAsState()
    val historyAnime by viewModel.historyAnime.collectAsState()
    val scheduleState by viewModel.scheduleState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

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

    val todaySchedule = remember(scheduleState, currentDayName) {
        if (scheduleState is UiState.Success) {
            (scheduleState as UiState.Success).data
                .find { it.day.equals(currentDayName, ignoreCase = true) }
                ?.animeList ?: emptyList()
        } else emptyList()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val gridColumns = responsiveGridColumns(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())

            // Editorial Top App Bar
            HomeEditorialTopBar(
                userName = userProfile.name,
                isRefreshing = latestUpdates is UiState.Loading,
                onProfileClick = { viewModel.navigateTo(Screen.Profile) },
                onRefreshClick = { viewModel.refreshData(forceRefresh = true) },
                onSearchClick = { viewModel.navigateTo(Screen.Search) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Spotlight Carousel
            if (trendingAnime.isNotEmpty()) {
                FeaturedSpotlightCarousel(
                    items = trendingAnime,
                    onAnimeClick = { viewModel.selectAnime(it.link) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Quick Discovery Genres
            EditorialGenreStrip(
                onGenreClick = { genre ->
                    if (genre.isBlank()) {
                        viewModel.navigateTo(Screen.Catalog)
                    } else {
                        viewModel.performSearch(genre, immediateSaveHistory = true)
                        viewModel.navigateTo(Screen.Search)
                    }
                }
            )

            // Real Watch History (only displayed if user has genuinely opened/watched items)
            if (historyAnime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                SectionHeader(
                    title = "Lanjutkan Nonton",
                    actionLabel = "Semua",
                    onActionClick = { viewModel.navigateTo(Screen.Library) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(historyAnime.take(10), key = { it.link }) { item ->
                        ContinueWatchingCard(
                            anime = item,
                            onClick = { viewModel.selectAnime(item.link) }
                        )
                    }
                }
            }

            // Today's Schedule Strip
            if (todaySchedule.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                TodayScheduleStrip(
                    scheduleData = todaySchedule,
                    onAnimeClick = { anime -> viewModel.selectAnime(anime.link) },
                    onSeeAllClick = { viewModel.navigateTo(Screen.Schedule) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            when (val state = latestUpdates) {
                is UiState.Loading -> {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().height(200.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(gridColumns) {
                                SkeletonBox(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.68f)
                                )
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val allData = state.data
                    val donghuaOnly = remember(allData) {
                        allData.filter {
                            AnimeSource.isDonghuaUrl(it.link) || it.type.equals("Donghua", ignoreCase = true)
                        }
                    }

                    // Dedicated Donghua Showcase
                    if (donghuaOnly.isNotEmpty()) {
                        SectionHeader(
                            title = "Donghua 3D Pilihan",
                            subtitle = "Anichin & Donghub",
                            actionLabel = "Katalog",
                            onActionClick = { viewModel.navigateTo(Screen.Catalog) },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(donghuaOnly.take(12), key = { it.link }) { item ->
                                Box(modifier = Modifier.width(136.dp)) {
                                    MediaPosterCard(
                                        anime = item,
                                        onClick = { viewModel.selectAnime(item.link) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }

                    val filteredData = remember(allData, selectedCategoryFilter) {
                        when (selectedCategoryFilter) {
                            "ANIME" -> allData.filter {
                                !AnimeSource.isDonghuaUrl(it.link) && !it.type.equals("Donghua", ignoreCase = true)
                            }
                            "DONGHUA" -> donghuaOnly
                            else -> allData
                        }
                    }

                    SectionHeader(
                        title = "Rilis Terbaru",
                        subtitle = "${filteredData.size} judul tersedia",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Category Filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "ALL" to "Semua",
                            "ANIME" to "Anime JP",
                            "DONGHUA" to "3D Donghua"
                        ).forEach { (key, label) ->
                            val selected = selectedCategoryFilter == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { selectedCategoryFilter = key }
                                    .testTag("home_filter_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (filteredData.isEmpty()) {
                        EmptyStateView(
                            title = "Belum ada rilis di kategori ini",
                            subtitle = "Coba pilih filter Semua atau muat ulang beranda."
                        )
                    } else {
                        AnimeGridSection(
                            animes = filteredData,
                            columns = gridColumns,
                            onAnimeClick = { viewModel.selectAnime(it.link) }
                        )
                    }
                }

                is UiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.refreshData(forceRefresh = true) }
                    )
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HomeEditorialTopBar(
    userName: String,
    isRefreshing: Boolean,
    onProfileClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VIBESTREAM",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kurasi rilis terbaru • $userName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_refresh_btn")
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
                        contentDescription = "Muat ulang",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(18.dp)
                            .then(if (isRefreshing) Modifier.rotate(angle) else Modifier)
                    )
                }
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_search_btn")
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

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_profile_btn")
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profil",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialGenreStrip(onGenreClick: (String) -> Unit) {
    val genres = listOf(
        "" to "Semua Katalog",
        "Donghua" to "3D Donghua",
        "Aksi" to "Aksi",
        "Kultivasi" to "Kultivasi",
        "Isekai" to "Isekai",
        "Fantasi" to "Fantasi",
        "Romantis" to "Romantis",
        "Movie" to "Movie"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres, key = { it.second }) { (query, label) ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { onGenreClick(query) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
