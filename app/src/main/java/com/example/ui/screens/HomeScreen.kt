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
import com.example.ui.components.*

import com.example.data.*
import com.example.*

@Composable
fun HomeScreen(viewModel: AnimeViewModel) {
    val latestUpdates by viewModel.latestUpdates.collectAsState()
    val trendingAnime by viewModel.trendingAnime.collectAsState()
    val historyAnime by viewModel.historyAnime.collectAsState()
    val scheduleState by viewModel.scheduleState.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

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

    val todaySchedule = remember(scheduleState, currentDayName) {
        if (scheduleState is UiState.Success) {
            val days = (scheduleState as UiState.Success).data
            days.find { it.day.equals(currentDayName, ignoreCase = true) }?.animeList ?: emptyList()
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color.Transparent)
    ) {
        HeaderWithBlurredBackground(
            trendingAnime = trendingAnime,
            viewModel = viewModel,
            onSearchClick = { viewModel.navigateTo(Screen.Search) },
            onAnimeClick = { viewModel.selectAnime(it.link) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        AnnouncementSection()
        
        Spacer(modifier = Modifier.height(24.dp))
        ContinueWatchingSection(
            latestAnime = if (historyAnime.isNotEmpty()) historyAnime.take(10) else ((latestUpdates as? UiState.Success)?.data?.shuffled()?.take(6) ?: emptyList()),
            onAnimeClick = { viewModel.selectAnime(it.link) },
            onHistoryClick = { viewModel.navigateTo(Screen.Library) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        GenresSection(onGenreClick = { genre ->
            viewModel.performSearch(genre)
            viewModel.navigateTo(Screen.Search)
        })
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (todaySchedule.isNotEmpty()) {
            TodayScheduleSection(
                scheduleData = todaySchedule,
                onAnimeClick = { anime ->
                    if (anime.link.contains("myanimelist.net")) {
                        viewModel.performSearch(anime.title)
                        viewModel.navigateTo(Screen.Search)
                    } else {
                        viewModel.selectAnime(anime.link)
                    }
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        when (val state = latestUpdates) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color(0xFFE53935))
                }
            }
            is UiState.Success -> {
                val allData = state.data
                val donghuaOnly = remember(allData) {
                    allData.filter { it.link.contains("anichin") || it.link.contains("donghub") || it.type == "Donghua" }
                }

                // Section Khusus Donghua (Anichin & Donghub)
                if (donghuaOnly.isNotEmpty()) {
                    DonghuaSection(
                        donghuaList = donghuaOnly,
                        onAnimeClick = { viewModel.selectAnime(it.link) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                val filteredData = remember(allData, selectedCategoryFilter) {
                    when (selectedCategoryFilter) {
                        "ANIME" -> allData.filter { !it.link.contains("anichin") && !it.link.contains("donghub") && it.type != "Donghua" }
                        "DONGHUA" -> donghuaOnly
                        else -> allData
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rilis Terbaru",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredData.size} Judul",
                            color = Color(0xFF00F2FE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter Chips Kategori: Semua, Anime, Donghua
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "🔥 Semua",
                            "ANIME" to "🇯🇵 Anime",
                            "DONGHUA" to "🇨🇳 3D Donghua"
                        ).forEach { (key, label) ->
                            val isSelected = selectedCategoryFilter == key
                            val bgBrush = if (isSelected) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF2A55), Color(0xFFFF5252))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgBrush)
                                    .clickable { selectedCategoryFilter = key }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (filteredData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada rilis yang ditemukan.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    AnimeGridSection(
                        animes = filteredData,
                        onAnimeClick = { viewModel.selectAnime(it.link) }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                RekomendasiFantasySection(
                    animes = trendingAnime,
                    onAnimeClick = { viewModel.selectAnime(it.link) }
                )
            }
            is UiState.Error -> {
                Text(
                    text = "Gagal memuat data rilis terbaru.",
                    color = Color.Gray,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> {}
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}
