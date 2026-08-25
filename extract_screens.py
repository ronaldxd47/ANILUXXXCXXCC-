import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# MyListScreen
mylist_regex = r"(@Composable\s+fun MyListScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})\n\n@Composable\s+fun ScheduleScreen"
mylist_match = re.search(mylist_regex, content, re.MULTILINE | re.DOTALL)
if mylist_match:
    mylist_code = mylist_match.group(1)
    content = content.replace(mylist_code + "\n\n", "")
    with open("app/src/main/java/com/example/ui/screens/MyListScreen.kt", "w") as f:
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
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.data.*
import com.example.CompactAnimeCard
import com.example.ResolveAnimeImage

"""
        f.write(imports + mylist_code + "\n")
    print("MyListScreen extracted.")

# ScheduleScreen
schedule_regex = r"(@Composable\s+fun ScheduleScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})\n\n@Composable\s+fun CatalogScreen"
schedule_match = re.search(schedule_regex, content, re.MULTILINE | re.DOTALL)
if schedule_match:
    schedule_code = schedule_match.group(1)
    content = content.replace(schedule_code + "\n\n", "")
    with open("app/src/main/java/com/example/ui/screens/ScheduleScreen.kt", "w") as f:
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
import com.example.CompactAnimeCard

"""
        f.write(imports + schedule_code + "\n")
    print("ScheduleScreen extracted.")

# CatalogScreen
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
import com.example.CompactAnimeCard

"""
        f.write(imports + catalog_code + "\n")
    print("CatalogScreen extracted.")


# Update MainActivity imports
import_stmt = "import com.example.ui.screens.*\n"
if "import com.example.ui.screens" not in content:
    if "import com.example.ui.*" in content:
        content = content.replace("import com.example.ui.*", "import com.example.ui.*\n" + import_stmt)
    else:
        content = content.replace("package com.example\n", "package com.example\n\n" + import_stmt)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

