package com.adzero.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100
    private lateinit var prefs: SharedPreferences
    private var isBlocking = false

    private val countReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val count = intent?.getIntExtra("count", 0) ?: 0
            updateBlockedCountUI(count)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE)
        isBlocking = prefs.getBoolean("is_blocking", false)

        updateUI()
        setupToggle()

        // Initial count update
        val savedCount = prefs.getInt("blocked_count", 0)
        updateBlockedCountUI(savedCount)
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(countReceiver, IntentFilter("com.adzero.app.UPDATE_COUNT"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(countReceiver, IntentFilter("com.adzero.app.UPDATE_COUNT"))
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(countReceiver)
    }

    private fun updateBlockedCountUI(count: Int) {
        val adsBlockedText = findViewById<TextView>(R.id.adsBlockedCount)
        adsBlockedText.text = count.toString()
    }

    private fun setupToggle() {
        val toggleBtn = findViewById<View>(R.id.toggleButton)
        toggleBtn.setOnClickListener {
            if (isBlocking) {
                stopBlocking()
            } else {
                startBlocking()
            }
        }
    }

    private fun startBlocking() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
        }
    }

    private fun stopBlocking() {
        val stopIntent = Intent(this, AdBlockVpnService::class.java)
        stopIntent.action = "STOP"
        startService(stopIntent)
        isBlocking = false
        prefs.edit().putBoolean("is_blocking", false).apply()
        updateUI()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val vpnIntent = Intent(this, AdBlockVpnService::class.java)
            vpnIntent.action = "START"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(vpnIntent)
            } else {
                startService(vpnIntent)
            }
            isBlocking = true
            prefs.edit().putBoolean("is_blocking", true).apply()
            updateUI()
        }
    }

    private fun updateUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val subStatusText = findViewById<TextView>(R.id.subStatusText)
        val mainSwitch = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.mainSwitch)
        val toggleText = findViewById<TextView>(R.id.toggleText)
        val shieldIcon = findViewById<ImageView>(R.id.shieldIcon)

        mainSwitch.isChecked = isBlocking

        if (isBlocking) {
            statusText.text = "PROTECTED"
            statusText.setTextColor(Color.parseColor("#00E676"))
            subStatusText.text = "Privacy Filter is Active ✓"
            toggleText.text = "Disable Privacy Protection"
            shieldIcon.alpha = 1.0f
        } else {
            statusText.text = "NOT PROTECTED"
            statusText.setTextColor(Color.parseColor("#FF5252"))
            subStatusText.text = "Privacy Filter is Inactive"
            toggleText.text = "Enable Privacy Protection"
            shieldIcon.alpha = 0.3f
        }
    }
}
