# Anime & Donghua Streaming Platform (Android / Kotlin Compose)

Aplikasi streaming premium berkinerja tinggi yang dirancang khusus untuk penggemar Anime dan Donghua. Dibangun menggunakan paradigma pengembangan Android modern (Jetpack Compose, Material 3, Clean Architecture), aplikasi ini menyajikan pengalaman menonton yang sangat mulus, responsif, berkelas, bebas iklan eksternal, dan siap dimigrasi ke backend terpusat di masa depan.

---

## 🚀 Fitur Utama & Keunggulan

Aplikasi ini telah dioptimalkan secara mendalam dengan berbagai fungsionalitas canggih:

### 1. Sistem Streaming Tanpa Gangguan Eksternal (Strictly Native)
- **Zero External Overloading:** Menghapus seluruh tombol pengalihan browser luar ("Buka Eksternal", "Buka Browser"). Seluruh proses penayangan, pemilihan episode, dan pengelolaan media berjalan murni di dalam aplikasi demi menjaga keamanan data pengguna dari iklan pop-up berbahaya.
- **Copy Link Manager:** Tautan unduhan media cermin (mirrors) disediakan melalui salinan aman langsung ke clipboard sistem agar dapat diputar atau diunduh memakai external manager pilihan pengguna secara independen.

### 2. Auto-Resolusi Maksimum (1080p / Max Quality)
- **Dynamic Desktop Simulation:** Penerapan header 'User Agent' tipe Chromium Desktop modern dalam mesin peramban web internal. Hal ini memicu penyedia streaming untuk secara otomatis menyajikan resolusi video tertinggi yang tersedia (1080p/720p/HD) tanpa mengalami kompresi seluler (throttle).
- **Auto Server Switcher:** Logika pencarian cerdas yang memprioritaskan server dengan resolusi tertinggi (misal: "1080p", "720p", "HD", "Max") secara instan saat episode pertama kali dibuka.

### 3. Modul Navigasi & Menu Episode Native
- **Menu Episode Inline:** Menghadirkan menu navigasi daftar episode langsung di halaman streaming penayangan aktif. Pengguna dapat melompat dari satu episode ke episode lain tanpa perlu kembali ke halaman informasi utama.
- **Interactive State Indicator:** Menandakan episode yang sedang diputar dengan warna aksen merah menyala bergaya premium.

### 4. Perbaikan Bug Layar Penuh (Backlight & Device Rotation Fix)
- **Immersive Mode Configuration:** Mengintegrasikan `configChanges` pada berkas konfigurasi sistem Android agar aktivitas tidak hancur atau keluar sendiri saat perangkat diputar secara fisik menjadi landscape untuk orientasi layar penuh.
- **Backlight Glow Aura Effect:** Menambahkan visual estetik di balik komponen pemutar dengan pancaran cahaya lembut (ambient glow) agar aktivitas penayangan terasa bioskopik.

### 5. Penyimpanan Lokal Mandiri (Room Database)
- Penyimpanan riwayat tontonan, daftar favorit, penanda bookmark, dan status rilis secara berkala tersimpan privat pada basis data internal SQLite (Room DB) dalam format asinkron penuh menggunakan Kotlin Coroutines.

---

## 🛠️ Arsitektur Sistem & Struktur Kode

Aplikasi ini dirancang dengan prinsip **Clean Architecture & MVVM (Model-View-ViewModel)** yang modular dan scalable, memisahkan logika pengambilan data scraper, resolver stream, penyimpanan lokal, serta antarmuka (UI).

```text
app/src/main/java/com/example/
├── data/
│   ├── AppDatabase.kt          <- Basis data internal SQLite (Room DB).
│   ├── SavedAnimeDao.kt        <- Data Access Object (DAO) untuk riwayat & bookmark.
│   ├── SavedAnimeRepository.kt  <- Abstraksi data layer / Repository pattern terpadu.
│   ├── SamehadakuScraper.kt    <- Scraper data provider anime.
│   ├── AnichinScraper.kt       <- Scraper data provider donghua #1.
│   ├── DonghubScraper.kt       <- Scraper data provider donghua #2.
│   └── stream/
│       ├── StreamResolver.kt    <- Extractor stream HLS (.m3u8), MP4, & resolver header.
│       ├── HeadlessStreamExtractor.kt <- Dynamic headless sniffer via WebView background.
│       └── ResolvedStream.kt   <- Value object stream beserta data header & media type.
├── ui/
│   ├── AnimeViewModel.kt       <- Logika bisnis, pemrosesan Flow, state management.
│   ├── PlayerManager.kt        <- Singleton ExoPlayer controller (Media3 lifecycle).
│   ├── ExoVideoPlayer.kt       <- Dual-engine player (Native ExoPlayer + Web Fallback).
│   ├── Theme.kt                <- Tema visual "Red Glass" & Material 3.
│   ├── screens/                <- Layar modular (HomeScreen, DetailScreen, EpisodeScreen, SearchScreen, CatalogScreen, SavedScreen).
│   └── components/             <- Komponen UI terpisah (BottomNav, AnimeCard, ServerSelector, CustomOverlay).
└── MainActivity.kt             <- Router navigasi utama & penanganan orientation.
```

---

## 🎬 Dual-Engine Video Player & Smart Auto-Fallback

Sistem pemutar video menggunakan pendekatan **Smart Dual-Engine**:
1. **Primary (Native ExoPlayer / Media3)**: Dipakai untuk pemutaran stream direct HLS (`.m3u8`) / MP4 berkinerja tinggi dengan akselerasi perangkat keras (GPU).
2. **Fallback (Web Engine / HLS.js v1.5.17)**: Berjalan otomatis jika ExoPlayer mengalami gangguan proteksi server (HTTP 403/401/429), `MALFORMED_MANIFEST`, atau respons challenge anti-bot.
3. **Header Handoff & Referer Retention**: `StreamResolver` meneruskan header lengkap (`Referer`, `Origin`, `User-Agent`) secara presisi baik ke ExoPlayer `HttpDataSource` maupun ke `WebPlayerView` (`xhrSetup` + `loadDataWithBaseURL`).
4. **Anti-Loop Session Guard**: Pemicu *fallback* diatur maksimal 1 kali per URL session streaming untuk mencegah *infinite loop*.

---

## 🛡️ Rencana Pengembangan & Persiapan Migrasi (Future Roadmap)

- **Persiapan API Backend:** Seluruh fungsi Repository dan Service sudah ditulis dengan standar fungsi gantung (`suspend`) asinkron. Transisi penyimpanan lokal menuju API Fetch (Express.js / NestJS dengan database PostgreSQL/MongoDB) dapat dilakukan dengan mengganti lapisan Repository tanpa merusak visual atau logika ViewModel sedikit pun.
- **HLS (m3u8) & Signed URL:** Mendukung streaming HLS berenkripsi tinggi untuk melindungi data video penayangan.
- **Keamanan Token:** Pengalihan data sesi menggunakan HttpOnly Cookies setelah peluncuran layanan administrasi pengguna jarak jauh dilakukan.

