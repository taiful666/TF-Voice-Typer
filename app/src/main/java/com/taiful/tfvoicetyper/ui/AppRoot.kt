package com.taiful.tfvoicetyper.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import com.taiful.tfvoicetyper.data.SettingsRepo
import com.taiful.tfvoicetyper.service.VoiceService

@Composable
fun AppRoot(vm: MainViewModel, resumeTick: Int, openRecord: Long?, onOpenRecord: (Long?) -> Unit) {
    val s = vm.settings.collectAsState().value
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var permTick by rememberSaveable { mutableIntStateOf(0) }
    val tick = resumeTick + permTick
    val perms = rememberPermRequester { permTick++ }

    // Bring the floating mic back whenever the app is opened (Android allows starting it now).
    LaunchedEffect(resumeTick, s.bubbleEnabled) {
        if (s.bubbleEnabled && vm.tf.lastProblem.value == null && Perm.mic(ctx) && Perm.overlay(ctx)) VoiceService.send(ctx, VoiceService.ACTION_BUBBLE_ON)
    }

    var pendingBubble by rememberSaveable { mutableStateOf(false) }
    // Came back from the "display over other apps" screen with permission given → switch the mic on.
    LaunchedEffect(resumeTick) {
        if (pendingBubble && Perm.mic(ctx) && Perm.overlay(ctx)) {
            pendingBubble = false
            vm.set(SettingsRepo.K.bubbleEnabled, true)
            VoiceService.send(ctx, VoiceService.ACTION_BUBBLE_ON)
        }
    }

    val setBubble: (Boolean) -> Unit = { on ->
        if (on) {
            when {
                !Perm.mic(ctx) -> perms.requestMic()
                !Perm.overlay(ctx) -> { pendingBubble = true; Perm.openOverlaySettings(ctx) }
                else -> {
                    vm.set(SettingsRepo.K.bubbleEnabled, true)
                    VoiceService.send(ctx, VoiceService.ACTION_BUBBLE_ON)
                    Toast.makeText(ctx, "ভাসমান মাইক চালু হলো। এখন যেকোনো অ্যাপ খুলে মাইকে ট্যাপ করুন।", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            pendingBubble = false
            vm.set(SettingsRepo.K.bubbleEnabled, false)
            if (Perm.mic(ctx)) VoiceService.send(ctx, VoiceService.ACTION_BUBBLE_OFF)
        }
    }

    TFTheme(s.theme, s.darkMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // Top bar with the full logo
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { FullLogo(Modifier.weight(1f)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f).imePadding()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "tab",
                        ) { t ->
                            when (t) {
                                0 -> HomeScreen(vm, s, tick, perms, setBubble, onOpenSettings = { tab = 2 }, onOpenRecord = { onOpenRecord(it) }, onOpenHistory = { tab = 1 })
                                1 -> HistoryScreen(vm, onOpen = { onOpenRecord(it) })
                                else -> SettingsScreen(vm, s, setBubble)
                            }
                        }
                    }
                    BottomBar(tab) { tab = it }
                }
                AnimatedVisibility(
                    visible = openRecord != null,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    var lastId by rememberSaveable { mutableIntStateOf(0) }
                    val id = openRecord ?: lastId.toLong()
                    LaunchedEffect(openRecord) { if (openRecord != null) lastId = openRecord.toInt() }
                    DetailScreen(vm, id, onBack = { onOpenRecord(null) })
                }
                BackHandler(enabled = openRecord != null) { onOpenRecord(null) }
                BackHandler(enabled = openRecord == null && tab != 0) { tab = 0 }
                // In-app update: checks on every app open, downloads inside the app (no browser).
                UpdateHost(vm.tf, resumeTick)
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem("হোম", Icons.Outlined.Mic, Icons.Rounded.Mic, tab == 0) { onTab(0) }
            NavItem("হিস্টরি", Icons.Outlined.History, Icons.Rounded.History, tab == 1) { onTab(1) }
            NavItem("সেটিংস", Icons.Outlined.Settings, Icons.Rounded.Settings, tab == 2) { onTab(2) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String, icon: ImageVector, iconSel: ImageVector, selected: Boolean, onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (selected) 1.08f else 1f, tween(160), label = "nav")
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).pressClick(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) c.primary.copy(alpha = 0.14f) else c.surface)
                .padding(horizontal = 16.dp, vertical = 3.dp),
        ) {
            Icon(if (selected) iconSel else icon, label, Modifier.size(22.dp).scale(sc), tint = if (selected) c.primary else c.onSurfaceVariant)
        }
        Spacer(Modifier.height(1.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) c.primary else c.onSurfaceVariant)
    }
}
