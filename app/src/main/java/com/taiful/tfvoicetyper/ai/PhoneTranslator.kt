package com.taiful.tfvoicetyper.ai

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Free Bangla → English translation that runs inside the phone (Google ML Kit).
 * The language pack (~30 MB) downloads once; after that it works even without internet.
 */
object PhoneTranslator {
    suspend fun bnToEn(text: String): String? {
        if (text.isBlank()) return ""
        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.BENGALI)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
        return try {
            withTimeoutOrNull(120_000) {
                t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                val out = ArrayList<String>()
                for (line in text.split('\n')) out += if (line.isBlank()) line else t.translate(line).await()
                out.joinToString("\n")
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { t.close() }
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { if (c.isActive) c.resume(it) }
        addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
        addOnCanceledListener { if (c.isActive) c.cancel() }
    }
}
