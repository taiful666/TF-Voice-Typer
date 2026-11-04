package com.taiful.tfvoicetyper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

object Perm {
    fun mic(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun overlay(c: Context) = Settings.canDrawOverlays(c)
    fun notif(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openOverlaySettings(c: Context) {
        Toast.makeText(c, "তালিকায় \"TF Voice Typer\" খুঁজে চালু করুন, তারপর ফিরে আসুন", Toast.LENGTH_LONG).show()
        runCatching {
            c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppSettings(c: Context) {
        Toast.makeText(c, "\"Permissions / অনুমতি\" থেকে চালু করুন", Toast.LENGTH_LONG).show()
        runCatching {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

class PermRequester(
    val requestMic: () -> Unit,
    val requestNotif: () -> Unit,
)

@Composable
fun rememberPermRequester(onChanged: () -> Unit): PermRequester {
    val ctx = LocalContext.current
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) {
            Toast.makeText(ctx, "মাইক্রোফোনের অনুমতি ছাড়া রেকর্ড করা যাবে না", Toast.LENGTH_LONG).show()
            if (!Perm.mic(ctx)) Perm.openAppSettings(ctx)
        }
        onChanged()
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    return remember {
        PermRequester(
            requestMic = { mic.launch(Manifest.permission.RECORD_AUDIO) },
            requestNotif = { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

/** Beginner-friendly list of the permissions that are still missing (shows nothing when all are given). */
@Composable
fun PermissionCard(tick: Int, perms: PermRequester) {
    val ctx = LocalContext.current
    val mic = remember(tick) { Perm.mic(ctx) }
    val overlay = remember(tick) { Perm.overlay(ctx) }
    val notif = remember(tick) { Perm.notif(ctx) }
    if (mic && overlay && notif) return
    SectionCard("অনুমতি দরকার", Icons.Outlined.Shield) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!mic) PermRow(Icons.Outlined.Mic, "মাইক্রোফোন", "আপনার কথা রেকর্ড করার জন্য (জরুরি)") { perms.requestMic() }
            if (!overlay) PermRow(Icons.Outlined.Layers, "অন্য অ্যাপের উপরে দেখানো", "WhatsApp, Messenger ইত্যাদির উপরে ভাসমান মাইক দেখাতে") { Perm.openOverlaySettings(ctx) }
            if (!notif) PermRow(Icons.Outlined.Notifications, "নোটিফিকেশন", "রেকর্ডিং চলছে কিনা নোটিফিকেশনে দেখাতে (ঐচ্ছিক)") { perms.requestNotif() }
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, why: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(9.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 6.dp))
        PillButton("অনুমতি দিন", filled = true, onClick = onClick)
    }
}
