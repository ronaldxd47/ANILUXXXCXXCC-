package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_animes")
data class HistoryAnime(
    @PrimaryKey val link: String,
    val title: String,
    val imageUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
