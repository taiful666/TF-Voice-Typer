package com.taiful.tfvoicetyper

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Text of the last crash (shown once in the app so it can be copied and reported). */
    val lastProblem = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashGuard()
        settings = SettingsRepo(this)
        db = AppDatabase.get(this)
        gemini = GeminiClient()
        engine = VoiceEngine(this)
        val prefs = getSharedPreferences("tf_crash", MODE_PRIVATE)
        if (prefs.getBoolean("crashed", false)) {
            prefs.edit().putBoolean("crashed", false).commit()
            lastProblem.value = runCatching { File(filesDir, "last_problem.txt").readText() }.getOrNull()
            // The floating mic is optional: after a crash, turn it off so the app always opens.
            appScope.launch { settings.set(SettingsRepo.K.bubbleEnabled, false) }
        }
        appScope.launch {
            engine.recoverInterrupted()
            engine.cleanupAudio()
        }
    }

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                saveProblem("ক্র্যাশ", e)
                getSharedPreferences("tf_crash", MODE_PRIVATE).edit().putBoolean("crashed", true).commit()
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun saveProblem(where: String, e: Throwable) {
        runCatching {
            val text = "TF Voice Typer ${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} • ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "$where: ${Log.getStackTraceString(e).take(4000)}"
            File(filesDir, "last_problem.txt").writeText(text)
            lastProblem.value = text
        }
    }

    fun clearProblem() {
        lastProblem.value = null
        runCatching { File(filesDir, "last_problem.txt").delete() }
    }

    companion object {
        lateinit var instance: TFApp
            private set
    }
}
