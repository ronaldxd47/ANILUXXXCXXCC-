import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

detail_regex = r"(@Composable\s+fun DetailScreen\(viewModel: AnimeViewModel\)\s+\{.*?\n\})\n\n@OptIn"
detail_match = re.search(detail_regex, content, re.MULTILINE | re.DOTALL)
if detail_match:
    detail_code = detail_match.group(1)
    
    # Remove from MainActivity
    new_content = content.replace(detail_code + "\n\n", "")
    
    # Update MainActivity imports
    import_stmt = "import com.example.ui.screens.DetailScreen\n"
    if "import com.example.ui.*" in new_content:
        new_content = new_content.replace("import com.example.ui.*", "import com.example.ui.*\n" + import_stmt)
    else:
        new_content = new_content.replace("package com.example\n", "package com.example\n\n" + import_stmt)

    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(new_content)
        
    with open("app/src/main/java/com/example/ui/screens/DetailScreen.kt", "w") as f:
        imports = """package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.ScrapedAnime
import com.example.data.AnimeDetails
import com.example.data.Episode
import com.example.MetaBorderChip

"""
        f.write(imports + detail_code + "\n")
        print("DetailScreen extracted successfully.")
