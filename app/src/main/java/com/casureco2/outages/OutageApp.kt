package com.casureco2.outages

import android.app.Application
import androidx.work.Configuration
import com.casureco2.outages.data.db.OutageDatabase
import com.casureco2.outages.util.SecureStorage

class OutageApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        SecureStorage.init(this)
        OutageDatabase.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
