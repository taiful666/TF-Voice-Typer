package com.taiful.tfvoicetyper.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.taiful.tfvoicetyper.MainActivity
import com.taiful.tfvoicetyper.TFApp
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.engine.EngineState
import com.taiful.tfvoicetyper.engine.Source
import com.taiful.tfvoicetyper.ui.LocalBrand
import com.taiful.tfvoicetyper.ui.LogoMark
import com.taiful.tfvoicetyper.ui.PillButton
import com.taiful.tfvoicetyper.ui.pressClick
import com.taiful.tfvoicetyper.ui.TFTheme
import com.taiful.tfvoicetyper.ui.TranscriptStyle
import com.taiful.tfvoicetyper.util.Clip
import com.taiful.tfvoicetyper.util.Fmt
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlin.math.hypot
import kotlin.math.max

/** Draws the floating mic, the "drop here to remove" target, and the small result panel. */
class OverlayController(
    private val service: VoiceService,
    private val app: TFApp,
    private val onRemoved: () -> Unit,
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Number): Int = (v.toFloat() * density + 0.5f).toInt()

    private var engineState by mutableStateOf<EngineState>(EngineState.Idle)
    private var settings by mutableStateOf(AppSettings())
    private var pressed by mutableStateOf(false)
    private var overTarget by mutableStateOf(false)
    private var targetShown by mutableStateOf(false)

    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var target: View? = null
    private var panel: View? = null
    private var snapAnim: ValueAnimator? = null

    private val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun windowSizePx() = dp(settings.bubbleSizeDp + 18)

    private fun screen(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = service.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun composeHost(content: @Composable () -> Unit): FrameLayout {
        val frame = FrameLayout(service)
        frame.id = View.generateViewId()
        frame.setViewTreeLifecycleOwner(service)
        frame.setViewTreeSavedStateRegistryOwner(service)
        frame.addView(ComposeView(service).apply {
            id = View.generateViewId()
            setContent { TFTheme(settings.theme, settings.darkMode) { content() } }
        })
        return frame
    }

    // ------------------------------------------------------------------ bubble

    @SuppressLint("ClickableViewAccessibility")
    fun show(s: AppSettings, pos: Pair<Int, Int>?) {
        if (bubble != null) return
        settings = s
        val size = windowSizePx()
        val (sw, sh) = screen()
        val params = WindowManager.LayoutParams(
            size, size, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (pos?.first ?: (sw - size)).coerceIn(0, max(0, sw - size))
            y = (pos?.second ?: (sh * 0.38f).toInt()).coerceIn(dp(24), max(dp(24), sh - size - dp(24)))
        }
        val host = object : FrameLayout(service) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                handleTouch(this, ev); return true
            }
        }
        host.id = View.generateViewId()
        host.setViewTreeLifecycleOwner(service)
        host.setViewTreeSavedStateRegistryOwner(service)
        host.addView(ComposeView(service).apply {
            id = View.generateViewId()
            setContent { TFTheme(settings.theme, settings.darkMode) { BubbleUi() } }
        })
        createTarget() // added first, so the bubble always stays on top of it
        bubble = host
        bubbleParams = params
        try {
            wm.addView(host, params)
        } catch (e: Exception) {
            bubble = null
            Toast.makeText(service, "ভাসমান মাইক দেখানো যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun onSettings(s: AppSettings) {
        val old = settings
        settings = s
        if (old.bubbleSizeDp != s.bubbleSizeDp) {
            val p = bubbleParams ?: return
            p.width = windowSizePx(); p.height = windowSizePx()
            update()
        }
    }

    fun onEngineState(st: EngineState) {
        engineState = st
        val wantPanel = (st is EngineState.Done && st.source == Source.BUBBLE) ||
            (st is EngineState.Error && st.source == Source.BUBBLE)
        if (wantPanel && bubble != null) showPanel() else hidePanel()
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private val slop = ViewConfiguration.get(service).scaledTouchSlop

    private fun handleTouch(v: View, ev: MotionEvent) {
        val p = bubbleParams ?: return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnim?.cancel()
                downX = ev.rawX; downY = ev.rawY
                startX = p.x; startY = p.y
                dragging = false
                pressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    pressed = false
                    showTarget()
                }
                if (dragging) {
                    p.x = (startX + dx).toInt()
                    p.y = (startY + dy).toInt()
                    update()
                    val over = isOverTarget()
                    if (over != overTarget) {
                        overTarget = over
                        if (over) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed = false
                if (dragging) {
                    val remove = isOverTarget()
                    hideTarget()
                    overTarget = false
                    dragging = false
                    if (remove) {
                        onRemoved()
                    } else {
                        snapToEdge()
                    }
                } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap()
                }
            }
        }
    }

    private fun onTap() {
        when (val st = engineState) {
            is EngineState.Recording -> app.engine.stop()
            is EngineState.Processing -> Toast.makeText(service, "লেখা তৈরি হচ্ছে, একটু অপেক্ষা করুন…", Toast.LENGTH_SHORT).show()
            else -> {
                if (!settings.hasApiKey) {
                    Toast.makeText(service, "আগে অ্যাপের Settings-এ Gemini API key দিন", Toast.LENGTH_LONG).show()
                    openApp(null)
                    return
                }
                if (st is EngineState.Done || st is EngineState.Error) app.engine.dismiss()
                app.engine.start(Source.BUBBLE)
            }
        }
    }

    private fun isOverTarget(): Boolean {
        if (!targetShown) return false
        val t = target ?: return false
        val p = bubbleParams ?: return false
        val loc = IntArray(2)
        t.getLocationOnScreen(loc)
        val tx = loc[0] + t.width / 2f
        val ty = loc[1] + t.height - dp(64)
        val bx = p.x + p.width / 2f
        val by = p.y + p.height / 2f
        return hypot(bx - tx, by - ty) < dp(72)
    }

    private fun snapToEdge() {
        val p = bubbleParams ?: return
        val (sw, sh) = screen()
        val toX = if (p.x + p.width / 2 < sw / 2) 0 else sw - p.width
        p.y = p.y.coerceIn(dp(24), max(dp(24), sh - p.height - dp(24)))
        snapAnim = ValueAnimator.ofInt(p.x, toX).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { p.x = it.animatedValue as Int; update() }
            start()
        }
        service.lifecycleScope.launch { app.settings.setBubblePos(toX, p.y) }
    }

    private fun update() {
        val b = bubble ?: return
        val p = bubbleParams ?: return
        runCatching { wm.updateViewLayout(b, p) }
    }

    @Composable
    private fun BubbleUi() {
        val st = engineState
        val brand = LocalBrand.current
        val sizeDp = settings.bubbleSizeDp.dp
        val recording = st is EngineState.Recording
        val busy = recording || st is EngineState.Processing
        val pressScale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(120), label = "p")
        val inf = rememberInfiniteTransition(label = "pulse")
        val pulse by inf.animateFloat(1f, 1.32f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "pr")
        val pulseAlpha by inf.animateFloat(0.55f, 0f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "pa")
        val level = (st as? EngineState.Recording)?.level ?: 0f
        val levelScale by animateFloatAsState(1f + level * 0.28f, tween(90), label = "lv")
        val brush = when {
            recording -> Brush.linearGradient(listOf(Color(0xFFF05252), Color(0xFFB91C1C)))
            else -> Brush.linearGradient(brand.gradient)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (recording) {
                Box(Modifier.size(sizeDp).scale(pulse).alpha(pulseAlpha).clip(CircleShape).background(Color(0xFFEF4444)))
                Box(Modifier.size(sizeDp).scale(levelScale).clip(CircleShape).background(Color(0x55EF4444)))
            }
            Box(
                Modifier
                    .size(sizeDp)
                    .scale(pressScale)
                    .alpha(if (busy) 1f else settings.bubbleAlpha)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(brush)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val icon = (sizeDp.value * 0.46f).dp
                when (st) {
                    is EngineState.Recording -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Stop, "থামান", Modifier.size(icon * 0.8f), tint = Color.White)
                        Text(Fmt.duration(st.elapsedMs), color = Color.White, fontSize = (sizeDp.value * 0.17f).sp, lineHeight = (sizeDp.value * 0.19f).sp, fontWeight = FontWeight.Bold)
                    }
                    is EngineState.Processing -> CircularProgressIndicator(Modifier.size(icon), color = Color.White, strokeWidth = 2.5.dp)
                    is EngineState.Done -> Icon(Icons.Rounded.Check, null, Modifier.size(icon), tint = Color.White)
                    else -> Icon(Icons.Rounded.Mic, "রেকর্ড", Modifier.size(icon), tint = Color.White)
                }
            }
        }
    }

    // ------------------------------------------------------------------ remove target

    private fun createTarget() {
        if (target != null) return
        val v = composeHost { TargetUi() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(170), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        runCatching { wm.addView(v, params); target = v }
    }

    private fun showTarget() {
        targetShown = true
    }

    private fun hideTarget() {
        targetShown = false
    }

    @Composable
    private fun TargetUi() {
        val big by animateFloatAsState(if (overTarget) 1.25f else 1f, tween(150), label = "t")
        val vis by animateFloatAsState(if (targetShown) 1f else 0f, tween(160), label = "tv")
        Box(
            Modifier.fillMaxSize().alpha(vis).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 30.dp)) {
                Box(
                    Modifier.size(64.dp).scale(big).clip(CircleShape)
                        .background(if (overTarget) Color(0xFFDC2626) else Color(0xCC1F2937))
                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Delete, null, Modifier.size(28.dp), tint = Color.White) }
                Spacer(Modifier.size(6.dp))
                Text("সরাতে এখানে ছেড়ে দিন", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ------------------------------------------------------------------ result panel

    private fun showPanel() {
        if (panel != null) return
        val v = composeHost { PanelUi() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(56)
        }
        runCatching { wm.addView(v, params); panel = v }
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun openApp(recordId: Long?) {
        val i = Intent(service, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (recordId != null) i.putExtra(MainActivity.EXTRA_RECORD, recordId)
        runCatching { service.startActivity(i) }
    }

    @Composable
    private fun PanelUi() {
        val st = engineState
        val ctx = service
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(20.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (st is EngineState.Error) "লেখা তৈরি হয়নি" else "আপনার লেখা তৈরি ✓",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (st is EngineState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { app.engine.dismiss() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Close, "বন্ধ", Modifier.size(19.dp))
                    }
                }
                Column(Modifier.padding(end = 6.dp)) {
                    when (st) {
                        is EngineState.Done -> {
                            val r = st.record
                            PanelText("বাংলা সংস্করণ", r.bangla, MaterialTheme.colorScheme.primary, 128) { Clip.copy(ctx, r.bangla, "বাংলা লেখা") }
                            if (r.english.isNotBlank()) {
                                Spacer(Modifier.size(6.dp))
                                PanelText("English Version", r.english, MaterialTheme.colorScheme.secondary, 92) { Clip.copy(ctx, r.english, "English text") }
                            }
                            Spacer(Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                PillButton("বাংলা", Icons.Outlined.ContentCopy, Modifier.weight(1f), filled = true) { Clip.copy(ctx, r.bangla, "বাংলা লেখা") }
                                PillButton("English", Icons.Outlined.ContentCopy, Modifier.weight(1f), filled = true, color = MaterialTheme.colorScheme.secondary, enabled = r.english.isNotBlank()) {
                                    Clip.copy(ctx, r.english, "English text")
                                }
                                PillButton("দুটোই", Icons.Outlined.ContentCopy, Modifier.weight(1f)) { Clip.copy(ctx, Clip.both(r.bangla, r.english), "দুটো লেখা") }
                            }
                            Spacer(Modifier.size(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (settings.autoCopy) "বাংলা লেখা কপি হয়ে গেছে — যেখানে চান পেস্ট করুন" else "কার্ডে ট্যাপ করলেই কপি হবে",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                PillButton("এডিট", Icons.Outlined.OpenInNew) { openApp(r.id); app.engine.dismiss() }
                            }
                        }
                        is EngineState.Error -> {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val id = st.recordId
                                if (id != null) PillButton("আবার চেষ্টা", Icons.Outlined.Refresh, filled = true) { app.engine.retry(id, Source.BUBBLE) }
                                PillButton("অ্যাপ খুলুন", Icons.Outlined.OpenInNew) { openApp(id); app.engine.dismiss() }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelText(title: String, text: String, accent: Color, maxH: Int, onCopy: () -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.07f))
                .pressClick(onClick = onCopy).padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
            Box(Modifier.heightIn(max = maxH.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = TranscriptStyle.copy(fontSize = 14.5.sp, lineHeight = 19.5.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    fun destroy() {
        snapAnim?.cancel()
        hidePanel()
        target?.let { runCatching { wm.removeView(it) } }
        target = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }
}
