package com.casureco2.outages.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.casureco2.outages.data.model.ParsedOutage
import com.casureco2.outages.data.model.RawPost

@Dao
interface ParsedOutageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outage: ParsedOutage)

    @Query("SELECT * FROM parsed_outages WHERE synced = 0")
    suspend fun getUnsynced(): List<ParsedOutage>

    @Query("SELECT * FROM parsed_outages")
    suspend fun getAll(): List<ParsedOutage>

    @Query("SELECT COUNT(*) FROM parsed_outages")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM parsed_outages WHERE synced = 1")
    suspend fun countSynced(): Int

    @Query("UPDATE parsed_outages SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Transaction
    @Query("""
        SELECT o.*, p.id as post_id, p.author, p.text, p.timestamp, p.scraped_at, p.parsed 
        FROM parsed_outages o 
        INNER JOIN raw_posts p ON o.raw_post_id = p.id
    """)
    suspend fun getAllWithPosts(): List<OutageWithPost>

    @Query("DELETE FROM parsed_outages WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}

data class OutageWithPost(
    val id: String,
    val barangays: String,
    val date: String,
    @ColumnInfo(name = "time_start")
    val timeStart: String?,
    @ColumnInfo(name = "time_end")
    val timeEnd: String?,
    val reason: String,
    val scope: String,
    @ColumnInfo(name = "raw_post_id")
    val rawPostId: String,
    val synced: Int,
    @ColumnInfo(name = "post_id")
    val postId: String,
    val author: String,
    val text: String,
    val timestamp: String,
    @ColumnInfo(name = "scraped_at")
    val scrapedAt: String,
    val parsed: Int
)
