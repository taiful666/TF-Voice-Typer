package com.taiful.tfvoicetyper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.data.db.Record
import com.taiful.tfvoicetyper.util.Clip

/**
 * The two result cards (বাংলা সংস্করণ / English Version).
 * Tap a card to copy it; pencil = edit; plus Copy Bangla / English / Both.
 */
@Composable
fun ResultCards(record: Record, onSave: (bangla: String, english: String) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextCard(
            title = "বাংলা সংস্করণ",
            text = record.bangla,
            accent = MaterialTheme.colorScheme.primary,
            key = "bn${record.id}",
            onCopy = { Clip.copy(ctx, record.bangla, "বাংলা লেখা") },
            onSave = { onSave(it, record.english) },
        )
        if (record.english.isNotBlank()) {
            TextCard(
                title = "English Version",
                text = record.english,
                accent = MaterialTheme.colorScheme.secondary,
                key = "en${record.id}",
                onCopy = { Clip.copy(ctx, record.english, "English text") },
                onSave = { onSave(record.bangla, it) },
            )
        }
        ButtonRow {
            PillButton("বাংলা", Icons.Outlined.ContentCopy, Modifier.weight(1f), filled = true) { Clip.copy(ctx, record.bangla, "বাংলা লেখা") }
            PillButton("English", Icons.Outlined.ContentCopy, Modifier.weight(1f), filled = true, color = MaterialTheme.colorScheme.secondary, enabled = record.english.isNotBlank()) {
                Clip.copy(ctx, record.english, "English text")
            }
            PillButton("দুটোই", Icons.Outlined.ContentCopy, Modifier.weight(1f)) { Clip.copy(ctx, Clip.both(record.bangla, record.english), "দুটো লেখা") }
        }
    }
}

@Composable
private fun TextCard(
    title: String,
    text: String,
    accent: Color,
    key: String,
    onCopy: () -> Unit,
    onSave: (String) -> Unit,
) {
    var editing by remember(key) { mutableStateOf(false) }
    var draft by remember(key, text) { mutableStateOf(text) }
    LaunchedEffect(text) { if (!editing) draft = text }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = if (editing) 0.8f else 0.28f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f)).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.weight(1f))
                if (editing) {
                    IconButton(onClick = { draft = text; editing = false }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Close, "বাতিল", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onSave(draft.trim()); editing = false }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Check, "সংরক্ষণ", Modifier.size(19.dp), tint = accent)
                    }
                } else {
                    IconButton(onClick = { editing = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Edit, "সম্পাদনা", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.ContentCopy, "কপি", Modifier.size(17.dp), tint = accent)
                    }
                }
            }
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TranscriptStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    text.ifBlank { "—" },
                    style = TranscriptStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().pressClick(onClick = onCopy).padding(horizontal = 11.dp, vertical = 8.dp),
                )
                AnimatedVisibility(text.isNotBlank()) {
                    Text(
                        "কার্ডে ট্যাপ করলেই পুরো লেখা কপি হবে",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 11.dp, bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
