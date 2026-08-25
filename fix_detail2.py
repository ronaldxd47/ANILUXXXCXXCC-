with open("app/src/main/java/com/example/ui/screens/DetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import com.example.ui.components.*\n", "")
content = content.replace("import com.example.data.ScrapedAnime\n", "import com.example.data.*\n")

with open("app/src/main/java/com/example/ui/screens/DetailScreen.kt", "w") as f:
    f.write(content)
