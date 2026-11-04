package com.taiful.tfvoicetyper.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Our recordings are raw AAC ("ADTS"). Some services (Groq/Whisper) only accept normal audio files,
 * so this wraps the same sound in an .m4a box. Nothing is re-encoded, so it is instant and lossless.
 */
object AdtsToM4a {
    private val RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun convert(adts: ByteArray, out: File): ByteArray {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()
        var pos = 0
        var muxer: MediaMuxer? = null
        var track = -1
        var sampleRate = 16000
        var frame = 0L
        val info = MediaCodec.BufferInfo()
        try {
            while (pos + 7 <= adts.size) {
                val b0 = adts[pos].toInt() and 0xFF
                val b1 = adts[pos + 1].toInt() and 0xFF
                if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) { pos++; continue } // resync
                val b2 = adts[pos + 2].toInt() and 0xFF
                val b3 = adts[pos + 3].toInt() and 0xFF
                val b4 = adts[pos + 4].toInt() and 0xFF
                val b5 = adts[pos + 5].toInt() and 0xFF
                val headerLen = if ((b1 and 0x01) == 1) 7 else 9
                val frameLen = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 shr 5) and 0x07)
                if (frameLen <= headerLen || pos + frameLen > adts.size) break
                if (muxer == null) {
                    val aot = ((b2 shr 6) and 0x03) + 1
                    val sfi = (b2 shr 2) and 0x0F
                    val ch = ((b2 and 0x01) shl 2) or ((b3 shr 6) and 0x03)
                    sampleRate = RATES.getOrElse(sfi) { 16000 }
                    val csd = ByteBuffer.wrap(byteArrayOf(
                        ((aot shl 3) or (sfi shr 1)).toByte(),
                        (((sfi and 1) shl 7) or (ch.coerceAtLeast(1) shl 3)).toByte(),
                    ))
                    val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, ch.coerceAtLeast(1))
                    fmt.setByteBuffer("csd-0", csd)
                    fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    track = muxer.addTrack(fmt)
                    muxer.start()
                }
                val payload = ByteBuffer.wrap(adts, pos + headerLen, frameLen - headerLen).slice()
                info.set(0, frameLen - headerLen, frame * 1024L * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(track, payload, info)
                frame++
                pos += frameLen
            }
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        if (frame == 0L) throw IllegalStateException("audio empty")
        return out.readBytes().also { out.delete() }
    }
}
