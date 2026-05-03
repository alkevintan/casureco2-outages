package com.casureco2.outages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.casureco2.outages.data.model.Barangay

@Dao
interface BarangayDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(barangays: List<Barangay>)

    @Query("SELECT * FROM barangays ORDER BY municipality, name")
    suspend fun getAll(): List<Barangay>

    @Query("SELECT * FROM barangays WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): Barangay?

    @Query("SELECT COUNT(*) FROM barangays")
    suspend fun count(): Int
}
