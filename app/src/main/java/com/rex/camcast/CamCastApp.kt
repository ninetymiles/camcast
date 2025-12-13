package com.rex.camcast

import android.app.Application
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CamCastApp : Application() {

    private var logger: Logger? = null

    override fun onCreate() {
        super.onCreate()

        // Special properties are initialized when the first logger is instantiated,
        // And that must be done when the application context is available.
        // Ref: https://github.com/tony19/logback-android/wiki
        logger = LoggerFactory.getLogger(CamCastApp::class.java) //  /data/data/com.rex.sms.hook/files
        logger!!.trace("{}", Integer.toHexString(hashCode()))

        //val prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    }
}