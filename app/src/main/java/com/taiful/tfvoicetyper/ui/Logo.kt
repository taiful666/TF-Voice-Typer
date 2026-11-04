package com.taiful.tfvoicetyper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The TF Voice Typer mark: microphone made of text lines, with sound waves, on the theme gradient. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, colors: List<Color> = LocalBrand.current.gradient) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            brush = Brush.linearGradient(colors, Offset.Zero, Offset(s, s)),
            cornerRadius = CornerRadius(s * 0.28f),
        )
        drawRoundRect(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), Offset(s * 0.3f, s * 0.22f), s * 0.7f),
            cornerRadius = CornerRadius(s * 0.28f),
        )
        drawMic(s, Color.White, colors.first())
    }
}

/** Draws the microphone symbol inside a square of side [s] (coordinates from the 512px icon design). */
fun DrawScope.drawMic(s: Float, ink: Color, lineInk: Color) {
    val k = s / 512f * 1.25f
    val ox = s / 2f - 256f * k
    val oy = s / 2f - 232f * k
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    val w = 7f * k
    // waves
    fun arc(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, a: Float) {
        val path = Path().apply { moveTo(p(x1, y1).x, p(x1, y1).y); quadraticBezierTo(p(cx, cy).x, p(cx, cy).y, p(x2, y2).x, p(x2, y2).y) }
        drawPath(path, ink.copy(alpha = a), style = Stroke(w * 1.15f, cap = StrokeCap.Round))
    }
    arc(196f, 196f, 182f, 216f, 196f, 236f, 0.95f)
    arc(176f, 182f, 154f, 216f, 176f, 250f, 0.55f)
    arc(316f, 196f, 330f, 216f, 316f, 236f, 0.95f)
    arc(336f, 182f, 358f, 216f, 336f, 250f, 0.55f)
    // holder
    val holder = Path().apply {
        moveTo(p(220f, 222f).x, p(220f, 222f).y)
        quadraticBezierTo(p(220f, 268f).x, p(220f, 268f).y, p(256f, 268f).x, p(256f, 268f).y)
        quadraticBezierTo(p(292f, 268f).x, p(292f, 268f).y, p(292f, 222f).x, p(292f, 222f).y)
    }
    drawPath(holder, ink, style = Stroke(w * 1.15f, cap = StrokeCap.Round))
    drawLine(ink, p(256f, 268f), p(256f, 288f), w * 1.15f, StrokeCap.Round)
    drawLine(ink, p(238f, 290f), p(274f, 290f), w * 1.15f, StrokeCap.Round)
    // capsule
    drawRoundRect(ink, p(232f, 168f), Size(48f * k, 84f * k), CornerRadius(24f * k))
    val lw = 5f * k
    drawLine(lineInk, p(245f, 192f), p(267f, 192f), lw, StrokeCap.Round)
    drawLine(lineInk, p(245f, 206f), p(267f, 206f), lw, StrokeCap.Round)
    drawLine(lineInk, p(245f, 220f), p(260f, 220f), lw, StrokeCap.Round)
}

/** Full logo: mark + "টিএফ ভয়েস টাইপার / TF Voice Typer" (TF written small). */
@Composable
fun FullLogo(modifier: Modifier = Modifier, markSize: Dp = 34.dp) {
    val brand = LocalBrand.current
    val onBg = MaterialTheme.colorScheme.onBackground
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LogoMark(markSize)
        Spacer(Modifier.width(9.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = brand.main, letterSpacing = 0.5.sp)) { append("টিএফ ") }
                    withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = onBg)) { append("ভয়েস টাইপার") }
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Light, color = onBg.copy(alpha = 0.4f))) { append("  /  ") }
                    withStyle(SpanStyle(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = brand.main, letterSpacing = 0.8.sp)) { append("TF ") }
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onBg, letterSpacing = 0.2.sp)) { append("Voice Typer") }
                },
                lineHeight = 20.sp,
                maxLines = 2,
            )
        }
    }
}
