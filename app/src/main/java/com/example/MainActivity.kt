package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.components.VibestreamNavigationBar
import com.example.ui.components.VibestreamNavigationRail
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val cacheJs = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!cacheJs.exists()) cacheJs.mkdirs()
            val cacheWasm = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val isWideLayout = maxWidth >= 600.dp
                    val showNavChrome = activeScreen != Screen.Episode

                    Scaffold(
                        bottomBar = {
                            if (!isWideLayout && showNavChrome) {
                                VibestreamNavigationBar(
                                    activeScreen = activeScreen,
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            if (isWideLayout && showNavChrome) {
                                VibestreamNavigationRail(
                                    activeScreen = activeScreen,
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                                VerticalDivider(
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 0.75.dp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                AnimatedContent(
                                    targetState = activeScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(220)) togetherWith
                                            fadeOut(animationSpec = tween(160))
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
    }
}
