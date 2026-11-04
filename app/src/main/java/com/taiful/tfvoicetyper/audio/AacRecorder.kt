package com.taiful.tfvoicetyper.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Records the microphone as compact AAC (16 kHz mono, 32 kbps ≈ 14 MB per hour).
 *
 * There is no time limit. Long speech is written as several back-to-back parts of
 * about 10 minutes each (split at a quiet moment when possible). Parts join with
 * no gap, so nothing is lost, and each part is small enough to send to Gemini.
 */
class AacRecorder(
    private val dir: File,
    private val onLevel: (Float) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val MIME = "audio/aac"
        private const val BIT_RATE = 32000
        private const val SOFT_SPLIT_SAMPLES = 9L * 60 * SAMPLE_RATE      // look for a pause after 9 min
        private const val HARD_SPLIT_SAMPLES = 21L * 30 * SAMPLE_RATE     // always split by 10.5 min
        private const val QUIET_RMS = 600.0
        private const val QUIET_NEEDED_MS = 350
    }

    @Volatile private var running = false
    @Volatile var failure: String? = null
        private set
    @Volatile var totalSamples = 0L
        private set

    private var thread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var partIndex = 0

    val durationMs: Long get() = totalSamples * 1000 / SAMPLE_RATE

    /** Throws if the microphone or encoder cannot start. */
    @SuppressLint("MissingPermission")
    fun start() {
        dir.mkdirs()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = max(minBuf, 8192) * 2
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("mic-init")
        }
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                it.start()
            }
        } catch (e: Exception) {
            rec.release(); throw e
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release(); enc.release(); throw e
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release(); enc.release()
            throw IllegalStateException("mic-busy")
        }
        audioRecord = rec
        codec = enc
        running = true
        thread = Thread({ loop(rec, enc) }, "tf-recorder").also { it.start() }
    }

    /** Stops and returns the recorded parts in order. */
    fun stop(): List<File> {
        running = false
        thread?.join(8000)
        thread = null
        return parts()
    }

    fun parts(): List<File> = partsIn(dir)

    private fun newPart(): OutputStream {
        partIndex++
        val f = File(dir, "part_%03d.aac".format(partIndex))
        return BufferedOutputStream(FileOutputStream(f), 64 * 1024)
    }

    private fun loop(rec: AudioRecord, enc: MediaCodec) {
        var out: OutputStream? = null
        try {
            out = newPart()
            val pcm = ByteArray(2048)
            val info = MediaCodec.BufferInfo()
            val header = ByteArray(7)
            var eosSent = false
            var partSamples = 0L
            var quietMs = 0
            var lastLevelAt = 0L
            var splitWanted = false

            outer@ while (true) {
                if (!eosSent) {
                    val inIdx = enc.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = enc.getInputBuffer(inIdx)!!
                        ib.clear()
                        val pts = totalSamples * 1_000_000L / SAMPLE_RATE
                        if (!running) {
                            enc.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSent = true
                        } else {
                            val n = rec.read(pcm, 0, min(ib.capacity(), pcm.size))
                            if (n > 0) {
                                ib.put(pcm, 0, n)
                                enc.queueInputBuffer(inIdx, 0, n, pts, 0)
                                val samples = n / 2
                                totalSamples += samples
                                partSamples += samples
                                // loudness for the animation + pause detection
                                var sum = 0.0
                                var i = 0
                                while (i + 1 < n) {
                                    val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
                                    sum += s * s
                                    i += 2
                                }
                                val rms = sqrt(sum / max(1, samples))
                                quietMs = if (rms < QUIET_RMS) quietMs + samples * 1000 / SAMPLE_RATE else 0
                                val now = System.currentTimeMillis()
                                if (now - lastLevelAt > 60) {
                                    lastLevelAt = now
                                    onLevel(min(1f, (rms / 5000.0).toFloat()))
                                }
                                if ((partSamples > SOFT_SPLIT_SAMPLES && quietMs >= QUIET_NEEDED_MS) || partSamples > HARD_SPLIT_SAMPLES) {
                                    splitWanted = true
                                    partSamples = 0
                                    quietMs = 0
                                }
                            } else {
                                enc.queueInputBuffer(inIdx, 0, 0, pts, 0)
                                if (n < 0) {
                                    failure = "mic-read"
                                    running = false
                                }
                            }
                        }
                    }
                }
                // Take every finished AAC frame out of the encoder
                while (true) {
                    val outIdx = enc.dequeueOutputBuffer(info, if (eosSent) 10_000 else 0)
                    if (outIdx >= 0) {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            val ob = enc.getOutputBuffer(outIdx)!!
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val data = ByteArray(info.size)
                            ob.get(data)
                            if (splitWanted) {
                                out?.close()
                                out = newPart()
                                splitWanted = false
                            }
                            writeAdtsHeader(header, info.size + 7)
                            out!!.write(header)
                            out.write(data)
                        }
                        enc.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@outer
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!eosSent) break
                    }
                    // INFO_OUTPUT_FORMAT_CHANGED etc.: just continue
                }
            }
        } catch (e: Exception) {
            if (failure == null) failure = "encoder"
        } finally {
            runCatching { out?.flush(); out?.close() }
            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { enc.stop() }
            runCatching { enc.release() }
            audioRecord = null
            codec = null
            running = false
            onLevel(0f)
        }
    }

    /** 7-byte ADTS header: AAC-LC, 16 kHz (index 8), mono. Makes every frame playable on its own. */
    private fun writeAdtsHeader(h: ByteArray, packetLen: Int) {
        val profile = 2
        val freqIdx = 8
        val chanCfg = 1
        h[0] = 0xFF.toByte()
        h[1] = 0xF1.toByte()
        h[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        h[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        h[4] = ((packetLen and 0x7FF) shr 3).toByte()
        h[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        h[6] = 0xFC.toByte()
    }
}

/** Audio parts saved in a recording folder, in order (empty files skipped). */
fun partsIn(dir: File): List<File> =
    dir.listFiles { f -> f.isFile && f.name.endsWith(".aac") && f.length() > 0 }
        ?.sortedBy { it.name } ?: emptyList()
