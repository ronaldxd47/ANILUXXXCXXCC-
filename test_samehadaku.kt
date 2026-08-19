import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup

fun main() = runBlocking {
    val scraper = SamehadakuScraper
    val result = scraper.fetchLatestUpdates(1)
    println("Result: ${result.size}")
    if (result.isNotEmpty()) {
        println(result[0])
    }
}
