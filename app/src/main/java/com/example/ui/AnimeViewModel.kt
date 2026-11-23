package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AnimeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AnimeViewModel"
        private const val AUTO_REFRESH_INTERVAL_MS = 300_000L
    }

    // Repository Abstractions (Clean Architecture — siap migrasi ke backend REST API)
    private val db = AppDatabase.getDatabase(application)
    private val libraryRepository: UserLibraryRepository = LocalUserLibraryRepository(db.savedAnimeDao())
    private val scheduleRepository: ScheduleRepository = ScheduleRepository()
    private val animeRepository: AnimeRepository = MultiSourceAnimeRepository(application)
    private val preferencesRepository: PreferencesRepository = LocalPreferencesRepository(application)

    // Screen State
    private val _activeScreen = MutableStateFlow<Screen>(Screen.Home)
    val activeScreen: StateFlow<Screen> = _activeScreen.asStateFlow()

    // Home Feeds
    private val _latestUpdates = MutableStateFlow<UiState<List<ScrapedAnime>>>(UiState.Idle)
    val latestUpdates: StateFlow<UiState<List<ScrapedAnime>>> = _latestUpdates.asStateFlow()

    private val _trendingAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val trendingAnime: StateFlow<List<ScrapedAnime>> = _trendingAnime.asStateFlow()

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<ScrapedAnime>>>(UiState.Idle)
    val searchResults: StateFlow<UiState<List<ScrapedAnime>>> = _searchResults.asStateFlow()

    val recentSearches: StateFlow<List<String>> = preferencesRepository.recentSearches

    // Detail & Episode State
    private val _selectedAnimeUrl = MutableStateFlow<String?>(null)
    val selectedAnimeUrl: StateFlow<String?> = _selectedAnimeUrl.asStateFlow()

    private val _animeDetail = MutableStateFlow<UiState<AnimeDetail>>(UiState.Idle)
    val animeDetail: StateFlow<UiState<AnimeDetail>> = _animeDetail.asStateFlow()

    private val _selectedEpisodeUrl = MutableStateFlow<String?>(null)
    val selectedEpisodeUrl: StateFlow<String?> = _selectedEpisodeUrl.asStateFlow()

    private val _episodeDetail = MutableStateFlow<UiState<EpisodeDetail>>(UiState.Idle)
    val episodeDetail: StateFlow<UiState<EpisodeDetail>> = _episodeDetail.asStateFlow()

    // Library (Bookmarks & History)
    private val _bookmarkedAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val bookmarkedAnime: StateFlow<List<ScrapedAnime>> = _bookmarkedAnime.asStateFlow()

    private val _historyAnime = MutableStateFlow<List<ScrapedAnime>>(emptyList())
    val historyAnime: StateFlow<List<ScrapedAnime>> = _historyAnime.asStateFlow()

    // Schedule State
    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleDay>>>(UiState.Idle)
    val scheduleState: StateFlow<UiState<List<ScheduleDay>>> = _scheduleState.asStateFlow()

    // User Profile, Preferences & Cache
    val userProfile: StateFlow<UserProfile> = preferencesRepository.userProfile
    val playbackPreferences: StateFlow<PlaybackPreferences> = preferencesRepository.playbackPreferences

    private val _cacheSize = MutableStateFlow("0 KB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val navigationStack = mutableListOf<Screen>()
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var episodeJob: Job? = null

    init {
        observeUserLibrary()
        startPeriodicFeedSync()
        fetchScheduleData(forceRefresh = false)
    }

    private fun observeUserLibrary() {
        viewModelScope.launch {
            launch {
                libraryRepository.bookmarksFlow.collect { _bookmarkedAnime.value = it }
            }
            launch {
                libraryRepository.historyFlow.collect { _historyAnime.value = it }
            }
        }
    }

    private fun startPeriodicFeedSync() {
        viewModelScope.launch {
            while (isActive) {
                refreshData(forceRefresh = false)
                delay(AUTO_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshData(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                if (_latestUpdates.value !is UiState.Success || forceRefresh) {
                    if (_latestUpdates.value !is UiState.Success) {
                        _latestUpdates.value = UiState.Loading
                    }
                }

                val page1 = animeRepository.getLatestUpdates(
                    phase1Timeout = 9000L,
                    forceRefresh = forceRefresh
                )
                if (page1.isNotEmpty()) {
                    _latestUpdates.value = UiState.Success(page1)
                    _trendingAnime.value = buildSpotlightList(page1)
                }

                // Background extension page 2
                val page2 = animeRepository.getPageUpdates(
                    page = 2,
                    timeout = 12000L,
                    forceRefresh = forceRefresh
                )
                val combined = (page1 + page2).distinctBy { it.link }
                if (combined.isNotEmpty()) {
                    _latestUpdates.value = UiState.Success(combined)
                    _trendingAnime.value = buildSpotlightList(combined)
                } else if (_latestUpdates.value !is UiState.Success) {
                    _latestUpdates.value = UiState.Error("Gagal memuat rilis terbaru. Periksa koneksi internet.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshData error: ${e.message}")
                if (_latestUpdates.value !is UiState.Success) {
                    _latestUpdates.value = UiState.Error(e.message ?: "Gagal memuat data")
                }
            }
        }
    }

    /**
     * Menyusun daftar sorotan editorial berdasarkan skor nyata (jika tersedia)
     * dan variasi sumber (Anime JP & Donghua), tanpa data fiktif.
     */
    private fun buildSpotlightList(items: List<ScrapedAnime>): List<ScrapedAnime> {
        return items
            .sortedWith(
                compareByDescending<ScrapedAnime> { item ->
                    item.score.toFloatOrNull() ?: 0f
                }
            )
            .take(12)
    }

    fun fetchScheduleData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_scheduleState.value !is UiState.Success || forceRefresh) {
                _scheduleState.value = UiState.Loading
            }
            try {
                val data = scheduleRepository.getReleaseSchedule(forceRefresh = forceRefresh)
                if (data.isNotEmpty()) {
                    _scheduleState.value = UiState.Success(data)
                } else if (_scheduleState.value !is UiState.Success) {
                    _scheduleState.value = UiState.Error("Jadwal rilis belum tersedia")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchScheduleData error: ${e.message}")
                _scheduleState.value = UiState.Error(e.message ?: "Gagal memuat jadwal rilis")
            }
        }
    }

    fun performSearch(query: String, immediateSaveHistory: Boolean = false) {
        _searchQuery.value = query
        searchJob?.cancel()

        val clean = query.trim()
        if (clean.isEmpty()) {
            _searchResults.value = UiState.Idle
            return
        }

        if (immediateSaveHistory) {
            preferencesRepository.addSearchHistory(clean)
        }

        searchJob = viewModelScope.launch {
            if (!immediateSaveHistory) {
                delay(380)
            }
            _searchResults.value = UiState.Loading
            val results = animeRepository.searchAnime(clean, timeout = 7000L)
            if (results.isNotEmpty()) {
                _searchResults.value = UiState.Success(results)
                if (clean.length >= 3) {
                    preferencesRepository.addSearchHistory(clean)
                }
            } else {
                _searchResults.value = UiState.Error("Tidak ada hasil untuk \"$clean\"")
            }
        }
    }

    fun clearSearchHistory() {
        preferencesRepository.clearSearchHistory()
    }

    fun selectAnime(url: String) {
        if (AnimeSource.fromUrl(url) == AnimeSource.MAL) {
            val scheduleLists = (_scheduleState.value as? UiState.Success)?.data ?: emptyList()
            val matchTitle = scheduleLists.asSequence()
                .flatMap { it.animeList.asSequence() }
                .firstOrNull { it.link == url }
                ?.title
                .orEmpty()

            if (matchTitle.isNotEmpty()) {
                performSearch(matchTitle, immediateSaveHistory = true)
                navigateTo(Screen.Search)
            }
            return
        }

        _selectedAnimeUrl.value = url
        navigateTo(Screen.Detail)

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _animeDetail.value = UiState.Loading
            val details = animeRepository.getAnimeDetail(url, timeout = 20000L)
            if (details != null && details.title.isNotEmpty() && details.title != "Title Missing") {
                val finalDetails = if (details.episodes.isEmpty()) {
                    details.copy(episodes = listOf(EpisodeItem(title = "Putar Episode Ini", link = url)))
                } else {
                    details
                }
                _animeDetail.value = UiState.Success(finalDetails)
                libraryRepository.recordHistory(
                    link = url,
                    title = finalDetails.title,
                    imageUrl = finalDetails.posterUrl
                )
            } else {
                _animeDetail.value = UiState.Error("Gagal memuat detail judul ini. Periksa koneksi lalu coba lagi.")
            }
        }
    }

    fun retryAnimeDetail() {
        _selectedAnimeUrl.value?.let { selectAnime(it) }
    }

    fun selectEpisode(url: String) {
        _selectedEpisodeUrl.value = url
        navigateTo(Screen.Episode)

        episodeJob?.cancel()
        episodeJob = viewModelScope.launch {
            _episodeDetail.value = UiState.Loading
            val episodeDetails = animeRepository.getEpisodeDetail(url, timeout = 20000L)
            if (episodeDetails != null && (episodeDetails.streamEmbeds.isNotEmpty() || episodeDetails.downloads.isNotEmpty())) {
                _episodeDetail.value = UiState.Success(episodeDetails)
            } else {
                _episodeDetail.value = UiState.Error("Server video belum tersedia atau timeout. Silakan coba muat ulang.")
            }
        }
    }

    fun retryEpisodeDetail() {
        _selectedEpisodeUrl.value?.let { selectEpisode(it) }
    }

    // Watch Progress Tracking
    fun saveEpisodeProgress(
        episodeUrl: String,
        episodeTitle: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val animeUrl = _selectedAnimeUrl.value.orEmpty()
        preferencesRepository.saveEpisodeProgress(
            animeUrl = animeUrl,
            progress = EpisodeProgress(
                episodeUrl = episodeUrl,
                episodeTitle = episodeTitle,
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }

    fun getLastEpisodeProgress(animeUrl: String): EpisodeProgress? =
        preferencesRepository.getLastEpisodeProgress(animeUrl)

    fun getPlaybackPosition(episodeUrl: String): Long =
        preferencesRepository.getPlaybackPosition(episodeUrl)

    // Library Actions
    fun toggleBookmark(anime: ScrapedAnime) {
        viewModelScope.launch {
            if (isBookmarked(anime.link)) {
                libraryRepository.removeBookmark(anime.link)
            } else {
                libraryRepository.saveBookmark(anime)
            }
        }
    }

    fun removeBookmark(link: String) {
        viewModelScope.launch { libraryRepository.removeBookmark(link) }
    }

    fun removeHistoryItem(link: String) {
        viewModelScope.launch { libraryRepository.removeHistoryItem(link) }
    }

    fun clearHistoryOnly() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }

    fun clearFavoritesOnly() {
        viewModelScope.launch { libraryRepository.clearBookmarks() }
    }

    fun clearAllHistoryAndFavorites() {
        viewModelScope.launch { libraryRepository.clearAll() }
    }

    fun isBookmarked(link: String): Boolean =
        _bookmarkedAnime.value.any { it.link == link }

    // Profile & Playback Preferences Actions
    fun updateProfile(name: String, title: String, email: String, avatarId: Int) {
        preferencesRepository.updateProfile(
            UserProfile(name = name, title = title, email = email, avatarId = avatarId)
        )
    }

    fun setPreferredServer(server: String) {
        preferencesRepository.updatePlaybackPreferences { it.copy(preferredServer = server) }
    }

    fun setDownloadQuality(quality: String) {
        preferencesRepository.updatePlaybackPreferences { it.copy(defaultQuality = quality) }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        preferencesRepository.updatePlaybackPreferences { it.copy(autoPlayNext = enabled) }
    }

    fun setHardwareAccel(enabled: Boolean) {
        preferencesRepository.updatePlaybackPreferences { it.copy(hardwareAccel = enabled) }
    }

    fun setNotifyUpdates(enabled: Boolean) {
        preferencesRepository.updatePlaybackPreferences { it.copy(notifyUpdates = enabled) }
    }

    fun setDataSaver(enabled: Boolean) {
        preferencesRepository.updatePlaybackPreferences { it.copy(dataSaver = enabled) }
    }

    fun setSubtitleStyle(style: String) {
        preferencesRepository.updatePlaybackPreferences { it.copy(subtitleStyle = style) }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = preferencesRepository.calculateCacheSizeLabel()
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            animeRepository.invalidateCache()
            scheduleRepository.invalidateCache()
            _cacheSize.value = preferencesRepository.clearAppCache()
        }
    }

    // Navigation
    private fun isTopLevelScreen(screen: Screen): Boolean = when (screen) {
        Screen.Home, Screen.Schedule, Screen.Catalog, Screen.Library, Screen.MyList, Screen.Profile -> true
        else -> false
    }

    fun navigateTo(screen: Screen) {
        val current = _activeScreen.value
        if (current == screen) return

        if (isTopLevelScreen(screen)) {
            navigationStack.clear()
            if (screen != Screen.Home) {
                navigationStack.add(Screen.Home)
            }
        } else {
            navigationStack.add(current)
            if (navigationStack.size > 20) {
                navigationStack.removeAt(0)
            }
        }
        _activeScreen.value = screen
    }

    fun goBack(): Boolean {
        if (navigationStack.isNotEmpty()) {
            _activeScreen.value = navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        if (_activeScreen.value != Screen.Home) {
            _activeScreen.value = Screen.Home
            return true
        }
        return false
    }
}

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    object Detail : Screen
    object Episode : Screen
    object MyList : Screen
    object Schedule : Screen
    object Catalog : Screen
    object Library : Screen
    object Profile : Screen
}
