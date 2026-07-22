package com.adzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val TAG = "AdZeroVpn"
    private val CHANNEL_ID = "AdZeroChannel"
    
    private var outputStream: FileOutputStream? = null
    private val threadPool = Executors.newFixedThreadPool(10) as ThreadPoolExecutor

    // Blocklist - Aggressively expanded for Sponsored banners and tracking
    private val blockedDomains = setOf(
        "googleadservices.com", "googlesyndication.com", "doubleclick.net",
        "googletagmanager.com", "googletagservices.com", "google-analytics.com",
        "analytics.google.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "ads.youtube.com", "ad.youtube.com",
        "play.googleapis.com", "beacons.gcp.gvt2.com",
        "redirector.googlevideo.com", "adsafeprotected.com", "moatads.com",
        "adsrvr.org", "serving-sys.com", "advertising.com", "adnxs.com",
        "adsystem.com", "criteo.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "smartadserver.com", "amazon-adsystem.com", "media.net",
        "outbrain.com", "taboola.com", "quantserve.com", "scorecardresearch.com",
        "comscore.com", "bluekai.com", "krxd.net", "rlcdn.com", "demdex.net",
        "everesttech.net", "mookie1.com", "flashtalking.com", "33across.com",
        "contextweb.com", "lijit.com", "sovrn.com", "sharethrough.com",
        "yieldmo.com", "undertone.com", "spotxchange.com", "spotx.tv",
        "liveintent.com", "yieldbot.com", "tidaltv.com", "videologygroup.com",
        "freewheel.tv", "stickyadstv.com",
        "s.youtube.com", "video-stats.l.google.com", "adssettings.google.com",
        "adstats.google.com", "googleads.g.doubleclick.net", "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net", "partnerad.l.doubleclick.net",
        "pagead.googlesyndication.com", "pagead-tpc.l.google.com", "beacons.gvt2.com",
        "ade.googlesyndication.com", "afad.googlesyndication.com",
        // New aggressive domains for "Sponsored" content
        "jnn-pa.googleapis.com", "youtube-ui.l.google.com",
        "app-measurement.com", "firebase-settings.crashlytics.com",
        "click.googleatls.com", "remotemessaging.googleapis.com",
        "beacons3.gvt2.com", "suggestqueries.google.com", "stats.g.doubleclick.net"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startVpn()
            "STOP" -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        createNotificationChannel()
        startForeground(1, buildNotification())

        try {
            val builder = Builder()
            builder.setMtu(1500)
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("8.8.8.8", 32)
            builder.addRoute("8.8.4.4", 32)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            builder.setSession("AdZero Privacy Filter")

            vpnInterface = builder.establish()
            isRunning = true
            outputStream = FileOutputStream(vpnInterface?.fileDescriptor)

            Log.d(TAG, "VPN Started - Async DNS Filtering Active")
            Thread { runVpnLoop() }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}")
            stopVpn()
        }
    }

    private fun runVpnLoop() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val buffer = ByteArray(32767)

        while (isRunning) {
            try {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    val packetData = buffer.copyOf(length)
                    threadPool.execute { 
                        val response = processPacket(ByteBuffer.wrap(packetData), length)
                        if (response != null) {
                            writeToVpn(response)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "VPN loop error: ${e.message}")
                break
            }
        }
    }

    @Synchronized
    private fun writeToVpn(data: ByteArray) {
        try {
            outputStream?.write(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to VPN: ${e.message}")
        }
    }

    private fun processPacket(packet: ByteBuffer, length: Int): ByteArray? {
        try {
            if (length < 28) return null
            val version = (packet.get(0).toInt() and 0xF0) shr 4
            if (version != 4) return null
            val protocol = packet.get(9).toInt() and 0xFF
            if (protocol != 17) return null // UDP
            
            val ihl = (packet.get(0).toInt() and 0x0F) * 4
            val destPort = ((packet.get(ihl + 2).toInt() and 0xFF) shl 8) or (packet.get(ihl + 3).toInt() and 0xFF)
            if (destPort != 53) return null

            val dnsOffset = ihl + 8
            val domain = extractDomain(packet, dnsOffset + 12, length)
            if (domain != null) {
                if (isBlocked(domain)) {
                    Log.d(TAG, "Blocked: $domain")
                    incrementBlockedCount()
                    return createNxDomainResponse(packet, length, dnsOffset)
                } else {
                    return forwardDnsQuery(packet, length, dnsOffset)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Packet error: ${e.message}") }
        return null
    }

    private fun incrementBlockedCount() {
        val prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE)
        val currentCount = prefs.getInt("blocked_count", 0)
        prefs.edit().putInt("blocked_count", currentCount + 1).apply()
    }

    private fun forwardDnsQuery(packet: ByteBuffer, length: Int, dnsOffset: Int): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            val dnsQuery = ByteArray(length - dnsOffset)
            packet.position(dnsOffset)
            packet.get(dnsQuery)

            val destIp = InetAddress.getByAddress(byteArrayOf(
                packet.get(16), packet.get(17), packet.get(18), packet.get(19)
            ))

            socket = DatagramSocket()
            protect(socket)
            
            val outPacket = DatagramPacket(dnsQuery, dnsQuery.size, destIp, 53)
            socket.send(outPacket)

            val responseBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.soTimeout = 2500
            socket.receive(inPacket)

            val ihl = (packet.get(0).toInt() and 0x0F) * 4
            val totalLen = ihl + 8 + inPacket.length
            val response = ByteArray(totalLen)
            
            for (i in 0 until ihl) response[i] = packet.get(i)
            swapIpAddresses(response)
            
            response[2] = (totalLen shr 8).toByte()
            response[3] = totalLen.toByte()
            updateIpChecksum(response, ihl)
            
            response[ihl] = 0; response[ihl + 1] = 53
            response[ihl + 2] = packet.get(ihl + 4); response[ihl + 3] = packet.get(ihl + 5)
            val udpLen = 8 + inPacket.length
            response[ihl + 4] = (udpLen shr 8).toByte(); response[ihl + 5] = udpLen.toByte()
            response[ihl + 6] = 0; response[ihl + 7] = 0
            
            System.arraycopy(inPacket.data, 0, response, ihl + 8, inPacket.length)
            response
        } catch (e: Exception) { 
            null 
        } finally {
            socket?.close()
        }
    }

    private fun updateIpChecksum(packet: ByteArray, ihl: Int) {
        packet[10] = 0; packet[11] = 0
        var checksum = 0
        for (i in 0 until ihl step 2) {
            checksum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (checksum shr 16 != 0) checksum = (checksum and 0xFFFF) + (checksum shr 16)
        checksum = checksum.inv()
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()
    }

    private fun extractDomain(packet: ByteBuffer, offset: Int, length: Int): String? {
        return try {
            val sb = StringBuilder(); var pos = offset
            while (pos < length) {
                val labelLen = packet.get(pos).toInt() and 0xFF
                if (labelLen == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                pos++; for (i in 0 until labelLen) {
                    if (pos + i >= length) return null
                    sb.append(packet.get(pos + i).toInt().toChar())
                }
                pos += labelLen
            }
            sb.toString().lowercase()
        } catch (e: Exception) { null }
    }

    private fun isBlocked(domain: String) = blockedDomains.any { domain == it || domain.endsWith(".$it") }

    private fun createNxDomainResponse(original: ByteBuffer, length: Int, dnsOffset: Int): ByteArray {
        val response = ByteArray(length)
        for (i in 0 until length) response[i] = original.get(i)
        response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte()
        response[dnsOffset + 3] = (response[dnsOffset + 3].toInt() or 0x03).toByte()
        swapIpAddresses(response)
        updateIpChecksum(response, (response[0].toInt() and 0x0F) * 4)
        swapUdpPorts(response, dnsOffset)
        return response
    }

    private fun swapIpAddresses(packet: ByteArray) {
        for (i in 0..3) { val temp = packet[12 + i]; packet[12 + i] = packet[16 + i]; packet[16 + i] = temp }
    }

    private fun swapUdpPorts(packet: ByteArray, offset: Int) {
        for (i in 0..1) { val temp = packet[offset + i]; packet[offset + i] = packet[offset + 2 + i]; packet[offset + 2 + i] = temp }
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        outputStream?.close()
        outputStream = null
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "VPN Stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, 
                "AdZero Privacy Filter", 
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background protection service"
                setShowBadge(false)
            })
        }
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ AdZero Privacy Active")
            .setContentText("DNS filtering is active for your protection.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(intent).setOngoing(true).build()
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
