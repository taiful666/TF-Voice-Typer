package com.taiful.tfvoicetyper.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Mic
import com.taiful.tfvoicetyper.BuildConfig
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.data.SettingsRepo
import kotlinx.coroutines.launch

private val DefaultModels = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-flash-lite-latest")

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings, setBubble: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val count by vm.count.collectAsState()
    val fetched by vm.savedModels.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineCard(vm, s)

        // ---------------- Gemini API ----------------
        SectionCard("Gemini API — প্রধান (সবচেয়ে ভালো মান)", Icons.Outlined.Key) {
            var key by remember { mutableStateOf("") }
            var show by remember { mutableStateOf(false) }
            var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
            var working by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { key = vm.storedKey() }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(start = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (key.isEmpty()) Text("এখানে API key পেস্ট করুন (AIza…)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.hasApiKey) Chip("সংরক্ষিত ✓ — শুধু এই ফোনে, এনক্রিপ্ট করা", MaterialTheme.colorScheme.primary)
                else Chip("এখনো দেওয়া হয়নি", MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            ButtonRow {
                PillButton("সংরক্ষণ", Icons.Outlined.Save, Modifier.weight(1f), filled = true, enabled = key.isNotBlank()) {
                    scope.launch {
                        vm.saveKey(key)
                        msg = true to "API key সংরক্ষিত হয়েছে"
                        show = false
                    }
                }
                PillButton("পরীক্ষা", Icons.Outlined.NetworkCheck, Modifier.weight(1f), enabled = key.isNotBlank() && !working) {
                    scope.launch {
                        working = true; msg = null
                        msg = vm.test(key, s.model)
                        working = false
                    }
                }
                PillButton("মুছুন", Icons.Outlined.Close, Modifier.weight(0.8f), color = MaterialTheme.colorScheme.error, enabled = s.hasApiKey) {
                    scope.launch { vm.saveKey(""); key = ""; msg = true to "API key মুছে ফেলা হয়েছে" }
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
            PillButton("বিনামূল্যে API key কোথায় পাবেন?", Icons.Outlined.OpenInNew) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
            }

            Spacer(Modifier.height(12.dp))
            Text("AI মডেল", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            var open by remember { mutableStateOf(false) }
            var loading by remember { mutableStateOf(false) }
            var modelMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
            val models = remember(fetched, s.model) { (listOf(s.model) + DefaultModels + fetched).distinct() }
            Box {
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).pressClick { open = true }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(m, style = MaterialTheme.typography.bodyMedium)
                                    modelHint(m)?.let { InfoText(it) }
                                }
                            },
                            trailingIcon = { if (m == s.model) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { vm.set(SettingsRepo.K.model, m); open = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton("Google থেকে নতুন মডেলের তালিকা আনুন", Icons.Outlined.CloudDownload, enabled = !loading && (key.isNotBlank() || s.hasApiKey)) {
                    scope.launch {
                        loading = true
                        modelMsg = vm.loadModels(key.ifBlank { vm.storedKey() })
                        loading = false
                    }
                }
                if (loading) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
            }
            modelMsg?.let { (ok, t) ->
                Spacer(Modifier.height(4.dp))
                Text(t, style = MaterialTheme.typography.bodySmall, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }

        GroqCard(vm, s)

        // ---------------- Theme ----------------
        SectionCard("থিম ও রং", Icons.Outlined.Palette) {
            Palettes.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { p ->
                        val sel = p.id == s.theme
                        Column(
                            Modifier.width(70.dp).clip(RoundedCornerShape(12.dp)).pressClick { vm.set(SettingsRepo.K.theme, p.id) }.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(p.primary, p.accent)))
                                    .border(if (sel) 2.5.dp else 0.dp, if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White) }
                            Spacer(Modifier.height(2.dp))
                            Text(p.nameBn, style = MaterialTheme.typography.labelMedium, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ButtonRow {
                listOf(0 to "ফোনের মতো", 1 to "লাইট", 2 to "ডার্ক").forEach { (v, label) ->
                    PillButton(label, modifier = Modifier.weight(1f), filled = s.darkMode == v) { vm.set(SettingsRepo.K.darkMode, v) }
                }
            }
        }

        // ---------------- Floating mic ----------------
        SectionCard("ভাসমান মাইক", Icons.Outlined.Layers) {
            SwitchRow("ভাসমান মাইক চালু", null, s.bubbleEnabled, setBubble)
            Spacer(Modifier.height(4.dp))
            Text("বাটন কীভাবে থাকবে", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BubbleStyleOption(
                    title = "ভাসমান বাটন", hint = "পুরো বাটন সবসময় দেখা যাবে",
                    edge = false, selected = s.bubbleStyle != AppSettings.STYLE_EDGE, modifier = Modifier.weight(1f),
                ) { vm.set(SettingsRepo.K.bubbleStyle, AppSettings.STYLE_FLOAT) }
                BubbleStyleOption(
                    title = "কিনারায় লুকানো", hint = "পাশে ছোট্ট মাইক — ট্যাপ করলেই বের হয়ে রেকর্ড শুরু",
                    edge = true, selected = s.bubbleStyle == AppSettings.STYLE_EDGE, modifier = Modifier.weight(1f),
                ) { vm.set(SettingsRepo.K.bubbleStyle, AppSettings.STYLE_EDGE) }
            }
            Spacer(Modifier.height(4.dp))
            var size by remember(s.bubbleSizeDp) { mutableFloatStateOf(s.bubbleSizeDp.toFloat()) }
            var alpha by remember(s.bubbleAlpha) { mutableFloatStateOf(s.bubbleAlpha) }
            Spacer(Modifier.height(4.dp))
            SliderRow("আকার", "${size.toInt()}", size, 44f..80f, steps = 8, onChange = { size = it }) { vm.set(SettingsRepo.K.bubbleSize, size.toInt()) }
            SliderRow("স্বচ্ছতা", "${(alpha * 100).toInt()}%", alpha, 0.3f..1f, steps = 6, onChange = { alpha = it }) { vm.set(SettingsRepo.K.bubbleAlpha, alpha) }
        }

        // ---------------- Text ----------------
        SectionCard("লেখা ও অনুবাদ", Icons.Outlined.Translate) {
            SwitchRow("ইংরেজি অনুবাদ", null, s.translate) { vm.set(SettingsRepo.K.translate, it) }
            SwitchRow("ভাসমান মাইকে অটো-কপি", null, s.autoCopy) { vm.set(SettingsRepo.K.autoCopy, it) }
        }

        // ---------------- Audio ----------------
        SectionCard("অডিও সংরক্ষণ", Icons.Outlined.AudioFile) {
            listOf(0 to "লেখা তৈরি হলেই মুছে ফেলুন", 7 to "৭ দিন রাখুন", 30 to "৩০ দিন রাখুন", -1 to "সবসময় রাখুন").forEach { (v, label) ->
                Row(
                    Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
                        .pressClick { vm.set(SettingsRepo.K.retention, v); vm.cleanupAudio() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.audioRetentionDays == v, onClick = { vm.set(SettingsRepo.K.retention, v); vm.cleanupAudio() })
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---------------- History ----------------
        SectionCard("হিস্টরি", Icons.Outlined.DeleteSweep) {
            var confirm by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("মোট ${count}টি লেখা সংরক্ষিত", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                PillButton("সব মুছুন", Icons.Outlined.DeleteSweep, color = MaterialTheme.colorScheme.error, enabled = count > 0) { confirm = true }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("সব হিস্টরি মুছবেন?") },
                    text = { Text("মোট ${count}টি লেখা ও অডিও স্থায়ীভাবে মুছে যাবে।") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteAll(); confirm = false
                            Toast.makeText(ctx, "সব হিস্টরি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }) { Text("সব মুছুন", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("না") } },
                )
            }
        }

        // ---------------- Privacy ----------------
        SectionCard("গোপনীয়তা", Icons.Outlined.PrivacyTip) {
            var more by remember { mutableStateOf(false) }
            Text(
                "API key ও হিস্টরি শুধু এই ফোনে থাকে। অডিও শুধু Google Gemini-তে যায়।",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (more) "কম দেখুন" else "বিস্তারিত",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressClick { more = !more }.padding(vertical = 6.dp, horizontal = 2.dp),
            )
            if (more) InfoText(
                "• আপনার API key শুধু এই ফোনে Android Keystore দিয়ে এনক্রিপ্ট করে রাখা হয় — কোথাও আপলোড হয় না।\n" +
                    "• রেকর্ড করা অডিও শুধু লেখা তৈরির জন্য সরাসরি Google Gemini-তে পাঠানো হয়; অন্য কোনো সার্ভারে নয়।\n" +
                    "• হিস্টরি ও অডিও শুধু এই ফোনে থাকে; ফোনের ক্লাউড ব্যাকআপেও যায় না।\n" +
                    "• Google-এর ফ্রি API ব্যবহার করলে Google তাদের নিয়ম অনুযায়ী ডেটা দেখতে পারে — খুব গোপন তথ্য বলার আগে ভাবুন।"
            )
        }

        // ---------------- Update ----------------
        UpdateCard(vm.tf)

        // ---------------- About ----------------
        SectionCard("অ্যাপ সম্পর্কে", Icons.Outlined.Info) {
            FullLogo(markSize = 42.dp)
            Spacer(Modifier.height(4.dp))
            Text("সংস্করণ ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** One choice card with a tiny phone-screen preview of how the floating mic looks. */
@Composable
private fun BubbleStyleOption(
    title: String,
    hint: String,
    edge: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.primary.copy(alpha = 0.08f) else c.surfaceContainerHigh.copy(alpha = 0.5f))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant, shape)
            .pressClick(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // mini phone screen
        Box(
            Modifier.width(54.dp).height(76.dp).clip(RoundedCornerShape(9.dp))
                .background(c.surface).border(1.5.dp, c.outline.copy(alpha = 0.6f), RoundedCornerShape(9.dp)),
        ) {
            Column(Modifier.padding(start = 6.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(30, 24, 34, 20).forEach { w ->
                    Box(Modifier.width(w.dp).height(4.dp).clip(RoundedCornerShape(50)).background(c.outlineVariant))
                }
            }
            if (edge) {
                Box(
                    Modifier.align(Alignment.CenterEnd).width(7.dp).height(22.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                        .background(Brush.verticalGradient(brand.gradient)),
                )
            } else {
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(18.dp).clip(CircleShape)
                        .background(Brush.linearGradient(brand.gradient)),
                    contentAlignment = Alignment.Center,
                ) { Icon(androidx.compose.material.icons.Icons.Rounded.Mic, null, Modifier.size(11.dp), tint = Color.White) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = c.primary)
                Spacer(Modifier.width(3.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) c.primary else c.onSurface, maxLines = 1)
        }
        Text(
            hint, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp,
        )
    }
}

private fun modelHint(m: String): String? = when {
    m == "gemini-flash-latest" -> "প্রস্তাবিত — দ্রুত ও নির্ভুল"
    m.contains("flash-lite") -> "সবচেয়ে দ্রুত, তুলনামূলক কম নির্ভুল"
    m.contains("pro") -> "সবচেয়ে নির্ভুল, তবে ধীর"
    else -> null
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) InfoText(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    onChange: (Float) -> Unit, onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(62.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, onValueChangeFinished = onDone, modifier = Modifier.weight(1f).height(34.dp))
        Text(valueText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}

