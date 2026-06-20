package com.antony.wififtp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

/**
 * Foreground service hosting an embedded FTP server rooted at the
 * device's shared storage directory, so files dropped there are
 * reachable from Windows Explorer / any FTP client over WiFi.
 */
class FtpServerService : Service() {

    private var ftpServer: FtpServer? = null

    companion object {
        const val DEFAULT_PORT = 2121
        const val PASSIVE_PORT_RANGE_START = 2300
        const val PASSIVE_PORT_RANGE_END = 2400
        const val CHANNEL_ID = "ftp_server_channel"
        const val ACTION_STOP = "com.antony.wififtp.STOP"

        @Volatile var isRunning: Boolean = false
        @Volatile var port: Int = DEFAULT_PORT
        @Volatile var username: String = "android"
        @Volatile var password: String = "1234"

        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore, fall through
            }
            return null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFtp()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, buildNotification())
        startFtp()
        return START_STICKY
    }

    private fun startFtp() {
        if (isRunning) return

        val serverFactory = FtpServerFactory()
        val listenerFactory = ListenerFactory()
        listenerFactory.port = port

        // Passive mode is what Windows Explorer/most FTP clients use for the data
        // connection. Without an explicit port range, the OS picks a random ephemeral
        // port each time, which is unreliable behind NAT/hotspot — pin a small range
        // and advertise this device's own LAN IP so clients connect back correctly.
        val dataConfig = DataConnectionConfigurationFactory()
        dataConfig.setPassivePorts("$PASSIVE_PORT_RANGE_START-$PASSIVE_PORT_RANGE_END")
        getLocalIpAddress()?.let { dataConfig.setPassiveExternalAddress(it) }
        listenerFactory.dataConnectionConfiguration = dataConfig.createDataConnectionConfiguration()

        serverFactory.addListener("default", listenerFactory.createListener())

        // Root directory exposed over FTP: public shared storage
        val rootDir: File = Environment.getExternalStorageDirectory()

        val userManager = serverFactory.userManager
        val user = BaseUser()
        user.name = username
        user.password = password
        user.homeDirectory = rootDir.absolutePath
        val authorities: MutableList<Authority> = ArrayList()
        authorities.add(WritePermission())
        user.authorities = authorities
        userManager.save(user)
        serverFactory.userManager = userManager

        ftpServer = serverFactory.createServer()
        ftpServer?.start()
        isRunning = true
    }

    private fun stopFtp() {
        ftpServer?.stop()
        ftpServer = null
        isRunning = false
    }

    override fun onDestroy() {
        stopFtp()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FTP Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val ip = getLocalIpAddress() ?: "unknown"
        val stopIntent = Intent(this, FtpServerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Helga is running")
            .setContentText("ftp://$ip:$port")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
