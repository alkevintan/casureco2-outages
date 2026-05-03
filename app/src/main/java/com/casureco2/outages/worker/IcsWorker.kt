package com.casureco2.outages.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.data.model.Barangay
import com.casureco2.outages.util.Config
import com.casureco2.outages.util.NotificationHelper
import com.casureco2.outages.util.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.Description
import biweekly.property.Location
import biweekly.property.Status
import biweekly.property.Summary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class IcsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "IcsWorker"
    }

    private val db = OutageDatabase.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val outages = db.parsedOutageDao().getAllWithPosts()
            val barangays = db.barangayDao().getAll()

            if (outages.isEmpty()) {
                Log.i(TAG, "No outages to process")
                return@withContext Result.success()
            }

            val icsMap = mutableMapOf<String, String>()

            // Group outages by barangay slug
            val grouped = mutableMapOf<String, MutableList<com.casureco2.outages.data.dao.OutageWithPost>>()
            val allEvents = mutableListOf<com.casureco2.outages.data.dao.OutageWithPost>()

            for (outage in outages) {
                val barangayNames = try {
                    Json.decodeFromString<List<String>>(outage.barangays)
                } catch (e: Exception) {
                    listOf(outage.barangays)
                }

                for (name in barangayNames) {
                    val slug = findSlug(name, barangays) ?: slugify(name)
                    grouped.getOrPut(slug) { mutableListOf() }.add(outage)
                }
                allEvents.add(outage)
            }

            // Build per-barangay ICS
            for ((slug, events) in grouped) {
                val barangayName = barangays.find { it.slug == slug }?.name ?: deslugify(slug)
                val calendar = buildCalendar(barangayName, events)
                icsMap[slug] = calendar
            }

            // Build all.ics
            val allCalendar = buildCalendar("All Barangays", allEvents)
            icsMap["all"] = allCalendar

            // Serialize to WorkData
            val outputData = androidx.work.Data.Builder().apply {
                icsMap.forEach { (k, v) -> putString(k, v) }
            }.build()

            Log.i(TAG, "Generated ${icsMap.size} ICS files")
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e(TAG, "ICS generation failed", e)
            Result.retry()
        }
    }

    private fun buildCalendar(barangayName: String, events: List<com.casureco2.outages.data.dao.OutageWithPost>): String {
        val ical = ICalendar()
        ical.setProductId("-//CASURECO2 Outage Tracker//EN")

        for (event in events) {
            val vevent = VEvent()

            val startDate = parseDate(event.date)
            val startTime = event.timeStart?.let { parseTime(it) }
            val endTime = event.timeEnd?.let { parseTime(it) }

            if (startTime != null) {
                val startCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                    time = startDate
                    set(Calendar.HOUR_OF_DAY, startTime.first)
                    set(Calendar.MINUTE, startTime.second)
                    set(Calendar.SECOND, 0)
                }
                vevent.setDateStart(startCal.time, true)

                val endCal = if (endTime != null) {
                    Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                        time = startDate
                        set(Calendar.HOUR_OF_DAY, endTime.first)
                        set(Calendar.MINUTE, endTime.second)
                        set(Calendar.SECOND, 0)
                    }
                } else {
                    Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                        time = startDate
                        add(Calendar.HOUR_OF_DAY, 4)
                    }
                }
                vevent.setDateEnd(endCal.time, true)
            } else {
                // All-day event
                vevent.setDateStart(startDate, false)
                val endDate = Calendar.getInstance().apply { time = startDate; add(Calendar.DATE, 1) }.time
                vevent.setDateEnd(endDate, false)
            }

            vevent.setSummary("Power Interruption — ${event.scope}")
            vevent.setDescription("${event.reason}\n\nSource: ${event.rawPostId}")
            vevent.setLocation("$barangayName, Camarines Sur")
            vevent.setStatus(Status.confirmed())
            vevent.setUid("${event.id}@casureco2.github.io")

            ical.addEvent(vevent)
        }

        return Biweekly.write(ical).go()
    }

    private fun parseDate(dateStr: String): Date {
        val ld = LocalDate.parse(dateStr)
        return Date.from(ld.atStartOfDay(ZoneId.of("Asia/Manila")).toInstant())
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }

    private fun findSlug(name: String, barangays: List<Barangay>): String? {
        val normalized = name.trim().lowercase()
        return barangays.find {
            it.name.lowercase() == normalized || it.slug == normalized
        }?.slug
    }

    private fun slugify(input: String): String {
        return input.trim()
            .lowercase()
            .replace("[^a-z0-9\\s-]".toRegex(), "")
            .replace("\\s+".toRegex(), "-")
            .replace("-+", "-")
    }

    private fun deslugify(slug: String): String {
        return slug.replace("-", " ").replaceFirstChar { it.uppercase() }
    }
}
