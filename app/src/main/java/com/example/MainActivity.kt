package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.data.*
import com.example.ui.*
import com.example.models.ProfileAvatar
import com.example.ui.screens.ProfileScreen

import com.example.ui.theme.MyApplicationTheme
import com.example.utils.FormatUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.RenderProcessGoneDetail
import android.annotation.SuppressLint
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import android.widget.VideoView
import android.media.MediaPlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: AnimeViewModel = viewModel()
                val activeScreen by viewModel.activeScreen.collectAsState()

                BackHandler(enabled = activeScreen != Screen.Home) {
                    viewModel.goBack()
                }

                Scaffold(
                    bottomBar = {
                        AnimeBottomNavigation(viewModel = viewModel)
                    },
                    containerColor = Color(0xFF030000), // Deep black-red base
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0C0101), // Near black red
                                        Color(0xFF060000), // Pure very deep dark
                                        Color(0xFF000000)
                                    )
                                )
                            )
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        // Glowing red circles under standard glass to simulate backlight
                        Box(
                            modifier = Modifier
                                .size(320.dp)
                                .offset(x = (-60).dp, y = 40.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x3BFF2335),
                                            Color(0x02FF2335),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .blur(80.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(360.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 100.dp, y = 200.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x44FF2335),
                                            Color(0x02FF2335),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .blur(100.dp)
                        )

                        AnimatedContent(
                            targetState = activeScreen,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = FastOutSlowInEasing))) togetherWith
                                    (fadeOut(animationSpec = tween(220, easing = FastOutLinearInEasing)) +
                                        scaleOut(targetScale = 1.02f, animationSpec = tween(220, easing = FastOutLinearInEasing)))
                            },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                Screen.Home -> HomeScreen(viewModel = viewModel)
                                Screen.Search -> SearchScreen(viewModel = viewModel)
                                Screen.Catalog -> CatalogScreen(viewModel = viewModel)
                                Screen.Detail -> DetailScreen(viewModel = viewModel)
                                Screen.Episode -> EpisodeScreen(viewModel = viewModel)
                                Screen.MyList, Screen.Library -> MyListScreen(viewModel = viewModel)
                                Screen.Schedule -> ScheduleScreen(viewModel = viewModel)
                                Screen.Profile -> ProfileScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Modifier.redGlass(
    backgroundColor: Color = Color(0x2B100101), // Indahnya glass moseph merah-gelap
    borderColor: Color = Color(0x4CFF3B4F),     // Border merah menyala lembut semi-transparan
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .border(width = borderWidth, color = borderColor, shape = shape)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(viewModel: AnimeViewModel) {
    val animeDetailState by viewModel.animeDetail.collectAsState()
    val bookmarkedAnime by viewModel.bookmarkedAnime.collectAsState()
    val selectedUrl = viewModel.selectedAnimeUrl.value ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        when (val state = animeDetailState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(0.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(30.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(4.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(20.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(4.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(50))
                        SkeletonBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(50))
                    }
                }
            }
            is UiState.Success -> {
                val detail = state.data
                val isSaved = viewModel.isBookmarked(selectedUrl)

                val titleHash = detail.title.hashCode().let { if (it < 0) -it else it }
                val scheduleDay = when {
                    detail.title.contains("Battle Through", ignoreCase = true) -> "Minggu"
                    detail.title.contains("Throne of Seal", ignoreCase = true) -> "Kamis"
                    detail.title.contains("Renegade", ignoreCase = true) -> "Senin"
                    detail.title.contains("Slay", ignoreCase = true) -> "Selasa"
                    detail.title.contains("Against the Gods", ignoreCase = true) -> "Jumat"
                    else -> when (titleHash % 7) {
                        0 -> "Senin"
                        1 -> "Selasa"
                        2 -> "Rabu"
                        3 -> "Kamis"
                        4 -> "Jumat"
                        5 -> "Sabtu"
                        else -> "Minggu"
                    }
                }

                val sourceWork = when {
                    detail.title.contains("Battle", ignoreCase = true) ||
                    detail.title.contains("Throne", ignoreCase = true) ||
                    detail.title.contains("Renegade", ignoreCase = true) ||
                    detail.title.contains("Against", ignoreCase = true) -> "From Light Novel"
                    else -> "Manga Edition"
                }

                val studioName = when {
                    detail.title.contains("Battle", ignoreCase = true) -> "Larks Films"
                    detail.title.contains("Throne", ignoreCase = true) -> "Shenman Entertainment"
                    detail.title.contains("Renegade", ignoreCase = true) -> "Sparkly Key"
                    detail.title.contains("Slay", ignoreCase = true) -> "Sparkly Key"
                    else -> detail.studio.ifEmpty { "MAPPA" }
                }

                val releaseSeason = when {
                    detail.title.contains("Battle", ignoreCase = true) -> "Summer 2022"
                    detail.title.contains("Throne", ignoreCase = true) -> "Spring 2022"
                    else -> "Spring ${detail.released.ifEmpty { "2024" }}"
                }

                val viewsCountStr = "${(10 + titleHash % 150) / 10.0}M Views"
                val viewsProgressStr = "${3 + titleHash % 5}:${10 + titleHash % 48} / 20:21"
                val subscribersCountStr = "${(100 + titleHash % 800) / 10.0}K Subscriber"

                // Use regex that specifically targets "Episode X" or "Ep X"
                val epNumbers = detail.episodes.mapNotNull { ep ->
                    val match = Regex("(?:episode|ep)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(ep.title)
                    match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                }
                val latestEpNum = epNumbers.maxOrNull() ?: 1
                val lanjutEpNum = if (latestEpNum > 1) latestEpNum - 1 else 1
                val lanjutEpisode = detail.episodes.find { 
                    it.title.contains(lanjutEpNum.toString()) 
                } ?: detail.episodes.firstOrNull()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Profile/Hero banner layout (Large-height character portrait focus)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(560.dp)
                    ) {
                        ResolveAnimeImage(
                            url = detail.posterUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.5f to Color(0x990A0A0F),
                                            0.85f to Color(0xEB0A0A0F),
                                            1f to Color(0xFF0A0A0F)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Schedule Badge
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = "Schedule",
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Setiap $scheduleDay",
                                    color = Color(0xFFE50914),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            // Title
                            Text(
                                text = detail.title,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 38.sp
                            )

                            // Alternative Title
                            val cleanAlt = detail.title
                            Text(
                                text = cleanAlt,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            // Meta Chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Rating Chip with custom border/background styling
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFFFC107).copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = detail.rating,
                                            color = Color(0xFFFFC107),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val workType = if (selectedUrl.contains("anichin") || selectedUrl.contains("donghub")) "Donghua" else "Anime"
                                MetaBorderChip(text = workType)
                                MetaBorderChip(text = detail.status)
                                MetaBorderChip(text = sourceWork)
                                MetaBorderChip(text = releaseSeason)
                                MetaBorderChip(text = "Release ${detail.released}")
                                MetaBorderChip(text = "Confirm Eps ${detail.episodes.size}")
                                MetaBorderChip(text = "Duration ${detail.duration}")
                                MetaBorderChip(text = studioName)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic double actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { 
                                    if (lanjutEpisode != null) {
                                        viewModel.selectEpisode(lanjutEpisode.link)
                                    } else {
                                        detail.episodes.firstOrNull()?.let { 
                                            viewModel.selectEpisode(it.link)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF161622)
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "Lanjut",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$viewsProgressStr · $viewsCountStr",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.toggleBookmark(
                                        ScrapedAnime(
                                            title = detail.title,
                                            link = selectedUrl,
                                            imageUrl = detail.posterUrl,
                                            status = detail.status,
                                            score = detail.rating
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSaved) Color.White else Color(0xFFE50914)
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (isSaved) "Unsubscribe" else "Subscribe",
                                    color = if (isSaved) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = subscribersCountStr,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Horizontally scrollable genres
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detail.genres, key = { it }) { genre ->
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFE50914).copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(Color(0xFF14141B), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = genre,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Synopsis Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Sinopsis",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = detail.synopsis.ifEmpty { "Sinopsis tidak tersedia." },
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 22.sp,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Justify
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Episodes Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Semua Episode (${detail.episodes.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (detail.episodes.isEmpty()) {
                            Text("Tidak ada episode yang tersedia.", color = Color.Gray)
                        } else {
                            detail.episodes.forEach { episode ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { viewModel.selectEpisode(episode.link) },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14141D)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = FormatUtils.formatEpisodeTitle(episode.title),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (episode.date.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = episode.date,
                                                    color = Color.Gray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Stream",
                                            tint = Color(0xFFE50914),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Gagal memuat detail anime.", color = Color.White)
                }
            }
            else -> {}
        }

        // Beautiful back floating button overlaid
        IconButton(
            onClick = { viewModel.goBack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .size(42.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeScreen(viewModel: AnimeViewModel) {
    val episodeDetailState by viewModel.episodeDetail.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        when (val state = episodeDetailState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            }
            is UiState.Success -> {
                val detail = state.data
                val animeDetailState by viewModel.animeDetail.collectAsState()

                val autoSelectedEmbed = remember(detail.streamEmbeds) {
                    val embeds = detail.streamEmbeds
                    // Mencari server dengan kualitas tertinggi secara otomatis (1080p -> 720p -> HD -> Max)
                    embeds.find { it.serverName.contains("1080p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("720p", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("HD", ignoreCase = true) }
                        ?: embeds.find { it.serverName.contains("Max", ignoreCase = true) }
                        ?: embeds.firstOrNull()
                }

                var activeEmbedServer by remember(autoSelectedEmbed) { mutableStateOf(autoSelectedEmbed?.serverName ?: "") }
                var activeIframeUrl by remember(autoSelectedEmbed) { mutableStateOf(autoSelectedEmbed?.iframeUrl ?: "") }
                var isPlaying by remember(activeIframeUrl) { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    Spacer(modifier = Modifier.height(16.dp))

                    // Back & Title Screen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.goBack() },
                            modifier = Modifier
                                .background(Color(0xFF1E1E24), RoundedCornerShape(8.dp))
                                .size(40.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Streaming Area",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Video Player Representation Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val animeDetail = (animeDetailState as? UiState.Success)?.data
                        val episodesList = animeDetail?.episodes ?: emptyList()
                        val currentEpisodeIndex = episodesList.indexOfFirst { 
                            it.link == viewModel.selectedEpisodeUrl.value || detail.title.contains(it.title) 
                        }.coerceAtLeast(0)
                        val totalEpisodes = episodesList.size
                          
                        ExoVideoPlayer(
                            title = detail.title,
                            streamUrl = activeIframeUrl,
                            streamEmbeds = detail.streamEmbeds,
                            currentEpisodeIndex = currentEpisodeIndex,
                            totalEpisodes = totalEpisodes,
                            onEpisodeChange = { index ->
                                val targetEpisode = episodesList.getOrNull(index)
                                if (targetEpisode != null) {
                                    viewModel.selectEpisode(targetEpisode.link)
                                }
                            },
                            onBack = { viewModel.goBack() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = FormatUtils.formatEpisodeTitle(detail.title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Servers Select Box List
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Pilih Server Putar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.streamEmbeds.forEach { server ->
                            val isSelected = activeEmbedServer == server.serverName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF1E1E24))
                                    .clickable {
                                        activeEmbedServer = server.serverName
                                        activeIframeUrl = server.iframeUrl
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = server.serverName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Menu Episode (Quick Episode Navigation)
                    Text(
                        text = "Menu Episode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val animeDetail = (animeDetailState as? UiState.Success)?.data
                    if (animeDetail != null && animeDetail.episodes.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(animeDetail.episodes) { epItem ->
                                val isCurrentEp = detail.title.contains(epItem.title) || epItem.link == viewModel.selectedEpisodeUrl.value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrentEp) Color(0xFFE50914).copy(alpha = 0.2f) else Color(0xFF1E1E24))
                                        .border(1.dp, if (isCurrentEp) Color(0xFFE50914) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!isCurrentEp) {
                                                viewModel.selectEpisode(epItem.link)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = FormatUtils.formatEpisodeShort(epItem.title),
                                        color = if (isCurrentEp) Color(0xFFE50914) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Memuat daftar episode lainnya...", color = Color.Gray, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Downloader Mirror Lists
                    Text(
                        text = "Download Links (Anichin Mirrors)", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (detail.downloads.isEmpty()) {
                        Text("Tidak ada link download yang ditemukan.", color = Color.Gray)
                    } else {
                        detail.downloads.forEach { group ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = Color(0xFF16161E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color(0xFFA0A0B0), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = group.resolution, 
                                                color = Color.White, 
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE50914).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFE50914).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = group.format, 
                                                color = Color(0xFFE50914), 
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        group.links.forEach { linkItem ->
                                            var showLinkOptionDialog by remember { mutableStateOf(false) }
                                            
                                            if (showLinkOptionDialog) {
                                                AlertDialog(
                                                    onDismissRequest = { showLinkOptionDialog = false },
                                                    containerColor = Color(0xFF1E1E24),
                                                    titleContentColor = Color.White,
                                                    textContentColor = Color.LightGray,
                                                    title = {
                                                        Text(
                                                            text = "Opsi Tautan Media",
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = "Server: ${linkItem.host} (${group.resolution})",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 14.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Text(
                                                                text = linkItem.url,
                                                                color = Color.Gray,
                                                                fontSize = 11.sp,
                                                                maxLines = 2,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showLinkOptionDialog = false
                                                                try {
                                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                    val clip = android.content.ClipData.newPlainText("Download Link", linkItem.url)
                                                                    clipboard.setPrimaryClip(clip)
                                                                } catch (e: Exception) {
                                                                    // Gagal
                                                                }
                                                            }
                                                        ) {
                                                            Text("Salin Link", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                                                        }
                                                    },

                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF2A2A35).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = LocalIndication.current,
                                                        onClick = {
                                                            showLinkOptionDialog = true
                                                        }
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = linkItem.host,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Gagal memuat server streaming.", color = Color.White)
                }
            }
            else -> {}
        }
    }
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
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
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
                                view?.let {
                                    it.stopLoading()
                                    (it.parent as? android.view.ViewGroup)?.removeView(it)
                                    it.destroy()
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

@Composable
fun CatalogScreen(viewModel: AnimeViewModel) {
    val alphabet = listOf("#") + ('A'..'Z').map { it.toString() }
    val trailingAnime by viewModel.latestUpdates.collectAsState()
    var selectedCategoryTab by remember { mutableStateOf("ALL") }
    var selectedLetter by remember { mutableStateOf("#") }
    
    val catalogData = remember(trailingAnime, selectedCategoryTab, selectedLetter) {
        if (trailingAnime is UiState.Success) {
            val raw = (trailingAnime as UiState.Success).data
            val byCategory = when (selectedCategoryTab) {
                "ANIME" -> raw.filter { !it.link.contains("anichin") && !it.link.contains("donghub") && it.type != "Donghua" }
                "DONGHUA" -> raw.filter { it.link.contains("anichin") || it.link.contains("donghub") || it.type == "Donghua" }
                else -> raw
            }
            if (selectedLetter == "#") {
                byCategory
            } else {
                byCategory.filter { it.title.startsWith(selectedLetter, ignoreCase = true) }
            }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Katalog ",
                    color = Color(0xFF00F2FE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedLetter == "#") "A-Z" else "A-Z ($selectedLetter)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color(0xFF00F2FE), modifier = Modifier.clickable { viewModel.refreshData() })
                Spacer(modifier = Modifier.width(16.dp))
                Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF00F2FE), modifier = Modifier.clickable { viewModel.navigateTo(Screen.Search) })
            }
        }

        // Category Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "Semua",
                "ANIME" to "🇯🇵 Anime",
                "DONGHUA" to "🇨🇳 Donghua"
            ).forEach { (key, label) ->
                val isSelected = selectedCategoryTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFF7000FF) else Color(0x33FFFFFF))
                        .clickable { selectedCategoryTab = key }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                if (catalogData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada konten ditemukan", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 100.dp)
                    ) {
                        items(catalogData, key = { it.title + it.link }) { anime ->
                            CompactAnimeCard(anime = anime, onClick = { viewModel.selectAnime(anime.link) })
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(40.dp).padding(bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(alphabet, key = { it }) { letter ->
                    val isSelected = selectedLetter == letter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .redGlass(
                                backgroundColor = if (isSelected) Color(0x73FF3030) else Color(0x1F1A0202),
                                borderColor = if (isSelected) Color(0xFFFF5252) else Color(0x3BFF3355),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedLetter = letter }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
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

/*
// --- DRACIN TIKTOK-STYLE SCREEN COMPOSABLES ---

data class DracinFloatingHeart(
    val id: Long,
    val x: Float,
    val y: Float
)



@Composable
fun DracinPlayerPage(
    videoItem: DracinVideoItem,
    isActive: Boolean,
    onLikeToggle: () -> Unit,
    onSaveToggle: () -> Unit,
    onCommentAdded: (String) -> Unit,
    onEpisodeSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember(videoItem.activeEpisode.id) { mutableStateOf(true) }
    var showPlayIndicator by remember { mutableStateOf(false) }
    var indicatorIcon by remember { mutableStateOf(Icons.Filled.PlayArrow) }
    var hearts by remember { mutableStateOf(listOf<DracinFloatingHeart>()) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var isFollowed by remember { mutableStateOf(false) }
    var showToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showToastMessage) {
        if (showToastMessage != null) {
            delay(2000)
            showToastMessage = null
        }
    }

    // Heart Spawning Animation Effect
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Video View Area with click events
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoItem.activeEpisode.id) {
                    detectTapGestures(
                        onTap = {
                            isPlaying = !isPlaying
                            indicatorIcon = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause
                            showPlayIndicator = true
                        },
                        onDoubleTap = { offset ->
                            val newHeart = DracinFloatingHeart(
                                id = System.currentTimeMillis(),
                                x = offset.x,
                                y = offset.y
                            )
                            hearts = hearts + newHeart
                            if (!videoItem.isLiked) {
                                onLikeToggle()
                            }
                        }
                    )
                }
        ) {
            if (isActive) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                if (isPlaying) {
                                    start()
                                }
                            }
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        val activeUri = Uri.parse(videoItem.activeEpisode.videoUrl)
                        val currentTag = view.tag as? String
                        if (currentTag != videoItem.activeEpisode.videoUrl) {
                            view.setVideoURI(activeUri)
                            view.tag = videoItem.activeEpisode.videoUrl
                        }
                        if (isPlaying) {
                            view.start()
                        } else {
                            view.pause()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Background Poster placeholder when page is inactive
                ResolveAnimeImage(
                    url = videoItem.series.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Dark subtle vignette gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )
        }

        // 1. Center Play/Pause Overlay Animation Indicator
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val scaleAnim = remember { Animatable(0.4f) }
            val alphaAnim = remember { Animatable(0f) }

            LaunchedEffect(showPlayIndicator) {
                if (showPlayIndicator) {
                    scaleAnim.snapTo(0.4f)
                    alphaAnim.snapTo(0.8f)
                    scaleAnim.animateTo(1.3f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    alphaAnim.animateTo(0f, animationSpec = tween(200, delayMillis = 150))
                    showPlayIndicator = false
                }
            }

            if (alphaAnim.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = scaleAnim.value,
                            scaleY = scaleAnim.value,
                            alpha = alphaAnim.value
                        )
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = indicatorIcon,
                        contentDescription = "Playback Feedback",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // 2. Double-Tap Floating Hearts Overlay
        hearts.forEach { heart ->
            key(heart.id) {
                val scale = remember { Animatable(0f) }
                val translationY = remember { Animatable(0f) }
                val alpha = remember { Animatable(1f) }

                LaunchedEffect(heart.id) {
                    launch { scale.animateTo(1.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    launch { translationY.animateTo(-280f, animationSpec = tween(800, easing = LinearEasing)) }
                    launch {
                        delay(500)
                        alpha.animateTo(0f, animationSpec = tween(300))
                        hearts = hearts.filter { it.id != heart.id }
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(LocalDensity.current) { heart.x.toDp() } - 24.dp,
                            y = with(LocalDensity.current) { heart.y.toDp() } - 24.dp + translationY.value.dp
                        )
                        .graphicsLayer(
                            scaleX = scale.value,
                            scaleY = scale.value,
                            alpha = alpha.value
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Floating Heart",
                        tint = Color(0xFFFF2B54),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // 3. Bottom-Left Information Overlay (Title, Synopsis, Tags, Episodes)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.82f)
                .padding(start = 16.dp, bottom = 120.dp) // Pushed above navigation and episode row
        ) {
            // Tags Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                videoItem.series.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tag,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Publisher & Series Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${videoItem.series.publisher}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFFF2B54), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Eps ${videoItem.activeEpisode.episodeNumber}/${videoItem.series.totalEpisodes}",
                    color = Color(0xFFFF2B54),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Episode Title Bold
            Text(
                text = "${videoItem.series.title}: ${videoItem.activeEpisode.title}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Expandable Synopsis
            Text(
                text = videoItem.series.synopsis,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = if (synopsisExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { synopsisExpanded = !synopsisExpanded }
                    .animateContentSize()
            )
            
            Text(
                text = if (synopsisExpanded) "Sembunyikan" else "Selengkapnya...",
                color = Color(0xFFFF2B54),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { synopsisExpanded = !synopsisExpanded }
                    .padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Episode Selector Row
            Text(
                text = "Pilih Episode:",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(videoItem.series.episodes) { ep ->
                    val isCurrent = ep.episodeNumber == videoItem.activeEpisode.episodeNumber
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isCurrent) Color(0xFFFF2B54) else Color.Black.copy(alpha = 0.6f)
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (!isCurrent) {
                                    onEpisodeSelected(ep.episodeNumber)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Eps ${ep.episodeNumber}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Right Side Interactive Button Column (Avatar, Like, Bookmark, Comment, Share)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Publisher Avatar Circle with Follow Button
            Box(
                modifier = Modifier.padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clip(CircleShape)
                ) {
                    ResolveAnimeImage(
                        url = videoItem.series.publisherAvatar,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Follow Plus Button Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isFollowed,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(20.dp)
                            .background(Color(0xFFFF2B54), CircleShape)
                            .clickable {
                                isFollowed = true
                                showToastMessage = "Mengikuti @${videoItem.series.publisher}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Follow",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Like Action Circle
            DracinActionButton(
                icon = if (videoItem.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (videoItem.isLiked) Color(0xFFFF2B54) else Color.White,
                label = videoItem.series.likesCount,
                onClick = onLikeToggle,
                isAnimated = videoItem.isLiked
            )

            // Bookmark/Save Action Circle
            DracinActionButton(
                icon = if (videoItem.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                tint = if (videoItem.isSaved) Color(0xFFFFC107) else Color.White,
                label = if (videoItem.isSaved) "Tersimpan" else "Simpan",
                onClick = onSaveToggle,
                isAnimated = videoItem.isSaved
            )

            // Comments Circle
            DracinActionButton(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = Color.White,
                label = videoItem.comments.size.toString(),
                onClick = { showCommentsSheet = true }
            )

            // Share Action Circle
            DracinActionButton(
                icon = Icons.Filled.Share,
                tint = Color.White,
                label = "Share",
                onClick = {
                    showToastMessage = "Tautan drama berhasil disalin ke clipboard!"
                }
            )
        }

        // 5. Sliding Comments Drawer Panel Overlay
        AnimatedVisibility(
            visible = showCommentsSheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
        ) {
            DracinCommentsDrawer(
                comments = videoItem.comments,
                onClose = { showCommentsSheet = false },
                onAddComment = { text ->
                    onCommentAdded(text)
                }
            )
        }

        // 6. Custom Overlay Message (Replaces Toast)
        androidx.compose.animation.AnimatedVisibility(
            visible = showToastMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = showToastMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DracinActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    isAnimated: Boolean = false
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isAnimated) {
        if (isAnimated) {
            scale.animateTo(1.4f, animationSpec = tween(150))
            scale.animateTo(1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DracinCommentsDrawer(
    comments: List<DracinComment>,
    onClose: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var textState by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF151518))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Komentar (${comments.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Comments List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada komentar. Jadilah yang pertama!",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp, start = 20.dp, end = 20.dp, top = 12.dp)
                    ) {
                        items(comments) { comment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Mini avatar or custom initial avatar
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFF2B54).copy(alpha = 0.15f), CircleShape)
                                        .border(0.5.dp, Color(0xFFFF2B54).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = comment.username.take(2).uppercase(),
                                        color = Color(0xFFFF2B54),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = comment.username,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = comment.timestamp,
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comment.text,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar Row at bottom of the Drawer panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F12))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = {
                        Text(
                            text = "Tambahkan komentar publik...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            onAddComment(textState)
                            textState = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFFF2B54),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Kirim",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
*/
