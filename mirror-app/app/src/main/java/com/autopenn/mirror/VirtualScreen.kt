package com.autopenn.mirror

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface

/**
 * Owns the single VirtualDisplay that mirrors into the car surface. Other apps are launched
 * onto [displayId]; whatever they draw ends up on the head unit.
 */
object VirtualScreen {
    private const val TAG = "VirtualScreen"
    // Hidden DisplayManager flag values (stable across releases).
    private const val FLAG_SUPPORTS_TOUCH = 1 shl 6

    private var display: VirtualDisplay? = null
    val displayId: Int get() = display?.display?.displayId ?: -1

    fun attach(context: Context, surface: Surface, width: Int, height: Int, dpi: Int) {
        release()
        val dm = context.getSystemService(DisplayManager::class.java)
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
            FLAG_SUPPORTS_TOUCH
        display = dm.createVirtualDisplay("AutoPennMirror", width, height, dpi, surface, flags)
        Log.i(TAG, "created virtual display id=$displayId ${width}x$height dpi=$dpi")
        HelperClient.setDisplay(displayId)
    }

    // "Fit" mode: render only into the visible part of the car surface (see FitRenderer).
    private var fit: FitRenderer? = null
    private var dpi = 160
    /** Where the mirrored display sits inside the car surface; touch coordinates are offset by this. */
    @Volatile var origin = Rect()
        private set
    val width: Int get() = origin.width()
    val height: Int get() = origin.height()

    fun attachFit(context: Context, surface: Surface, surfaceHeight: Int, visible: Rect, dpi: Int) {
        release()
        this.dpi = dpi
        origin = Rect(visible)
        val r = FitRenderer(surface, surfaceHeight, visible).also { fit = it }
        val dm = context.getSystemService(DisplayManager::class.java)
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
            FLAG_SUPPORTS_TOUCH
        display = dm.createVirtualDisplay("AutoPennMirror", visible.width(), visible.height(), dpi, r.inputSurface, flags)
        Log.i(TAG, "created fit virtual display id=$displayId $visible dpi=$dpi")
        HelperClient.setDisplay(displayId)
    }

    fun updateVisible(visible: Rect) {
        val f = fit ?: return
        if (visible == origin || visible.isEmpty) return
        val sizeChanged = visible.width() != origin.width() || visible.height() != origin.height()
        origin = Rect(visible)
        f.setRect(visible)
        if (sizeChanged) {
            f.setBufferSize(visible.width(), visible.height())
            display?.resize(visible.width(), visible.height(), dpi)
        }
        Log.i(TAG, "fit rect $visible")
    }

    val isFit: Boolean get() = fit != null

    fun release() {
        if (display != null) HelperClient.setDisplay(-1)
        display?.release()
        display = null
        fit?.release()
        fit = null
    }
}
