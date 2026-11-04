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
    /** True only after Android has accepted us as a foreground service. Before that we must never stop. */
    private var inForeground = false

    override fun onCreate() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        super.onCreate()
        createChannel()
        lifecycleScope.launch {
            app.engine.state.collect { st ->
                runCatching { overlay?.onEngineState(st) }
                if (inForeground) {
                    updateNotification(st)
                    maybeStop()
                }
            }
        }
        lifecycleScope.launch {
            app.settings.flow.collect { s -> runCatching { overlay?.onSettings(s) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Android requires startForeground() for EVERY start request, before anything can stop us.
        if (!goForeground()) {
            Toast.makeText(this, "মাইক্রোফোনের অনুমতি দরকার। অ্যাপ খুলে অনুমতি দিন।", Toast.LENGTH_LONG).show()
            stopEverything()
            return START_NOT_STICKY
        }
        running = true
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
            inForeground = true
            true
        } catch (e: Exception) {
            // Already a foreground service (e.g. tapped from the notification) → keep going.
            if (inForeground) return true
            app.saveProblem("সার্ভিস", e)
            // Still satisfy Android's rule with a plain foreground notice, so the app is never killed.
            runCatching {
                ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(app.engine.state.value), 0)
                inForeground = true
            }
            false
        }
    }

    private fun stopEverything() {
        running = false
        if (inForeground) runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        inForeground = false
        stopSelf()
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "\"অন্য অ্যাপের উপরে দেখানো\" অনুমতি দরকার", Toast.LENGTH_LONG).show()
            return
        }
        bubbleWanted = true
        refreshNotification()
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
                try {
                    overlay?.show(app.settings.current(), app.settings.bubblePos())
                    overlay?.onEngineState(app.engine.state.value)
                } catch (e: Exception) {
                    // Never let the floating mic break the app: switch it off and tell the user.
                    app.saveProblem("ভাসমান মাইক", e)
                    app.settings.set(SettingsRepo.K.bubbleEnabled, false)
                    Toast.makeText(this@VoiceService, "ভাসমান মাইক চালু করা যায়নি, বন্ধ করা হলো। অ্যাপ স্বাভাবিকভাবে চলবে।", Toast.LENGTH_LONG).show()
                    hideBubble()
                }
            }
        }
    }

    private fun hideBubble() {
        bubbleWanted = false
        runCatching { overlay?.destroy() }
        overlay = null
        refreshNotification()
        maybeStop()
    }

    private fun maybeStop() {
        if (!inForeground) return
        if (!bubbleWanted && !app.engine.isBusy) stopEverything()
    }

    private fun refreshNotification() {
        if (!inForeground) return
        lastNotifKey = ""
        updateNotification(app.engine.state.value)
    }

    override fun onDestroy() {
        running = false
        inForeground = false
        overlay?.destroy()
        overlay = null
        // If the system stops us while recording, save what we have.
        if (app.engine.state.value is EngineState.Recording) app.engine.stop()
        super.onDestroy()
    }

    // ---------- notification ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // Old channel had buttons/extra text — remove it (channel importance can't be changed once made).
        runCatching { nm.deleteNotificationChannel("tf_voice") }
        nm.createNotificationChannel(
            // MIN = silent, folded into the bottom "silent" section, no pop-up, no sound.
            NotificationChannel(CHANNEL, "মাইক চালু", NotificationManager.IMPORTANCE_MIN).apply {
                description = "ভাসমান মাইক চালু থাকলে ছোট করে দেখায়"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun updateNotification(st: EngineState) {
        val key = "mic" // one fixed, simple notice — never changes
        if (key == lastNotifKey) return
        lastNotifKey = key
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(st)) }
    }

    /** Only one tiny line: "মাইক চালু আছে". No buttons, no extra text — so nobody clears it by mistake. */
    @Suppress("UNUSED_PARAMETER")
    private fun buildNotification(st: EngineState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("মাইক চালু আছে")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        private const val CHANNEL = "tf_voice_min"
        private const val NOTIF_ID = 42
        const val ACTION_BUBBLE_ON = "tf.bubble.on"
        const val ACTION_BUBBLE_OFF = "tf.bubble.off"
        const val ACTION_TOGGLE = "tf.toggle"
        const val ACTION_STOP_RECORDING = "tf.stop"
        const val ACTION_QUIT = "tf.quit"
        const val EXTRA_SOURCE = "source"

        /** True while the service is up and in the foreground. */
        @Volatile var running = false
            private set

        /** Call only while the app is on screen (Android allows starting the microphone service then). */
        fun send(context: Context, action: String, source: String = "app") {
            val app = context.applicationContext as TFApp
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            // Nothing to switch off if the service isn't running — don't start it just to stop it.
            if (!running && (action == ACTION_BUBBLE_OFF || action == ACTION_QUIT)) return
            if (!running && action == ACTION_STOP_RECORDING) { app.engine.stop(); return }
            if (!hasMic) {
                // Never start the microphone service without permission (Android would close the app).
                if (action == ACTION_TOGGLE) app.engine.toggle(Source.APP)
                return
            }
            val i = Intent(context, VoiceService::class.java).setAction(action).putExtra(EXTRA_SOURCE, source)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                app.saveProblem("সার্ভিস চালু", e)
                // Fallback: in-app voice typing still works directly while the app is open.
                when (action) {
                    ACTION_TOGGLE -> app.engine.toggle(Source.APP)
                    ACTION_STOP_RECORDING -> app.engine.stop()
                    else -> Toast.makeText(context, "ভাসমান মাইক চালু করা যায়নি। অ্যাপের ভেতরের মাইক ব্যবহার করুন।", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
