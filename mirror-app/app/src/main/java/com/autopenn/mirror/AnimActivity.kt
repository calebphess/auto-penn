package com.autopenn.mirror

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View

/** Diagnostics: a full-rate animation (moving bars + frame counter) to measure the mirror pipeline without video decode. */
class AnimActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AnimView(this))
    }

    private class AnimView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var frame = 0
        override fun onDraw(c: Canvas) {
            c.drawColor(Color.BLACK)
            val w = width.toFloat(); val h = height.toFloat()
            val colors = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.BLUE)
            val bw = w / colors.size
            val shift = (frame * 12 % w.toInt()).toFloat()
            colors.forEachIndexed { i, col ->
                paint.color = col
                c.drawRect(((i * bw + shift) % w), 0f, ((i * bw + shift) % w) + bw, h * 0.6f, paint)
            }
            paint.color = Color.WHITE; paint.textSize = h / 10f
            c.drawText("anim frame $frame", 24f, h * 0.9f, paint)
            frame++
            postInvalidateOnAnimation()
        }
    }
}
