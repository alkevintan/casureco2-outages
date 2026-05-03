package com.casureco2.outages.worker

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.model.Barangay
import com.casureco2.outages.util.Config
import com.casureco2.outages.util.NotificationHelper
import com.casureco2.outages.util.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.withContext

class GitHubSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "GitHubSyncWorker"
    }

    private val db = OutageDatabase.getInstance(context)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = SecureStorage.githubToken
        val owner = SecureStorage.githubOwner
        val repo = SecureStorage.githubRepo

        if (token.isNullOrBlank() || owner.isNullOrBlank() || repo.isNullOrBlank()) {
            Log.e(TAG, "GitHub credentials not configured")
            return@withContext Result.failure()
        }

        try {
            val icsMap = mutableMapOf<String, String>()
            inputData.keyValueMap.forEach { (k, v) ->
                if (v is String) icsMap[k] = v
            }

            if (icsMap.isEmpty()) {
                Log.i(TAG, "No ICS data to sync")
                return@withContext Result.success()
            }

            val barangays = db.barangayDao().getAll()
            val syncedIds = mutableListOf<String>()

            // Sync each ICS file
            for ((slug, content) in icsMap) {
                if (slug == "all") continue // sync all.ics last
                val success = syncFile(owner, repo, token, "ics/$slug.ics", content)
                if (success) {
                    syncedIds.add(slug)
                }
            }

            // Sync all.ics
            icsMap["all"]?.let { content ->
                syncFile(owner, repo, token, "ics/all.ics", content)
            }

            // Generate and sync barangays.json
            val barangaysJson = generateBarangaysJson(barangays, owner, repo)
            syncFile(owner, repo, token, "docs/barangays.json", barangaysJson)

            // Mark synced
            val unsynced = db.parsedOutageDao().getUnsynced()
            if (unsynced.isNotEmpty()) {
                db.parsedOutageDao().markSynced(unsynced.map { it.id })
            }

            val barangayCount = icsMap.size - 1 // exclude "all"
            val outageCount = unsynced.size
            NotificationHelper.showSuccess(applicationContext, outageCount, barangayCount)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "GitHub sync failed", e)
            NotificationHelper.showFailed(applicationContext, TAG, e.message ?: "Unknown error")
            Result.retry()
        }
    }

    private suspend fun syncFile(owner: String, repo: String, token: String, path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
        val currentSha = getFileSha(owner, repo, token, path)

        val body = if (currentSha != null) {
            """
            {
                "message": "chore: update $path [${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}]",
                "content": "$encodedContent",
                "sha": "$currentSha",
                "branch": "${Config.GITHUB_BRANCH}"
            }
            """.trimIndent()
        } else {
            """
            {
                "message": "chore: create $path [${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}]",
                "content": "$encodedContent",
                "branch": "${Config.GITHUB_BRANCH}"
            }
            """.trimIndent()
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        when (response.code) {
            200, 201 -> {
                Log.i(TAG, "Synced $path")
                true
            }
            409 -> {
                // Conflict: retry once with fresh SHA
                val newSha = getFileSha(owner, repo, token, path)
                if (newSha != null) {
                    val retryBody = """
                        {
                            "message": "chore: update $path",
                            "content": "$encodedContent",
                            "sha": "$newSha",
                            "branch": "${Config.GITHUB_BRANCH}"
                        }
                    """.trimIndent()
                    val retryRequest = Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                        .header("Authorization", "token $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .put(retryBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    val retryResponse = client.newCall(retryRequest).execute()
                    retryResponse.isSuccessful
                } else {
                    false
                }
            }
            401 -> {
                Log.e(TAG, "Unauthorized")
                false
            }
            else -> {
                Log.e(TAG, "GitHub API error ${response.code}: ${response.body?.string()}")
                false
            }
        }
    }

    private suspend fun getFileSha(owner: String, repo: String, token: String, path: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path?ref=${Config.GITHUB_BRANCH}")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null

        val body = response.body?.string() ?: return@withContext null
        try {
            json.decodeFromString<GitHubContentResponse>(body).sha
        } catch (e: Exception) {
            null
        }
    }

    private fun generateBarangaysJson(barangays: List<Barangay>, owner: String, repo: String): String {
        val items = barangays.map {
            BarangayJsonItem(
                name = it.name,
                slug = it.slug,
                municipality = it.municipality,
                ics_url = "https://raw.githubusercontent.com/$owner/$repo/${Config.GITHUB_BRANCH}/ics/${it.slug}.ics",
                webcal_url = "webcal://raw.githubusercontent.com/$owner/$repo/${Config.GITHUB_BRANCH}/ics/${it.slug}.ics"
            )
        }
        val data = BarangaysJson(
            generated_at = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            barangays = items
        )
        return json.encodeToString(data)
    }

    @Serializable
    data class GitHubContentResponse(
        val sha: String
    )

    @Serializable
    data class BarangaysJson(
        val generated_at: String,
        val barangays: List<BarangayJsonItem>
    )

    @Serializable
    data class BarangayJsonItem(
        val name: String,
        val slug: String,
        val municipality: String,
        val ics_url: String,
        val webcal_url: String
    )
}
