package com.openfont.changer

import android.app.Application
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration
import java.io.File

class FontCraftApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // OpenStreetMap (osmdroid) 전역 설정 초기화 - 구글 지도급 타일 캐싱 및 성능 보장
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "FontCraft/1.4.0 (Android; OpenStreetMap Native)"
        
        // 타일 캐시 저장 경로 설정
        val osmTileDir = File(cacheDir, "osmdroid")
        osmTileDir.mkdirs()
        Configuration.getInstance().osmdroidTileCache = osmTileDir
    }

    companion object {
        lateinit var instance: FontCraftApp
            private set
    }
}
