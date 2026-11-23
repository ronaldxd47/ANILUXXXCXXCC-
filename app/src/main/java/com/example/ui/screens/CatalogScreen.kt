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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.ui.components.*

@Composable
fun CatalogScreen(viewModel: AnimeViewModel) {
    val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val latestUpdates by viewModel.latestUpdates.collectAsState()
    var selectedCategoryTab by remember { mutableStateOf("ALL") }
    var selectedLetter by remember { mutableStateOf("#") }

    val catalogData = remember(latestUpdates, selectedCategoryTab, selectedLetter) {
        if (latestUpdates is UiState.Success) {
            val raw = (latestUpdates as UiState.Success).data
            val byCategory = when (selectedCategoryTab) {
                "ANIME" -> raw.filter {
                    !AnimeSource.isDonghuaUrl(it.link) && !it.type.equals("Donghua", ignoreCase = true)
                }
                "DONGHUA" -> raw.filter {
                    AnimeSource.isDonghuaUrl(it.link) || it.type.equals("Donghua", ignoreCase = true)
                }
                else -> raw
            }
            if (selectedLetter == "#") {
                byCategory
            } else {
                byCategory.filter { it.title.trim().startsWith(selectedLetter, ignoreCase = true) }
            }
        } else {
            emptyList()
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
                        text = if (selectedLetter == "#") "Katalog A–Z" else "Katalog ($selectedLetter)",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${catalogData.size} judul ditemukan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.refreshData(forceRefresh = true) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("catalog_refresh_btn")
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
                                contentDescription = "Muat Ulang",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Search) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("catalog_search_btn")
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

            // Segmented Category Bar
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
                    val selected = selectedCategoryTab == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedCategoryTab = key }
                            .testTag("catalog_category_$key"),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal A-Z Index Strip (maximizes full horizontal space for poster grid)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                items(alphabet, key = { it }) { letter ->
                    val selected = selectedLetter == letter
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedLetter = letter }
                            .padding(horizontal = 10.dp)
                            .testTag("catalog_letter_$letter"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Adaptive Content
            when (val state = latestUpdates) {
                is UiState.Loading -> {
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

                is UiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.refreshData(forceRefresh = true) }
                    )
                }

                else -> {
                    if (catalogData.isEmpty()) {
                        EmptyStateView(
                            title = "Belum ada judul untuk huruf \"$selectedLetter\"",
                            subtitle = "Pilih huruf lain atau tampilkan semua katalog.",
                            icon = Icons.Filled.FilterList,
                            actionLabel = if (selectedLetter != "#") "Reset Filter" else null,
                            onAction = if (selectedLetter != "#") { { selectedLetter = "#" } } else null
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(catalogData, key = { it.link + it.title }) { anime ->
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
}
