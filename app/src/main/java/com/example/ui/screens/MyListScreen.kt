package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
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
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.components.*

@Composable
fun MyListScreen(viewModel: AnimeViewModel) {
    val bookmarkedAnime by viewModel.bookmarkedAnime.collectAsState()
    val historyAnime by viewModel.historyAnime.collectAsState()

    var selectedTab by remember { mutableStateOf("Favorite") }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val displayData = if (selectedTab == "Favorite") bookmarkedAnime else historyAnime

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
                        text = "Koleksi Saya",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${bookmarkedAnime.size} favorit • ${historyAnime.size} riwayat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (displayData.isNotEmpty()) {
                        IconButton(
                            onClick = { showConfirmClearDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("library_clear_btn")
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
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Bersihkan Daftar",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Search) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("library_search_btn")
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

            // Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Favorite" to "Favorit (${bookmarkedAnime.size})",
                    "History" to "Riwayat (${historyAnime.size})"
                ).forEach { (tabKey, tabLabel) ->
                    val selected = selectedTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = tabKey }
                            .testTag("library_tab_$tabKey"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (displayData.isEmpty()) {
                val isFavTab = selectedTab == "Favorite"
                EmptyStateView(
                    title = if (isFavTab) "Daftar favorit masih kosong" else "Belum ada riwayat tonton",
                    subtitle = if (isFavTab)
                        "Simpan anime atau donghua yang ingin Anda ikuti agar mudah ditemukan."
                    else
                        "Judul yang Anda buka akan otomatis tercatat di riwayat.",
                    icon = if (isFavTab) Icons.Filled.Bookmark else Icons.Filled.History,
                    actionLabel = "Jelajahi Katalog",
                    onAction = { viewModel.navigateTo(Screen.Catalog) }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayData, key = { it.link + it.title }) { anime ->
                        MediaPosterCard(
                            anime = anime,
                            onClick = { viewModel.selectAnime(anime.link) }
                        )
                    }
                }
            }
        }
    }

    if (showConfirmClearDialog) {
        val isFavTab = selectedTab == "Favorite"
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = if (isFavTab) "Hapus Semua Favorit?" else "Hapus Semua Riwayat?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isFavTab)
                        "Seluruh daftar favorit tersimpan akan dihapus dari perangkat ini."
                    else
                        "Seluruh riwayat menonton akan dihapus dari perangkat ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isFavTab) viewModel.clearFavoritesOnly() else viewModel.clearHistoryOnly()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Hapus", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmClearDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
