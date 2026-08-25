import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Remove the duplicated @OptIn in MainActivity.kt
content = re.sub(r"@OptIn\(ExperimentalLayoutApi::class\)\s+@OptIn\(ExperimentalLayoutApi::class\)", "@OptIn(ExperimentalLayoutApi::class)", content)
content = re.sub(r"@OptIn\(ExperimentalLayoutApi::class\)\s+@Composable\s+fun EpisodeScreen", "@OptIn(ExperimentalLayoutApi::class)\n@Composable\nfun EpisodeScreen", content)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/ui/screens/DetailScreen.kt", "r") as f:
    detail = f.read()

# Fix imports in DetailScreen.kt
detail = detail.replace("import com.example.data.AnimeDetails\n", "")
detail = detail.replace("import com.example.data.Episode\n", "")
detail = detail.replace("import com.example.MetaBorderChip\n", "")
detail = detail.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\nimport com.example.utils.FormatUtils\nimport com.example.MetaBorderChip\nimport com.example.SkeletonBox\nimport com.example.ResolveAnimeImage\n")

with open("app/src/main/java/com/example/ui/screens/DetailScreen.kt", "w") as f:
    f.write(detail)
