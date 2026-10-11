package com.autopenn.mirror

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs as the `shell` user (started by init via app_process, see device/autopenn-helper.rc), because only
 * shell may launch arbitrary activities onto our untrusted VirtualDisplay and inject touch events.
 *
 * Protocol, one command per line on the abstract socket [SOCKET_NAME]:
 *   START <displayId> <package>
 *   DISPLAY <displayId>        (the current mirror display, -1 when there is none)
 *   TAP <displayId> <x> <y>
 *   SWIPE <displayId> <x1> <y1> <x2> <y2> <durationMs>
 *
 * Only the Auto Penn Mirror app's uid may connect (checked via SO_PEERCRED).
 *
 * Phones run SELinux enforcing, which denies untrusted_app -> shell unix sockets. With the argument
 * `tcp <token>` the helper also listens on 127.0.0.1:[TCP_PORT]; a client must send `AUTH <token>` first.
 */
object Helper {
    const val SOCKET_NAME = "autopenn.helper"
    const val TCP_PORT = 5279
    private const val APP_PACKAGE = "com.penn.auto_penn"

    /** Packages started on the mirror: their tasks are kept there (some apps, e.g. Netflix, relaunch onto display 0). */
    private val pinned = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var mirrorDisplay = -1

    private lateinit var inputManager: Any
    private lateinit var injectMethod: java.lang.reflect.Method
    private val setDisplayId = MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)

    @JvmStatic
    fun main(args: Array<String>) {
        val global = Class.forName("android.hardware.input.InputManagerGlobal")
        inputManager = global.getMethod("getInstance").invoke(null)!!
        injectMethod = global.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

        Thread(::keepTasksOnMirror, "autopenn-pin").apply { isDaemon = true }.start()
        if (args.size >= 2 && args[0] == "tcp") {
            val token = args[1]
            val tcp = ServerSocket(TCP_PORT, 8, InetAddress.getByName("127.0.0.1"))
            Thread {
                while (true) {
                    val c = tcp.accept()
                    Thread {
                        c.use {
                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                            if (reader.readLine() != "AUTH $token") { System.err.println("tcp auth rejected"); return@Thread }
                            loop(reader)
                        }
                    }.start()
                }
            }.start()
            println("autopenn helper listening on tcp 127.0.0.1:$TCP_PORT")
        }
        val server = LocalServerSocket(SOCKET_NAME)
        println("autopenn helper listening, app uid=${appUid()}")
        while (true) {
            val client = server.accept()
            Thread { serve(client) }.start()
        }
    }

    /** Resolved fresh each connection: cheap, and survives reinstalls that change the uid. */
    private fun appUid(): Int {
        val p = ProcessBuilder("cmd", "package", "list", "packages", "-U", APP_PACKAGE)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return Regex("uid:(\\d+)").find(out)?.groupValues?.get(1)?.toInt() ?: -1
    }

    private fun serve(client: LocalSocket) {
        client.use {
            if (it.peerCredentials.uid != appUid()) {
                System.err.println("rejected uid ${it.peerCredentials.uid}")
                return
            }
            loop(BufferedReader(InputStreamReader(it.inputStream)))
        }
    }

    private fun loop(reader: BufferedReader) {
        while (true) {
            val line = reader.readLine() ?: break
            try {
                handle(line.trim().split(' '))
            } catch (e: Exception) {
                System.err.println("bad command '$line': $e")
            }
        }
    }

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    /** `pkg/.Activity` of the launcher activity, or null. Some apps (Netflix) only start properly by explicit component. */
    private fun launcherComponent(pkg: String): String? =
        run("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER", pkg)
            .lines().map { it.trim() }.lastOrNull { Regex("[\\w.]+/[\\w.$]+").matches(it) }

    /** Moves tasks of pinned packages that ended up on another display (usually display 0) back onto the mirror. */
    private fun keepTasksOnMirror() {
        val header = Regex("Display #(\\d+) ")
        val task = Regex("Task\\{\\w+ #(\\d+) type=standard A=\\d+:([\\w.]+)")
        while (true) {
            Thread.sleep(1500)
            val target = mirrorDisplay
            if (target < 0 || pinned.isEmpty()) continue
            try {
                var current = -1
                for (line in run("dumpsys", "activity", "activities").lineSequence()) {
                    if (line.startsWith("ActivityTaskSupervisor state")) break
                    header.find(line)?.let { current = it.groupValues[1].toInt() }
                    val m = task.find(line) ?: continue
                    if (m.groupValues[2] in pinned && current != target) {
                        System.err.println("moving task ${m.groupValues[1]} of ${m.groupValues[2]} from display $current to $target")
                        run("am", "display", "move-stack", m.groupValues[1], target.toString())
                    }
                }
            } catch (e: Exception) {
                System.err.println("pin loop: $e")
            }
        }
    }

    private fun handle(t: List<String>) {
        when (t[0]) {
            "DISPLAY" -> {
                mirrorDisplay = t[1].toInt()
                if (mirrorDisplay < 0) pinned.clear()
            }
            "START" -> {
                val pkg = t[2]
                require(Regex("[A-Za-z0-9._]+").matches(pkg)) { "bad package" }
                val display = t[1].toInt()
                mirrorDisplay = display
                pinned.add(pkg)
                val component = launcherComponent(pkg)
                val cmd = mutableListOf("am", "start", "--display", display.toString())
                if (component != null) cmd += listOf("-n", component)
                else cmd += listOf("-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", pkg)
                ProcessBuilder(cmd).inheritIO().start()
            }
            "TAP" -> {
                val d = t[1].toInt(); val x = t[2].toFloat(); val y = t[3].toFloat()
                val down = SystemClock.uptimeMillis()
                inject(d, down, down, MotionEvent.ACTION_DOWN, x, y)
                inject(d, down, SystemClock.uptimeMillis() + 40, MotionEvent.ACTION_UP, x, y)
            }
            "SWIPE" -> {
                val d = t[1].toInt()
                val x1 = t[2].toFloat(); val y1 = t[3].toFloat()
                val x2 = t[4].toFloat(); val y2 = t[5].toFloat()
                val dur = t[6].toLong().coerceIn(30, 2000)
                val steps = (dur / 16).toInt().coerceAtLeast(2)
                val down = SystemClock.uptimeMillis()
                inject(d, down, down, MotionEvent.ACTION_DOWN, x1, y1)
                for (i in 1 until steps) {
                    val f = i / steps.toFloat()
                    Thread.sleep(dur / steps)
                    inject(d, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f)
                }
                inject(d, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x2, y2)
            }
            else -> error("unknown command ${t[0]}")
        }
    }

    private fun inject(displayId: Int, downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        setDisplayId.invoke(e, displayId)
        injectMethod.invoke(inputManager, e, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */)
        e.recycle()
    }
}
