import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

home_regex = r"(@Composable\s+fun HomeScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})$"
home_match = re.search(home_regex, content, re.MULTILINE | re.DOTALL)
if home_match:
    home_code = home_match.group(1)
    
    # Remove from MainActivity
    new_content = content.replace(home_code, "")
    
    with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
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
import com.example.*

"""
        f.write(imports + home_code + "\n")
        print("HomeScreen extracted.")
    
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(new_content)
else:
    print("HomeScreen not found.")
