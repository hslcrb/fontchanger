package com.openfont.changer

import android.app.Application
import rikka.shizuku.Shizuku

class FontCraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FontCraftApp
            private set

        fun isShizukuAvailable(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }
        }
    }
}
