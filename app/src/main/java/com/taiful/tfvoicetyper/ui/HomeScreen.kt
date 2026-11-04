package com.taiful.tfvoicetyper.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.engine.EngineState
import com.taiful.tfvoicetyper.service.VoiceService
import com.taiful.tfvoicetyper.util.Fmt
import com.taiful.tfvoicetyper.util.Clip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: MainViewModel,
    s: AppSettings,
    tick: Int,
    perms: PermRequester,
    setBubble: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val st by vm.engine.collectAsState()
    val latest by vm.latest.collectAsState()

    val onMic: () -> Unit = {
        when {
            st is EngineState.Processing -> Toast.makeText(ctx, "লেখা তৈরি হচ্ছে, একটু অপেক্ষা করুন…", Toast.LENGTH_SHORT).show()
            st is EngineState.Recording -> VoiceService.send(ctx, VoiceService.ACTION_STOP_RECORDING)
            !Perm.mic(ctx) -> perms.requestMic()
            else -> {
                vm.tf.engine.dismiss()
                VoiceService.send(ctx, VoiceService.ACTION_TOGGLE, "app")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val problem by vm.tf.lastProblem.collectAsState()
        problem?.let { p ->
            SectionCard("আগের বার একটি সমস্যা হয়েছিল", Icons.Outlined.ErrorOutline) {
                Text(p.take(300), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5)
                Spacer(Modifier.height(8.dp))
                ButtonRow {
                    PillButton("কপি", Icons.Outlined.ContentCopy, filled = true) { Clip.copy(ctx, p, "সমস্যার বিবরণ") }
                    PillButton("ঠিক আছে", Icons.Outlined.Close) { vm.tf.clearProblem() }
                }
            }
        }
        if (!s.hasApiKey && !s.hasGroqKey && s.engine != com.taiful.tfvoicetyper.data.AppSettings.ENGINE_PHONE) {
            SectionCard("এখন key ছাড়াই চলছে (ফোনের ভয়েস টাইপিং)", Icons.Outlined.Key) {
                Text(
                    "আরও শুদ্ধ লেখা, দাঁড়ি-কমা আর ভালো অনুবাদের জন্য একটি ফ্রি Gemini বা Groq key দিন (২ মিনিটের কাজ)।",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                PillButton("ফ্রি key দিন", Icons.Outlined.Key, filled = true, onClick = onOpenSettings)
            }
        }
        PermissionCard(tick, perms)

        RecorderCard(st, onMic, onCancel = { vm.tf.engine.cancel() })

        // Floating microphone quick switch
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Layers, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text("ভাসমান মাইক", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = s.bubbleEnabled, onCheckedChange = setBubble)
            }
        }

        val err = st as? EngineState.Error
        AnimatedVisibility(err != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val e = err ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(7.dp))
                        Text(e.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    ButtonRow {
                        val id = e.recordId
                        if (id != null) PillButton("আবার চেষ্টা", Icons.Outlined.Refresh, filled = true) { vm.tf.engine.retry(id) }
                        PillButton("ঠিক আছে", Icons.Outlined.Close) { vm.tf.engine.dismiss() }
                    }
                }
            }
        }

        val recent by vm.recent.collectAsState()
        if (recent.isNotEmpty() && st !is EngineState.Recording) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("সাম্প্রতিক লেখা", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "সব দেখুন ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenHistory).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(4.dp)) {
                    recent.forEach { r -> HistoryItem(r, selecting = false, selected = false, onClick = { onOpenRecord(r.id) }) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RecorderCard(st: EngineState, onMic: () -> Unit, onCancel: () -> Unit) {
    val brand = LocalBrand.current
    val c = MaterialTheme.colorScheme
    val recording = st is EngineState.Recording
    val processing = st is EngineState.Processing
    val levels = remember { mutableStateListOf<Float>().apply { repeat(28) { add(0f) } } }
    LaunchedEffect(st) {
        if (st is EngineState.Recording) {
            levels.removeAt(0); levels.add(st.level)
        } else if (!processing) {
            for (i in levels.indices) levels[i] = 0f
        }
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = when (st) {
                        is EngineState.Recording -> "শুনছি…"
                        is EngineState.Processing -> if (st.parts > 1) "লেখা তৈরি হচ্ছে… ${st.part}/${st.parts}" else "লেখা তৈরি হচ্ছে…"
                        else -> "ট্যাপ করে বলুন"
                    },
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                    label = "title",
                ) { t -> Text(t, style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(12.dp))

                BigMic(st, onMic)

                Spacer(Modifier.height(10.dp))
                when (st) {
                    is EngineState.Recording -> {
                        Text(Fmt.duration(st.elapsedMs), style = MaterialTheme.typography.headlineSmall, color = Color(0xFFDC2626))
                        Spacer(Modifier.height(6.dp))
                        LevelBars(levels)
                        Spacer(Modifier.height(10.dp))
                        ButtonRow {
                            PillButton("থামান", Icons.Rounded.Stop, Modifier.weight(1.4f), filled = true, onClick = onMic)
                            PillButton("বাতিল", Icons.Outlined.Close, Modifier.weight(0.8f), color = c.error, onClick = onCancel)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun BigMic(st: EngineState, onClick: () -> Unit) {
    val brand = LocalBrand.current
    val recording = st is EngineState.Recording
    val processing = st is EngineState.Processing
    val level = (st as? EngineState.Recording)?.level ?: 0f
    val inf = rememberInfiniteTransition(label = "mic")
    val pulse by inf.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "p")
    val pulseA by inf.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pa")
    val lv by animateFloatAsState(1f + level * 0.22f, tween(90), label = "lv")
    val size by animateDpAsState(if (recording) 112.dp else 104.dp, tween(220), label = "sz")
    val brush = if (recording) Brush.linearGradient(listOf(Color(0xFFF05252), Color(0xFFB91C1C)))
    else Brush.linearGradient(brand.gradient)
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(Modifier.size(size).scale(pulse).alpha(pulseA).clip(CircleShape).background(Color(0xFFEF4444)))
            Box(Modifier.size(size).scale(lv).clip(CircleShape).background(Color(0x40EF4444)))
        }
        Box(
            Modifier.size(size).shadow(10.dp, CircleShape).clip(CircleShape).background(brush).pressClick(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                processing -> CircularProgressIndicator(Modifier.size(46.dp), color = Color.White, strokeWidth = 3.5.dp)
                recording -> Icon(Icons.Rounded.Stop, "থামান", Modifier.size(48.dp), tint = Color.White)
                else -> Icon(Icons.Rounded.Mic, "রেকর্ড শুরু", Modifier.size(50.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun LevelBars(levels: List<Float>) {
    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        levels.forEach { l ->
            val h by animateDpAsState((4 + l * 26).dp, tween(90), label = "b")
            Box(Modifier.width(4.dp).height(h).clip(RoundedCornerShape(50)).background(Color(0xFFEF4444).copy(alpha = 0.35f + l * 0.65f)))
        }
    }
}

