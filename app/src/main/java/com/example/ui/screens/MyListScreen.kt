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
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.data.*
import com.example.ui.components.*


@Composable
fun MyListScreen(viewModel: AnimeViewModel) {
    val bookmarkedAnime by viewModel.bookmarkedAnime.collectAsState()
    val historyAnime by viewModel.historyAnime.collectAsState()
    var selectedTab by remember { mutableStateOf("Favorite") }

    val displayData = if (selectedTab == "Favorite") bookmarkedAnime else historyAnime

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    color = Color(0xFF00F2FE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Delete, 
                        contentDescription = "Clear All", 
                        tint = Color.Gray, 
                        modifier = Modifier.clickable { viewModel.clearAllHistoryAndFavorites() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color(0xFF00F2FE), modifier = Modifier.clickable { viewModel.refreshData() })
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF00F2FE), modifier = Modifier.clickable { viewModel.navigateTo(Screen.Search) })
                }
            }
            
            Row(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selectedTab == "Favorite") Color(0xFF7000FF) else Color(0xFF1B1E2E))
                        .clickable { selectedTab = "Favorite" }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Favorite", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selectedTab == "History") Color(0xFF7000FF) else Color(0xFF1B1E2E))
                        .clickable { selectedTab = "History" }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            
            if (displayData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                    Text("No ${selectedTab}s yet", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayData, key = { it.title + it.link }) { anime ->
                        CompactAnimeCard(anime = anime, onClick = { viewModel.selectAnime(anime.link) })
                    }
                }
            }
        }
    }
}
