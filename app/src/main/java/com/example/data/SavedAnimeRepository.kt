package com.example.data

import kotlinx.coroutines.flow.Flow

class SavedAnimeRepository(private val dao: SavedAnimeDao) {
    // Favorites
    val allSavedAnimes: Flow<List<SavedAnime>> = dao.getAllSavedAnimes()
    suspend fun insert(anime: SavedAnime) = dao.insertAnime(anime)
    suspend fun deleteByLink(link: String) = dao.deleteAnimeByLink(link)
    suspend fun deleteAll() = dao.deleteAll()

    // History
    val allHistory: Flow<List<HistoryAnime>> = dao.getAllHistory()
    suspend fun insertHistory(anime: HistoryAnime) = dao.insertHistory(anime)
    suspend fun deleteAllHistory() = dao.deleteAllHistory()
}
