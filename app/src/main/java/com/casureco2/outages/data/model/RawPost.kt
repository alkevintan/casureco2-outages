package com.casureco2.outages.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_posts")
data class RawPost(
    @PrimaryKey
    val id: String,
    val author: String,
    val text: String,
    val timestamp: String,
    @ColumnInfo(name = "scraped_at")
    val scrapedAt: String,
    @ColumnInfo(defaultValue = "0")
    val parsed: Int = 0
)
