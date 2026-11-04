package com.taiful.tfvoicetyper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.engine.EngineState
import com.taiful.tfvoicetyper.util.Fmt

@Composable
fun DetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val flow = remember(id) { vm.record(id) }
    val r by flow.collectAsState(null)
    val st by vm.engine.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val busyHere = st is EngineState.Processing && (st as EngineState.Processing).recordId == id

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "ফিরে যান") }
                Column(Modifier.weight(1f)) {
                    Text("লেখার বিস্তারিত", style = MaterialTheme.typography.titleMedium)
                    r?.let {
                        Text(
                            dayLabel(it.createdAt) + " • " + timeLabel(it.createdAt) + " • " + Fmt.duration(it.durationMs),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { confirm = true }, enabled = r != null) {
                    Icon(Icons.Outlined.Delete, "মুছুন", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val rec = r
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rec == null) {
                    InfoText("লেখাটি পাওয়া যায়নি (মুছে ফেলা হয়েছে হয়তো)।")
                    return@Column
                }
                if (busyHere) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("লেখা তৈরি হচ্ছে…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (rec.isFailed && !busyHere) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row {
                                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(7.dp))
                                Text(rec.error ?: "লেখা তৈরি হয়নি", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (rec.audioDir.isNotEmpty()) {
                                PillButton("আবার চেষ্টা করুন", Icons.Outlined.Refresh, filled = true) { vm.tf.engine.retry(rec.id) }
                            } else {
                                InfoText("অডিও আর নেই, তাই আবার চেষ্টা করা যাবে না।")
                            }
                        }
                    }
                }
                if (rec.bangla.isNotBlank() || rec.isDone) {
                    ResultCards(rec) { bn, en -> vm.saveText(rec.id, bn, en) }
                }
                if (rec.isDone && rec.audioDir.isNotEmpty() && !busyHere) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoText("ফলাফল পছন্দ না হলে একই অডিও থেকে আবার লেখা বানাতে পারেন।")
                    }
                    PillButton("আবার লেখা তৈরি করুন", Icons.Outlined.Refresh) { vm.tf.engine.retry(rec.id) }
                }
                if (rec.model.isNotBlank()) InfoText("মডেল: ${rec.model}")
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("এই লেখাটি মুছবেন?") },
            text = { Text("মুছে ফেললে আর ফেরত আনা যাবে না।") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.delete(listOf(id)); onBack() }) {
                    Text("মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("না") } },
        )
    }
}

