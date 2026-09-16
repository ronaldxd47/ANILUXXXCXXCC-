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
import com.example.ui.components.*

import com.example.ui.screens.SearchScreen

import com.example.ui.screens.EpisodeScreen

import com.example.ui.screens.DetailScreen



import com.example.models.ProfileAvatar
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.*

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
        try {
            val cacheJs = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!cacheJs.exists()) cacheJs.mkdirs()
            val cacheWasm = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!cacheWasm.exists()) cacheWasm.mkdirs()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed creating WebView cache dirs: ${e.message}")
        }
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

