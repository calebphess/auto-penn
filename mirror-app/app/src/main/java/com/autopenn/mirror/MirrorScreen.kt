package com.autopenn.mirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

/**
 * Step 1 spike: ask the car host for a raw drawing surface and paint a test pattern on it.
 * If the pattern shows up in the head unit, the surface path works and we can swap the pattern
 * for a VirtualDisplay output in the next step.
 */
const val ACTION_LAUNCH = "com.autopenn.mirror.LAUNCH"

class MirrorScreen(carContext: CarContext) : Screen(carContext) {
    private val tag = "MirrorScreen"
    init { HelperClient.init(carContext) }
    private val handler = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var frame = 0
    private var lastTouch = "none"
    private val drawing = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, 33)
        }
    }

    private var receiverRegistered = false

    /** `adb shell am broadcast -a com.autopenn.mirror.LAUNCH --es pkg com.google.android.youtube` */
    private val launchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra("pkg") ?: return
            val id = VirtualScreen.displayId
            Log.i(tag, "launching $pkg on display $id")
            context.getSharedPreferences("auto", Context.MODE_PRIVATE).edit().putString("last_pkg", pkg).apply()
            HelperClient.start(id, pkg)
        }
    }

    private var pendingContainer: SurfaceContainer? = null
    private var lastVisible: Rect? = null
    private val attachFitFallback = Runnable {
        pendingContainer?.let { attachFit(it, lastVisible ?: Rect(0, 0, it.width, it.height)) }
    }

    /** `adb shell settings put global autopenn_fit 0` keeps the old direct path (cheaper on the Pi). */
    private fun fitMode() = android.provider.Settings.Global.getString(carContext.contentResolver, "autopenn_fit") != "0"

    private fun attachFit(c: SurfaceContainer, visible: Rect) {
        handler.removeCallbacks(attachFitFallback)
        pendingContainer = null
        VirtualScreen.attachFit(carContext, c.surface!!, c.height, visible, c.dpi)
        carContext.registerReceiver(launchReceiver, IntentFilter(ACTION_LAUNCH), Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            Log.i(tag, "surface available ${container.width}x${container.height} dpi=${container.dpi}")
            surface = container.surface
            width = container.width
            height = container.height
            handler.removeCallbacks(drawing)
            // Diagnostics: `adb shell settings put global autopenn_mode pattern` paints an animated test pattern straight
            // into the car surface (no VirtualDisplay), to measure Android Auto's own encode/transport capacity.
            val mode = android.provider.Settings.Global.getString(carContext.contentResolver, "autopenn_mode")
            if (mode == "pattern") {
                Log.i(tag, "pattern mode: painting directly into the car surface")
                handler.post(drawing)
            } else if (fitMode()) {
                // Wait for the first visible-area callback (it follows immediately); fall back to the full surface.
                pendingContainer = container
                handler.postDelayed(attachFitFallback, 600)
            } else
            VirtualScreen.attach(carContext, container.surface!!, container.width, container.height, container.dpi)
            carContext.registerReceiver(launchReceiver, IntentFilter(ACTION_LAUNCH), Context.RECEIVER_EXPORTED)
            receiverRegistered = true
            // Fallback test pattern is no longer painted: the VirtualDisplay owns the surface.
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            Log.i(tag, "surface destroyed")
            handler.removeCallbacks(drawing)
            handler.removeCallbacks(attachFitFallback)
            pendingContainer = null
            VirtualScreen.release()
            if (receiverRegistered) {
                carContext.unregisterReceiver(launchReceiver)
                receiverRegistered = false
            }
            surface = null
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            Log.i(tag, "visible area $visibleArea")
            lastVisible = Rect(visibleArea)
            val c = pendingContainer
            if (c != null) attachFit(c, visibleArea)
            else VirtualScreen.updateVisible(visibleArea)
        }

        override fun onClick(x: Float, y: Float) {
            lastTouch = "click ${x.toInt()},${y.toInt()}"
            Log.i(tag, lastTouch)
            val o = VirtualScreen.origin
            HelperClient.tap(VirtualScreen.displayId, if (VirtualScreen.isFit) x - o.left else x, if (VirtualScreen.isFit) y - o.top else y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            lastTouch = "scroll ${distanceX.toInt()},${distanceY.toInt()}"
            // distance is previous-minus-current, so the finger moved the opposite way
            val cx = (if (VirtualScreen.isFit) VirtualScreen.width else width) / 2f
            val cy = (if (VirtualScreen.isFit) VirtualScreen.height else height) / 2f
            HelperClient.swipe(VirtualScreen.displayId, cx, cy, cx - distanceX, cy - distanceY, 60)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            lastTouch = "fling ${velocityX.toInt()},${velocityY.toInt()}"
            val cx = (if (VirtualScreen.isFit) VirtualScreen.width else width) / 2f
            val cy = (if (VirtualScreen.isFit) VirtualScreen.height else height) / 2f
            // ~0.25 s worth of travel at the fling velocity
            HelperClient.swipe(VirtualScreen.displayId, cx, cy, cx + velocityX / 4f, cy + velocityY / 4f, 150)
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
    }

    private val bars = intArrayOf(
        Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.BLUE,
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun draw() {
        val s = surface ?: return
        if (!s.isValid) return
        val c: Canvas = try { s.lockHardwareCanvas() } catch (e: Exception) { s.lockCanvas(null) }
        try {
            c.drawColor(Color.BLACK)
            val barW = width / bars.size.toFloat()
            bars.forEachIndexed { i, col ->
                paint.color = col
                c.drawRect(i * barW, 0f, (i + 1) * barW, height * 0.6f, paint)
            }
            // moving box proves frames are really updating
            paint.color = Color.WHITE
            val x = (frame * 8 % (width - 120)).toFloat()
            c.drawRect(x, height * 0.65f, x + 120f, height * 0.65f + 120f, paint)
            paint.color = Color.WHITE
            paint.textSize = height / 14f
            c.drawText("Auto Penn Mirror  ${width}x$height  frame $frame", 24f, height * 0.9f, paint)
            c.drawText("last touch: $lastTouch", 24f, height * 0.97f, paint)
        } finally {
            s.unlockCanvasAndPost(c)
        }
        frame++
    }

    override fun onGetTemplate(): Template =
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Exit").setOnClickListener { carContext.finishCarApp() }.build())
                    .build()
            )
            .build()
}
