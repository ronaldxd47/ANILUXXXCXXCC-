package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kontrak Repository untuk daftar favorit (watchlist) dan riwayat menonton pengguna.
 * Abstraksi ini memudahkan migrasi dari Room lokal ke backend sync di masa depan.
 */
interface UserLibraryRepository {
    val bookmarksFlow: Flow<List<ScrapedAnime>>
    val historyFlow: Flow<List<ScrapedAnime>>

    suspend fun saveBookmark(anime: ScrapedAnime)
    suspend fun removeBookmark(link: String)
    suspend fun clearBookmarks()

    suspend fun recordHistory(link: String, title: String, imageUrl: String)
    suspend fun removeHistoryItem(link: String)
    suspend fun clearHistory()

    suspend fun clearAll()
}

class LocalUserLibraryRepository(
    private val dao: SavedAnimeDao
) : UserLibraryRepository {

    override val bookmarksFlow: Flow<List<ScrapedAnime>> =
        dao.getAllSavedAnimes().map { savedList ->
            savedList.map {
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
        }

    override val historyFlow: Flow<List<ScrapedAnime>> =
        dao.getAllHistory().map { historyList ->
            historyList.map {
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
        }

    override suspend fun saveBookmark(anime: ScrapedAnime) {
        dao.insertAnime(
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

    override suspend fun removeBookmark(link: String) {
        dao.deleteAnimeByLink(link)
    }

    override suspend fun clearBookmarks() {
        dao.deleteAll()
    }

    override suspend fun recordHistory(link: String, title: String, imageUrl: String) {
        dao.insertHistory(
            HistoryAnime(
                link = link,
                title = title,
                imageUrl = imageUrl,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeHistoryItem(link: String) {
        dao.deleteHistoryByLink(link)
    }

    override suspend fun clearHistory() {
        dao.deleteAllHistory()
    }

    override suspend fun clearAll() {
        dao.deleteAll()
        dao.deleteAllHistory()
    }
}

typealias SavedAnimeRepository = LocalUserLibraryRepository
