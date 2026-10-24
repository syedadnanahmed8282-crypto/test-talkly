package com.example.app.ui.glasschat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Light sky blue + white palette. */
object GlassColors {
    val SkyLight = Color(0xFF6BC7FA)
    val Sky = Color(0xFF33A0EE)
    val SkyDeep = Color(0xFF2E9BEA)
    val SkyTint = Color(0xFFE6F4FF)
    val Ink = Color(0xFF1C2B3A)
    val Muted = Color(0xFF6B85A0)
    val Danger = Color(0xFFE5484D)
}

val SkyBrush: Brush = Brush.verticalGradient(listOf(GlassColors.SkyLight, GlassColors.Sky))

/**
 * Liquid-glass surface.
 *  - Android 12+ (API 31+): real background blur through Haze.
 *  - Android 8-11: Haze automatically falls back to [fallbackTint] (a translucent white scrim),
 *    so the same translucent look + white border + soft shadow is kept without real blur.
 */
fun Modifier.glass(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Float = 0.28f,
): Modifier = this
    .shadow(
        elevation = 10.dp,
        shape = shape,
        clip = false,
        ambientColor = GlassColors.Sky.copy(alpha = 0.25f),
        spotColor = GlassColors.Sky.copy(alpha = 0.35f),
    )
    .clip(shape)
    .hazeEffect(state = hazeState) {
        blurRadius = 22.dp
        tints = listOf(HazeTint(Color.White.copy(alpha = tint)))
        fallbackTint = HazeTint(Color.White.copy(alpha = 0.55f))
        noiseFactor = 0f
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.75f),
            )
        ),
        shape = shape,
    )

@Composable
fun GlassCircleButton(
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .glass(hazeState, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
