package com.autopenn.mirror

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executors

/** App-side connection to [Helper]. Commands are sent from a single background thread. */
object HelperClient {
    private const val TAG = "HelperClient"
    private val executor = Executors.newSingleThreadExecutor()
    private var closer: (() -> Unit)? = null
    private var out: OutputStream? = null

    /** File holding the shared token for the TCP transport (phones: SELinux blocks the unix socket). */
    @Volatile var tokenFile: java.io.File? = null

    fun init(ctx: android.content.Context) { tokenFile = HelperSetup.tokenFile(ctx) }

    private fun connect(): OutputStream {
        try {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(Helper.SOCKET_NAME))
            closer = { s.close() }
            return s.outputStream
        } catch (e: Exception) {
            Log.w(TAG, "unix socket unavailable ($e), trying tcp")
        }
        val token = tokenFile!!.readText().trim()
        val s = Socket("127.0.0.1", Helper.TCP_PORT)
        closer = { s.close() }
        s.outputStream.write("AUTH $token\n".toByteArray())
        return s.outputStream
    }

    private fun send(line: String) = executor.execute {
        repeat(2) {
            try {
                val o = out ?: connect().also { out = it }
                o.write((line + "\n").toByteArray())
                o.flush()
                return@execute
            } catch (e: Exception) {
                Log.w(TAG, "send failed, reconnecting: $e")
                runCatching { closer?.invoke() }
                out = null
            }
        }
    }

    /** Tells the helper which display is the mirror right now (-1 = none), so it can keep app tasks on it. */
    fun setDisplay(displayId: Int) = send("DISPLAY $displayId")
    fun start(displayId: Int, pkg: String) = send("START $displayId $pkg")
    fun tap(displayId: Int, x: Float, y: Float) = send("TAP $displayId $x $y")
    fun swipe(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) =
        send("SWIPE $displayId $x1 $y1 $x2 $y2 $ms")
}
