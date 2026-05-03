package com.casureco2.outages.util

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

object AppLog {

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val message: String
    )

    private val entries = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_ENTRIES = 500

    fun d(message: String) = add("DEBUG", message)
    fun i(message: String) = add("INFO", message)
    fun w(message: String) = add("WARN", message)
    fun e(message: String) = add("ERROR", message)

    fun add(level: String, message: String) {
        val entry = LogEntry(
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            level = level,
            message = message
        )
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun getEntries(): List<LogEntry> = entries.toList()

    fun clear() = entries.clear()
}
