package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.models.ProfileAvatar

@Composable
fun ProfileScreen(viewModel: AnimeViewModel) {
    val context = LocalContext.current
    val countList by viewModel.bookmarkedAnime.collectAsState()
    val historyList by viewModel.historyAnime.collectAsState()

    val userName by viewModel.userName.collectAsState()
    val userTitle by viewModel.userTitle.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userAvatarId by viewModel.userAvatarId.collectAsState()

    val preferredServer by viewModel.preferredServer.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val hardwareAccel by viewModel.hardwareAccel.collectAsState()
    val notifyUpdates by viewModel.notifyUpdates.collectAsState()
    val dataSaver by viewModel.dataSaver.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()

    // Dialog state
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

    val avatarPresets = listOf(
        ProfileAvatar(0, "Shadow Monarch", "Hunter S-Rank", listOf(Color(0xFF7000FF), Color(0xFF00F2FE)), Color(0xFF00F2FE)),
        ProfileAvatar(1, "Straw Hat", "Pirate King", listOf(Color(0xFFFF3366), Color(0xFFFF9900)), Color(0xFFFF5252)),
        ProfileAvatar(2, "Six Eyes", "Special Grade", listOf(Color(0xFF00C6FF), Color(0xFF0072FF)), Color(0xFF00C6FF)),
        ProfileAvatar(3, "Dragon Sovereign", "Donghua Celestial", listOf(Color(0xFFFFB300), Color(0xFFF57C00)), Color(0xFFFFD54F)),
        ProfileAvatar(4, "Shinobi Master", "Hokage", listOf(Color(0xFF00E676), Color(0xFF1DE9B6)), Color(0xFF69F0AE)),
        ProfileAvatar(5, "Cosmic Void", "Dimension Master", listOf(Color(0xFFFF007F), Color(0xFF7928CA)), Color(0xFFFF4081))
    )

    val currentAvatar = avatarPresets.getOrElse(userAvatarId % avatarPresets.size) { avatarPresets[0] }

    val totalMinutes = historyList.size * 24
    val hoursWatched = totalMinutes / 60
    val minutesWatched = totalMinutes % 60
    val watchTimeString = if (hoursWatched > 0) "${hoursWatched}j ${minutesWatched}m" else "${minutesWatched}m"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A0B10),
                        Color(0xFF06070B),
                        Color(0xFF030305)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Spacer(modifier = Modifier.height(16.dp))

        // Top Navigation Title Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Profil Pengguna",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Kelola akun dan preferensi streaming Anda",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { showEditProfileDialog = true }
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Profile",
                    tint = Color(0xFF00F2FE),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main User Card with Glowing Halo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF161926),
                            Color(0xFF0F111A)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(currentAvatar.gradient)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with Edit Badge
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(currentAvatar.gradient))
                        .clickable { showAvatarPickerDialog = true }
                        .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )

                    // Small edit icon tag
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE50914))
                            .border(2.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Change",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "$userName 💭",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = userTitle,
                    color = currentAvatar.accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = userEmail,
                    color = Color.LightGray.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // VIP Badge & Level Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33000000))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "VIP",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "VIP PASS AKTIF",
                                color = Color(0xFFFFC107),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Streaming Tanpa Iklan • Kecepatan Max",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFC107).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LIFETIME",
                            color = Color(0xFFFFC107),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Interactive Statistics Grid (Daftar Saya, History, Waktu Tonton, XP)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bookmarks card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141724))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { viewModel.navigateTo(Screen.Library) }
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "Bookmark",
                            tint = Color(0xFF00F2FE),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${countList.size}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Daftar Favorit", color = Color.Gray, fontSize = 12.sp)
                }
            }

            // History card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141724))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .clickable { viewModel.navigateTo(Screen.Library) }
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "History",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${historyList.size}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Riwayat Tonton", color = Color.Gray, fontSize = 12.sp)
                }
            }

            // Watch time card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141724))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Time",
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = watchTimeString,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Total Nonton", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: Streaming & Server
        Text(
            text = "Preferensi Streaming & Video",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141724)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Preferred server selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showServerPickerDialog = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Server Utama", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Prioritas pemutaran video otomatis", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text(
                        text = preferredServer,
                        color = Color(0xFF00F2FE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Default Quality selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showQualityPickerDialog = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Kualitas Bawaan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Resolusi default saat video dimulai", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text(
                        text = downloadQuality,
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Auto-play switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Auto-Play Episode Selanjutnya", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Lanjut putar otomatis setelah episode selesai", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoPlayNext,
                        onCheckedChange = { viewModel.setAutoPlayNext(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFE50914)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Hardware Acceleration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Akselerasi Hardware (GPU Boost)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Streaming lebih lancar tanpa lag 60fps", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = hardwareAccel,
                        onCheckedChange = { viewModel.setHardwareAccel(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00F2FE)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: Subtitle & Tampilan
        Text(
            text = "Tampilan & Subtitle",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141724)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Subtitle style picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showSubtitlePickerDialog = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Gaya Subtitle", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Warna dan bayangan teks subtitle", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text(
                        text = subtitleStyle,
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Update Notifications
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Pemberitahuan Rilis Baru", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Notifikasi jika anime favorit update", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = notifyUpdates,
                        onCheckedChange = { viewModel.setNotifyUpdates(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF7000FF)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                // Data Saver Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Mode Hemat Kuota Seluler", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Kompresi video saat menggunakan data paket", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = dataSaver,
                        onCheckedChange = { viewModel.setDataSaver(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E676)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: Cache & Penyimpanan
        Text(
            text = "Penyimpanan & Cache",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141724)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Cache Sementara", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Ukuran cache gambar & buffer video: $cacheSize", color = Color.Gray, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.clearAppCache()
                            Toast.makeText(context, "Cache aplikasi berhasil dibersihkan!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23283A)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Bersihkan", color = Color(0xFF00F2FE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: Manajemen Data (Riwayat & Favorit)
        Text(
            text = "Manajemen Data Akun",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showClearHistoryDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF3366)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x66FF3366)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Hapus Riwayat", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showClearFavoritesDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33E50914)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x66E50914)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Hapus Favorit", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // App Version Info Box
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "StreamAnime & Donghua Pro v2.6.0",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Engine: Multi-Scraper Turbo Core (Samehadaku • Anichin • Donghub)",
                color = Color.Gray.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // DIALOG: Edit Profile (Username, Title, Email)
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(userName) }
        var tempTitle by remember { mutableStateOf(userTitle) }
        var tempEmail by remember { mutableStateOf(userEmail) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Edit Profil Pengguna", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Nama Panggilan") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FE),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF00F2FE),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        label = { Text("Gelar Otaku / Status") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FE),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF00F2FE),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempEmail,
                        onValueChange = { tempEmail = it },
                        label = { Text("Email Akun") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FE),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF00F2FE),
                            unfocusedLabelColor = Color.Gray
                        ),
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
                                title = tempTitle.trim().ifEmpty { "Otaku Enthusiast" },
                                email = tempEmail.trim().ifEmpty { "user@stream.id" },
                                avatarId = userAvatarId
                            )
                            Toast.makeText(context, "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        }
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Avatar Picker
    if (showAvatarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Pilih Avatar Otaku", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                ) {
                    items(avatarPresets, key = { it.id }) { preset ->
                        val isSelected = preset.id == userAvatarId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0x3300F2FE) else Color(0x1AFFFFFF))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) Color(0xFF00F2FE) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.updateProfile(
                                        name = userName,
                                        title = preset.title,
                                        email = userEmail,
                                        avatarId = preset.id
                                    )
                                    Toast.makeText(context, "Avatar diubah: ${preset.name}", Toast.LENGTH_SHORT).show()
                                    showAvatarPickerDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(preset.gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = preset.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarPickerDialog = false }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Preferred Server Picker
    if (showServerPickerDialog) {
        val serverOptions = listOf(
            "Alpha Stream (Fast CDN)",
            "Beta VIP Stream (1080p)",
            "Direct MP4 Stream",
            "Hydra Multi-Server"
        )
        AlertDialog(
            onDismissRequest = { showServerPickerDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Pilih Server Utama", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    serverOptions.forEach { server ->
                        val isSelected = preferredServer == server
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x3300F2FE) else Color(0x11FFFFFF))
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFF00F2FE) else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setPreferredServer(server)
                                    Toast.makeText(context, "Server: $server", Toast.LENGTH_SHORT).show()
                                    showServerPickerDialog = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = server,
                                    color = if (isSelected) Color(0xFF00F2FE) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00F2FE),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerPickerDialog = false }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Video Quality Picker
    if (showQualityPickerDialog) {
        val qualityOptions = listOf("1080p Full HD", "720p HD", "480p SD", "360p Hemat")
        AlertDialog(
            onDismissRequest = { showQualityPickerDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Pilih Kualitas Bawaan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualityOptions.forEach { quality ->
                        val isSelected = downloadQuality == quality
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x33FF3366) else Color(0x11FFFFFF))
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFFFF5252) else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setDownloadQuality(quality)
                                    Toast.makeText(context, "Kualitas: $quality", Toast.LENGTH_SHORT).show()
                                    showQualityPickerDialog = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality,
                                    color = if (isSelected) Color(0xFFFF5252) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityPickerDialog = false }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Subtitle Picker
    if (showSubtitlePickerDialog) {
        val subtitleOptions = listOf("Kuning Anime (Shadow)", "Putih Bersih (Bold)", "Hijau Neon", "Cyan Crystal")
        AlertDialog(
            onDismissRequest = { showSubtitlePickerDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Pilih Gaya Subtitle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subtitleOptions.forEach { style ->
                        val isSelected = subtitleStyle == style
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x33FFD54F) else Color(0x11FFFFFF))
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFFFFD54F) else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setSubtitleStyle(style)
                                    showSubtitlePickerDialog = false
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = style,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitlePickerDialog = false }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Konfirmasi Hapus Riwayat
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Hapus Riwayat Tonton?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Seluruh histori anime yang pernah Anda tonton akan dihapus dari perangkat.", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistoryOnly()
                        Toast.makeText(context, "Riwayat tontonan berhasil dihapus", Toast.LENGTH_SHORT).show()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Hapus Sekarang", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // DIALOG: Konfirmasi Hapus Favorit
    if (showClearFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showClearFavoritesDialog = false },
            containerColor = Color(0xFF161926),
            title = {
                Text("Hapus Semua Favorit?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Daftar tontonan favorit / bookmark Anda akan dikosongkan.", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearFavoritesOnly()
                        Toast.makeText(context, "Daftar favorit berhasil dikosongkan", Toast.LENGTH_SHORT).show()
                        showClearFavoritesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Hapus Semua", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFavoritesDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}
