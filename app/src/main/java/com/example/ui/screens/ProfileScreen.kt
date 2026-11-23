package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.models.ProfileAvatar
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.JadeVerified
import com.example.ui.theme.SkyAccent

@Composable
fun ProfileScreen(viewModel: AnimeViewModel) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarkedAnime.collectAsState()
    val history by viewModel.historyAnime.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val playbackPrefs by viewModel.playbackPreferences.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarPickerDialog by remember { mutableStateOf(false) }
    var showServerPickerDialog by remember { mutableStateOf(false) }
    var showQualityPickerDialog by remember { mutableStateOf(false) }
    var showSubtitlePickerDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.calculateCacheSize()
    }

    val avatarPresets = remember {
        listOf(
            ProfileAvatar(0, "Crimson", "Kolektor Anime", listOf(Color(0xFFE11D48), Color(0xFF9F1239)), Color(0xFFE11D48)),
            ProfileAvatar(1, "Amber", "Penggemar Donghua", listOf(Color(0xFFF59E0B), Color(0xFFB45309)), Color(0xFFF59E0B)),
            ProfileAvatar(2, "Skyline", "Pencinta Mecha", listOf(Color(0xFF0284C7), Color(0xFF1D4ED8)), Color(0xFF38BDF8)),
            ProfileAvatar(3, "Jade", "Penjelajah Sekai", listOf(Color(0xFF10B981), Color(0xFF047857)), Color(0xFF10B981)),
            ProfileAvatar(4, "Obsidian", "Maraton Sub Indo", listOf(Color(0xFF52525B), Color(0xFF27272A)), Color(0xFFA1A1AA)),
            ProfileAvatar(5, "Gold", "Kultivator Senior", listOf(Color(0xFFD97706), Color(0xFF92400E)), Color(0xFFFBBF24))
        )
    }

    val currentAvatar = avatarPresets.getOrElse(
        userProfile.avatarId.coerceAtLeast(0) % avatarPresets.size
    ) { avatarPresets[0] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Spacer(modifier = Modifier.height(14.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Profil & Setelan",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Preferensi pemutar dan manajemen koleksi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { showEditProfileDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("profile_edit_btn")
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
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Profil",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Editorial Profile Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(currentAvatar.gradient))
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { showAvatarPickerDialog = true }
                        .testTag("profile_avatar_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Pilih Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userProfile.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userProfile.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = currentAvatar.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userProfile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Honest Library & Playback Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatCard(
                label = "Favorit",
                value = "${bookmarks.size}",
                icon = Icons.Filled.Bookmark,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { viewModel.navigateTo(Screen.Library) },
                modifier = Modifier.weight(1f)
            )
            ProfileStatCard(
                label = "Riwayat",
                value = "${history.size}",
                icon = Icons.Filled.History,
                accent = SkyAccent,
                onClick = { viewModel.navigateTo(Screen.Library) },
                modifier = Modifier.weight(1f)
            )
            ProfileStatCard(
                label = "Resolusi",
                value = playbackPrefs.defaultQuality.substringBefore(" "),
                icon = Icons.Filled.HighQuality,
                accent = AmberGold,
                onClick = { showQualityPickerDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Pemutaran Video
        Text(
            text = "Preferensi Pemutaran",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingNavRow(
                    title = "Server Utama",
                    subtitle = "Prioritas sumber stream saat membuka episode",
                    valueText = playbackPrefs.preferredServer,
                    valueColor = MaterialTheme.colorScheme.secondary,
                    onClick = { showServerPickerDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavRow(
                    title = "Kualitas Bawaan",
                    subtitle = "Resolusi target pada ExoPlayer",
                    valueText = playbackPrefs.defaultQuality,
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = { showQualityPickerDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Auto-Play Episode Selanjutnya",
                    subtitle = "Otomatis memutar episode berikutnya setelah selesai",
                    checked = playbackPrefs.autoPlayNext,
                    onCheckedChange = { viewModel.setAutoPlayNext(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Akselerasi Hardware",
                    subtitle = "Gunakan decoder hardware perangkat untuk pemutaran halus",
                    checked = playbackPrefs.hardwareAccel,
                    onCheckedChange = { viewModel.setHardwareAccel(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Section: Tampilan & Jaringan
        Text(
            text = "Tampilan & Jaringan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingNavRow(
                    title = "Gaya Subtitle",
                    subtitle = "Preferensi tampilan teks terjemahan",
                    valueText = playbackPrefs.subtitleStyle,
                    valueColor = AmberGold,
                    onClick = { showSubtitlePickerDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Mode Hemat Kuota",
                    subtitle = "Batasi resolusi maksimal 480p pada bitrate hemat",
                    checked = playbackPrefs.dataSaver,
                    onCheckedChange = { viewModel.setDataSaver(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Pemberitahuan Rilis",
                    subtitle = "Pantau pembaruan rilis judul terbaru",
                    checked = playbackPrefs.notifyUpdates,
                    onCheckedChange = { viewModel.setNotifyUpdates(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Section: Penyimpanan & Data
        Text(
            text = "Penyimpanan & Data Lokal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cache Gambar & Jaringan",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ukuran tersimpan: $cacheSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.clearAppCache()
                        Toast.makeText(context, "Cache aplikasi berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("profile_clear_cache_btn")
                ) {
                    Text("Bersihkan", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showClearHistoryDialog = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("profile_clear_history_btn")
            ) {
                Text("Hapus Riwayat", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = { showClearFavoritesDialog = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("profile_clear_favorites_btn")
            ) {
                Text("Hapus Favorit", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vibestream",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sumber: Samehadaku • Anichin • Donghub",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    // Dialog: Edit Profile
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(userProfile.name) }
        var tempTitle by remember { mutableStateOf(userProfile.title) }
        var tempEmail by remember { mutableStateOf(userProfile.email) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Edit Profil", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Nama Tampilan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        label = { Text("Bio Singkat / Gelar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Email Lokal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.trim().isNotEmpty()) {
                            viewModel.updateProfile(
                                name = tempName.trim(),
                                title = tempTitle.trim().ifEmpty { "Kolektor Anime" },
                                email = tempEmail.trim().ifEmpty { "akun@vibestream.local" },
                                avatarId = userProfile.avatarId
                            )
                            Toast.makeText(context, "Profil disimpan", Toast.LENGTH_SHORT).show()
                        }
                        showEditProfileDialog = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog: Avatar Picker
    if (showAvatarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Pilih Warna Avatar", style = MaterialTheme.typography.titleMedium) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                ) {
                    items(avatarPresets, key = { it.id }) { preset ->
                        val isSelected = preset.id == userProfile.avatarId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.updateProfile(
                                        name = userProfile.name,
                                        title = preset.title,
                                        email = userProfile.email,
                                        avatarId = preset.id
                                    )
                                    showAvatarPickerDialog = false
                                }
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(preset.gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = preset.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAvatarPickerDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dialog: Preferred Server Picker
    if (showServerPickerDialog) {
        OptionPickerDialog(
            title = "Pilih Server Utama",
            options = listOf(
                "Auto (Kualitas Tertinggi)",
                "1080p",
                "720p",
                "HD",
                "Direct MP4"
            ),
            selectedOption = playbackPrefs.preferredServer,
            onSelect = {
                viewModel.setPreferredServer(it)
                showServerPickerDialog = false
            },
            onDismiss = { showServerPickerDialog = false }
        )
    }

    // Dialog: Default Quality Picker
    if (showQualityPickerDialog) {
        OptionPickerDialog(
            title = "Pilih Kualitas Bawaan",
            options = listOf("Auto", "1080p", "720p", "480p", "360p"),
            selectedOption = playbackPrefs.defaultQuality,
            onSelect = {
                viewModel.setDownloadQuality(it)
                showQualityPickerDialog = false
            },
            onDismiss = { showQualityPickerDialog = false }
        )
    }

    // Dialog: Subtitle Style Picker
    if (showSubtitlePickerDialog) {
        OptionPickerDialog(
            title = "Pilih Gaya Subtitle",
            options = listOf("Kuning Sinematik", "Putih Bersih", "Kontras Tinggi"),
            selectedOption = playbackPrefs.subtitleStyle,
            onSelect = {
                viewModel.setSubtitleStyle(it)
                showSubtitlePickerDialog = false
            },
            onDismiss = { showSubtitlePickerDialog = false }
        )
    }

    // Dialog: Confirm Clear History
    if (showClearHistoryDialog) {
        ConfirmDestructiveDialog(
            title = "Hapus Semua Riwayat?",
            message = "Seluruh catatan riwayat menonton akan dihapus permanen dari perangkat ini.",
            onConfirm = {
                viewModel.clearHistoryOnly()
                showClearHistoryDialog = false
                Toast.makeText(context, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    // Dialog: Confirm Clear Favorites
    if (showClearFavoritesDialog) {
        ConfirmDestructiveDialog(
            title = "Hapus Semua Favorit?",
            message = "Seluruh daftar anime & donghua tersimpan akan dihapus permanen.",
            onConfirm = {
                viewModel.clearFavoritesOnly()
                showClearFavoritesDialog = false
                Toast.makeText(context, "Daftar favorit dibersihkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearFavoritesDialog = false }
        )
    }
}

@Composable
private fun ProfileStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingNavRow(
    title: String,
    subtitle: String,
    valueText: String,
    valueColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val selected = option.equals(selectedOption, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(option) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Dipilih",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Hapus")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Batal")
            }
        }
    )
}
