package com.talkly.designui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkly.designui.theme.ChatPalette
import com.talkly.designui.theme.LocalChatPalette
import com.talkly.designui.theme.Ink

/**
 * Glass looks.
 *  - Frosted (default, works on EVERY Android version): fairly opaque, tinted with the theme colour, with a top
 *    sheen and a gradient edge. Looks good even with no blur at all.
 *  - Clear: very translucent. Use ONLY if you add a real backdrop blur behind it (e.g. Haze on Android 12+),
 *    otherwise text under it shows through unblurred.
 */
enum class GlassMode { Frosted, Clear }

val LocalGlassMode = compositionLocalOf { GlassMode.Frosted }

fun Modifier.glass(shape: Shape, strong: Boolean = false, dark: Boolean = false): Modifier = composed {
    val frosted = LocalGlassMode.current == GlassMode.Frosted
    val tint = LocalChatPalette.current.bgMid
    val light1 = lerp(Color.White, tint, 0.22f)
    val light2 = lerp(Color.White, tint, 0.34f)
    val base = when {
        dark && frosted -> Brush.linearGradient(listOf(Color(0xCC101828), Color(0xB3101828)))
        dark -> Brush.linearGradient(listOf(Color(0x6610182A), Color(0x5510182A)))
        frosted && strong -> Brush.linearGradient(listOf(light1.copy(alpha = 0.96f), light2.copy(alpha = 0.88f)))
        frosted -> Brush.linearGradient(listOf(light1.copy(alpha = 0.90f), light2.copy(alpha = 0.78f)))
        strong -> Brush.linearGradient(listOf(Color(0xE0FFFFFF), Color(0xA6FFFFFF)))
        else -> Brush.linearGradient(listOf(Color(0xA6FFFFFF), Color(0x4DFFFFFF)))
    }
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.55f), Color.Transparent), endY = 150f)
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.42f else 1f), Color.White.copy(alpha = if (dark) 0.12f else 0.35f), Color.White.copy(alpha = if (dark) 0.30f else 0.9f))
    )
    this
        .shadow(if (strong) 14.dp else 8.dp, shape, clip = false, ambientColor = Color(0x330369A1), spotColor = Color(0x660369A1))
        .clip(shape)
        .background(base)
        .background(sheen)
        .border(1.dp, edge, shape)
}

/**
 * Fades the content out before it reaches the floating glass bars (top and bottom), so nothing messy shows
 * through them. This is what makes the glass look clean on phones without backdrop blur.
 * Use on the scrolling list: Modifier.fadeEdges(top = headerHeight, bottom = composerHeight)
 */
fun Modifier.fadeEdges(top: Dp, bottom: Dp, fade: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h > 0f) {
            val t = top.toPx(); val b = bottom.toPx(); val f = fade.toPx()
            val s1 = ((t - f) / h).coerceIn(0f, 1f)
            val s2 = ((t + f / 2f) / h).coerceIn(s1, 1f)
            val s4 = ((h - b + f) / h).coerceIn(s2, 1f)
            val s3 = ((h - b - f / 2f) / h).coerceIn(s2, s4)
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, s1 to Color.Transparent, s2 to Color.Black, s3 to Color.Black, s4 to Color.Transparent, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Spring "press" feedback like the prototype buttons. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Ink.Deep,
    dark: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).glass(CircleShape, dark = dark).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f)) }
}

/** Soft gradient background with floating blobs. */
@Composable
fun ChatBackground(palette: ChatPalette, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val t = rememberInfiniteTransition(label = "blobs")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgMid, palette.bgBottom)))) {
        Box(
            Modifier.offset(x = (-80).dp + 40.dp * a, y = (-60).dp).size(300.dp)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.align(Alignment.CenterEnd).offset(x = 120.dp - 40.dp * a, y = 60.dp).size(340.dp)
                .background(Brush.radialGradient(listOf(palette.blobA.copy(alpha = 0.7f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-60).dp, y = 90.dp - 30.dp * a).size(320.dp)
                .background(Brush.radialGradient(listOf(palette.blobB.copy(alpha = 0.8f), Color.Transparent)), CircleShape)
        )
        content()
    }
}

/** Placeholder "photo" gradients. Replace with your real image loader (see imageContent in README). */
val ArtPairs = listOf(
    Color(0xFF7FCDF6) to Color(0xFF0369A1), Color(0xFFF9C0D2) to Color(0xFFBE185D),
    Color(0xFF99E0DA) to Color(0xFF0F766E), Color(0xFFFDCB9E) to Color(0xFFB93A0C),
    Color(0xFFD1C4F7) to Color(0xFF6D28D9), Color(0xFFB7E8C4) to Color(0xFF166534),
    Color(0xFFFDE68A) to Color(0xFFB45309), Color(0xFFBFDBFE) to Color(0xFF1D4ED8),
)

@Composable
fun ArtBox(index: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val p = ArtPairs[index.mod(ArtPairs.size)]
    Box(modifier.background(Brush.linearGradient(listOf(p.first, p.second))), content = content)
}

@Composable
fun Waveform(seed: Int, progress: Float, filled: Color, dim: Color, modifier: Modifier = Modifier, bars: Int = 20) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = (6 + ((i * 37 + seed * 7) % 17)).dp.toPx()
            drawRoundRect(
                color = if ((i + 0.5f) / bars <= progress) filled else dim,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}
