package com.taiful.tfvoicetyper

import android.app.Application
import com.taiful.tfvoicetyper.ai.GeminiClient
import com.taiful.tfvoicetyper.data.SettingsRepo
import com.taiful.tfvoicetyper.data.db.AppDatabase
import com.taiful.tfvoicetyper.engine.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TFApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: SettingsRepo
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var gemini: GeminiClient
        private set
    lateinit var engine: VoiceEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepo(this)
        db = AppDatabase.get(this)
        gemini = GeminiClient()
        engine = VoiceEngine(this)
        appScope.launch {
            engine.recoverInterrupted()
            engine.cleanupAudio()
        }
    }

    companion object {
        lateinit var instance: TFApp
            private set
    }
}
