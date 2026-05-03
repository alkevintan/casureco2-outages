package com.casureco2.outages.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "parsed_outages",
    foreignKeys = [
        ForeignKey(
            entity = RawPost::class,
            parentColumns = ["id"],
            childColumns = ["raw_post_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ParsedOutage(
    @PrimaryKey
    val id: String,
    val barangays: String,
    val date: String,
    @ColumnInfo(name = "time_start")
    val timeStart: String?,
    @ColumnInfo(name = "time_end")
    val timeEnd: String?,
    val reason: String,
    val scope: String,
    @ColumnInfo(name = "raw_post_id", index = true)
    val rawPostId: String,
    @ColumnInfo(defaultValue = "0")
    val synced: Int = 0
)
