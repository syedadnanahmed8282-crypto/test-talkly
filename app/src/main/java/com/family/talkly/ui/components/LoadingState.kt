package com.family.talkly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal

// ==========================================
// TALKLY PREMIUM COLOR PALETTE
// ==========================================
private val BackgroundDark = Color(0xFF080B10)
private val SurfaceMain = Color(0xFF11161D)
private val SurfaceCard = Color(0xFF18212B)
private val SurfaceElevated = Color(0xFF202B36)
private val ElectricCyan = Color(0xFF22D3EE)
private val DeepAqua = Color(0xFF0EA5A4)
private val MintAccent = Color(0xFF5EEAD4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0BA)
private val TextMuted = Color(0xFF64748B)

/**
 * Centralized LoadingState component for displaying progress indicators
 * while fetching messages, authenticating users, or performing background tasks.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    title: String = "Loading...",
    message: String? = null,
    icon: ImageVector? = Icons.Default.Chat,
    progressColor: Color = WhatsappGreen,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    isCardStyle: Boolean = false
) {
    if (isCardStyle) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicatorCore(
                    title = title,
                    message = message,
                    icon = icon,
                    progressColor = progressColor,
                    textColor = Color(0xFF111B21)
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                LoadingIndicatorCore(
                    title = title,
                    message = message,
                    icon = icon,
                    progressColor = progressColor,
                    textColor = if (backgroundColor == WhatsappTeal) Color.White else Color(0xFF111B21)
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicatorCore(
    title: String,
    message: String?,
    icon: ImageVector?,
    progressColor: Color,
    textColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp)
    ) {
        CircularProgressIndicator(
            color = progressColor,
            strokeWidth = 4.dp,
            modifier = Modifier.fillMaxSize()
        )
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = title,
        color = textColor,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    if (!message.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = textColor.copy(alpha = 0.75f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Specialized LoadingState for Authentication processes.
 * Redesigned with the Talkly Graphite + Electric Cyan/Aqua visual identity.
 */
@Composable
fun AuthLoadingState(
    message: String = "Signing you in",
    subMessage: String? = "Securing your connection..."
) {
    val displayTitle = when {
        message.contains("Signing in", ignoreCase = true) ||
                message.contains("Authenticating", ignoreCase = true) ||
                message.isBlank() -> "Signing you in"
        else -> message
    }

    val displaySubtitle = when {
        subMessage == null ||
                subMessage.contains("Please wait a moment", ignoreCase = true) ||
                subMessage.contains("Connecting securely", ignoreCase = true) ||
                subMessage.isBlank() -> "Securing your connection..."
        else -> subMessage
    }

    val infiniteTransition = rememberInfiniteTransition(label = "authLoadingTransition")

    // Continuous smooth rotation for the outer progress ring
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // Breathing pulse scale for the logo badge
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Pulsing outer ambient glow
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Animated dots alpha values
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3Alpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .drawBehind {
                    // Ambient radial glow behind the center authentication badge
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = 0.12f * glowAlpha),
                                DeepAqua.copy(alpha = 0.04f * glowAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.85f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // ==========================================
                // 1 & 2. TALKLY LOGO + AUTHENTICATION ANIMATION
                // ==========================================
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(116.dp)
                ) {
                    // Rotating Thin Progress / Sweep Ring
                    Box(
                        modifier = Modifier
                            .size(114.dp)
                            .rotate(ringRotation)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        ElectricCyan,
                                        MintAccent,
                                        DeepAqua,
                                        Color.Transparent,
                                        ElectricCyan
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Secondary faint counter-rotating orbit accent
                    Box(
                        modifier = Modifier
                            .size(98.dp)
                            .rotate(-ringRotation * 0.7f)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = ElectricCyan.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )

                    // Central Talkly Logo Badge with breathing scale & shadow
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(pulseScale)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = ElectricCyan.copy(alpha = glowAlpha),
                                ambientColor = DeepAqua.copy(alpha = glowAlpha)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricCyan, DeepAqua)
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                color = MintAccent.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Talkly Logo",
                            tint = Color(0xFF040E14),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ==========================================
                // 3. MAIN TITLE
                // ==========================================
                Text(
                    text = displayTitle,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 4. SUBTITLE
                // ==========================================
                Text(
                    text = displaySubtitle,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ==========================================
                // 5. PROGRESS DETAIL / ANIMATED DOTS
                // ==========================================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .border(1.dp, Color(0xFF24303E), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Connecting",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = dot1Alpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MintAccent.copy(alpha = dot2Alpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(DeepAqua.copy(alpha = dot3Alpha))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Specialized LoadingState for fetching or loading Chat Messages.
 */
@Composable
fun MessageLoadingState(
    message: String = "Fetching messages...",
    subMessage: String? = "Syncing end-to-end encrypted family conversation"
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = WhatsappGreen,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = WhatsappTeal
                )
                if (subMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subMessage,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * LoadingOverlay wrapper component that presents content and overlays
 * a progress indicator screen when [isLoading] is true.
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Please wait...",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(
                    title = message,
                    message = null,
                    icon = Icons.Default.FamilyRestroom,
                    progressColor = WhatsappGreen,
                    isCardStyle = true
                )
            }
        }
    }
}
