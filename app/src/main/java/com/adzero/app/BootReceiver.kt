package com.adzero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val prefs = context.getSharedPreferences("adblock_prefs", Context.MODE_PRIVATE)
            val wasBlocking = prefs.getBoolean("is_blocking", false)

            if (wasBlocking) {
                val serviceIntent = Intent(context, AdBlockVpnService::class.java)
                serviceIntent.action = "START"
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // System might block startup, but "Always-on VPN" will handle it if enabled
                }
            }
        }
    }
}
