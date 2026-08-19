package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAnimeDao {
    // Favorites
    @Query("SELECT * FROM saved_animes ORDER BY timestamp DESC")
    fun getAllSavedAnimes(): Flow<List<SavedAnime>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnime(anime: SavedAnime)

    @Query("DELETE FROM saved_animes WHERE link = :link")
    suspend fun deleteAnimeByLink(link: String)
    
    @Query("DELETE FROM saved_animes")
    suspend fun deleteAll()

    // History
    @Query("SELECT * FROM history_animes ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryAnime>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(anime: HistoryAnime)
    
    @Query("DELETE FROM history_animes")
    suspend fun deleteAllHistory()
}
