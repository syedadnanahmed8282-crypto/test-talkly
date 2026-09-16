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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
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
    val detachProgress: Float, // Progress (0f..1f) when this particle detaches
    val velocityX: Float,      // Outward drift velocity in pixels
    val liftY: Float,          // Initial upward buoyant lift in pixels
    val gravityY: Float,       // Downward gravity drift in pixels
    val maxRotation: Float     // Subtle rotation in degrees (within ±12°)
)

/**
 * Manages active particle dissolve animations across chat message items.
 * Decouples visual disintegration from backend deletion by retaining a visual snapshot of
 * dissolving messages so the chat list layout does not jump or collapse prematurely.
 */
@Stable
class ParticleDissolveManager(private val coroutineScope: CoroutineScope) {
    private val animatables = mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>()
    private val _dissolvingMessages = mutableStateMapOf<String, ChatMessage>()

    // State counter observed by ChatDetailScreen to retain dissolving messages in layout
    var dissolvingCount by mutableIntStateOf(0)
        private set

    fun isDissolving(messageId: String): Boolean {
        return animatables.containsKey(messageId) || _dissolvingMessages.containsKey(messageId)
    }

    fun getProgress(messageId: String): Float? {
        return animatables[messageId]?.value
    }

    fun getDissolvingMessages(): List<ChatMessage> {
        return _dissolvingMessages.values.toList()
    }

    /**
     * Immediately registers [messages] as visually dissolving and executes the smooth
     * ~1850ms cinematic disintegration animation.
     */
    fun startDissolve(
        messages: Collection<ChatMessage>,
        onComplete: (() -> Unit)? = null
    ) {
        if (messages.isEmpty()) {
            onComplete?.invoke()
            return
        }

        val targetList = messages.toList()
        for (msg in targetList) {
            _dissolvingMessages[msg.id] = msg
            animatables[msg.id] = Animatable(0f)
        }
        dissolvingCount++

        coroutineScope.launch {
            try {
                val jobs = targetList.map { msg ->
                    async {
                        val anim = animatables[msg.id] ?: return@async
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 1850,
                                easing = CubicBezierEasing(0.22f, 0.0f, 0.20f, 1.0f)
                            )
                        )
                    }
                }
                jobs.awaitAll()
            } catch (_: CancellationException) {
                // Safeguard on coroutine cancellation
            } finally {
                for (msg in targetList) {
                    animatables.remove(msg.id)
                    _dissolvingMessages.remove(msg.id)
                }
                dissolvingCount++
                onComplete?.invoke()
            }
        }
    }

    /**
     * Fallback overload supporting message IDs directly.
     */
    fun startDissolve(
        messageIds: Set<String>,
        onComplete: (() -> Unit)? = null
    ) {
        if (messageIds.isEmpty()) {
            onComplete?.invoke()
            return
        }
        for (id in messageIds) {
            animatables[id] = Animatable(0f)
        }
        dissolvingCount++

        coroutineScope.launch {
            try {
                val jobs = messageIds.map { id ->
                    async {
                        val anim = animatables[id] ?: return@async
                        anim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 1850,
                                easing = CubicBezierEasing(0.22f, 0.0f, 0.20f, 1.0f)
                            )
                        )
                    }
                }
                jobs.awaitAll()
            } catch (_: CancellationException) {
            } finally {
                for (id in messageIds) {
                    animatables.remove(id)
                    _dissolvingMessages.remove(id)
                }
                dissolvingCount++
                onComplete?.invoke()
            }
        }
    }

    fun clearDissolved(ids: Set<String>) {
        for (id in ids) {
            _dissolvingMessages.remove(id)
            animatables.remove(id)
        }
        dissolvingCount++
    }
}

@Composable
fun rememberParticleDissolveManager(): ParticleDissolveManager {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { ParticleDissolveManager(coroutineScope) }
}

/**
 * Generates an adaptive, dense set of micro-pixel fragments (160 to 720) that visually mirror
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
    // - Small text bubble: ~160–240 particles
    // - Normal message: ~240–380 particles
    // - Large media/message/cluster: ~380–720 particles
    val count = when {
        baseArea < 6000f -> (160f + (baseArea / 6000f) * 80f).toInt().coerceIn(160, 240)
        baseArea < 25000f -> (240f + ((baseArea - 6000f) / 19000f) * 140f).toInt().coerceIn(240, 380)
        baseArea < 55000f -> (380f + ((baseArea - 25000f) / 30000f) * 160f).toInt().coerceIn(380, 540)
        else -> (540f + ((baseArea - 55000f) / 40000f) * 180f).toInt().coerceIn(540, 720)
    }

    val palette = when {
        hasMedia -> listOf(
            TalklyCyan, TalklyAqua, TalklySkyBlue,
            Color(0xFF38BDF8), Color(0xFF0284C7),
            Color(0xFF1E293B), Color(0xFF334155), Color(0xFF475569),
            Color(0xFF64748B), Color(0xFF94A3B8), TalklyTextPrimary,
            Color(0xFF0F172A), Color(0xFFE2E8F0)
        )
        messageType == MessageType.VOICE_NOTE -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, TalklyElevated, TalklyCard,
            TalklyTextPrimary, TalklyTextSecondary, Color(0xFF0F766E)
        )
        isSelf -> listOf(
            TalklyCyan, TalklyAqua, TalklyMint,
            TalklySkyBlue, Color(0xFF06B6D4), Color(0xFF0891B2),
            TalklyTextPrimary, Color(0xFFE2E8F0), Color(0xFFCCFBF1),
            Color(0xFF14B8A6), Color(0xFF0F766E)
        )
        else -> listOf(
            TalklyCard, TalklyElevated, Color(0xFF1A2430),
            Color(0xFF253342), Color(0xFF2D3B4B),
            TalklyTextPrimary, TalklyTextSecondary,
            Color(0xFF334155), Color(0xFF475569),
            TalklyCyan.copy(alpha = 0.6f)
        )
    }

    val rnd = Random((width.toLong() xor (height.toLong() shl 16)) + (if (isSelf) 17 else 31))
    val particles = ArrayList<DisintegrateParticle>(count)

    val aspectRatio = (width / height.coerceAtLeast(1f)).coerceIn(0.25f, 4.0f)
    val cols = sqrt(count * aspectRatio).toInt().coerceIn(14, 64)
    val rows = ((count.toFloat() / cols) + 0.5f).toInt().coerceAtLeast(8)

    val cellW = width / cols
    val cellH = height / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (particles.size >= count) break

            // Controlled spatial jitter within each cell
            val jitterX = ((c + 0.5f + (rnd.nextFloat() - 0.5f) * 0.88f) * cellW).coerceIn(1f, width - 1f)
            val jitterY = ((r + 0.5f + (rnd.nextFloat() - 0.5f) * 0.88f) * cellH).coerceIn(1f, height - 1f)

            // Normalized position along the erosion sweep:
            // For isSelf: erosion moves right-to-left (x: width -> 0)
            // For !isSelf: erosion moves left-to-right (x: 0 -> width)
            val normX = if (isSelf) {
                (1f - jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                (jitterX / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }

            // Cinematic 4-Phase Progression Mapping:
            // Phase 1: 0% - 14% -> Hold & recognition (intact)
            // Phase 2: 14% - 46% -> Leading edge erosion
            // Phase 3: 46% - 76% -> Body fragmentation & flow
            // Phase 4: 76% - 100% -> Residual dissolve & fade
            val detachBase = 0.14f + normX * 0.60f
            val bandIndex = ((jitterY / height.coerceAtLeast(1f)) * 50).toInt().coerceIn(0, 49)
            val pathJitter = sin(bandIndex * 1.57f) * 0.024f + sin(bandIndex * 3.73f + 0.8f) * 0.016f
            val localJitter = (rnd.nextFloat() - 0.5f) * 0.035f
            val detach = (detachBase - pathJitter * 0.6f + localJitter).coerceIn(0.12f, 0.76f)

            // Coherent velocity field:
            // Gentle directional drift with subtle individual variation
            val speedFactor = 0.65f + rnd.nextFloat() * 0.70f
            val velX = if (isSelf) {
                with(density) { (4f + speedFactor * 9f).dp.toPx() }
            } else {
                with(density) { (-4f - speedFactor * 9f).dp.toPx() }
            }
            // Vertical movement: subtle initial buoyant flutter, followed by natural gravity settling
            val liftY = with(density) { (1.5f + rnd.nextFloat() * 3.5f).dp.toPx() }
            val gravityY = with(density) { (8f + rnd.nextFloat() * 16f).dp.toPx() }
            // Subtle rotation within ±12 degrees
            val maxRot = -12f + rnd.nextFloat() * 24f

            // Particle size: 65% micro-dust (1.2px - 2.0px), 25% medium (2.0px - 3.0px), 10% flakes (3.0px - 3.8px)
            val sizeRoll = rnd.nextFloat()
            val pWidth = when {
                sizeRoll < 0.65f -> 1.2f + rnd.nextFloat() * 0.8f
                sizeRoll < 0.90f -> 2.0f + rnd.nextFloat() * 1.0f
                else -> 3.0f + rnd.nextFloat() * 0.8f
            }
            val pHeight = if (rnd.nextFloat() < 0.6f) pWidth else pWidth * (0.8f + rnd.nextFloat() * 0.4f)

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
 * Calculates a fine organic fractal erosion path into the provided reusable Path instance.
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
        // Completely eroded
        return
    }

    val bands = 50
    val bandHeight = height / bands

    if (isSelf) {
        // Erode from right to left: un-eroded portion is on the left [0 .. rightEdge]
        path.moveTo(0f, 0f)
        for (i in 0 until bands) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val jitter = sin(i * 1.57f) * 0.024f +
                    sin(i * 3.73f + 0.8f) * 0.016f +
                    sin(i * 7.19f + 2.1f) * 0.008f
            val bandSweep = (sweep + jitter).coerceIn(0f, 1f)
            val currentRight = width * (1f - bandSweep)
            path.lineTo(currentRight, bandTop)
            path.lineTo(currentRight, bandBottom)
        }
        path.lineTo(0f, height)
        path.close()
    } else {
        // Erode from left to right: un-eroded portion is on the right [leftEdge .. width]
        val firstJitter = sin(0f) * 0.024f + sin(0.8f) * 0.016f
        val firstSweep = (sweep + firstJitter).coerceIn(0f, 1f)
        val startLeft = width * firstSweep
        path.moveTo(startLeft, 0f)
        path.lineTo(width, 0f)
        path.lineTo(width, height)
        for (i in bands - 1 downTo 0) {
            val bandTop = i * bandHeight
            val bandBottom = (i + 1) * bandHeight
            val jitter = sin(i * 1.57f) * 0.024f +
                    sin(i * 3.73f + 0.8f) * 0.016f +
                    sin(i * 7.19f + 2.1f) * 0.008f
            val bandSweep = (sweep + jitter).coerceIn(0f, 1f)
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

    val masterFade = if (progress > 0.82f) {
        ((1.0f - progress) / 0.18f).coerceIn(0f, 1f)
    } else {
        1.0f
    }
    if (masterFade <= 0.005f) return

    for (i in 0 until particles.size) {
        val p = particles[i]
        if (progress < p.detachProgress) continue

        val lifetime = (1f - p.detachProgress).coerceAtLeast(0.15f)
        val tau = ((progress - p.detachProgress) / lifetime).coerceIn(0f, 1f)
        if (tau >= 1f) continue

        // Coherent non-linear horizontal drift (smooth air drag deceleration)
        val curX = p.initialX + p.velocityX * (tau * (1.6f - 0.6f * tau))

        // Natural vertical trajectory: subtle buoyant flutter then gentle gravity settling
        val liftProgress = sin(tau * PI.toFloat()).coerceAtLeast(0f) * (1f - tau * 0.5f)
        val gravityProgress = tau * tau
        val curY = p.initialY - p.liftY * liftProgress + p.gravityY * gravityProgress

        // Subtle rotation flutter
        val rot = p.maxRotation * tau

        // Progressive shrink: full size until tau=0.35, then scales smoothly to 0.3
        val scale = if (tau < 0.35f) {
            1.0f
        } else {
            (1.0f - ((tau - 0.35f) / 0.65f) * 0.70f).coerceIn(0.30f, 1.0f)
        }

        // Smooth opacity decay using cubic smoothstep
        val alpha = if (tau < 0.40f) {
            1.0f
        } else {
            val fadeTau = ((tau - 0.40f) / 0.60f).coerceIn(0f, 1f)
            (1.0f - fadeTau * fadeTau * (3f - 2f * fadeTau)).coerceIn(0f, 1f)
        }

        val partW = p.width * scale
        val partH = p.height * scale
        val effectiveAlpha = p.color.alpha * alpha * masterFade

        if (partW <= 0.2f || partH <= 0.2f || effectiveAlpha <= 0.008f) continue

        val finalColor = p.color.copy(alpha = effectiveAlpha)

        if (rot != 0f && (partW > 2.0f || partH > 2.0f)) {
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

    // Phase 1 (0% - 14%): Hold/Recognition - 100% solid, fully recognizable
    // Phase 2 & 3 (14% - 76%): Progressive edge erosion & fragmentation
    // Phase 4 (76% - 100%): Message body completely eroded, residual particles drifting to empty space
    val contentAlpha = when {
        progress < 0.55f -> 1.0f
        progress < 0.76f -> (1.0f - ((progress - 0.55f) / 0.21f) * 0.25f).coerceIn(0f, 1f)
        else -> 0.0f
    }

    Box(
        modifier = modifier
            .onSizeChanged { if (it.width > 0 && it.height > 0) bubbleSize = it }
            .pointerInput(Unit) {}
    ) {
        // Base content with organic fractal erosion mask
        Box(
            modifier = Modifier
                .drawWithContent {
                    if (progress < 0.76f) {
                        val sweep = if (progress <= 0.14f) 0f else ((progress - 0.14f) / 0.62f).coerceIn(0f, 1f)
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

        // Particle Canvas overlay: micro-fragments taking flight from the message surface
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            drawDissolveParticles(particles, progress)
        }
    }
}


