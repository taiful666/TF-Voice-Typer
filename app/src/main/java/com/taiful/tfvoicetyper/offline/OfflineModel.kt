package com.taiful.tfvoicetyper.offline

import android.content.Context
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
import java.util.zip.ZipInputStream

sealed interface OfflineState {
    data object NotInstalled : OfflineState
    data class Downloading(val progress: Float) : OfflineState
    data object Unpacking : OfflineState
    data object Installed : OfflineState
    data class Failed(val message: String) : OfflineState
}

/**
 * The free offline Bangla voice model (about 22 MB). It is NOT inside the app: the user taps one
 * button in Settings, it downloads once, and from then on typing works with no internet and no key.
 *
 * Safety: the file comes only from this app's own GitHub release page, over https, and is checked
 * against a SHA-256 fingerprint before anything is unpacked. Only four known file names are accepted
 * from the zip, so nothing can be written anywhere else.
 */
class OfflineModel(private val context: Context, private val scope: CoroutineScope) {

    companion object {
        private const val BASE = "https://github.com/taiful666/TF-Voice-Typer/releases/latest/download"
        private const val ZIP_NAME = "tf-bangla-offline.zip"
        const val APPROX_MB = 22
        private const val MAX_BYTES = 60L * 1024 * 1024
        const val ENCODER = "encoder.int8.onnx"
        const val DECODER = "decoder.onnx"
        const val JOINER = "joiner.int8.onnx"
        const val TOKENS = "tokens.txt"
        private val REQUIRED = listOf(ENCODER, DECODER, JOINER, TOKENS)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false) // stay on https
        .build()

    val dir: File get() = File(context.filesDir, "offline_bn")
    private val marker get() = File(dir, ".ok")

    private val _state = MutableStateFlow<OfflineState>(if (check()) OfflineState.Installed else OfflineState.NotInstalled)
    val state: StateFlow<OfflineState> = _state

    val isInstalled: Boolean get() = _state.value is OfflineState.Installed

    private var job: Job? = null

    private fun check(): Boolean = marker.exists() && REQUIRED.all { File(dir, it).let { f -> f.isFile && f.length() > 0 } }

    fun file(name: String) = File(dir, name)

    /** Downloads, checks and unpacks the model. Safe to call again after a failure. */
    fun install() {
        if (job?.isActive == true) return
        if (isInstalled) return
        job = scope.launch(Dispatchers.IO) {
            val tmpDir = File(context.cacheDir, "offline_dl").apply { deleteRecursively(); mkdirs() }
            val part = File(tmpDir, "$ZIP_NAME.part")
            val unpack = File(context.filesDir, "offline_bn.tmp")
            try {
                _state.value = OfflineState.Downloading(0f)
                if (context.filesDir.usableSpace < 120L * 1024 * 1024) {
                    throw FriendlyError("ফোনে জায়গা কম। অন্তত ১২০ MB ফাঁকা জায়গা লাগবে।")
                }
                val meta = fetchMeta()
                val req = Request.Builder().url("$BASE/$ZIP_NAME").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val body = resp.body ?: throw Exception("empty")
                    val total = body.contentLength().takeIf { it > 0 } ?: meta.size
                    if (total > MAX_BYTES || meta.size > MAX_BYTES) throw FriendlyError("মডেল ফাইলের আকার ঠিক নেই, তাই নামানো হয়নি।")
                    val md = MessageDigest.getInstance("SHA-256")
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                done += n
                                if (done > MAX_BYTES) throw FriendlyError("মডেল ফাইলের আকার ঠিক নেই, তাই নামানো হয়নি।")
                                output.write(buf, 0, n)
                                md.update(buf, 0, n)
                                if (total > 0 && done - lastEmit > 64 * 1024) {
                                    lastEmit = done
                                    _state.value = OfflineState.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    if (!isActive) throw kotlinx.coroutines.CancellationException()
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (!hash.equals(meta.sha256, ignoreCase = true)) {
                        throw FriendlyError("ফাইলটি ঠিকমতো নামেনি (মিলছে না)। আবার চেষ্টা করুন।")
                    }
                }
                _state.value = OfflineState.Unpacking
                unpack.deleteRecursively(); unpack.mkdirs()
                unzipKnownFiles(part, unpack)
                File(unpack, ".ok").writeText(meta.sha256)
                dir.deleteRecursively()
                if (!unpack.renameTo(dir)) throw Exception("rename failed")
                _state.value = OfflineState.Installed
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = OfflineState.NotInstalled
                throw e
            } catch (e: FriendlyError) {
                _state.value = OfflineState.Failed(e.message ?: "ডাউনলোড হয়নি")
            } catch (e: Exception) {
                _state.value = OfflineState.Failed("ডাউনলোড হয়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।")
            } finally {
                runCatching { tmpDir.deleteRecursively() }
                runCatching { unpack.deleteRecursively() }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Removes the model from the phone (it can be downloaded again any time). */
    fun remove() {
        job?.cancel()
        runCatching { dir.deleteRecursively() }
        _state.value = OfflineState.NotInstalled
    }

    fun dismissError() {
        if (_state.value is OfflineState.Failed) _state.value = if (check()) OfflineState.Installed else OfflineState.NotInstalled
    }

    private class FriendlyError(msg: String) : Exception(msg)
    private class Meta(val sha256: String, val size: Long)

    /** The fingerprint of the model file is published next to the app update info. */
    private fun fetchMeta(): Meta {
        val req = Request.Builder().url("$BASE/version.json").header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val j = JSONObject(resp.body?.string().orEmpty())
            val sha = j.optString("modelSha256", "")
            if (!Regex("[0-9a-fA-F]{64}").matches(sha)) {
                throw FriendlyError("অফলাইন মডেল এখনো প্রস্তুত নয়। অ্যাপ আপডেট করে আবার চেষ্টা করুন।")
            }
            return Meta(sha, j.optLong("modelSize", 0L))
        }
    }

    /** Takes only the four expected file names out of the zip (anything else is ignored). */
    private fun unzipKnownFiles(zip: File, into: File) {
        val seen = HashSet<String>()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || name !in REQUIRED) continue
                var written = 0L
                File(into, name).outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > MAX_BYTES) throw FriendlyError("মডেল ফাইলের আকার ঠিক নেই।")
                        out.write(buf, 0, n)
                    }
                }
                if (written == 0L) throw FriendlyError("মডেল ফাইল ভাঙা।")
                seen += name
            }
        }
        if (!seen.containsAll(REQUIRED)) throw FriendlyError("মডেল ফাইল অসম্পূর্ণ। আবার চেষ্টা করুন।")
    }
}
