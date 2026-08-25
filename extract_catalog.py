import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

catalog_regex = r"(@Composable\s+fun CatalogScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})\n\n@Composable\s+fun AnimeBottomNavigation"
catalog_match = re.search(catalog_regex, content, re.MULTILINE | re.DOTALL)
if catalog_match:
    catalog_code = catalog_match.group(1)
    content = content.replace(catalog_code + "\n\n", "")
    with open("app/src/main/java/com/example/ui/screens/CatalogScreen.kt", "w") as f:
        imports = """package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.AnimeGridSection
import com.example.RekomendasiFantasySection

"""
        f.write(imports + catalog_code + "\n")
    print("CatalogScreen extracted.")
    
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

