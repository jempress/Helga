package com.antony.wififtp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Settings.init(context)
            if (Settings.autoStartOnBoot) {
                val serviceIntent = Intent(context, FtpServerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
