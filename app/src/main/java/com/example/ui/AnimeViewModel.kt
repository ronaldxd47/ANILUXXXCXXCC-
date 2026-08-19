package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONArray

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AnimeViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("animestream_prefs", Context.MODE_PRIVATE)

    // Screen State
    private val _activeScreen = MutableStateFlow<Screen>(Screen.Home)
    val activeScreen = _activeScreen.asStateFlow()

    // Home feed lists
    private val _latestUpdates = MutableStateFlow<UiState<List<ScrapedAnime>>>(UiState.Idle)
    val latestUpdates = _latestUpdates.asStateFlow()

    private val _trendingAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val trendingAnime = _trendingAnime.asStateFlow()

    // Dynamic View Counts Tracking
    private val _viewCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val viewCounts = _viewCounts.asStateFlow()

    fun getViewCount(link: String): Int {
        val current = _viewCounts.value[link]
        if (current != null) return current
        
        // Seed based on link hash for natural starting values
        val seed = kotlin.math.abs(link.hashCode() % 14500) + 1200
        val stored = sharedPrefs.getInt("views_$link", seed)
        
        // Update in-memory map
        val newMap = _viewCounts.value.toMutableMap()
        newMap[link] = stored
        _viewCounts.value = newMap
        return stored
    }

    fun incrementViewCount(link: String) {
        val current = getViewCount(link)
        val newVal = current + 1
        sharedPrefs.edit().putInt("views_$link", newVal).apply()
        
        val newMap = _viewCounts.value.toMutableMap()
        newMap[link] = newVal
        _viewCounts.value = newMap
        
        // Re-sort current trending list descending
        val currentTrending = _trendingAnime.value
        if (currentTrending.isNotEmpty()) {
            _trendingAnime.value = currentTrending.sortedByDescending { getViewCount(it.link) }
        }
    }

    fun formatViewCount(views: Int): String {
        return if (views >= 1_000_000) {
            "${views / 100_000 / 10.0}M views"
        } else if (views >= 1_000) {
            "${views / 100 / 10.0}K views"
        } else {
            "$views views"
        }
    }

    // Search results State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<ScrapedAnime>>>(UiState.Idle)
    val searchResults = _searchResults.asStateFlow()

    // Details of currently viewed anime
    private val _selectedAnimeUrl = MutableStateFlow<String?>(null)
    val selectedAnimeUrl = _selectedAnimeUrl.asStateFlow()

    private val _animeDetail = MutableStateFlow<UiState<AnimeDetail>>(UiState.Idle)
    val animeDetail = _animeDetail.asStateFlow()

    // Details of currently viewed episode
    private val _selectedEpisodeUrl = MutableStateFlow<String?>(null)
    val selectedEpisodeUrl = _selectedEpisodeUrl.asStateFlow()

    private val _episodeDetail = MutableStateFlow<UiState<EpisodeDetail>>(UiState.Idle)
    val episodeDetail = _episodeDetail.asStateFlow()

    private val db = AppDatabase.getDatabase(application)
    private val repository = SavedAnimeRepository(db.savedAnimeDao())
    private val scheduleRepository = ScheduleRepository()

    // Bookmark / Watchlist list
    private val _bookmarkedAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val bookmarkedAnime = _bookmarkedAnime.asStateFlow()

    // History list
    private val _historyAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val historyAnime = _historyAnime.asStateFlow()

    // Schedule list State (Real Scraped Data)
    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleDay>>>(UiState.Idle)
    val scheduleState = _scheduleState.asStateFlow()

    // Navigation Stack for going back
    private val navigationStack = mutableListOf<Screen>()

    init {
        com.example.data.SamehadakuScraper.appContext = application
        loadBookmarksAndHistory()
        fetchHomeFeed()
        fetchScheduleData()
    }

    /**
     * Mengambil data jadwal rilis dari repository secara asynchronous (Coroutines).
     * Siap migrasi ke backend / API di masa depan tanpa merusak UI.
     */
    fun fetchScheduleData() {
        viewModelScope.launch {
            _scheduleState.value = UiState.Loading
            try {
                val data = scheduleRepository.getReleaseSchedule()
                if (data.isNotEmpty()) {
                    _scheduleState.value = UiState.Success(data)
                } else {
                    _scheduleState.value = UiState.Error("Gagal memuat jadwal rilis")
                }
            } catch (e: Exception) {
                _scheduleState.value = UiState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    // User Profile & Preferences State (Persisted in SharedPreferences)
    private val _userName = MutableStateFlow(sharedPrefs.getString("profile_name", "Rey") ?: "Rey")
    val userName = _userName.asStateFlow()

    private val _userTitle = MutableStateFlow(sharedPrefs.getString("profile_title", "Otaku VIP Master") ?: "Otaku VIP Master")
    val userTitle = _userTitle.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("profile_email", "rey.otaku@stream.id") ?: "rey.otaku@stream.id")
    val userEmail = _userEmail.asStateFlow()

    private val _userAvatarId = MutableStateFlow(sharedPrefs.getInt("profile_avatar_id", 0))
    val userAvatarId = _userAvatarId.asStateFlow()

    private val _preferredServer = MutableStateFlow(sharedPrefs.getString("pref_server", "Alpha Stream (Fast CDN)") ?: "Alpha Stream (Fast CDN)")
    val preferredServer = _preferredServer.asStateFlow()

    private val _downloadQuality = MutableStateFlow(sharedPrefs.getString("pref_quality", "720p HD") ?: "720p HD")
    val downloadQuality = _downloadQuality.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(sharedPrefs.getBoolean("pref_autoplay", true))
    val autoPlayNext = _autoPlayNext.asStateFlow()

    private val _hardwareAccel = MutableStateFlow(sharedPrefs.getBoolean("pref_hardware_accel", true))
    val hardwareAccel = _hardwareAccel.asStateFlow()

    private val _notifyUpdates = MutableStateFlow(sharedPrefs.getBoolean("pref_notify", true))
    val notifyUpdates = _notifyUpdates.asStateFlow()

    private val _dataSaver = MutableStateFlow(sharedPrefs.getBoolean("pref_datasaver", false))
    val dataSaver = _dataSaver.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(sharedPrefs.getString("pref_subtitle", "Kuning Anime (Shadow)") ?: "Kuning Anime (Shadow)")
    val subtitleStyle = _subtitleStyle.asStateFlow()

    private val _cacheSize = MutableStateFlow("0 MB")
    val cacheSize = _cacheSize.asStateFlow()

    fun updateProfile(name: String, title: String, email: String, avatarId: Int) {
        _userName.value = name
        _userTitle.value = title
        _userEmail.value = email
        _userAvatarId.value = avatarId
        sharedPrefs.edit()
            .putString("profile_name", name)
            .putString("profile_title", title)
            .putString("profile_email", email)
            .putInt("profile_avatar_id", avatarId)
            .apply()
    }

    fun setPreferredServer(server: String) {
        _preferredServer.value = server
        sharedPrefs.edit().putString("pref_server", server).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        sharedPrefs.edit().putString("pref_quality", quality).apply()
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        sharedPrefs.edit().putBoolean("pref_autoplay", enabled).apply()
    }

    fun setHardwareAccel(enabled: Boolean) {
        _hardwareAccel.value = enabled
        sharedPrefs.edit().putBoolean("pref_hardware_accel", enabled).apply()
    }

    fun setNotifyUpdates(enabled: Boolean) {
        _notifyUpdates.value = enabled
        sharedPrefs.edit().putBoolean("pref_notify", enabled).apply()
    }

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        sharedPrefs.edit().putBoolean("pref_datasaver", enabled).apply()
    }

    fun setSubtitleStyle(style: String) {
        _subtitleStyle.value = style
        sharedPrefs.edit().putString("pref_subtitle", style).apply()
    }

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                var totalBytes = 0L
                context.cacheDir?.walkTopDown()?.forEach { file ->
                    if (file.isFile) totalBytes += file.length()
                }
                context.externalCacheDir?.walkTopDown()?.forEach { file ->
                    if (file.isFile) totalBytes += file.length()
                }
                val formatted = if (totalBytes > 1024 * 1024) {
                    String.format(java.util.Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
                } else if (totalBytes > 1024) {
                    "${totalBytes / 1024} KB"
                } else {
                    "${totalBytes} B"
                }
                _cacheSize.value = formatted
            } catch (e: Exception) {
                _cacheSize.value = "45.2 MB"
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.cacheDir?.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
                _cacheSize.value = "0 KB"
            } catch (e: Exception) {
                _cacheSize.value = "0 KB"
            }
        }
    }

    fun clearHistoryOnly() {
        viewModelScope.launch {
            repository.deleteAllHistory()
        }
    }

    fun clearFavoritesOnly() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun clearAllHistoryAndFavorites() {
        viewModelScope.launch {
            repository.deleteAll()
            repository.deleteAllHistory()
        }
    }


    /**
     * Fetch Home Updates & populate some mockup sliders
     */
    fun fetchHomeFeed() {
        viewModelScope.launch {
            while (isActive) {
                refreshData()
                // Auto update periodic (5 minutes) -> To prevent too many requests but still fulfill "auto update juga semua"
                kotlinx.coroutines.delay(300_000) 
            }
        }
    }

    /**
     * Memfilter dan membuang duplikasi anime berdasarkan URL serta judul yang dinormalisasi.
     */
    private fun deduplicateAnimeList(list: List<ScrapedAnime>): List<ScrapedAnime> {
        val result = mutableListOf<ScrapedAnime>()
        val seenLinks = mutableSetOf<String>()
        val seenTitleKeys = mutableSetOf<String>()

        for (item in list) {
            val cleanLink = item.link.trim().trimEnd('/')
            if (cleanLink.isEmpty()) continue
            if (seenLinks.contains(cleanLink)) continue

            val rawTitle = item.title
            val cleanTitle = rawTitle
                .replace("[SVIP]", "", ignoreCase = true)
                .replace("Sub Indo", "", ignoreCase = true)
                .replace("Subtitle Indonesia", "", ignoreCase = true)
                .trim()

            // Normalized key without stripping season or numbers to avoid dropping different seasons/donghua
            val titleKey = cleanTitle
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")
                .trim()

            if (titleKey.isNotEmpty() && seenTitleKeys.contains(titleKey)) {
                continue
            }

            seenLinks.add(cleanLink)
            if (titleKey.isNotEmpty()) {
                seenTitleKeys.add(titleKey)
            }
            result.add(item.copy(title = cleanTitle.ifEmpty { rawTitle }, link = cleanLink))
        }

        return result
    }

    /**
     * Menyelang-nyelingkan item dari beberapa scraper agar Anime & Donghua seimbang di posisi atas
     */
    private fun interleaveLists(vararg lists: List<ScrapedAnime>): List<ScrapedAnime> {
        val result = mutableListOf<ScrapedAnime>()
        val maxSize = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (list in lists) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                if (_latestUpdates.value !is UiState.Success) {
                    _latestUpdates.value = UiState.Loading
                }
                
                val rawFastScraped = mutableListOf<ScrapedAnime>()

                // PHASE 1: Fast initial load with independent per-scraper coroutines & timeouts
                var s1 = emptyList<ScrapedAnime>()
                var s2 = emptyList<ScrapedAnime>()
                var s3 = emptyList<ScrapedAnime>()

                kotlinx.coroutines.supervisorScope {
                    val d1 = async { try { kotlinx.coroutines.withTimeoutOrNull(6000) { SamehadakuScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }
                    val d2 = async { try { kotlinx.coroutines.withTimeoutOrNull(6000) { AnichinScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }
                    val d3 = async { try { kotlinx.coroutines.withTimeoutOrNull(6000) { DonghubScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }

                    s1 = d1.await()
                    s2 = d2.await()
                    s3 = d3.await()
                }

                // Interleave agar Anime & Donghua berimbang di bagian atas
                val fastInterleaved = interleaveLists(s1, s2, s3)
                val fastCombined = deduplicateAnimeList(fastInterleaved)

                if (fastCombined.isNotEmpty()) {
                    _latestUpdates.value = UiState.Success(fastCombined)
                    _trendingAnime.value = fastCombined.take(15).sortedByDescending { getViewCount(it.link) }
                }

                // PHASE 2: Silent background extension for page 2/3 & catalog
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        var b1 = emptyList<ScrapedAnime>()
                        var b2 = emptyList<ScrapedAnime>()
                        var b3 = emptyList<ScrapedAnime>()

                        kotlinx.coroutines.supervisorScope {
                            val bg1 = async { try { kotlinx.coroutines.withTimeoutOrNull(8000) { SamehadakuScraper.fetchLatestUpdates(2) } ?: emptyList() } catch (e: Exception) { emptyList() } }
                            val bg2 = async { try { kotlinx.coroutines.withTimeoutOrNull(8000) { AnichinScraper.fetchLatestUpdates(2) } ?: emptyList() } catch (e: Exception) { emptyList() } }
                            val bg3 = async { try { kotlinx.coroutines.withTimeoutOrNull(8000) { DonghubScraper.fetchLatestUpdates(2) } ?: emptyList() } catch (e: Exception) { emptyList() } }

                            b1 = bg1.await()
                            b2 = bg2.await()
                            b3 = bg3.await()
                        }

                        val fullInterleaved = interleaveLists(s1 + b1, s2 + b2, s3 + b3)
                        val fullCombined = deduplicateAnimeList(fullInterleaved)
                        if (fullCombined.isNotEmpty()) {
                            _latestUpdates.value = UiState.Success(fullCombined)
                            _trendingAnime.value = fullCombined.take(20).sortedByDescending { getViewCount(it.link) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AnimeViewModel", "Background refresh error", e)
                    }
                }

                if (fastCombined.isEmpty() && _latestUpdates.value !is UiState.Success) {
                    _latestUpdates.value = UiState.Error("Failed to fetch fresh data")
                }
            } catch (e: Exception) {
                android.util.Log.e("AnimeViewModel", "supervisorScope error", e)
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Search anime (Debounced)
     */
    fun performSearch(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        
        if (query.trim().isEmpty()) {
            _searchResults.value = UiState.Idle
            return
        }
        
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400) // Debounce delay
            _searchResults.value = UiState.Loading
            
            val rawScraped = mutableListOf<ScrapedAnime>()
            
            try {
                kotlinx.coroutines.supervisorScope {
                    val d1 = async { try { SamehadakuScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }
                    val d2 = async { try { AnichinScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }
                    val d3 = async { try { DonghubScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }
                    
                    val results = kotlinx.coroutines.withTimeoutOrNull(6000) {
                        kotlinx.coroutines.awaitAll(d1, d2, d3)
                    } ?: emptyList()

                    for (res in results) {
                        rawScraped.addAll(res)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AnimeViewModel", "Search supervisorScope error", e)
            }
            
            val combinedResults = deduplicateAnimeList(rawScraped)
            
            if (combinedResults.isNotEmpty()) {
                _searchResults.value = UiState.Success(combinedResults)
            } else {
                _searchResults.value = UiState.Error("No results found.")
            }
        }
    }

    /**
     * Show Anime detailed screen
     */
    fun selectAnime(url: String) {
        if (url.contains("myanimelist.net")) {
            // Find the title matching this URL from the schedule state to perform a search
            val scheduleLists = (_scheduleState.value as? UiState.Success)?.data ?: emptyList()
            var searchTitle = ""
            for (day in scheduleLists) {
                val match = day.animeList.find { it.link == url }
                if (match != null) {
                    searchTitle = match.title
                    break
                }
            }
            if (searchTitle.isNotEmpty()) {
                performSearch(searchTitle)
                navigateTo(Screen.Search)
            }
            return
        }

        incrementViewCount(url)
        _selectedAnimeUrl.value = url
        navigateTo(Screen.Detail)

        viewModelScope.launch {
            _animeDetail.value = UiState.Loading
            val details = kotlinx.coroutines.withTimeoutOrNull(20000) {
                when {
                    url.contains("anichin") -> AnichinScraper.fetchAnimeDetail(url)
                    url.contains("donghub") -> DonghubScraper.fetchAnimeDetail(url)
                    else -> SamehadakuScraper.fetchAnimeDetail(url)
                }
            }
            if (details != null && details.title.isNotEmpty() && details.title != "Title Missing") {
                val finalDetails = if (details.episodes.isEmpty()) {
                    // Fallback episode if details is a movie or direct episode link
                    details.copy(episodes = listOf(EpisodeItem(title = "Putar Episode Ini", link = url)))
                } else {
                    details
                }
                _animeDetail.value = UiState.Success(finalDetails)
                repository.insertHistory(HistoryAnime(link = url, title = finalDetails.title, imageUrl = finalDetails.posterUrl))
            } else {
                _animeDetail.value = UiState.Error("Gagal memuat detail anime untuk: $url. Silakan periksa koneksi atau coba lagi.")
            }
        }
    }

    /**
     * Show Episode streaming or downloading options
     */
    fun selectEpisode(url: String) {
        _selectedEpisodeUrl.value = url
        navigateTo(Screen.Episode)

        viewModelScope.launch {
            _episodeDetail.value = UiState.Loading
            val episodeDetails = kotlinx.coroutines.withTimeoutOrNull(20000) {
                when {
                    url.contains("anichin") -> AnichinScraper.fetchEpisodeDetail(url)
                    url.contains("donghub") -> DonghubScraper.fetchEpisodeDetail(url)
                    else -> SamehadakuScraper.fetchEpisodeDetail(url)
                }
            }
            if (episodeDetails != null && (episodeDetails.streamEmbeds.isNotEmpty() || episodeDetails.downloads.isNotEmpty())) {
                _episodeDetail.value = UiState.Success(episodeDetails)
            } else {
                _episodeDetail.value = UiState.Error("Server video sedang dipersiapkan atau belum tersedia. Silakan pilih server alternatif atau refresh.")
            }
        }
    }

    /**
     * Switch Screens with simple custom stack
     */
    fun navigateTo(screen: Screen) {
        if (_activeScreen.value != screen) {
            navigationStack.add(_activeScreen.value)
            _activeScreen.value = screen
        }
    }

    fun goBack(): Boolean {
        if (navigationStack.isNotEmpty()) {
            _activeScreen.value = navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        return false
    }

    /**
     * Bookmarks management (persistent via Room)
     */
    fun toggleBookmark(anime: ScrapedAnime) {
        viewModelScope.launch {
            val existing = isBookmarked(anime.link)
            if (existing) {
                repository.deleteByLink(anime.link)
            } else {
                repository.insert(
                    SavedAnime(
                        link = anime.link,
                        title = anime.title,
                        imageUrl = anime.imageUrl,
                        status = anime.status,
                        episode = anime.episode,
                        score = anime.score,
                        type = anime.type,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun isBookmarked(link: String): Boolean {
        // Fast synchronous check against in-memory state flow
        return _bookmarkedAnime.value.any { it.link == link }
    }

    private fun saveBookmarks(list: List<ScrapedAnime>) {
        // Deprecated, Room handles saves.
    }

    private fun loadBookmarksAndHistory() {
        // Migration: Clear old shared preferences to fulfill 'delete all history' request
        if (sharedPrefs.contains("bookmarks")) {
            sharedPrefs.edit().remove("bookmarks").apply()
        }

        viewModelScope.launch {
            launch {
                repository.allSavedAnimes.collect { savedList ->
                    val converted = savedList.map {
                        ScrapedAnime(
                            title = it.title,
                            link = it.link,
                            imageUrl = it.imageUrl,
                            status = it.status,
                            episode = it.episode,
                            score = it.score,
                            type = it.type
                        )
                    }
                    _bookmarkedAnime.value = converted
                }
            }
            
            launch {
                repository.allHistory.collect { historyList ->
                    val converted = historyList.map {
                        ScrapedAnime(
                            title = it.title,
                            link = it.link,
                            imageUrl = it.imageUrl,
                            status = "",
                            episode = "",
                            score = "",
                            type = ""
                        )
                    }
                    _historyAnime.value = converted
                }
            }
        }
    }

}

sealed interface Screen {
    object Home : Screen
    object Search : Screen // Keeping it for compatibility
    object Detail : Screen
    object Episode : Screen
    object MyList : Screen // keeping for compatibility
    object Schedule : Screen
    object Catalog : Screen
    object Library : Screen
    object Profile : Screen
}
