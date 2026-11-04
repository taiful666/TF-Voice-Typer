package com.taiful.tfvoicetyper.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object Clip {
    fun copy(context: Context, text: String, what: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "কপি করার মতো লেখা নেই", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TF Voice Typer", text))
        Toast.makeText(context, "$what কপি হয়েছে ✓", Toast.LENGTH_SHORT).show()
    }

    fun both(bangla: String, english: String): String =
        listOf(bangla.trim(), english.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
}

object Fmt {
    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
