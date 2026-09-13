package com.family.talkly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.family.talkly.data.models.MessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

// Talkly Palette Constants for Particle Generation
private val TalklyCyan = Color(0xFF22D3EE)
private val TalklyAqua = Color(0xFF0EA5A4)
private val TalklyMint = Color(0xFF5EEAD4)
private val TalklyCard = Color(0xFF18212B)
private val TalklyElevated = Color(0xFF202B36)
private val TalklyTextPrimary = Color(0xFFF8FAFC)
private val TalklyTextSecondary = Color(0xFFA7B0BA)
private val TalklySkyBlue = Color(0xFF38BDF8)
private val TalklyMidBlue = Color(0xFF1E293B)
private val TalklyDarkBlue = Color(0xFF0F172A)

/**
 * Represents an individual pixel fragment of a message surface during disintegration.
 */
data class DisintegrateParticle(
    val initialX: Float,
    val initialY: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val detachProgress: Float, // Progress (0.05..0.65) when this particle detaches
    val velocityX: Float,      // Outward drift velocity
    val liftY: Float,          // Initial upward flutter before gravity takes over
    val gravityY: Float,       // Downward gravity drift
    val maxRotation: Float     // Subtle rotation in degrees (-35..+35)
)

/**
 * Manages active particle dissolve animations across chat message IDs.
 */
@Stable
class ParticleDissolveManager(private val coroutineScope: CoroutineScope) {
    private val animatables = mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val fullyDissolvedIds = mutableStateMapOf<String, Boolean>()

    fun isDissolving(messageId: String): Boolean {
        return animatables.containsKey(messageId) || fullyDissolvedIds.containsKey(messageId)
    }

    fun getProgress(messageId: String): Float? {
        val anim = animatables[messageId]
        if (anim != null) return anim.value
        if (fullyDissolvedIds.containsKey(messageId)) return 1.0f
        return null
    }

    fun startDissolve(messageIds: Set<String>, onComplete: () -> Unit) {
        if (messageIds.isEmpty()) {
            onComplete()
            return
        }

        coroutineScope.launch {
            try {
                // Initialize animatable for each target message
                for (id in messageIds) {
                    animatables[id] = Animatable(0f)
                }

                // Run animations in parallel over 800ms
                val jobs = messageIds.map { id ->
                    async {
                        val anim = animatables[id] ?: return@async
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 800,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
                jobs.awaitAll()

                // Mark fully dissolved so items stay invisible while waiting for repo deletion
                for (id in messageIds) {
                    animatables.remove(id)
                    fullyDissolvedIds[id] = true
                }

                onComplete()

                // Clean up fullyDissolvedIds after 600ms so repo state update has arrived
                delay(600)
                for (id in messageIds) {
                    fullyDissolvedIds.remove(id)
                }
            } catch (e: CancellationException) {
                // In case of cancellation (e.g., navigating away), ensure deletion executes
                onComplete()
            }
        }
    }

    fun clearDissolved(ids: Set<String>) {
        for (id in ids) {
            fullyDissolvedIds.remove(id)
            animatables.remove(id)
        }
    }
}

@Composable
fun rememberParticleDissolveManager(): ParticleDissolveManager {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { ParticleDissolveManager(coroutineScope) }
}

/**
 * Generates an adaptive set of rectangular/square pixel fragments that visually mirror
 * the message or media surface.
 */
fun generateDisintegrateParticles(
    width: Float,
    height: Float,
    isSelf: Boolean,
    messageType: MessageType,
    hasMedia: Boolean,
    density: Density
): List<DisintegrateParticle> {
    if (width <= 0f || height <= 0f) return emptyList()

    val widthDp = with(density) { width.toDp().value }
    val heightDp = with(density) { height.toDp().value }
    val baseArea = widthDp * heightDp

    // Adaptive particle count based on visible message dimensions (36 to 110 fragments)
    val count = (baseArea / 160f).toInt().coerceIn(36, 110)

    val palette = when {
        hasMedia -> listOf(
            TalklySkyBlue, TalklyCyan, TalklyAqua,
            TalklyMidBlue, TalklyDarkBlue,
            Color(0xFF334155), Color(0xFF475569), Color(0xFF64748B),
            Color(0xFF94A3B8), TalklyTextPrimary
        )
        messageType == MessageType.VOICE_NOTE -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, TalklyElevated, TalklyTextPrimary
        )
        isSelf -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, Color(0xFF06B6D4),
            TalklyTextPrimary, Color(0xFFE2E8F0)
        )
        else -> listOf(
            TalklyCard, TalklyElevated, TalklyMidBlue,
            Color(0xFF253342), TalklyTextPrimary,
            TalklyTextSecondary, TalklyCyan.copy(alpha = 0.7f)
        )
    }

    val rnd = Random((width.toLong() xor (height.toLong() shl 16)) + (if (isSelf) 17 else 31))
    val particles = ArrayList<DisintegrateParticle>(count)

    val cols = sqrt(count * (width / height.coerceAtLeast(1f))).toInt().coerceIn(4, 20)
    val rows = (count / cols).coerceAtLeast(2)

    val minSizePx = with(density) { 3.5.dp.toPx() }
    val maxSizePx = with(density) { 7.5.dp.toPx() }

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (particles.size >= count) break

            // Spatial jitter across the grid
            val jitterX = (c + 0.5f + (rnd.nextFloat() - 0.5f) * 0.7f) / cols * width
            val jitterY = (r + 0.5f + (rnd.nextFloat() - 0.5f) * 0.7f) / rows * height

            val pWidth = minSizePx + rnd.nextFloat() * (maxSizePx - minSizePx)
            // Some square, some slightly rectangular
            val pHeight = if (rnd.nextBoolean()) pWidth else pWidth * (0.8f + rnd.nextFloat() * 0.4f)

            // Sweep position determines detachment phase
            val sweepPos = if (isSelf) {
                (1f - jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                (jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }

            val detach = (0.06f + sweepPos * 0.50f + (rnd.nextFloat() - 0.5f) * 0.08f).coerceIn(0.04f, 0.65f)

            // Outward velocity + subtle flutter
            val velX = if (isSelf) {
                with(density) { (6f + rnd.nextFloat() * 22f).dp.toPx() }
            } else {
                with(density) { (-6f - rnd.nextFloat() * 22f).dp.toPx() }
            }
            val liftY = with(density) { (4f + rnd.nextFloat() * 10f).dp.toPx() }
            val gravityY = with(density) { (18f + rnd.nextFloat() * 26f).dp.toPx() }
            val maxRot = -35f + rnd.nextFloat() * 70f

            val color = palette[rnd.nextInt(palette.size)]

            particles.add(
                DisintegrateParticle(
                    initialX = jitterX.coerceIn(0f, width),
                    initialY = jitterY.coerceIn(0f, height),
                    width = pWidth,
                    height = pHeight,
                    color = color,
                    detachProgress = detach,
                    velocityX = velX,
                    liftY = liftY,
                    gravityY = gravityY,
                    maxRotation = maxRot
                )
            )
        }
    }

    return particles
}

/**
 * Calculates a stepped, jagged pixel erosion path into the provided reusable Path instance.
 */
fun calculateErosionPathInto(
    path: Path,
    size: Size,
    sweep: Float,
    isSelf: Boolean
) {
    path.reset()
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    if (sweep <= 0f) {
        path.addRect(Rect(0f, 0f, width, height))
        return
    }
    if (sweep >= 1f) {
        // Completely eroded - leave empty path so nothing is drawn
        return
    }

    val bands = 8
    val bandHeight = height / bands

    if (isSelf) {
        // Erode from right to left
        path.moveTo(0f, 0f)
        for (i in 0 until bands) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val stagger = when (i % 4) {
                0 -> 0.06f
                1 -> -0.05f
                2 -> 0.08f
                else -> -0.04f
            }
            val bandSweep = (sweep + stagger).coerceIn(0f, 1f)
            val currentRight = width * (1f - bandSweep)
            path.lineTo(currentRight, bandTop)
            path.lineTo(currentRight, bandBottom)
        }
        path.lineTo(0f, height)
        path.close()
    } else {
        // Erode from left to right
        val firstStagger = -0.04f
        val firstSweep = (sweep + firstStagger).coerceIn(0f, 1f)
        val startLeft = width * firstSweep
        path.moveTo(startLeft, 0f)
        path.lineTo(width, 0f)
        path.lineTo(width, height)
        for (i in bands - 1 downTo 0) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val stagger = when (i % 4) {
                0 -> -0.06f
                1 -> 0.05f
                2 -> -0.08f
                else -> 0.04f
            }
            val bandSweep = (sweep + stagger).coerceIn(0f, 1f)
            val currentLeft = width * bandSweep
            path.lineTo(currentLeft, bandBottom)
            path.lineTo(currentLeft, bandTop)
        }
        path.close()
    }
}

/**
 * Draws active flying fragments on a DrawScope canvas.
 */
fun DrawScope.drawDissolveParticles(
    particles: List<DisintegrateParticle>,
    progress: Float
) {
    if (particles.isEmpty() || progress <= 0f) return

    for (i in particles.indices) {
        val p = particles[i]
        if (progress < p.detachProgress) continue

        val tau = ((progress - p.detachProgress) / (1f - p.detachProgress)).coerceIn(0f, 1f)
        if (tau >= 1f) continue

        // Non-linear horizontal displacement
        val curX = p.initialX + p.velocityX * (tau * (2f - tau))

        // Vertical movement: subtle lift then gravity drop
        val liftProgress = sin(tau * PI.toFloat() * 0.5f)
        val gravityProgress = tau * tau
        val curY = p.initialY - p.liftY * liftProgress + p.gravityY * gravityProgress

        // Subtle rotation
        val rot = p.maxRotation * tau

        // Progressive shrink
        val scale = if (tau < 0.25f) {
            1f
        } else {
            (1f - (tau - 0.25f) / 0.75f).coerceIn(0f, 1f)
        }

        // Progressive alpha decay
        val alpha = if (tau < 0.35f) {
            1f
        } else {
            (1f - (tau - 0.35f) / 0.65f).coerceIn(0f, 1f)
        }

        val partW = p.width * scale
        val partH = p.height * scale

        if (partW <= 0.5f || partH <= 0.5f || alpha <= 0.01f) continue

        withTransform({
            translate(curX, curY)
            rotate(rot)
        }) {
            drawRect(
                color = p.color.copy(alpha = p.color.alpha * alpha),
                topLeft = Offset(-partW / 2f, -partH / 2f),
                size = Size(partW, partH)
            )
        }
    }
}

/**
 * Wraps a chat message or media cluster with the particle dissolve delete animation.
 * When [progress] is null, renders with zero overhead.
 */
@Composable
fun ParticleDissolveWrapper(
    progress: Float?,
    isSelf: Boolean,
    messageType: MessageType,
    hasMedia: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (progress == null) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val particles = remember(bubbleSize) {
        if (bubbleSize.width > 0 && bubbleSize.height > 0) {
            generateDisintegrateParticles(
                width = bubbleSize.width.toFloat(),
                height = bubbleSize.height.toFloat(),
                isSelf = isSelf,
                messageType = messageType,
                hasMedia = hasMedia,
                density = density
            )
        } else {
            emptyList()
        }
    }

    val erosionPath = remember { Path() }

    val contentAlpha = when {
        progress < 0.15f -> 1.0f
        progress < 0.70f -> (1.0f - (progress - 0.15f) / 0.55f).coerceIn(0f, 1f)
        else -> 0.0f
    }

    Box(
        modifier = modifier.onSizeChanged { bubbleSize = it }
    ) {
        // Base content with physical erosion mask and alpha decay
        Box(
            modifier = Modifier
                .drawWithContent {
                    if (progress < 0.70f) {
                        val sweep = (progress / 0.70f).coerceIn(0f, 1f)
                        calculateErosionPathInto(erosionPath, size, sweep, isSelf)
                        clipPath(erosionPath) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                .graphicsLayer {
                    alpha = contentAlpha
                }
        ) {
            content()
        }

        // Particle Canvas overlay: rectangular pixel fragments taking flight
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            drawDissolveParticles(particles, progress)
        }
    }
}
