package com.taiful.tfvoicetyper.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.taiful.tfvoicetyper.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String, val sha256: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

/**
 * In-app updater: checks the app's own GitHub Releases page, downloads the new APK inside the app
 * (no browser), checks it is genuine (SHA-256 + same package + newer version), then opens
 * Android's install screen. Android itself also refuses an APK signed with a different key.
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {

    private val base = "https://github.com/taiful666/TF-Voice-Typer/releases/latest/download"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false) // stay on https
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val prefs = context.getSharedPreferences("tf_update", Context.MODE_PRIVATE)
    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private var job: Job? = null
    private var lastCheck = 0L

    /** Quiet check (on app open). [force] = user pressed "check for update". */
    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (job?.isActive == true) return
        val st = _state.value
        if (!force && (st is UpdateState.Downloading || st is UpdateState.Ready)) return
        if (!force && now - lastCheck < 30 * 60_000L) return
        lastCheck = now
        job = scope.launch(Dispatchers.IO) {
            if (force) _state.value = UpdateState.Checking
            try {
                val info = fetchInfo()
                cleanupOld()
                _state.value = if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                _state.value = if (force) UpdateState.Failed(null, "আপডেট দেখা যায়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।") else UpdateState.Idle
            }
        }
    }

    /** Should the "new update" popup be shown? (Not again for 24 h after "পরে".) */
    fun shouldPrompt(info: UpdateInfo): Boolean {
        val snoozedVer = prefs.getInt("snooze_ver", -1)
        val snoozedAt = prefs.getLong("snooze_at", 0L)
        return !(snoozedVer == info.versionCode && System.currentTimeMillis() - snoozedAt < 24 * 3600_000L)
    }

    fun snooze(info: UpdateInfo) {
        prefs.edit().putInt("snooze_ver", info.versionCode).putLong("snooze_at", System.currentTimeMillis()).apply()
    }

    fun download(info: UpdateInfo) {
        if (job?.isActive == true && _state.value is UpdateState.Downloading) return
        job = scope.launch(Dispatchers.IO) {
            val out = File(dir, "TF-Voice-Typer-${info.versionCode}.apk")
            val part = File(dir, out.name + ".part")
            try {
                _state.value = UpdateState.Downloading(info, 0f)
                val req = Request.Builder().url("$base/TF-Voice-Typer.apk").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val body = resp.body ?: throw Exception("খালি ফাইল")
                    val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                    val md = MessageDigest.getInstance("SHA-256")
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                md.update(buf, 0, n)
                                done += n
                                if (total > 0 && done - lastEmit > 32 * 1024) {
                                    lastEmit = done
                                    _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (info.sha256.isNotBlank() && !hash.equals(info.sha256, ignoreCase = true)) {
                        throw SecurityException("ফাইলটি ঠিকমতো নামেনি (মিলছে না)। আবার চেষ্টা করুন।")
                    }
                }
                verifyApk(part)
                out.delete()
                part.renameTo(out)
                _state.value = UpdateState.Ready(info, out)
            } catch (e: SecurityException) {
                part.delete()
                _state.value = UpdateState.Failed(info, e.message ?: "ফাইলটি যাচাই করা যায়নি")
            } catch (e: Exception) {
                part.delete()
                _state.value = UpdateState.Failed(info, "ডাউনলোড হয়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।")
            }
        }
    }

    /** Can this app open Android's install screen? (First time the user must allow it once.) */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens Android's own "Update this app?" screen. */
    fun install(ctx: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    fun dismissError() {
        val st = _state.value
        if (st is UpdateState.Failed) _state.value = st.info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
        else if (st is UpdateState.UpToDate) _state.value = UpdateState.Idle
    }

    private fun fetchInfo(): UpdateInfo {
        val req = Request.Builder().url("$base/version.json").header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val j = JSONObject(resp.body?.string().orEmpty())
            return UpdateInfo(
                versionCode = j.getInt("versionCode"),
                versionName = j.optString("versionName", ""),
                notes = j.optString("notes", "").take(300),
                sha256 = j.optString("sha256", ""),
                sizeBytes = j.optLong("size", 0L),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(f: File) {
        val pi = context.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
            ?: throw SecurityException("ফাইলটি সঠিক অ্যাপ নয়")
        if (pi.packageName != context.packageName) throw SecurityException("ফাইলটি এই অ্যাপের নয়")
        val vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        if (vc <= BuildConfig.VERSION_CODE) throw SecurityException("এটি নতুন সংস্করণ নয়")
    }

    private fun cleanupOld() {
        dir.listFiles()?.forEach { f ->
            val v = Regex("TF-Voice-Typer-(\\d+)\\.apk").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (f.name.endsWith(".part") || v == null || v <= BuildConfig.VERSION_CODE) f.delete()
        }
    }
}
