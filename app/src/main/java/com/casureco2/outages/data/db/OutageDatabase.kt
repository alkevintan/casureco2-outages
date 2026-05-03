package com.casureco2.outages.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.casureco2.outages.data.dao.BarangayDao
import com.casureco2.outages.data.dao.ParsedOutageDao
import com.casureco2.outages.data.dao.RawPostDao
import com.casureco2.outages.data.model.Barangay
import com.casureco2.outages.data.model.ParsedOutage
import com.casureco2.outages.data.model.RawPost

@Database(
    entities = [RawPost::class, ParsedOutage::class, Barangay::class],
    version = 1,
    exportSchema = false
)
abstract class OutageDatabase : RoomDatabase() {
    abstract fun rawPostDao(): RawPostDao
    abstract fun parsedOutageDao(): ParsedOutageDao
    abstract fun barangayDao(): BarangayDao

    companion object {
        @Volatile
        private var INSTANCE: OutageDatabase? = null

        fun getInstance(context: Context): OutageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OutageDatabase::class.java,
                    "outage_database"
                ).build().also { INSTANCE = it }
            }
        }

        fun init(context: Context) {
            if (INSTANCE == null) {
                getInstance(context)
            }
        }
    }
}
