import re
import os

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Extract ProfileAvatar
profile_avatar_regex = r"(data class ProfileAvatar\(.*?\))"
profile_avatar_match = re.search(profile_avatar_regex, content, re.DOTALL)
profile_avatar_code = profile_avatar_match.group(1)

# Remove from MainActivity
content = content.replace(profile_avatar_code, "")

with open("app/src/main/java/com/example/models/ProfileAvatar.kt", "w") as f:
    f.write("package com.example.models\n\nimport androidx.compose.ui.graphics.Color\n\n" + profile_avatar_code + "\n")


# 2. Extract ProfileScreen
profile_screen_regex = r"(@Composable\s+fun ProfileScreen\(viewModel: AnimeViewModel\)\s+\{.*?^\})"
profile_screen_match = re.search(profile_screen_regex, content, re.MULTILINE | re.DOTALL)
profile_screen_code = profile_screen_match.group(1)

# Remove from MainActivity
content = content.replace(profile_screen_code, "")

with open("app/src/main/java/com/example/ui/screens/ProfileScreen.kt", "w") as f:
    imports = """package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnimeViewModel
import com.example.ui.Screen
import com.example.models.ProfileAvatar

"""
    f.write(imports + profile_screen_code + "\n")

# Update MainActivity imports
import_stmt = "import com.example.models.ProfileAvatar\nimport com.example.ui.screens.ProfileScreen\n"
if "import com.example.ui.*" in content:
    content = content.replace("import com.example.ui.*", "import com.example.ui.*\n" + import_stmt)
else:
    content = content.replace("package com.example\n", "package com.example\n\n" + import_stmt)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

