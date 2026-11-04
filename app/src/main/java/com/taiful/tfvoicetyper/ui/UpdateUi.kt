package com.taiful.tfvoicetyper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.BuildConfig
import com.taiful.tfvoicetyper.TFApp
import com.taiful.tfvoicetyper.update.UpdateState
import java.io.File

/** Small shared UI flags for the update popup. */
private object UpdUi {
    var open by mutableStateOf(false)          // popup visible
    var askedThisOpen by mutableStateOf(false)  // popup already shown once since the app was opened
    var installedFor by mutableStateOf<String?>(null) // installer already opened for this file
    var waitingPermission by mutableStateOf(false)
}

/** Opens the install screen, asking once for "install unknown apps" if needed. */
private fun startInstall(ctx: android.content.Context, tf: TFApp, file: File) {
    val up = tf.updater
    if (!up.canInstall()) {
        UpdUi.waitingPermission = true
        Toast.makeText(ctx, "\"TF Voice Typer\"-এর পাশে \"অনুমতি দিন\" চালু করে ফিরে আসুন", Toast.LENGTH_LONG).show()
        up.openInstallPermission(ctx)
        return
    }
    UpdUi.installedFor = file.absolutePath
    if (!up.install(ctx, file)) Toast.makeText(ctx, "ইনস্টল স্ক্রিন খোলা যায়নি", Toast.LENGTH_LONG).show()
}

/** Checks for an update when the app opens and shows the popup. Put once in the app root. */
@Composable
fun UpdateHost(tf: TFApp, resumeTick: Int) {
    val ctx = LocalContext.current
    val up = tf.updater
    val st by up.state.collectAsState()

    LaunchedEffect(resumeTick) {
        up.check()
        // Came back after allowing "install unknown apps" → continue straight to install.
        val s = up.state.value
        if (UpdUi.waitingPermission && s is UpdateState.Ready && up.canInstall()) {
            UpdUi.waitingPermission = false
            startInstall(ctx, tf, s.file)
        }
    }

    // New version found on app open → show the popup once (not again for 24 h after "পরে").
    LaunchedEffect(st) {
        val s = st
        if (s is UpdateState.Available && !UpdUi.askedThisOpen && up.shouldPrompt(s.info)) {
            UpdUi.askedThisOpen = true
            UpdUi.open = true
        }
        // Download finished → open Android's install screen right away.
        if (s is UpdateState.Ready && UpdUi.installedFor != s.file.absolutePath) {
            startInstall(ctx, tf, s.file)
        }
    }

    if (!UpdUi.open) return
    val close = { UpdUi.open = false; up.dismissError() }

    when (val s = st) {
        is UpdateState.Checking -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Outlined.SystemUpdate, null) },
            title = { Text("আপডেট দেখা হচ্ছে…") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp); Spacer(Modifier.width(10.dp)); Text("একটু অপেক্ষা করুন") } },
            confirmButton = { TextButton(onClick = close) { Text("বন্ধ") } },
        )
        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Outlined.SystemUpdate, null) },
            title = { Text("আপনার অ্যাপ আপ-টু-ডেট ✓") },
            text = { Text("সংস্করণ ${BuildConfig.VERSION_NAME} — এটাই সর্বশেষ।") },
            confirmButton = { TextButton(onClick = close) { Text("ঠিক আছে") } },
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { up.snooze(s.info); close() },
            icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("নতুন আপডেট এসেছে") },
            text = {
                Column {
                    Text("সংস্করণ ${s.info.versionName}  (এখন আছে ${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (s.info.notes.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(s.info.notes, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(6.dp))
                    InfoText("\"আপডেট করুন\" চাপলে অ্যাপের ভেতরেই নামবে, তারপর শুধু \"Update\" চাপবেন। আপনার লেখা, হিস্টরি ও সেটিংস সব থাকবে।")
                }
            },
            confirmButton = {
                PillButton("আপডেট করুন", Icons.Rounded.Download, filled = true) { up.download(s.info) }
            },
            dismissButton = { TextButton(onClick = { up.snooze(s.info); close() }) { Text("পরে") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { UpdUi.open = false },
            icon = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("আপডেট নামছে… ${(s.progress * 100).toInt()}%") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    Spacer(Modifier.height(8.dp))
                    InfoText("অ্যাপ ব্যবহার করতে থাকুন — নামা শেষ হলে নিজেই ইনস্টল স্ক্রিন আসবে।")
                }
            },
            confirmButton = { TextButton(onClick = { UpdUi.open = false }) { Text("লুকান") } },
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Rounded.InstallMobile, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("আপডেট প্রস্তুত") },
            text = { Text("সংস্করণ ${s.info.versionName} নামানো হয়েছে। \"ইনস্টল\" চাপুন, তারপর Android-এর স্ক্রিনে \"Update\" চাপুন।") },
            confirmButton = { PillButton("ইনস্টল", Icons.Rounded.InstallMobile, filled = true) { startInstall(ctx, tf, s.file) } },
            dismissButton = { TextButton(onClick = close) { Text("পরে") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = close,
            icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("আপডেট হয়নি") },
            text = { Text(s.message) },
            confirmButton = {
                PillButton("আবার চেষ্টা", Icons.Rounded.Refresh, filled = true) {
                    val info = s.info
                    if (info != null) up.download(info) else up.check(force = true)
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("বন্ধ") } },
        )
        else -> SideEffect { UpdUi.open = false }
    }
}

/** "Update" card for the Settings screen. */
@Composable
fun UpdateCard(tf: TFApp) {
    val ctx = LocalContext.current
    val st by tf.updater.state.collectAsState()
    SectionCard("আপডেট", Icons.Outlined.SystemUpdate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("সংস্করণ ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                InfoText(
                    when (val s = st) {
                        is UpdateState.Available -> "নতুন সংস্করণ ${s.info.versionName} পাওয়া গেছে"
                        is UpdateState.Downloading -> "নামছে… ${(s.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "নামানো হয়েছে — ইনস্টল করুন"
                        is UpdateState.UpToDate -> "এটাই সর্বশেষ সংস্করণ"
                        else -> "অ্যাপ খুললেই নতুন আপডেট আছে কিনা নিজে দেখে নেয়"
                    }
                )
            }
            when (val s = st) {
                is UpdateState.Available -> PillButton("আপডেট", Icons.Rounded.Download, filled = true) { UpdUi.open = true; tf.updater.download(s.info) }
                is UpdateState.Ready -> PillButton("ইনস্টল", Icons.Rounded.InstallMobile, filled = true) { startInstall(ctx, tf, s.file) }
                is UpdateState.Downloading -> PillButton("দেখুন", Icons.Rounded.Download) { UpdUi.open = true }
                else -> PillButton("আপডেট দেখুন", Icons.Rounded.Refresh) { UpdUi.open = true; tf.updater.check(force = true) }
            }
        }
    }
}

