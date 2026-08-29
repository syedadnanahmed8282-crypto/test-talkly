package com.family.talkly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.family.talkly.data.models.FamilyMember

/**
 * Animated/Steady Online Presence Dot.
 * - If user is actively online (foreground): Steady solid green light.
 * - If user is in background / recently active within 5 mins: Soft, gentle pulsing/blinking green light (1500ms cycle).
 * - After 5 minutes: Completely turns off.
 */
@Composable
fun OnlinePresenceIndicator(
    member: FamilyMember,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    borderColor: Color = Color(0xFF1E1024),
    borderWidth: Dp = 2.dp,
    greenColor: Color = Color(0xFF22C55E)
) {
    if (!member.isRecentlyActive()) return

    val isBackgroundActive = member.isBackgroundRecentlyActive()

    val alpha = if (isBackgroundActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "presence_pulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animatedAlpha
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(greenColor, CircleShape)
            .border(borderWidth, borderColor, CircleShape)
    )
}
