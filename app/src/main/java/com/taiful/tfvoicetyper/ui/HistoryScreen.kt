package com.taiful.tfvoicetyper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.data.db.Record
import com.taiful.tfvoicetyper.util.Clip
import com.taiful.tfvoicetyper.util.Fmt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val bn = Locale("bn", "BD")

fun dayLabel(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(c, today)) return "আজ"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(c, today)) return "গতকাল"
    return SimpleDateFormat("d MMMM yyyy, EEEE", bn).format(Date(t))
}

fun timeLabel(t: Long): String = SimpleDateFormat("h:mm a", bn).format(Date(t))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val list by vm.history.collectAsState()
    val total by vm.count.collectAsState()
    val q by vm.query.collectAsState()
    val selected = remember { mutableStateListOf<Long>() }
    var confirmSel by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        // Search + actions
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("হিস্টরিতে খুঁজুন…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = q, onValueChange = { vm.query.value = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (q.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, "মুছুন", Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            AnimatedContent(selecting, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sel") { sel ->
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sel) {
                        IconButton(onClick = { selected.clear() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "বাতিল", Modifier.size(19.dp)) }
                        Text("${selected.size}টি নির্বাচিত", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        PillButton("সব", Icons.Outlined.SelectAll) { selected.clear(); selected.addAll(list.map { it.id }) }
                        Spacer(Modifier.width(6.dp))
                        PillButton("মুছুন", Icons.Outlined.Delete, filled = true, color = MaterialTheme.colorScheme.error) { confirmSel = true }
                    } else {
                        Text(
                            if (q.isBlank()) "মোট ${total}টি লেখা" else "${list.size}টি পাওয়া গেছে",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (total > 0) PillButton("সব মুছুন", Icons.Outlined.DeleteSweep, color = MaterialTheme.colorScheme.error) { confirmAll = true }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(if (q.isBlank()) "এখনো কোনো লেখা নেই" else "কিছু পাওয়া যায়নি", style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }

        val grouped = remember(list) { list.groupBy { dayLabel(it.createdAt) } }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            grouped.forEach { (day, items) ->
                item(key = "h_$day") {
                    Text(
                        day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 1.dp, start = 2.dp),
                    )
                }
                items(items, key = { it.id }) { r ->
                    val isSel = r.id in selected
                    HistoryItem(
                        r, selecting, isSel,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            if (selecting) { if (isSel) selected.remove(r.id) else selected.add(r.id) } else onOpen(r.id)
                        },
                        onLong = { if (!isSel) selected.add(r.id) },
                        onCopy = { Clip.copy(ctx, r.bangla, "বাংলা লেখা") },
                    )
                }
            }
        }
    }

    if (confirmSel) {
        AlertDialog(
            onDismissRequest = { confirmSel = false },
            title = { Text("${selected.size}টি লেখা মুছবেন?") },
            text = { Text("মুছে ফেললে আর ফেরত আনা যাবে না।") },
            confirmButton = { TextButton(onClick = { vm.delete(selected.toList()); selected.clear(); confirmSel = false }) { Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmSel = false }) { Text("না") } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("সব হিস্টরি মুছবেন?") },
            text = { Text("মোট ${total}টি লেখা ও তাদের অডিও স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); selected.clear(); confirmAll = false }) { Text("সব মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("না") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    r: Record, selecting: Boolean, selected: Boolean, modifier: Modifier,
    onClick: () -> Unit, onLong: () -> Unit, onCopy: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) c.primary else c.outlineVariant, label = "b")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.primary.copy(alpha = 0.07f) else c.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                    Modifier.size(20.dp), tint = if (selected) c.primary else c.outline,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeLabel(r.createdAt), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                    Text("  •  " + Fmt.duration(r.durationMs), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    when {
                        r.isFailed -> Chip("ব্যর্থ — আবার চেষ্টা করুন", c.error)
                        r.status == Record.STATUS_PROCESSING -> Chip("প্রসেস হচ্ছে", c.secondary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    r.bangla.ifBlank { r.error ?: "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selecting && r.isDone) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "কপি", Modifier.size(17.dp), tint = c.primary)
                }
            }
        }
    }
}
