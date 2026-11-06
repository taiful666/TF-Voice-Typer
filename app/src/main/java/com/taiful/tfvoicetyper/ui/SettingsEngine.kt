package com.taiful.tfvoicetyper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.audio.PhoneRecognizer
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.data.SettingsRepo
import com.taiful.tfvoicetyper.offline.OfflineModel
import com.taiful.tfvoicetyper.offline.OfflineState

/** Which service writes the text. Default "auto" = free Google voice typing, never leaves the user stuck. */
@Composable
fun EngineCard(vm: MainViewModel, s: AppSettings) {
    val ctx = LocalContext.current
    val off by vm.tf.offline.state.collectAsState()
    val offlineOn = off is OfflineState.Installed
    val google = remember { PhoneRecognizer.available(ctx) }
    SectionCard("লেখা তৈরির ইঞ্জিন", Icons.Outlined.AutoAwesome) {
        val now = when {
            s.engine == AppSettings.ENGINE_OFFLINE && offlineOn -> "এখন চলছে: অফলাইন বাংলা ভয়েস (নেট লাগে না)"
            s.engine == AppSettings.ENGINE_GEMINI && s.hasApiKey -> "এখন চলছে: Gemini" + (if (offlineOn) " → অফলাইন" else "")
            google -> "এখন চলছে: Google ভয়েস (ফ্রি)" + (if (s.hasApiKey && s.aiPolish) " + Gemini সংশোধন" else "") +
                (if (offlineOn) " • নেট না থাকলে অফলাইন" else "")
            offlineOn -> "এখন চলছে: অফলাইন বাংলা ভয়েস"
            else -> "এই ফোনে Google ভয়েস নেই — অফলাইন বাংলা ভয়েস ডাউনলোড করুন"
        }
        Chip(now, if (google || offlineOn || s.hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(6.dp))
        val choices = buildList {
            add(Triple(AppSettings.ENGINE_AUTO, "Google ভয়েস — ফ্রি, key ছাড়া (প্রস্তাবিত)",
                "speechtexter.com যে Google প্রযুক্তি দিয়ে লেখে, সেটাই। বলার সাথে সাথে লেখা আসে, থামলেও নিজে থেকে শুনতে থাকে — আবার চাপতে হয় না। নেট না থাকলে অফলাইন বাংলা ভয়েস (ডাউনলোড করা থাকলে)।"))
            add(Triple(AppSettings.ENGINE_GEMINI, "Gemini (অডিও পাঠিয়ে)",
                "Gemini key লাগে। ফ্রি key-তে Gemini প্রায়ই \"ব্যস্ত (503)\" থাকে, তখন অডিও রাখা থাকে, পরে \"আবার চেষ্টা\" করা যায়।"))
            if (offlineOn) add(Triple(AppSettings.ENGINE_OFFLINE, "শুধু অফলাইন বাংলা ভয়েস",
                "নেট ও key কিছুই লাগে না। তবে Google-এর মতো নিখুঁত নয়, দাঁড়ি-কমা বসে না।"))
        }
        choices.forEach { (v, title, sub) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).pressClick { vm.set(SettingsRepo.K.engine, v) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.engine == v, onClick = { vm.set(SettingsRepo.K.engine, v) })
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    InfoText(sub)
                }
            }
        }
        if (s.engine == AppSettings.ENGINE_AUTO) {
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                "Gemini দিয়ে বানান ও দাঁড়ি-কমা ঠিক করুন",
                if (s.hasApiKey) "শুধু লেখাটুকু পাঠানো হয় (অডিও নয়), তাই দ্রুত। Gemini ব্যস্ত থাকলে Google-এর লেখাই থাকবে — কাজ আটকাবে না।"
                else "নিচে Gemini key দিলে চালু হবে (ঐচ্ছিক)।",
                s.aiPolish && s.hasApiKey,
            ) { vm.set(SettingsRepo.K.aiPolish, it) }
        }
    }
}

/** One-tap download of the free offline Bangla voice (works with no internet and no key afterwards). */
@Composable
fun OfflineCard(vm: MainViewModel, s: AppSettings) {
    val st by vm.tf.offline.state.collectAsState()
    SectionCard("অফলাইন বাংলা ভয়েস — নেট ও key ছাড়া", Icons.Outlined.CloudDownload) {
        InfoText("একবার ডাউনলোড করে রাখলে নেট না থাকলেও বাংলা লেখা চলবে, কোনো key লাগবে না। নেট থাকলে Google ভয়েস কাজ করবে; নেট না থাকলে এটি নিজে থেকে কাজ করবে।")
        Spacer(Modifier.height(8.dp))
        when (val x = st) {
            is OfflineState.Installed -> {
                Chip("ইনস্টল করা আছে ✓ — নেট ছাড়াই চলবে", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                ButtonRow {
                    PillButton("মুছে ফেলুন", Icons.Outlined.Delete, color = MaterialTheme.colorScheme.error) {
                        vm.tf.offlineAsr.release()
                        vm.tf.offline.remove()
                        if (s.engine == AppSettings.ENGINE_OFFLINE) vm.set(SettingsRepo.K.engine, AppSettings.ENGINE_AUTO)
                    }
                }
                Spacer(Modifier.height(4.dp))
                InfoText("মুছলে ফোনের ~২৮ MB জায়গা ফিরে আসবে; দরকার হলে আবার ডাউনলোড করা যায়।")
            }
            is OfflineState.Downloading -> {
                Chip("ডাউনলোড হচ্ছে… ${(x.progress * 100).toInt()}%", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { x.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ButtonRow { PillButton("বন্ধ করুন", Icons.Outlined.Close, color = MaterialTheme.colorScheme.error) { vm.tf.offline.cancel() } }
            }
            is OfflineState.Unpacking -> {
                Chip("প্রস্তুত করা হচ্ছে…", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is OfflineState.Failed -> {
                Text(x.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                ButtonRow {
                    PillButton("আবার চেষ্টা", Icons.Outlined.CloudDownload, filled = true) { vm.tf.offline.dismissError(); vm.tf.offline.install() }
                }
            }
            is OfflineState.NotInstalled -> {
                Chip("এখনো ডাউনলোড করা নেই", MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                ButtonRow {
                    PillButton("ডাউনলোড করুন (~${OfflineModel.APPROX_MB} MB, একবারই)", Icons.Outlined.CloudDownload, filled = true) { vm.tf.offline.install() }
                }
                Spacer(Modifier.height(4.dp))
                InfoText("ওয়াইফাইয়ে নামানো ভালো। ডাউনলোড শেষ হলে আর নেট লাগবে না।")
            }
        }
    }
}
