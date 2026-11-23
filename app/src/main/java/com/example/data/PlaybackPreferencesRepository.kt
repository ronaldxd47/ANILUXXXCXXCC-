package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

data class UserProfile(
    val name: String = "Penonton",
    val title: String = "Kolektor Anime",
    val email: String = "akun@vibestream.local",
    val avatarId: Int = 0
)

data class PlaybackPreferences(
    val preferredServer: String = "Auto (Kualitas Tertinggi)",
    val defaultQuality: String = "Auto",
    val autoPlayNext: Boolean = true,
    val hardwareAccel: Boolean = true,
    val notifyUpdates: Boolean = true,
    val dataSaver: Boolean = false,
    val subtitleStyle: String = "Kuning Sinematik"
)

data class EpisodeProgress(
    val episodeUrl: String,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)

interface PreferencesRepository {
    val userProfile: StateFlow<UserProfile>
    val playbackPreferences: StateFlow<PlaybackPreferences>
    val recentSearches: StateFlow<List<String>>

    fun updateProfile(profile: UserProfile)
    fun updatePlaybackPreferences(transform: (PlaybackPreferences) -> PlaybackPreferences)

    fun saveEpisodeProgress(animeUrl: String, progress: EpisodeProgress)
    fun getLastEpisodeProgress(animeUrl: String): EpisodeProgress?
    fun getPlaybackPosition(episodeUrl: String): Long

    fun addSearchHistory(query: String)
    fun clearSearchHistory()

    suspend fun calculateCacheSizeLabel(): String
    suspend fun clearAppCache(): String
}

class LocalPreferencesRepository(context: Context) : PreferencesRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("animestream_prefs", Context.MODE_PRIVATE)

    init {
        if (prefs.contains("bookmarks")) {
            prefs.edit().remove("bookmarks").apply()
        }
    }

    private val _userProfile = MutableStateFlow(loadProfile())
    override val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _playbackPreferences = MutableStateFlow(loadPlaybackPreferences())
    override val playbackPreferences: StateFlow<PlaybackPreferences> = _playbackPreferences.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadRecentSearches())
    override val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("profile_name", "Penonton") ?: "Penonton",
            title = prefs.getString("profile_title", "Kolektor Anime") ?: "Kolektor Anime",
            email = prefs.getString("profile_email", "akun@vibestream.local") ?: "akun@vibestream.local",
            avatarId = prefs.getInt("profile_avatar_id", 0)
        )
    }

    private fun loadPlaybackPreferences(): PlaybackPreferences {
        return PlaybackPreferences(
            preferredServer = prefs.getString("pref_server", "Auto (Kualitas Tertinggi)") ?: "Auto (Kualitas Tertinggi)",
            defaultQuality = prefs.getString("pref_quality", "Auto") ?: "Auto",
            autoPlayNext = prefs.getBoolean("pref_autoplay", true),
            hardwareAccel = prefs.getBoolean("pref_hardware_accel", true),
            notifyUpdates = prefs.getBoolean("pref_notify", true),
            dataSaver = prefs.getBoolean("pref_datasaver", false),
            subtitleStyle = prefs.getString("pref_subtitle", "Kuning Sinematik") ?: "Kuning Sinematik"
        )
    }

    private fun loadRecentSearches(): List<String> {
        val raw = prefs.getString("recent_searches", "") ?: ""
        return raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
    }

    override fun updateProfile(profile: UserProfile) {
        _userProfile.value = profile
        prefs.edit()
            .putString("profile_name", profile.name)
            .putString("profile_title", profile.title)
            .putString("profile_email", profile.email)
            .putInt("profile_avatar_id", profile.avatarId)
            .apply()
    }

    override fun updatePlaybackPreferences(transform: (PlaybackPreferences) -> PlaybackPreferences) {
        val updated = transform(_playbackPreferences.value)
        _playbackPreferences.value = updated
        prefs.edit()
            .putString("pref_server", updated.preferredServer)
            .putString("pref_quality", updated.defaultQuality)
            .putBoolean("pref_autoplay", updated.autoPlayNext)
            .putBoolean("pref_hardware_accel", updated.hardwareAccel)
            .putBoolean("pref_notify", updated.notifyUpdates)
            .putBoolean("pref_datasaver", updated.dataSaver)
            .putString("pref_subtitle", updated.subtitleStyle)
            .apply()
    }

    override fun saveEpisodeProgress(animeUrl: String, progress: EpisodeProgress) {
        if (progress.episodeUrl.isBlank()) return
        val epKey = "pos_${progress.episodeUrl.hashCode()}"
        val editor = prefs.edit().putLong(epKey, progress.positionMs.coerceAtLeast(0L))
        if (animeUrl.isNotBlank()) {
            val baseKey = "last_ep_${animeUrl.hashCode()}"
            editor
                .putString("${baseKey}_url", progress.episodeUrl)
                .putString("${baseKey}_title", progress.episodeTitle)
                .putLong("${baseKey}_pos", progress.positionMs)
                .putLong("${baseKey}_dur", progress.durationMs)
                .putLong("${baseKey}_ts", progress.updatedAtMs)
        }
        editor.apply()
    }

    override fun getLastEpisodeProgress(animeUrl: String): EpisodeProgress? {
        if (animeUrl.isBlank()) return null
        val baseKey = "last_ep_${animeUrl.hashCode()}"
        val epUrl = prefs.getString("${baseKey}_url", null) ?: return null
        val epTitle = prefs.getString("${baseKey}_title", "") ?: ""
        val pos = prefs.getLong("${baseKey}_pos", 0L)
        val dur = prefs.getLong("${baseKey}_dur", 0L)
        val ts = prefs.getLong("${baseKey}_ts", 0L)
        return EpisodeProgress(epUrl, epTitle, pos, dur, ts)
    }

    override fun getPlaybackPosition(episodeUrl: String): Long {
        if (episodeUrl.isBlank()) return 0L
        return prefs.getLong("pos_${episodeUrl.hashCode()}", 0L)
    }

    override fun addSearchHistory(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val updated = (listOf(clean) + _recentSearches.value.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
        _recentSearches.value = updated
        prefs.edit().putString("recent_searches", updated.joinToString("||")).apply()
    }

    override fun clearSearchHistory() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("recent_searches").apply()
    }

    override suspend fun calculateCacheSizeLabel(): String = withContext(Dispatchers.IO) {
        try {
            var totalBytes = 0L
            appContext.cacheDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
            appContext.externalCacheDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
            formatBytes(totalBytes)
        } catch (_: Exception) {
            "0 KB"
        }
    }

    override suspend fun clearAppCache(): String = withContext(Dispatchers.IO) {
        try {
            appContext.cacheDir?.deleteRecursively()
            appContext.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {
        }
        "0 KB"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}
