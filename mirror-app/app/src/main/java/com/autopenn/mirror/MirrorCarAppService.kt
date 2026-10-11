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
        // No manual steps: keep trying to bring the helper up (Wi-Fi may still be joining), the home screen (clock + app buttons)
        // launches the app itself.
        Thread {
            HelperClient.init(this)
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline && !HelperSetup.isHelperUp(this)) {
                if (HelperSetup.isPaired(this)) HelperSetup.startHelper(this)
                if (!HelperSetup.isHelperUp(this)) Thread.sleep(4000)
            }
        }.start()
        return session()
    }

    private fun session(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = HomeScreen(carContext)
    }
}
