package com.autopenn.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Starts [Helper] (a shell-uid process) from inside the app: pair once with this phone's own Wireless debugging,
 * then connect to adbd over the loopback/Wi-Fi TLS endpoint and run `app_process` as shell. Needs Wi-Fi only while
 * (re)starting; the helper keeps running until the phone reboots.
 */
object HelperSetup {
    private const val TAG = "HelperSetup"
    const val CHANNEL = "setup"
    const val NOTIF_ID = 42
    const val KEY_CODE = "code"

    @Volatile var lastError: String? = null
    @Volatile var busy = false

    fun tokenFile(ctx: Context) = File(ctx.filesDir, "helper-token")

    /** True when a helper accepts our token on the loopback port. */
    fun isHelperUp(ctx: Context): Boolean = try {
        val token = tokenFile(ctx).takeIf { it.exists() }?.readText()?.trim() ?: return false
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", Helper.TCP_PORT), 400)
            s.getOutputStream().write("AUTH $token\n".toByteArray())
            true
        }
    } catch (e: Exception) { false }

    fun isPaired(ctx: Context) = File(ctx.filesDir, "adb_paired").exists()

    /** Turns Wireless debugging on if this app was granted WRITE_SECURE_SETTINGS (one-time `adb shell pm grant`). Android clears it on car/ignition cycles. */
    private fun enableWirelessDebugging(ctx: Context) {
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            if (Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1) return
            Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
            Thread.sleep(2500) // let adbd open the TLS port and advertise it
        } catch (e: Exception) { Log.w(TAG, "enableWirelessDebugging failed", e) }
    }

    /** Connects to adbd (already paired) and (re)starts the helper. Blocking: call from a background thread. */
    fun startHelper(ctx: Context): Boolean {
        if (busy) return false
        busy = true
        try {
            val mgr = AdbManager.get(ctx)
            enableWirelessDebugging(ctx)
            if (!mgr.isConnected && !mgr.autoConnect(ctx, 8000)) {
                lastError = "Could not reach Wireless debugging. Turn it on in Developer options (Wi-Fi needed)."
                return false
            }
            val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            tokenFile(ctx).writeText(token)
            val apk = ctx.applicationInfo.sourceDir
            val cmd = "kill \$(ps -A | grep autopenn_helpe[r] | awk '{print \$2}') 2>/dev/null; " +
                "setsid nohup app_process -Djava.class.path=$apk /system/bin --nice-name=autopenn_helper " +
                "${Helper::class.java.name} tcp $token </dev/null >/data/local/tmp/helper.log 2>&1 & " +
                // adbd kills a freshly forked child when the stream closes right away (raw shell service); hold it open until the JVM is up.
                "sleep 4"
            Log.i(TAG, "shell: $cmd")
            Log.i(TAG, "shell out: " + runShell(mgr, cmd))
            repeat(15) { if (isHelperUp(ctx)) { lastError = null; return true }; Thread.sleep(300) }
            Log.w(TAG, "diag: " + runShell(mgr, "cat /data/local/tmp/helper.log; ps -A | grep -i autopenn"))
            lastError = "Helper did not start (see /data/local/tmp/helper.log)."
            return false
        } catch (e: Exception) {
            Log.w(TAG, "startHelper failed", e)
            lastError = e.message ?: e.toString()
            return false
        } finally { busy = false }
    }

    /** Runs one shell command over ADB and returns its output; end of stream is reported as an IOException by the library. */
    private fun runShell(mgr: AdbManager, cmd: String): String {
        val out = java.io.ByteArrayOutputStream()
        mgr.openStream("shell:$cmd").use { st ->
            try { st.openInputStream().copyTo(out) } catch (_: java.io.IOException) { /* EOF */ }
        }
        return out.toString().trim()
    }

    /** Looks up the pairing endpoint advertised while the "Pair with code" dialog is open. */
    private fun discoverPairing(ctx: Context, timeoutMs: Long): InetSocketAddress? {
        val nsd = ctx.getSystemService(NsdManager::class.java)
        val latch = CountDownLatch(1)
        var found: InetSocketAddress? = null
        val resolve = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
            override fun onServiceUpdated(info: NsdServiceInfo) {
                val addr = info.hostAddresses.firstOrNull() ?: return
                found = InetSocketAddress(addr, info.port); latch.countDown()
            }
            override fun onServiceLost() {}
            override fun onServiceInfoCallbackUnregistered() {}
        }
        // Run callbacks inline on NSD's own thread: a separate executor that we shut down raced with late callbacks and crashed.
        val exec = java.util.concurrent.Executor { it.run() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String, e: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) { nsd.registerServiceInfoCallback(i, exec, resolve) }
        }
        nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        runCatching { nsd.stopServiceDiscovery(listener) }
        runCatching { nsd.unregisterServiceInfoCallback(resolve) }
        return found
    }

    /** Pairs with the code typed into the notification, then starts the helper. Blocking. */
    fun pairAndStart(ctx: Context, code: String) {
        try {
            val ep = discoverPairing(ctx, 10_000)
            if (ep == null) { lastError = "Pairing dialog not found. Keep 'Pair device with pairing code' open."; return }
            val ok = AdbManager.get(ctx).pair(ep.address.hostAddress!!, ep.port, code)
            if (!ok) { lastError = "Pairing failed (wrong code?)."; return }
            File(ctx.filesDir, "adb_paired").writeText("1")
            startHelper(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "pair failed", e)
            lastError = e.message ?: e.toString()
        }
    }

    /** Opens Developer options and posts a notification whose reply box takes the 6-digit pairing code. */
    fun beginPairing(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Setup", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getService(
            ctx, 0, Intent(ctx, PairingService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(null, "Enter pairing code", pi)
            .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("6-digit pairing code").build()).build()
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Auto Penn setup")
                .setContentText("Open Wireless debugging > Pair device with pairing code, then type the code here.")
                .addAction(action).setOngoing(true).build(),
        )
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Receives the pairing code from the notification reply and runs the pairing off the main thread. */
class PairingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(HelperSetup.KEY_CODE)?.toString() }
        val nm = getSystemService(NotificationManager::class.java)
        if (code.isNullOrBlank()) { stopSelf(startId); return START_NOT_STICKY }
        Thread {
            HelperSetup.pairAndStart(applicationContext, code.trim())
            val up = HelperSetup.isHelperUp(applicationContext)
            nm.notify(
                HelperSetup.NOTIF_ID,
                Notification.Builder(this, HelperSetup.CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle(if (up) "Auto Penn is ready" else "Auto Penn setup failed")
                    .setContentText(if (up) "Helper is running. You can turn Wireless debugging off." else HelperSetup.lastError ?: "Unknown error")
                    .build(),
            )
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }
}
