with open("app/src/main/java/com/example/ui/components/AllComponents.kt", "r") as f:
    content = f.read()

imports_to_add = """
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.pager.*
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import android.webkit.RenderProcessGoneDetail
"""

content = content.replace("package com.example.ui.components", "package com.example.ui.components\n" + imports_to_add)

with open("app/src/main/java/com/example/ui/components/AllComponents.kt", "w") as f:
    f.write(content)

import glob

for filename in glob.glob("app/src/main/java/com/example/ui/screens/*.kt"):
    with open(filename, "r") as f:
        file_content = f.read()
    
    if "import com.example.ui.components.*" not in file_content:
        file_content = file_content.replace("import com.example.ui.UiState", "import com.example.ui.UiState\nimport com.example.ui.components.*\n")
    
    with open(filename, "w") as f:
        f.write(file_content)

