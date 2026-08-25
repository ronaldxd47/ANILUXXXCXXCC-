import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

search_regex = r"(@Composable\s+fun SearchScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})\n\n@Composable\s+fun MetaBorderChip"
search_match = re.search(search_regex, content, re.MULTILINE | re.DOTALL)
if search_match:
    search_code = search_match.group(1)
    
    # Remove from MainActivity
    new_content = content.replace(search_code + "\n\n", "")
    
    # Update MainActivity imports
    import_stmt = "import com.example.ui.screens.SearchScreen\n"
    if "import com.example.ui.*" in new_content:
        new_content = new_content.replace("import com.example.ui.*", "import com.example.ui.*\n" + import_stmt)
    else:
        new_content = new_content.replace("package com.example\n", "package com.example\n\n" + import_stmt)

    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(new_content)
        
    with open("app/src/main/java/com/example/ui/screens/SearchScreen.kt", "w") as f:
        imports = """package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.CompactAnimeCard
import com.example.GenreGridCard

"""
        f.write(imports + search_code + "\n")
        print("SearchScreen extracted successfully.")
