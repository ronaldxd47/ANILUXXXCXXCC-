package com.example.models

import androidx.compose.ui.graphics.Color

data class ProfileAvatar(
    val id: Int,
    val name: String,
    val title: String,
    val gradient: List<Color>,
    val accentColor: Color
)
