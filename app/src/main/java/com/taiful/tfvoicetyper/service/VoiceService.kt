package com.taiful.tfvoicetyper.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.taiful.tfvoicetyper.MainActivity
import com.taiful.tfvoicetyper.R
import com.taiful.tfvoicetyper.TFApp
import com.taiful.tfvoicetyper.data.SettingsRepo
import com.taiful.tfvoicetyper.engine.EngineState
import com.taiful.tfvoicetyper.engine.Source
import kotlinx.coroutines.launch

/**
 * Foreground service (type: microphone). It keeps recording working while you use
 * other apps, and it shows the floating microphone button.
 */
class VoiceService : LifecycleService(), SavedStateRegistryOwner {

    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

    private val app get() = application as TFApp
    private var overlay: OverlayController? = null
    private var bubbleWanted = false
    private var lastNotifKey = ""

    override fun onCreate() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        super.onCreate()
        createChannel()
        lifecycleScope.launch {
            app.engine.state.collect { st ->
                overlay?.onEngineState(st)
                updateNotification(st)
                maybeStop()
            }
        }
        lifecycleScope.launch {
            app.settings.flow.collect { s -> overlay?.onSettings(s) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!goForeground()) {
            Toast.makeText(this, "মাইক্রোফোনের অনুমতি দরকার। অ্যাপ খুলে অনুমতি দিন।", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_BUBBLE_ON -> showBubble()
            ACTION_BUBBLE_OFF -> hideBubble()
            ACTION_TOGGLE -> app.engine.toggle(sourceOf(intent))
            ACTION_STOP_RECORDING -> app.engine.stop()
            ACTION_QUIT -> {
                lifecycleScope.launch { app.settings.set(SettingsRepo.K.bubbleEnabled, false) }
                hideBubble()
            }
        }
        maybeStop()
        return START_NOT_STICKY
    }

    private fun sourceOf(i: Intent) = if (i.getStringExtra(EXTRA_SOURCE) == "bubble") Source.BUBBLE else Source.APP

    private fun goForeground(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(app.engine.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "\"অন্য অ্যাপের উপরে দেখানো\" অনুমতি দরকার", Toast.LENGTH_LONG).show()
            return
        }
        bubbleWanted = true
        if (overlay == null) {
            overlay = OverlayController(this, app, onRemoved = {
                lifecycleScope.launch { app.settings.set(SettingsRepo.K.bubbleEnabled, false) }
                if (app.engine.state.value is EngineState.Recording) {
                    app.engine.stop()
                    Toast.makeText(this, "রেকর্ডিং থামানো হলো — লেখা History-তে পাবেন", Toast.LENGTH_LONG).show()
                }
                hideBubble()
            })
            lifecycleScope.launch {
                overlay?.show(app.settings.current(), app.settings.bubblePos())
                overlay?.onEngineState(app.engine.state.value)
            }
        }
    }

    private fun hideBubble() {
        bubbleWanted = false
        overlay?.destroy()
        overlay = null
        maybeStop()
    }

    private fun maybeStop() {
        if (!bubbleWanted && !app.engine.isBusy) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        overlay?.destroy()
        overlay = null
        // If the system stops us while recording, save what we have.
        if (app.engine.state.value is EngineState.Recording) app.engine.stop()
        super.onDestroy()
    }

    // ---------- notification ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "ভয়েস টাইপার", NotificationManager.IMPORTANCE_LOW).apply {
                description = "রেকর্ডিং ও ভাসমান মাইক চালু থাকলে দেখায়"
                setShowBadge(false)
            }
        )
    }

    private fun updateNotification(st: EngineState) {
        val key = when (st) {
            is EngineState.Recording -> "rec"
            is EngineState.Processing -> "proc${st.part}/${st.parts}"
            else -> "idle"
        }
        if (key == lastNotifKey) return
        lastNotifKey = key
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(st)) }
    }

    private fun buildNotification(st: EngineState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(a: String, req: Int): PendingIntent = PendingIntent.getService(
            this, req, Intent(this, VoiceService::class.java).setAction(a).putExtra(EXTRA_SOURCE, "bubble"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when (st) {
            is EngineState.Recording -> b.setContentTitle("রেকর্ড হচ্ছে…")
                .setContentText("কথা শেষ হলে থামান চাপুন")
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - st.elapsedMs)
                .addAction(0, "থামান", action(ACTION_STOP_RECORDING, 2))
            is EngineState.Processing -> b.setContentTitle("লেখা তৈরি হচ্ছে…")
                .setContentText(if (st.parts > 1) "অংশ ${st.part}/${st.parts} — Gemini AI কাজ করছে" else "Gemini AI কাজ করছে")
                .setProgress(0, 0, true)
            else -> b.setContentTitle("TF Voice Typer চালু আছে")
                .setContentText(if (bubbleWanted) "ভাসমান মাইকে ট্যাপ করে বাংলায় বলুন" else "প্রস্তুত")
                .addAction(0, "রেকর্ড", action(ACTION_TOGGLE, 1))
                .addAction(0, "মাইক বন্ধ", action(ACTION_QUIT, 3))
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL = "tf_voice"
        private const val NOTIF_ID = 42
        const val ACTION_BUBBLE_ON = "tf.bubble.on"
        const val ACTION_BUBBLE_OFF = "tf.bubble.off"
        const val ACTION_TOGGLE = "tf.toggle"
        const val ACTION_STOP_RECORDING = "tf.stop"
        const val ACTION_QUIT = "tf.quit"
        const val EXTRA_SOURCE = "source"

        /** Call only while the app is on screen (Android allows starting the microphone service then). */
        fun send(context: Context, action: String, source: String = "app") {
            val i = Intent(context, VoiceService::class.java).setAction(action).putExtra(EXTRA_SOURCE, source)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Toast.makeText(context, "সার্ভিস চালু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
