package com.taiful.tfvoicetyper.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.data.SettingsRepo
import kotlinx.coroutines.launch

/** Which service writes the text. Default "auto" never leaves the user stuck. */
@Composable
fun EngineCard(vm: MainViewModel, s: AppSettings) {
    SectionCard("লেখা তৈরির ইঞ্জিন", Icons.Outlined.AutoAwesome) {
        val now = when {
            s.engine == AppSettings.ENGINE_PHONE || (!s.hasApiKey && !s.hasGroqKey) -> "এখন চলছে: ফোনের ভয়েস টাইপিং (key ছাড়া)"
            s.engine == AppSettings.ENGINE_GROQ -> "এখন চলছে: " + listOfNotNull("Groq".takeIf { s.hasGroqKey }, "Gemini".takeIf { s.hasApiKey }).joinToString(" → ")
            else -> "এখন চলছে: " + listOfNotNull("Gemini".takeIf { s.hasApiKey }, "Groq".takeIf { s.hasGroqKey }).joinToString(" → ")
        }
        Chip(now, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        listOf(
            Triple(AppSettings.ENGINE_AUTO, "স্বয়ংক্রিয় (প্রস্তাবিত)", "আগে Gemini, ব্যস্ত হলে নিজে থেকে Groq। কোনো key না থাকলে ফোনের ভয়েস টাইপিং।"),
            Triple(AppSettings.ENGINE_GROQ, "আগে Groq", "Groq দ্রুত; ব্যস্ত হলে Gemini।"),
            Triple(AppSettings.ENGINE_PHONE, "শুধু ফোনের ভয়েস টাইপিং", "কোনো key লাগে না, সীমাহীন। তবে দাঁড়ি-কমা ও বানান AI দিয়ে ঠিক হয় না।"),
        ).forEach { (v, title, sub) ->
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
    }
}

/** Free backup: Groq key (stored encrypted on this phone only). */
@Composable
fun GroqCard(vm: MainViewModel, s: AppSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    SectionCard("Groq API — ফ্রি ব্যাকআপ", Icons.Outlined.Bolt) {
        var key by remember { mutableStateOf("") }
        var show by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
        var working by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { key = vm.storedGroqKey() }

        InfoText("Gemini ব্যস্ত থাকলে অ্যাপ নিজে থেকে Groq দিয়ে লেখা তৈরি করবে। Groq সম্পূর্ণ ফ্রি, কার্ড লাগে না।")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(start = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (key.isEmpty()) Text("এখানে Groq key পেস্ট করুন (gsk_…)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = key, onValueChange = { key = it.trim() }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "লুকান" else "দেখুন", Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        if (s.hasGroqKey) Chip("সংরক্ষিত ✓ — শুধু এই ফোনে, এনক্রিপ্ট করা", MaterialTheme.colorScheme.primary)
        else Chip("এখনো দেওয়া হয়নি", MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        ButtonRow {
            PillButton("সংরক্ষণ", Icons.Outlined.Save, Modifier.weight(1f), filled = true, enabled = key.isNotBlank() && !working) {
                scope.launch {
                    working = true; msg = null
                    vm.saveGroqKey(key)
                    show = false
                    // check it right away, so the user knows it works
                    val r = vm.testGroq(key)
                    msg = if (r.first) true to "সংরক্ষিত হয়েছে — ${r.second}" else r
                    working = false
                }
            }
            PillButton("পরীক্ষা", Icons.Outlined.NetworkCheck, Modifier.weight(1f), enabled = key.isNotBlank() && !working) {
                scope.launch { working = true; msg = null; msg = vm.testGroq(key); working = false }
            }
            PillButton("মুছুন", Icons.Outlined.Close, Modifier.weight(0.8f), color = MaterialTheme.colorScheme.error, enabled = s.hasGroqKey) {
                scope.launch { vm.saveGroqKey(""); key = ""; msg = true to "Groq key মুছে ফেলা হয়েছে" }
            }
        }
        if (working) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); InfoText("পরীক্ষা হচ্ছে…")
            }
        }
        msg?.let { (ok, t) ->
            Spacer(Modifier.height(6.dp))
            Text(t, style = MaterialTheme.typography.bodySmall, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        PillButton("ফ্রি Groq key নিন (Google দিয়ে লগইন)", Icons.Outlined.OpenInNew) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }
        }
        Spacer(Modifier.height(4.dp))
        InfoText("ধাপ: লিংকে ঢুকুন → Google দিয়ে লগইন → \"Create API Key\" → নাম দিন → কপি করে এখানে পেস্ট → সংরক্ষণ।")
    }
}
