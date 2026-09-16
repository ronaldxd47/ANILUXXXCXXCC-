package com.example.ui.components

import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.pager.*
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import android.webkit.RenderProcessGoneDetail


import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.utils.FormatUtils
import kotlinx.coroutines.*

@Composable
fun ResolveAnimeImage(
    url: String, 
    modifier: Modifier = Modifier, 
    contentScale: ContentScale = ContentScale.Crop, 
    contentDescription: String? = null
) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(300)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    )
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

@Composable
fun TopBar(viewModel: AnimeViewModel, onSearchClick: () -> Unit) {
    val currentName by viewModel.userName.collectAsState()
    val avatarId by viewModel.userAvatarId.collectAsState()

    val avatarGradients = listOf(
        listOf(Color(0xFF7000FF), Color(0xFF00F2FE)), // Shadow Monarch
        listOf(Color(0xFFFF3366), Color(0xFFFF9900)), // Straw Hat
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF)), // Six Eyes
        listOf(Color(0xFFFFB300), Color(0xFFF57C00)), // Dragon Sovereign
        listOf(Color(0xFF00E676), Color(0xFF1DE9B6)), // Shinobi
        listOf(Color(0xFFFF007F), Color(0xFF7928CA))  // Cosmic
    )
    val currentGrad = avatarGradients.getOrElse(avatarId % avatarGradients.size) { avatarGradients[0] }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .clickable { viewModel.navigateTo(Screen.Profile) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(currentGrad))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hai,", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFB300), Color(0xFFFF7043))
                                )
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("VIP", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    text = currentName, 
                    color = Color.White, 
                    fontSize = 15.sp, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isRefreshing = viewModel.latestUpdates.collectAsState().value is UiState.Loading
            val infiniteTransition = rememberInfiniteTransition(label = "refresh")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isRefreshing) 360f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spin"
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x28FFFFFF))
                    .border(1.dp, Color(0x3300F2FE), CircleShape)
                    .clickable { viewModel.refreshData() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF00F2FE),
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isRefreshing) Modifier.rotate(angle) else Modifier)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x28FFFFFF))
                    .border(1.dp, Color(0x33FF2A55), CircleShape)
                    .clickable { onSearchClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = Color(0xFFFF3366),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeaderWithBlurredBackground(
    trendingAnime: List<ScrapedAnime>,
    viewModel: AnimeViewModel,
    onSearchClick: () -> Unit,
    onAnimeClick: (ScrapedAnime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { if (trendingAnime.isEmpty()) 1 else minOf(trendingAnime.size, 6) })
    val activeAnime = if (trendingAnime.isNotEmpty()) trendingAnime.getOrNull(pagerState.currentPage) else null
    val backdropUrl = activeAnime?.imageUrl ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // Dynamic Blurred Backdrop from active Carousel poster
        if (backdropUrl.isNotEmpty()) {
            AnimatedContent(
                targetState = backdropUrl,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                },
                label = "HeaderBackdrop"
            ) { targetUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                ) {
                    ResolveAnimeImage(
                        url = targetUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp)
                            .graphicsLayer { alpha = 0.45f },
                        contentScale = ContentScale.Crop
                    )
                    
                    // Dark ambient gradient overlays for high legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0xFF040404).copy(alpha = 0.65f),
                                    0.3f to Color(0xFF040404).copy(alpha = 0.35f),
                                    0.8f to Color(0xFF040404).copy(alpha = 0.85f),
                                    1.0f to Color(0xFF040404)
                                )
                            )
                    )
                }
            }
        }

        // Foreground Header & Carousel Content
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.statusBarsPadding())
            TopBar(viewModel = viewModel, onSearchClick = onSearchClick)
            Spacer(modifier = Modifier.height(8.dp))
            if (trendingAnime.isNotEmpty()) {
                HeroCarousel(
                    trendingAnime = trendingAnime,
                    viewModel = viewModel,
                    onAnimeClick = onAnimeClick,
                    pagerState = pagerState
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    trendingAnime: List<ScrapedAnime>,
    viewModel: AnimeViewModel,
    onAnimeClick: (ScrapedAnime) -> Unit,
    pagerState: PagerState = rememberPagerState(pageCount = { if (trendingAnime.isEmpty()) 1 else minOf(trendingAnime.size, 6) })
) {
    if (trendingAnime.isEmpty()) return
    
    LaunchedEffect(pagerState.currentPage) {
        kotlinx.coroutines.delay(4000) // Auto-scroll every 4 seconds
        var newPosition = pagerState.currentPage + 1
        if (newPosition >= pagerState.pageCount) newPosition = 0
        pagerState.animateScrollToPage(newPosition)
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // High-Fidelity Trending Now Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF2A55)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF2A55),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Trending Pekan Ini",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0x33FF2A55), Color(0x337000FF))))
                    .border(1.dp, Color(0x66FF2A55), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HOT 🔥",
                    color = Color(0xFFFF5252),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 14.dp
        ) { page ->
            val anime = trendingAnime[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val scale = 1f - (kotlin.math.abs(pageOffset) * 0.05f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable { onAnimeClick(anime) }
            ) {
                ResolveAnimeImage(
                    url = anime.imageUrl,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Cinematic dynamic gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to Color(0x33000000),
                                0.7f to Color(0xCC04060A),
                                1.0f to Color(0xF704060A)
                            )
                        )
                )
                
                // Dynamic Rank Badge top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF2A55), Color(0xFFFF6B00))
                            )
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Leaderboard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TOP #${page + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Text & Play Button content at bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val viewsValue = viewModel.getViewCount(anime.link)
                            val viewsText = viewModel.formatViewCount(viewsValue)

                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Views",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = viewsText,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val isDonghua = anime.link.contains("anichin") || anime.link.contains("donghub")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDonghua) Color(0xFF7000FF).copy(alpha = 0.3f) else Color(0xFFFF2A55).copy(alpha = 0.3f))
                                    .border(0.5.dp, if (isDonghua) Color(0xFF7000FF) else Color(0xFFFF2A55), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isDonghua) "3D DONGHUA" else "ANIME JP",
                                    color = if (isDonghua) Color(0xFFD4B0FF) else Color(0xFFFF8FA3),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = anime.status.ifEmpty { "ONGOING" },
                                color = Color(0xFF00F2FE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Floating Glass Play Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF2A55), Color(0xFFFF5252))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nonton",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Fluid Animated Pager indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val width by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "indicator_width"
                )
                val color by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFFF2A55) else Color(0x33FFFFFF),
                    label = "indicator_color"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(5.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun AnnouncementSection() {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFB300)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pemberitahuan",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0x3B250810), Color(0x3B100620))
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0x66FF2A55), Color(0x667000FF))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📢",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = "Streaming lancar & update tercepat setiap hari. Selamat menonton! ✨",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GenresSection(onGenreClick: (String) -> Unit) {
    val genres = listOf(
        "🔥 Semua", 
        "⚔️ 3D Donghua", 
        "🇯🇵 Anime", 
        "⚡ Aksi", 
        "🌸 Romantis", 
        "✨ Isekai", 
        "🥋 Kultivasi", 
        "🎬 Movie"
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Eksplor Kategori",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(genres, key = { it }) { genre ->
                var isPressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "genre_scale"
                )
                val cleanGenre = genre.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0x44FF2A55), Color(0x2200F2FE))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current
                        ) {
                            onGenreClick(if (cleanGenre.equals("Semua", ignoreCase = true)) "" else cleanGenre)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = genre,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CompactAnimeCard(anime: ScrapedAnime, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141824))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            ResolveAnimeImage(
                url = anime.imageUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dynamic cinematic gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.75f to Color(0xAA080B12),
                            1.0f to Color(0xF5080B12)
                        )
                    )
            )

            // Top Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isDonghua = anime.link.contains("anichin") || anime.link.contains("donghub") || anime.type == "Donghua"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isDonghua) Brush.horizontalGradient(listOf(Color(0xFF7000FF), Color(0xFF9D4EDD)))
                            else Brush.horizontalGradient(listOf(Color(0xFFFF2A55), Color(0xFFFF6B00)))
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isDonghua) "3D" else "ANIME",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Score Badge with glass pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .border(0.5.dp, Color(0x88FFC107), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Score",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (anime.score.isNotEmpty()) anime.score else "7.8",
                            color = Color(0xFFFFE082),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Episode pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xD90D1017))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (anime.episode.isNotEmpty()) FormatUtils.formatEpisodeShort(anime.episode) else "Sub Indo",
                    color = Color(0xFF00F2FE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(7.dp))
        
        Text(
            text = anime.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AnimeGridSection(animes: List<ScrapedAnime>, onAnimeClick: (ScrapedAnime) -> Unit) {
    val chunkedList = animes.chunked(3)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        chunkedList.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { anime ->
                    Box(modifier = Modifier.weight(1f)) {
                        CompactAnimeCard(anime, onClick = { onAnimeClick(anime) })
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun RekomendasiFantasySection(animes: List<ScrapedAnime>, onAnimeClick: (ScrapedAnime) -> Unit) {
    if (animes.isEmpty()) return
    Column {
        Text(
            text = "Rekomendasi Pilihan",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(animes, key = { it.title + it.link }) { anime ->
                Box(modifier = Modifier.width(115.dp)) {
                    CompactAnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingSection(latestAnime: List<ScrapedAnime>, onAnimeClick: (ScrapedAnime) -> Unit, onHistoryClick: () -> Unit) {
    if (latestAnime.isEmpty()) return
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onHistoryClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x3300F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lanjutkan Nonton",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Semua Riwayat", color = Color(0xFF00F2FE), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(latestAnime, key = { it.title + it.link }) { anime ->
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onAnimeClick(anime) }
                ) {
                    ResolveAnimeImage(
                        url = anime.imageUrl,
                        contentDescription = anime.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.2f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.92f)
                                )
                            )
                    )
                    // Floating Center Play Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (anime.episode.isNotEmpty()) FormatUtils.formatEpisodeShort(anime.episode) else "Lanjut", 
                            color = Color(0xFF00F2FE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = anime.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonghuaSection(
    donghuaList: List<com.example.data.ScrapedAnime>,
    onAnimeClick: (com.example.data.ScrapedAnime) -> Unit
) {
    if (donghuaList.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🇨🇳 Donghua Rilis Terbaru",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF8E24AA))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Anichin", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(donghuaList, key = { it.link + it.title }) { item ->
                CompactAnimeCard(anime = item, onClick = { onAnimeClick(item) })
            }
        }
    }
}

@Composable
fun TodayScheduleSection(scheduleData: List<com.example.data.ScheduleAnime>, onAnimeClick: (com.example.data.ScheduleAnime) -> Unit) {
    if (scheduleData.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        PaddingValues(horizontal = 24.dp).let { padding ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rilis Hari Ini",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(scheduleData) { anime ->
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onAnimeClick(anime) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data("https://via.placeholder.com/300x450?text=MAL+Anime")
                                .crossfade(true)
                                .build(),
                            contentDescription = anime.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                        startY = 100f
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color(0xFFE53935), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HARI INI",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = anime.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HeroSection(onMoreInfoClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
    ) {
        ResolveAnimeImage(
            url = "https://samehadaku.email/wp-content/uploads/2023/10/Jujutsu-Kaisen-S2-720x405.jpg",
            contentDescription = "Anime Hero Banner",
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0.3f to Color.Transparent,
                                1.0f to Color(0xFF0A0A0F)
                            )
                        )
                    }
                },
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NEO TOKYO ZERO",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        blurRadius = 8f
                    )
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sci-Fi • Action • Mecha • 2026",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.LightGray,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { onMoreInfoClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE50914), // Netflix Accent Red
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Putar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = { onMoreInfoClick() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = "More Info", tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Info Detail", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun HorizontalAnimeRow(
    title: String,
    list: List<ScrapedAnime>,
    onAnimeClick: (ScrapedAnime) -> Unit
) {
    Column {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(list, key = { it.title + it.link }) { anime ->
                AnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun AnimePosterCard(anime: ScrapedAnime, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "poster_scale"
    )

    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = Color(0xFF16161E),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.02f))
                )
            )
        ) {
            Box {
                ResolveAnimeImage(
                    url = anime.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Bottom Shadow Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )

                if (anime.episode.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF2A55), Color(0xFFFF5252))
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatEpisodeShort(anime.episode),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (anime.score.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(0.5.dp, Color(0x88FFC107), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Score",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = anime.score,
                                color = Color(0xFFFFE082),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = anime.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GenreGridCard(onClickGenre: (String) -> Unit) {
    val genres = listOf("Aksi", "Fantasi", "Mecha", "Romantis", "Sci-Fi", "Isekai", "Harem", "Petualangan")
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "Populer Kategori",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(vertical = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(180.dp),
            userScrollEnabled = false
        ) {
            items(genres, key = { it }) { genre ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E24))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .clickable { onClickGenre(genre) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = genre,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MetaBorderChip(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color(0xFF7000FF).copy(alpha = 0.5f),
                shape = RoundedCornerShape(50)
            )
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = alpha))
    )
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    val cleanUrl = remember(url) {
        var u = url.trim()
        if (u.startsWith("//")) {
            "https:$u"
        } else if (u.startsWith("/")) {
            if (u.contains("anichin")) {
                "https://anichin.co$u"
            } else if (u.contains("donghub")) {
                "https://donghub.org$u"
            } else {
                "https://anichin.co$u"
            }
        } else if (!u.startsWith("http")) {
            "https://$u"
        } else {
            u
        }
    }
    var isLoading by remember(cleanUrl) { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(cleanUrl) {
        onDispose {
            try {
                webViewInstance?.let { wv ->
                    webViewInstance = null
                    wv.stopLoading()
                    wv.webChromeClient = null
                    wv.webViewClient = object : WebViewClient() {}
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.onPause()
                    wv.postDelayed({
                        try {
                            wv.destroy()
                        } catch (e: Exception) {}
                    }, 200)
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerWebView", "Error disposing WebView in AllComponents: ${e.message}")
            }
            webViewInstance = null
        }
    }

    // For Fullscreen Video
    var isFullscreen by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    val context = LocalContext.current

    DisposableEffect(isFullscreen) {
        val activity = context.findActivity()
        if (activity != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (isFullscreen) {
                // Hide System Bars and enter immersive mode
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                // Show System Bars
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {
            if (isFullscreen) {
                 val act = context.findActivity()
                 if (act != null) {
                     val insetsController = androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                     insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                     act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                 }
            }
        }
    }

    // Intercept back button when in fullscreen
    BackHandler(enabled = isFullscreen) {
        customViewCallback?.onCustomViewHidden()
        isFullscreen = false
        customView = null
    }

    if (isFullscreen && customView != null) {
        // Render fullscreen View using AndroidView
        AndroidView(
            factory = { _ -> customView!! },
            modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {} 
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            mediaPlaybackRequiresUserGesture = false
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            // Memaksa player memberikan resolusi tertinggi (1080p / Max) dengan UA Desktop
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onShowCustomView(
                                view: android.view.View?,
                                callback: CustomViewCallback?
                            ) {
                                customView = view
                                customViewCallback = callback
                                isFullscreen = true
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback = null
                                isFullscreen = false
                            }
                        }
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            android.util.Log.w("VideoPlayerWebView", "Render process gone in VideoPlayerWebView (didCrash=${detail?.didCrash()})")
                            try {
                                view?.let { wv ->
                                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                    wv.destroy()
                                }
                            } catch (e: Exception) {}
                            isLoading = false
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            val cleanUiJs = """
                                (function() {
                                    try {
                                        var style = document.createElement('style');
                                        style.innerHTML = `
                                            video::-webkit-media-controls { display: none !important; }
                                            video::-webkit-media-controls-enclosure { display: none !important; }
                                            .jw-controls, .jw-controlbar, .jw-overlays, .jw-display-icon-container, .jw-preview,
                                            .vjs-control-bar, .vjs-big-play-button, .vjs-loading-spinner,
                                            .plyr__controls, .plyr__captions, .plyr__control,
                                            .player-controls, .controls, .control-bar, .watermark, .logo,
                                            .header, .footer, .ads, .ad, div[id*="ad"], div[class*="ad"] { display: none !important; }
                                            html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; }
                                            video { width: 100% !important; height: 100% !important; object-fit: contain !important; position: absolute !important; top: 0 !important; left: 0 !important; z-index: 9999 !important; }
                                        `;
                                        document.head.appendChild(style);
                                        var v = document.querySelector('video');
                                        if (v) {
                                            v.style.width = '100%';
                                            v.style.height = '100%';
                                            v.play().catch(function(e){});
                                        }
                                    } catch(e){}
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(cleanUiJs, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val requestedUrl = request?.url?.toString() ?: ""
                            val initialUri = android.net.Uri.parse(cleanUrl)
                            val initialHost = initialUri.host ?: ""
                            val requestedUri = android.net.Uri.parse(requestedUrl)
                            val requestedHost = requestedUri.host ?: ""
                            
                            // Always allow the same host
                            if (initialHost.isNotEmpty() && (requestedHost.contains(initialHost) || initialHost.contains(requestedHost))) {
                                return false
                            }
                            
                            // Allow common stream/view/cdn services
                            val allowedHosts = listOf(
                                "blogger", "blogspot", "google", "youtube", "samehadaku", "anichin",
                                "wibufile", "mega", "dood", "krakenfiles", "pixeldrain", 
                                "mediafire", "facebook", "vimeo", "ok.ru", "yourupload", 
                                "mp4upload", "fembed", "gdrive", "drive", "player", "embed",
                                "stream", "cdn"
                            )
                            if (allowedHosts.any { requestedHost.contains(it) || requestedUrl.contains(it) }) {
                                return false
                            }
                            
                            // Block obvious ads / adult / betting links
                            val adPatterns = listOf(
                                "adsystem", "onclick", "popunder", "slot", "bet", "casino", 
                                "vulkan", "doubleclick", "ads", "score", "game", "promo", 
                                "tracker", "redirect"
                            )
                            if (adPatterns.any { requestedUrl.contains(it, ignoreCase = true) }) {
                                return true
                            }
                            
                            // If it doesn't start with http/https, handle it via external intent
                            if (!requestedUrl.startsWith("http")) {
                                try {
                                    val intent = android.content.Intent.parseUri(requestedUrl, android.content.Intent.URI_INTENT_SCHEME)
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {}
                                return true
                            }
                            
                            // Default back to allow so players do not break, but filter popups
                            return false
                        }
                    }
                    
                    tag = cleanUrl
                    val isDirectVideo = cleanUrl.endsWith(".mp4", ignoreCase = true) || 
                                        cleanUrl.contains(".mp4?", ignoreCase = true) || 
                                        cleanUrl.contains("commondatastorage.googleapis.com", ignoreCase = true) || 
                                        cleanUrl.endsWith(".m3u8", ignoreCase = true)
                    if (isDirectVideo) {
                        val videoHtml = """
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body { margin: 0; padding: 0; background-color: black; display: flex; justify-content: center; align-items: center; height: 100vh; width: 100vw; overflow: hidden; }
                                    video { width: 100%; height: 100%; max-width: 100%; max-height: 100%; object-fit: contain; }
                                </style>
                            </head>
                            <body>
                                <video controls autoplay name="media">
                                    <source src="$cleanUrl" type="video/mp4">
                                </video>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL(null, videoHtml, "text/html", "UTF-8", null)
                    } else {
                        loadUrl(cleanUrl)
                    }
                    webViewInstance = this
                }
            },
            update = { webView ->
                val lastLoadedUrl = webView.tag as? String
                if (lastLoadedUrl != cleanUrl) {
                    webView.tag = cleanUrl
                    val isDirectVideo = cleanUrl.endsWith(".mp4", ignoreCase = true) || 
                                        cleanUrl.contains(".mp4?", ignoreCase = true) || 
                                        cleanUrl.contains("commondatastorage.googleapis.com", ignoreCase = true) || 
                                        cleanUrl.endsWith(".m3u8", ignoreCase = true)
                    if (isDirectVideo) {
                        val videoHtml = """
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body { margin: 0; padding: 0; background-color: black; display: flex; justify-content: center; align-items: center; height: 100vh; width: 100vw; overflow: hidden; }
                                    video { width: 100%; height: 100%; max-width: 100%; max-height: 100%; object-fit: contain; }
                                </style>
                            </head>
                            <body>
                                <video controls autoplay name="media">
                                    <source src="$cleanUrl" type="video/mp4">
                                </video>
                            </body>
                            </html>
                        """.trimIndent()
                        webView.loadDataWithBaseURL(null, videoHtml, "text/html", "UTF-8", null)
                    } else {
                        webView.loadUrl(cleanUrl)
                    }
                }
            },
            onReset = { webView ->
                try {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (window.hls) { window.hls.destroy(); window.hls = null; }
                                var v = document.querySelector('video');
                                if (v) { v.pause(); v.removeAttribute('src'); v.load(); }
                            } catch(e){}
                        })();
                        """.trimIndent(), null
                    )
                    webView.stopLoading()
                    webView.onPause()
                } catch (e: Exception) {}
            },
            onRelease = { webView ->
                try {
                    webView.stopLoading()
                    webView.webChromeClient = null
                    webView.webViewClient = object : WebViewClient() {}
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    webView.onPause()
                    webView.postDelayed({
                        try {
                            webView.destroy()
                        } catch (e: Exception) {}
                    }, 200)
                } catch (e: Exception) {}
                webViewInstance = null
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        }
    }
}
}




@Composable
fun AnimeBottomNavigation(viewModel: AnimeViewModel) {
    val activeScreen by viewModel.activeScreen.collectAsState()
    
    val items = listOf(
        Triple(Screen.Home, "Home", Icons.Outlined.Home),
        Triple(Screen.Schedule, "Jadwal", Icons.Outlined.DateRange),
        Triple(Screen.Catalog, "Katalog", Icons.AutoMirrored.Filled.List),
        Triple(Screen.Library, "Koleksi", Icons.Outlined.BookmarkBorder),
        Triple(Screen.Profile, "Profil", Icons.Outlined.Person) 
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE610131E))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0x55FF2A55), Color(0x3300F2FE))
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, label, icon) ->
                val isSelected = when (label) {
                    "Home" -> activeScreen == Screen.Home || activeScreen == Screen.Detail || activeScreen == Screen.Episode
                    "Jadwal" -> activeScreen == Screen.Schedule
                    "Katalog" -> activeScreen == Screen.Catalog || activeScreen == Screen.Search
                    "Koleksi" -> activeScreen == Screen.Library || activeScreen == Screen.MyList
                    "Profil" -> activeScreen == Screen.Profile
                    else -> false
                }

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "nav_icon_scale"
                )

                val pillBg = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(Color(0x33FF2A55), Color(0x337000FF))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Transparent)
                    )
                }

                val tint = if (isSelected) Color(0xFFFF2A55) else Color(0xFF8E99AC)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { 
                                viewModel.navigateTo(screen) 
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label, 
                            color = tint, 
                            fontSize = 10.sp, 
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// --- DRACIN TIKTOK-STYLE SCREEN COMPOSABLES ---





fun Modifier.redGlass(
    backgroundColor: Color = Color(0x2B100101), // Indahnya glass moseph merah-gelap
    borderColor: Color = Color(0x4CFF3B4F),     // Border merah menyala lembut semi-transparan
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .border(width = borderWidth, color = borderColor, shape = shape)

