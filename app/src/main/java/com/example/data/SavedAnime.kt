package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_animes")
data class SavedAnime(
    @PrimaryKey val link: String,
    val title: String,
    val imageUrl: String,
    val status: String,
    val episode: String,
    val score: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)
