package com.family.talkly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
    val detachProgress: Float, // Progress when this particle detaches
    val velocityX: Float,      // Outward drift velocity
    val liftY: Float,          // Initial upward flutter before gravity takes over
    val gravityY: Float,       // Downward gravity drift
    val maxRotation: Float     // Subtle rotation in degrees (within ±15°)
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

                // Run animations in parallel over ~1350ms with smooth progressive easing
                val jobs = messageIds.map { id ->
                    async {
                        val anim = animatables[id] ?: return@async
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 1350,
                                easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)
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
 * Generates an adaptive, dense set of micro-pixel fragments (150 to 800) that visually mirror
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

    // Adaptive particle count based on visible message surface size:
    // - Small text bubble: ~150–250 particles
    // - Normal image/message: ~300–500 particles
    // - Large media/message/cluster: ~500–800 particles
    val count = when {
        baseArea < 5000f -> (150f + (baseArea / 5000f) * 70f).toInt().coerceIn(150, 220)
        baseArea < 25000f -> (220f + ((baseArea - 5000f) / 20000f) * 130f).toInt().coerceIn(220, 350)
        baseArea < 55000f -> (350f + ((baseArea - 25000f) / 30000f) * 170f).toInt().coerceIn(350, 520)
        else -> (520f + ((baseArea - 55000f) / 40000f) * 280f).toInt().coerceIn(520, 800)
    }

    val palette = when {
        hasMedia -> listOf(
            TalklySkyBlue, TalklyCyan, TalklyAqua,
            TalklyMidBlue, TalklyDarkBlue,
            Color(0xFF334155), Color(0xFF475569), Color(0xFF64748B),
            Color(0xFF94A3B8), TalklyTextPrimary, Color(0xFF020617),
            Color(0xFFE0F2FE), Color(0xFF1E293B)
        )
        messageType == MessageType.VOICE_NOTE -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, TalklyElevated, TalklyTextPrimary,
            Color(0xFF18212B), Color(0xFF334155), TalklyTextSecondary
        )
        isSelf -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, Color(0xFF06B6D4),
            TalklyTextPrimary, Color(0xFFE2E8F0), Color(0xFFCBD5E1),
            Color(0xFF0F766E), Color(0xFF14B8A6)
        )
        else -> listOf(
            TalklyCard, TalklyElevated, TalklyMidBlue,
            Color(0xFF253342), TalklyTextPrimary,
            TalklyTextSecondary, Color(0xFF334155), Color(0xFF475569),
            TalklyCyan.copy(alpha = 0.5f), Color(0xFF1E293B)
        )
    }

    val rnd = Random((width.toLong() xor (height.toLong() shl 16)) + (if (isSelf) 17 else 31))
    val particles = ArrayList<DisintegrateParticle>(count)

    val aspectRatio = (width / height.coerceAtLeast(1f)).coerceIn(0.25f, 4.0f)
    val cols = sqrt(count * aspectRatio).toInt().coerceIn(12, 60)
    val rows = ((count.toFloat() / cols) + 0.5f).toInt().coerceAtLeast(6)

    val cellW = width / cols
    val cellH = height / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (particles.size >= count) break

            // Controlled spatial jitter within each cell: prevents grid lines, avoids outside borders
            val jitterX = ((c + 0.5f + (rnd.nextFloat() - 0.5f) * 0.85f) * cellW).coerceIn(1f, width - 1f)
            val jitterY = ((r + 0.5f + (rnd.nextFloat() - 0.5f) * 0.85f) * cellH).coerceIn(1f, height - 1f)

            // Particle dimensions (in physical pixels):
            // ~70%: 1.2–2.2 px
            // ~25%: 2.2–3.2 px
            // ~5%: 3.2–4.2 px
            val sizeRoll = rnd.nextFloat()
            val pWidth = when {
                sizeRoll < 0.70f -> 1.2f + rnd.nextFloat() * 1.0f
                sizeRoll < 0.95f -> 2.2f + rnd.nextFloat() * 1.0f
                else -> 3.2f + rnd.nextFloat() * 1.0f
            }
            val pHeight = if (rnd.nextBoolean()) {
                pWidth
            } else {
                pWidth * (0.75f + rnd.nextFloat() * 0.55f)
            }

            // Sweep position determines detachment phase along erosion direction
            val sweepPos = if (isSelf) {
                (1f - jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                (jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }

            // Progressive detachment:
            // 0-12%: intact
            // 12-68%: progressive erosion
            // 68-100%: drift, shrink, fade
            val detach = (0.12f + sweepPos * 0.54f + (rnd.nextFloat() - 0.5f) * 0.06f).coerceIn(0.08f, 0.72f)

            // Organic subtle motion:
            // Outward X displacement: small-to-moderate (self -> right, other -> left)
            val velX = if (isSelf) {
                with(density) { (3f + rnd.nextFloat() * 10f).dp.toPx() }
            } else {
                with(density) { (-3f - rnd.nextFloat() * 10f).dp.toPx() }
            }
            // Vertical movement: downward Y displacement slightly greater than X
            val liftY = with(density) { (1f + rnd.nextFloat() * 3f).dp.toPx() }
            val gravityY = with(density) { (6f + rnd.nextFloat() * 14f).dp.toPx() }
            // Subtle rotation within ±15 degrees
            val maxRot = -15f + rnd.nextFloat() * 30f

            val color = palette[rnd.nextInt(palette.size)]

            particles.add(
                DisintegrateParticle(
                    initialX = jitterX,
                    initialY = jitterY,
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
 * Calculates a fine stepped, jagged pixel erosion path into the provided reusable Path instance.
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

    val bands = 20
    val bandHeight = height / bands

    if (isSelf) {
        // Erode from right to left with fine stepped pixel offsets
        path.moveTo(0f, 0f)
        for (i in 0 until bands) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val stagger = when (i % 5) {
                0 -> 0.035f
                1 -> -0.030f
                2 -> 0.045f
                3 -> -0.020f
                else -> 0.015f
            }
            val bandSweep = (sweep + stagger).coerceIn(0f, 1f)
            val currentRight = width * (1f - bandSweep)
            path.lineTo(currentRight, bandTop)
            path.lineTo(currentRight, bandBottom)
        }
        path.lineTo(0f, height)
        path.close()
    } else {
        // Erode from left to right with fine stepped pixel offsets
        val firstStagger = -0.025f
        val firstSweep = (sweep + firstStagger).coerceIn(0f, 1f)
        val startLeft = width * firstSweep
        path.moveTo(startLeft, 0f)
        path.lineTo(width, 0f)
        path.lineTo(width, height)
        for (i in bands - 1 downTo 0) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val stagger = when (i % 5) {
                0 -> -0.035f
                1 -> 0.030f
                2 -> -0.045f
                3 -> 0.020f
                else -> -0.015f
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
 * Draws active flying micro-pixel fragments on a DrawScope canvas.
 */
fun DrawScope.drawDissolveParticles(
    particles: List<DisintegrateParticle>,
    progress: Float
) {
    if (particles.isEmpty() || progress <= 0f) return

    for (i in 0 until particles.size) {
        val p = particles[i]
        if (progress < p.detachProgress) continue

        val lifetime = 1f - p.detachProgress
        if (lifetime <= 0f) continue
        val tau = ((progress - p.detachProgress) / lifetime).coerceIn(0f, 1f)
        if (tau >= 1f) continue

        // Non-linear horizontal displacement (subtle drag deceleration)
        val curX = p.initialX + p.velocityX * (tau * (1.7f - 0.7f * tau))

        // Vertical movement: subtle lift then gentle gravity drop
        val liftProgress = sin(tau * PI.toFloat()).coerceAtLeast(0f) * (1f - tau)
        val gravityProgress = tau * tau
        val curY = p.initialY - p.liftY * liftProgress + p.gravityY * gravityProgress

        // Subtle rotation within ±15 degrees
        val rot = p.maxRotation * tau

        // Progressive shrink: full size until tau=0.35, then smoothly scales down to 0.15
        val scale = if (tau < 0.35f) {
            1.0f
        } else {
            (1.0f - ((tau - 0.35f) / 0.65f) * 0.85f).coerceIn(0.15f, 1.0f)
        }

        // Progressive smooth alpha decay: full until tau=0.40, then smoothstep fade to 0
        val alpha = if (tau < 0.40f) {
            1.0f
        } else {
            val fadeTau = ((tau - 0.40f) / 0.60f).coerceIn(0f, 1f)
            (1.0f - fadeTau * fadeTau * (3f - 2f * fadeTau)).coerceIn(0f, 1f)
        }

        val partW = p.width * scale
        val partH = p.height * scale

        if (partW <= 0.2f || partH <= 0.2f || alpha <= 0.005f) continue

        val finalColor = p.color.copy(alpha = p.color.alpha * alpha)

        // Performance optimization: 1-2px micro particles bypass canvas save/restore overhead
        if (rot != 0f && (partW > 2.2f || partH > 2.2f)) {
            withTransform({
                translate(curX, curY)
                rotate(rot)
            }) {
                drawRect(
                    color = finalColor,
                    topLeft = Offset(-partW / 2f, -partH / 2f),
                    size = Size(partW, partH)
                )
            }
        } else {
            drawRect(
                color = finalColor,
                topLeft = Offset(curX - partW / 2f, curY - partH / 2f),
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

    // Content stays fully solid in the beginning (0-30%), then smoothly fades as erosion sweeps through 72%
    val contentAlpha = when {
        progress < 0.30f -> 1.0f
        progress < 0.72f -> (1.0f - (progress - 0.30f) / 0.42f).coerceIn(0f, 1f)
        else -> 0.0f
    }

    Box(
        modifier = modifier.onSizeChanged { bubbleSize = it }
    ) {
        // Base content with physical stepped erosion mask and alpha decay
        Box(
            modifier = Modifier
                .drawWithContent {
                    if (progress < 0.72f) {
                        val sweep = if (progress <= 0.12f) 0f else ((progress - 0.12f) / 0.56f).coerceIn(0f, 1f)
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

        // Particle Canvas overlay: dense micro-pixel fragments taking flight
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            drawDissolveParticles(particles, progress)
        }
    }
}

