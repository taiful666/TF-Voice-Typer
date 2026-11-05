package com.taiful.tfvoicetyper.offline

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.taiful.tfvoicetyper.ai.GeminiException
import com.taiful.tfvoicetyper.audio.AacPcmDecoder
import com.taiful.tfvoicetyper.audio.AdtsToM4a
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes Bangla text from a saved recording right inside the phone (no internet, no key).
 * Uses the open-source sherpa-onnx engine with a small Bangla model (Apache-2.0).
 */
class OfflineAsr(private val model: OfflineModel, private val cacheDir: File) {

    private val lock = Any()
    private var recognizer: OnlineRecognizer? = null

    private fun recognizer(): OnlineRecognizer = synchronized(lock) {
        recognizer ?: run {
            val tcfg = OnlineTransducerModelConfig().apply {
                encoder = model.file(OfflineModel.ENCODER).absolutePath
                decoder = model.file(OfflineModel.DECODER).absolutePath
                joiner = model.file(OfflineModel.JOINER).absolutePath
            }
            val modelCfg = OnlineModelConfig().apply {
                transducer = tcfg
                tokens = model.file(OfflineModel.TOKENS).absolutePath
                numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
                provider = "cpu"
                modelType = "zipformer2"
                debug = false
            }
            val feat = FeatureConfig().apply {
                sampleRate = AacPcmDecoder.OUT_RATE
                featureDim = 80
            }
            val cfg = OnlineRecognizerConfig().apply {
                featConfig = feat
                modelConfig = modelCfg
                enableEndpoint = false
                decodingMethod = "greedy_search"
            }
            OnlineRecognizer(config = cfg).also { recognizer = it }
        }
    }

    /** Frees the memory (called when the model is removed). */
    fun release() = synchronized(lock) {
        runCatching { recognizer?.release() }
        recognizer = null
    }

    /**
     * [aac] is one saved recording part (raw AAC). Returns the Bangla text (may be empty if nobody spoke).
     * Throws [GeminiException] with a plain-language message if something goes wrong.
     */
    suspend fun transcribe(aac: File, onProgress: (Float) -> Unit = {}): String = withContext(Dispatchers.Default) {
        val job: Job? = currentCoroutineContext()[Job]
        val m4a = File(cacheDir, "offline_part.m4a")
        try {
            if (!model.isInstalled) throw GeminiException("অফলাইন বাংলা মডেল ডাউনলোড করা নেই। Settings থেকে ডাউনলোড করুন।")
            m4a.writeBytes(AdtsToM4a.convert(aac.readBytes(), m4a))
            val r = recognizer()
            val stream = r.createStream("")
            try {
                AacPcmDecoder.decode(m4a) { samples, frac ->
                    job?.ensureActive()
                    stream.acceptWaveform(samples, AacPcmDecoder.OUT_RATE)
                    while (r.isReady(stream)) r.decode(stream)
                    onProgress(frac)
                }
                // a little silence at the end so the last word is not cut
                stream.acceptWaveform(FloatArray(AacPcmDecoder.OUT_RATE / 2), AacPcmDecoder.OUT_RATE)
                stream.inputFinished()
                while (r.isReady(stream)) r.decode(stream)
                r.getResult(stream).text.trim()
            } finally {
                runCatching { stream.release() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiException) {
            throw e
        } catch (e: OutOfMemoryError) {
            release()
            throw GeminiException("ফোনের মেমরি কম, অফলাইন লেখা তৈরি করা যায়নি। অন্য অ্যাপ বন্ধ করে আবার চেষ্টা করুন।", retryable = true)
        } catch (e: Throwable) {
            throw GeminiException("অফলাইন লেখা তৈরি করা যায়নি: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        } finally {
            runCatching { m4a.delete() }
        }
    }
}
