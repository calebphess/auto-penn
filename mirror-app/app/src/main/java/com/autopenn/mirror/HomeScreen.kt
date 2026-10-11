package com.autopenn.mirror

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
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

    private val apps = listOf(
        "YouTube" to "com.google.android.youtube",
        "Google Maps" to "com.google.android.apps.maps",
        "Spotify" to "com.spotify.music",
        "Netflix" to "com.netflix.mediaclient",
        "Crunchyroll" to "com.crunchyroll.crunchyroid",
        "Chrome" to "com.android.chrome",
    )

    override fun onGetTemplate(): Template {
        val now = Date()
        val list = ItemList.Builder()
        apps.forEach { (name, pkg) ->
            list.addItem(
                Row.Builder().setTitle(name).setOnClickListener {
                    screenManager.push(MirrorScreen(carContext, pkg))
                }.build()
            )
        }
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(now) + "  ·  " +
            java.text.SimpleDateFormat("EEE MMM d", java.util.Locale.getDefault()).format(now)
        return ListTemplate.Builder().setSingleList(list.build()).setTitle("Auto Penn   $clock").build()
    }
}
