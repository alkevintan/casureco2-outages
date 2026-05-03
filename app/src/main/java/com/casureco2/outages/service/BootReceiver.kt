package com.casureco2.outages.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.casureco2.outages.worker.WorkScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkScheduler.scheduleWeekly(context)
        }
    }
}
