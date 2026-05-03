package com.casureco2.outages.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.model.ParsedOutage
import com.casureco2.outages.util.Config
import com.casureco2.outages.util.NotificationHelper
import com.casureco2.outages.util.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ParserWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ParserWorker"
        const val OUTPUT_PARSED = "parsed_count"
        const val OUTPUT_SKIPPED = "skipped_count"
        const val OUTPUT_FAILED = "failed_count"
    }

    private val db = OutageDatabase.getInstance(context)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pending = db.rawPostDao().getPending()
        if (pending.isEmpty()) {
            return@withContext Result.success()
        }

        var parsedCount = 0
        var skippedCount = 0
        var failedCount = 0

        val apiKey = SecureStorage.openCodeApiKey
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "OpenCode API key not set")
            return@withContext Result.failure()
        }

        for (post in pending) {
            try {
                val result = parsePost(post.text, apiKey)

                if (result?.skip == true) {
                    db.rawPostDao().updateStatus(post.id, 1)
                    skippedCount++
                    continue
                }

                if (result != null) {
                    // Check date threshold
                    val postDate = try {
                        LocalDate.parse(result.date, DateTimeFormatter.ISO_DATE)
                    } catch (e: Exception) {
                        null
                    }

                    val cutoff = LocalDate.now().minusDays(Config.PARSE_SKIP_THRESHOLD_DAYS.toLong())
                    if (postDate != null && postDate.isBefore(cutoff)) {
                        db.rawPostDao().updateStatus(post.id, 1)
                        skippedCount++
                        continue
                    }

                    val outage = ParsedOutage(
                        id = post.id,
                        barangays = Json.encodeToString(result.barangays),
                        date = result.date,
                        timeStart = result.time_start,
                        timeEnd = result.time_end,
                        reason = result.reason ?: "Scheduled maintenance",
                        scope = result.scope ?: "Whole",
                        rawPostId = post.id
                    )

                    db.parsedOutageDao().insert(outage)
                    db.rawPostDao().updateStatus(post.id, 1)
                    parsedCount++
                } else {
                    db.rawPostDao().updateStatus(post.id, 2)
                    failedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse post ${post.id}", e)
                db.rawPostDao().updateStatus(post.id, 2)
                failedCount++
            }
        }

        Result.success(
            androidx.work.workDataOf(
                OUTPUT_PARSED to parsedCount,
                OUTPUT_SKIPPED to skippedCount,
                OUTPUT_FAILED to failedCount
            )
        )
    }

    private suspend fun parsePost(text: String, apiKey: String): ParseResult? = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a structured data extractor for power outage advisories in Camarines Sur, Philippines.
            Given a raw Facebook post from CASURECO 2 (Camarines Sur II Electric Cooperative), extract the
            following fields and respond ONLY with a valid JSON object. No explanation, no markdown.

            Fields:
            - barangays: array of strings — affected barangay names, normalized to title case.
                         If "all barangays" or similar is mentioned, return ["ALL"].
                         If a municipality is affected wholesale, list its barangays individually if known,
                         otherwise return the municipality name suffixed with " (all)".
            - date: ISO date string (YYYY-MM-DD). Infer year from context if missing (current year).
            - time_start: "HH:MM" in 24h format, or null if not specified.
            - time_end:   "HH:MM" in 24h format, or null if not specified.
            - reason: short string describing the cause, e.g. "Scheduled maintenance", "Line repair".
            - scope: "Whole" if the entire area is affected, "Partial" otherwise.

            If the post is not an outage advisory, return: {"skip": true}
        """.trimIndent()

        val requestBody = """
            {
                "model": "opencode/big-pickle",
                "messages": [
                    {"role": "system", "content": "$systemPrompt"},
                    {"role": "user", "content": ${Json.encodeToString(text)}}
                ],
                "temperature": 0.1
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://opencode.ai/zen/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        var retries = 0
        while (retries < 3) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e(TAG, "API call failed", e)
                delay(60_000)
                retries++
                continue
            }

            if (response.code == 429) {
                delay(60_000)
                retries++
                continue
            }

            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $body")
                return@withContext null
            }

            try {
                val completion = json.decodeFromString<CompletionResponse>(body)
                val content = completion.choices.firstOrNull()?.message?.content ?: return@withContext null
                return@withContext json.decodeFromString<ParseResult>(content)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse error: $body", e)
                return@withContext null
            }
        }

        null
    }

    @Serializable
    data class CompletionResponse(
        val choices: List<Choice>
    )

    @Serializable
    data class Choice(
        val message: Message
    )

    @Serializable
    data class Message(
        val content: String
    )

    @Serializable
    data class ParseResult(
        val skip: Boolean? = null,
        val barangays: List<String>? = null,
        val date: String? = null,
        val time_start: String? = null,
        val time_end: String? = null,
        val reason: String? = null,
        val scope: String? = null
    )
}
