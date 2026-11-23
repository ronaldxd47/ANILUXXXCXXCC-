package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.Screen

private data class NavDestination(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    NavDestination(Screen.Home, "Beranda", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    NavDestination(Screen.Schedule, "Jadwal", Icons.Filled.DateRange, Icons.Outlined.DateRange, "nav_schedule"),
    NavDestination(Screen.Catalog, "Katalog", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, "nav_catalog"),
    NavDestination(Screen.Library, "Koleksi", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_library"),
    NavDestination(Screen.Profile, "Profil", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
)

private fun isDestinationSelected(dest: NavDestination, activeScreen: Screen): Boolean {
    return when (dest.screen) {
        Screen.Home -> activeScreen == Screen.Home || activeScreen == Screen.Detail || activeScreen == Screen.Episode
        Screen.Schedule -> activeScreen == Screen.Schedule
        Screen.Catalog -> activeScreen == Screen.Catalog || activeScreen == Screen.Search
        Screen.Library -> activeScreen == Screen.Library || activeScreen == Screen.MyList
        Screen.Profile -> activeScreen == Screen.Profile
        else -> activeScreen == dest.screen
    }
}

@Composable
fun VibestreamNavigationBar(
    activeScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.75.dp)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            TopDestinations.forEach { dest ->
                val selected = isDestinationSelected(dest, activeScreen)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(dest.screen) },
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label
                        )
                    },
                    label = {
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag(dest.testTag)
                )
            }
        }
    }
}

@Composable
fun VibestreamNavigationRail(
    activeScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.weight(0.15f))
        TopDestinations.forEach { dest ->
            val selected = isDestinationSelected(dest, activeScreen)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(dest.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag(dest.testTag)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
