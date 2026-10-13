package com.player1.app

import android.app.Application
import com.player1.app.repository.SettingsRepository
import com.player1.app.service.ThumbnailGenerator
import com.player1.app.service.VideoScanner

class MainApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var videoScanner: VideoScanner
        private set
    lateinit var thumbnailGenerator: ThumbnailGenerator
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        videoScanner = VideoScanner(this)
        thumbnailGenerator = ThumbnailGenerator(this)
    }
}
