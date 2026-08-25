package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.ui.components.*



@Composable
fun SearchScreen(viewModel: AnimeViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val searchResultsState by viewModel.searchResults.collectAsState()
    var selectedSource by remember { mutableStateOf("Semua") }
    val sources = listOf("Semua", "Samehadaku", "Anichin", "Donghub")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Temukan Anime",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 28.sp
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Premium Search Outlined Box
        OutlinedTextField(
            value = query,
            onValueChange = { 
                viewModel.performSearch(it) 
            },
            placeholder = { Text("Ketik judul anime...", color = Color.Gray) },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color.Gray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.performSearch("") }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE50914),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color(0xFF1E1E24),
                unfocusedContainerColor = Color(0xFF1E1E24)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Source Filters Chips Row (Only visible if searching or ready to search)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sources.forEach { sourceName ->
                val isSelected = selectedSource == sourceName
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFF7000FF) else Color(0xFF121520),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedSource = sourceName }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = sourceName,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Search Content Box
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (query.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie, 
                        contentDescription = "Search", 
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Mulai ketik judul anime atau donghua...",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                when (val state = searchResultsState) {
                    is UiState.Loading -> {
                        // Skeletons grid loading
                        LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(6) {
                                Column {
                                    SkeletonBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SkeletonBox(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Success -> {
                        val filteredList = remember(state.data, selectedSource) {
                            val rawList = state.data
                            when (selectedSource) {
                                "Samehadaku" -> rawList.filter { !it.link.contains("anichin") && !it.link.contains("donghub") }
                                "Anichin" -> rawList.filter { it.link.contains("anichin") }
                                "Donghub" -> rawList.filter { it.link.contains("donghub") }
                                else -> rawList
                            }
                        }

                        if (filteredList.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = "No Results",
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Tidak ditemukan kecocokan dengan filter '$selectedSource'.",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(filteredList, key = { it.title + it.link }) { anime ->
                                    CompactAnimeCard(
                                        anime = anime,
                                        onClick = { viewModel.selectAnime(anime.link) }
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
