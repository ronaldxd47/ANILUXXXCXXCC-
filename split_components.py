import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# All code after 
# @Composable
# fun ResolveAnimeImage(
# to the end of the file, except setContent.
# Actually, wait. Let's find where the composables start.
# They start at "@Composable\nfun ResolveAnimeImage"

start_idx = content.find("@Composable\nfun ResolveAnimeImage")
if start_idx != -1:
    composables_content = content[start_idx:]
    new_main_content = content[:start_idx]
    
    with open("app/src/main/java/com/example/ui/components/AllComponents.kt", "w") as f:
        imports = """package com.example.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.ui.UiState
import com.example.data.*
import com.example.utils.FormatUtils
import kotlinx.coroutines.*

"""
        # There might be some helper functions mixed in, so let's copy all of them.
        f.write(imports + composables_content + "\n")
        print("AllComponents extracted.")
    
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        # We need to make sure import com.example.ui.components.* is added
        import_stmt = "import com.example.ui.components.*\n"
        if "import com.example.ui.components" not in new_main_content:
            new_main_content = new_main_content.replace("import com.example.ui.*", "import com.example.ui.*\n" + import_stmt)
        f.write(new_main_content)
else:
    print("Could not find start of composables.")

