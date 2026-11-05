package com.taiful.tfvoicetyper.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Turns a saved recording (.m4a) back into plain sound: 16 kHz, mono, floats between -1 and 1.
 * Used by the offline Bangla engine. The sound is handed over piece by piece, so even a very long
 * recording never has to sit in memory all at once.
 */
object AacPcmDecoder {
    const val OUT_RATE = 16000

    /** [onChunk] gets the next piece of sound and how far we are (0..1). */
    fun decode(file: File, onChunk: (FloatArray, Float) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) throw IllegalStateException("audio track missing")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            var rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else OUT_RATE
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            var resampler = Resampler(rate, OUT_RATE)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0
            while (!outputDone) {
                var progressed = false
                if (!inputDone) {
                    val inIdx = dec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = dec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(ib, 0)
                        if (n < 0) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                val outIdx = dec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        progressed = true
                        if (info.size > 0) {
                            val ob = dec.getOutputBuffer(outIdx)!!
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val shorts = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val total = shorts.remaining()
                            val ch = channels.coerceAtLeast(1)
                            val frames = total / ch
                            val mono = FloatArray(frames)
                            var k = 0
                            for (i in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until ch) sum += shorts.get(k++)
                                mono[i] = sum / ch / 32768f
                            }
                            val out = resampler.process(mono)
                            if (out.isNotEmpty()) {
                                val frac = if (durationUs > 0) (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f) else 0f
                                onChunk(out, frac)
                            }
                        }
                        dec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        progressed = true
                        val nf = dec.outputFormat
                        val newRate = if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) nf.getInteger(MediaFormat.KEY_SAMPLE_RATE) else rate
                        channels = if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else channels
                        if (newRate != rate) { rate = newRate; resampler = Resampler(rate, OUT_RATE) }
                    }
                }
                // Safety: never spin forever if the decoder stops answering
                idleLoops = if (progressed) 0 else idleLoops + 1
                if (idleLoops > 500) throw IllegalStateException("decoder stuck")
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Straight-line resampling (only used if a phone's decoder returns a sound rate other than 16 kHz). */
    private class Resampler(private val inRate: Int, private val outRate: Int) {
        private val step = inRate.toDouble() / outRate
        private var pos = 0.0
        private var prev = 0f

        fun process(x: FloatArray): FloatArray {
            if (x.isEmpty()) return x
            if (inRate == outRate) return x
            val out = FloatArray(((x.size + 1) / step).toInt() + 2)
            var n = 0
            while (pos < x.size - 1) {
                val i = Math.floor(pos).toInt()
                val a = if (i < 0) prev else x[i]
                val b = x[i + 1]
                val t = (pos - i).toFloat()
                out[n++] = a + (b - a) * t
                pos += step
            }
            pos -= x.size
            prev = x[x.size - 1]
            return out.copyOf(n)
        }
    }
}
