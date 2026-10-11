package com.autopenn.mirror

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MirrorCarAppService : CarAppService() {
    // Development only: accept any host so the Desktop Head Unit and sideloaded Android Auto work.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session {
        // No manual steps: keep trying to bring the helper up (Wi-Fi may still be joining), then put the last-used app
        // (YouTube by default) on the mirror as soon as the car screen is attached.
        Thread {
            HelperClient.init(this)
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline && !HelperSetup.isHelperUp(this)) {
                if (HelperSetup.isPaired(this)) HelperSetup.startHelper(this)
                if (!HelperSetup.isHelperUp(this)) Thread.sleep(4000)
            }
            while (System.currentTimeMillis() < deadline && VirtualScreen.displayId < 0) Thread.sleep(500)
            if (HelperSetup.isHelperUp(this) && VirtualScreen.displayId >= 0) {
                val pkg = getSharedPreferences("auto", MODE_PRIVATE).getString("last_pkg", "com.google.android.youtube")!!
                Thread.sleep(1000)
                HelperClient.start(VirtualScreen.displayId, pkg)
            }
        }.start()
        return session()
    }

    private fun session(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
    }
}
