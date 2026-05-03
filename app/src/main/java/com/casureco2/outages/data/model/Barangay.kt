package com.casureco2.outages.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barangays")
data class Barangay(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val slug: String,
    val municipality: String
)
