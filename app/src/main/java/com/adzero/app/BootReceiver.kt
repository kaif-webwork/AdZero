package com.adzero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("adblock_prefs", Context.MODE_PRIVATE)
            val wasBlocking = prefs.getBoolean("is_blocking", false)

            if (wasBlocking) {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    // Permission already granted, start service
                    val serviceIntent = Intent(context, AdBlockVpnService::class.java)
                    serviceIntent.action = "START"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
