package com.casureco2.outages.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.model.RawPost
import com.casureco2.outages.util.Config
import com.casureco2.outages.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class ScrapingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ScrapingWorker"
        const val OUTPUT_NEW_POSTS = "new_posts_count"
    }

    private val db = OutageDatabase.getInstance(context)
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting scrape")
            NotificationHelper.showRunning(applicationContext)

            var url = "https://mbasic.facebook.com/hashtag/${Config.SCRAPE_HASHTAG}"
            var pages = 0
            val newPosts = mutableListOf<RawPost>()

            while (url.isNotBlank() && pages < Config.SCRAPE_MAX_PAGES) {
                val result = scrapePage(url)
                if (result == null) {
                    Log.w(TAG, "Session expired or network error")
                    NotificationHelper.showSessionExpired(applicationContext)
                    return@withContext Result.failure()
                }

                val (pagePosts, nextUrl) = result
                val filtered = pagePosts.filter { !db.rawPostDao().exists(it.id) }
                newPosts.addAll(filtered)

                url = nextUrl ?: ""
                pages++

                if (url.isNotBlank()) {
                    val delayMs = Random.nextLong(Config.SCRAPE_PAGE_DELAY_MS.first, Config.SCRAPE_PAGE_DELAY_MS.last)
                    delay(delayMs)
                }
            }

            if (newPosts.isNotEmpty()) {
                db.rawPostDao().insertAll(newPosts)
                Log.i(TAG, "Inserted ${newPosts.size} new posts")
            }

            Result.success(
                androidx.work.workDataOf(OUTPUT_NEW_POSTS to newPosts.size)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Scraping failed", e)
            NotificationHelper.showFailed(applicationContext, TAG, e.message ?: "Unknown error")
            Result.retry()
        }
    }

    private data class PageResult(val posts: List<RawPost>, val nextUrl: String?)

    private suspend fun scrapePage(url: String): PageResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Config.FB_USER_AGENT)
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}")
            return@withContext null
        }

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code}")
            return@withContext null
        }

        val body = response.body?.string() ?: return@withContext PageResult(emptyList(), null)

        // Check for login redirect
        if (body.contains("log in", ignoreCase = true) || body.contains("login", ignoreCase = true)) {
            if (body.length < 5000 || body.contains("password")) {
                return@withContext null
            }
        }

        val doc = Jsoup.parse(body, url)
        val posts = mutableListOf<RawPost>()
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        // mbasic.facebook.com post selectors
        val articles = doc.select("div[role=article], div.story_body_container, article")
        for (article in articles) {
            val permalink = article.selectFirst("a[href*=/story.php]")
                ?.attr("abs:href")
                ?: article.selectFirst("a[href*=/posts/]")
                    ?.attr("abs:href")
                ?: continue

            val text = article.selectFirst("div[data-ft]")
                ?.text()
                ?: article.selectFirst("p")
                    ?.text()
                ?: ""

            val author = article.selectFirst("h3 a")
                ?.text()
                ?: article.selectFirst("strong a")
                    ?.text()
                ?: "CASURECO 2"

            val timestamp = article.selectFirst("abbr")
                ?.text()
                ?: ""

            posts.add(
                RawPost(
                    id = permalink,
                    author = author,
                    text = text,
                    timestamp = timestamp,
                    scrapedAt = now
                )
            )
        }

        val nextUrl = doc.selectFirst("a:contains(See More Posts), a:contains(Show more), #more a")
            ?.attr("abs:href")

        PageResult(posts, nextUrl)
    }
}
