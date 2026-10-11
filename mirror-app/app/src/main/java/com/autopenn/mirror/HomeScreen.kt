package com.autopenn.mirror

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.text.DateFormat
import java.util.Date

/** Landing screen on the car display: a clock and one button per app. Tapping YouTube opens the mirror and launches it. */
class HomeScreen(carContext: CarContext) : Screen(carContext) {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { invalidate(); handler.postDelayed(this, 15_000) }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { handler.post(tick) }
            override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(tick) }
        })
    }

    override fun onGetTemplate(): Template {
        val now = Date()
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(DateFormat.getTimeInstance(DateFormat.SHORT).format(now))
                    .addText(DateFormat.getDateInstance(DateFormat.FULL).format(now))
                    .build()
            )
            .addAction(
                Action.Builder().setTitle("YouTube").setOnClickListener {
                    screenManager.push(MirrorScreen(carContext, "com.google.android.youtube"))
                }.build()
            )
            .build()
        return PaneTemplate.Builder(pane).setTitle("Auto Penn").build()
    }
}
