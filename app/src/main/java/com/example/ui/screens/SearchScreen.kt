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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.AnimeSource
import com.example.ui.AnimeViewModel
import com.example.ui.UiState
import com.example.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: AnimeViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val searchResultsState by viewModel.searchResults.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val focusManager = LocalFocusManager.current

    var selectedSource by remember { mutableStateOf("Semua") }
    val sources = listOf("Semua", "Samehadaku", "Anichin", "Donghub")
    val quickTopics = listOf("Donghua", "Kultivasi", "Aksi", "Isekai", "One Piece", "Battle Through")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val gridColumns = responsiveGridColumns(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Pencarian",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Cari lintas Samehadaku, Anichin, dan Donghub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.performSearch(it) },
                placeholder = {
                    Text(
                        "Ketik judul anime atau donghua...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Cari",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.performSearch("") },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("search_clear_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Hapus kata kunci",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.performSearch(query, immediateSaveHistory = true)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Source Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(sources, key = { it }) { sourceName ->
                    val isSelected = selectedSource == sourceName
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
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
                            .clickable { selectedSource = sourceName }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("search_source_$sourceName"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (query.trim().isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Riwayat Pencarian",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(
                                onClick = { viewModel.clearSearchHistory() },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Hapus", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { term ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.performSearch(term, immediateSaveHistory = true)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Text(
                        text = "Eksplor Cepat",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTopics.forEach { topic ->
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.performSearch(topic, immediateSaveHistory = true)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                when (val state = searchResultsState) {
                    is UiState.Loading -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(6) {
                                Column {
                                    SkeletonBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.68f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(14.dp))
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        val filteredList = remember(state.data, selectedSource) {
                            when (selectedSource) {
                                "Samehadaku" -> state.data.filter { AnimeSource.fromUrl(it.link) == AnimeSource.SAMEHADAKU }
                                "Anichin" -> state.data.filter { AnimeSource.fromUrl(it.link) == AnimeSource.ANICHIN }
                                "Donghub" -> state.data.filter { AnimeSource.fromUrl(it.link) == AnimeSource.DONGHUB }
                                else -> state.data
                            }
                        }

                        if (filteredList.isEmpty()) {
                            EmptyStateView(
                                title = "Tidak ada kecocokan di $selectedSource",
                                subtitle = "Coba pilih filter \"Semua\" atau ubah kata kunci pencarian.",
                                icon = Icons.Filled.Search,
                                actionLabel = if (selectedSource != "Semua") "Tampilkan Semua Sumber" else null,
                                onAction = if (selectedSource != "Semua") { { selectedSource = "Semua" } } else null
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredList, key = { it.link + it.title }) { anime ->
                                    MediaPosterCard(
                                        anime = anime,
                                        onClick = { viewModel.selectAnime(anime.link) }
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Error -> {
                        EmptyStateView(
                            title = state.message,
                            subtitle = "Periksa ejaan judul atau coba kata kunci yang lebih singkat.",
                            icon = Icons.Filled.Search,
                            actionLabel = "Coba Lagi",
                            onAction = { viewModel.performSearch(query, immediateSaveHistory = true) }
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}
