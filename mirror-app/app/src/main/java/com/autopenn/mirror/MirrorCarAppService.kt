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
        // Best effort: if the helper died (phone reboot) and we are already paired, restart it when the car connects.
        Thread {
            if (HelperSetup.isPaired(this) && !HelperSetup.isHelperUp(this)) HelperSetup.startHelper(this)
        }.start()
        return session()
    }

    private fun session(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
    }
}
