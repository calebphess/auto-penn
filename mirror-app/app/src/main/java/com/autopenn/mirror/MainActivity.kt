package com.autopenn.mirror

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Phone-side remote control until the in-car launcher exists: while the phone is connected to the car, tap an app here
 * and it opens on the car screen (via [HelperClient]). The mirror itself lives in [MirrorCarAppService].
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView

    private val apps = listOf(
        "YouTube" to "com.google.android.youtube",
        "Netflix" to "com.netflix.mediaclient",
        "Crunchyroll" to "com.crunchyroll.crunchyroid",
        "Google Maps" to "com.google.android.apps.maps",
        "Chrome" to "com.android.chrome",
    )

    private lateinit var helperStatus: TextView
    private var helperUp = false

    private val refresh = object : Runnable {
        override fun run() {
            val id = VirtualScreen.displayId
            status.text = if (id >= 0) "Connected to the car. Tap an app to show it on the car screen."
            else "Not connected to the car yet.\nPlug into the car, open Auto Penn in the Android Auto launcher, then come back here."
            Thread {
                val up = HelperSetup.isHelperUp(this@MainActivity)
                handler.post {
                    helperUp = up
                    helperStatus.text = when {
                        up -> "Helper: running"
                        HelperSetup.busy -> "Helper: starting..."
                        HelperSetup.lastError != null -> "Helper: not running. ${HelperSetup.lastError}"
                        else -> "Helper: not running. Needed after every phone reboot (Wi-Fi + Wireless debugging)."
                    }
                }
            }.start()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperClient.init(this)
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#04132B"))
        }
        root.addView(TextView(this).apply {
            text = "Auto Penn"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#9FB4D6"))
            setPadding(0, 24, 0, 48)
        }
        root.addView(status)
        helperStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#9FB4D6"))
            setPadding(0, 0, 0, 24)
        }
        root.addView(helperStatus)
        root.addView(Button(this).apply {
            text = "Start helper"
            setOnClickListener { Thread { HelperSetup.startHelper(this@MainActivity) }.start() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        root.addView(Button(this).apply {
            text = "Pair this phone (first time only)"
            setOnClickListener { HelperSetup.beginPairing(this@MainActivity) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 36 })
        apps.filter { packageManager.getLaunchIntentForPackage(it.second) != null }.forEach { (label, pkg) ->
            root.addView(Button(this).apply {
                text = label
                textSize = 20f
                gravity = Gravity.CENTER
                setOnClickListener {
                    val id = VirtualScreen.displayId
                    if (id >= 0) HelperClient.start(id, pkg)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 })
        }
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
