package com.casureco2.outages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.casureco2.outages.data.model.RawPost

@Dao
interface RawPostDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(posts: List<RawPost>)

    @Query("SELECT * FROM raw_posts WHERE parsed = 0 ORDER BY scraped_at DESC")
    suspend fun getPending(): List<RawPost>

    @Query("SELECT * FROM raw_posts WHERE parsed = 2 ORDER BY scraped_at DESC")
    suspend fun getFailed(): List<RawPost>

    @Query("SELECT COUNT(*) FROM raw_posts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM raw_posts WHERE parsed = 0")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM raw_posts WHERE parsed = 1")
    suspend fun countParsed(): Int

    @Query("SELECT COUNT(*) FROM raw_posts WHERE parsed = 2")
    suspend fun countFailed(): Int

    @Query("UPDATE raw_posts SET parsed = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM raw_posts WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
