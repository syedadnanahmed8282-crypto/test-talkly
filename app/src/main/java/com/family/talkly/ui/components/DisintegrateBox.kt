package com.family.talkly.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.family.talkly.data.models.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * [dissolve] true হলে content-এর ছবি তুলে ধুলোর কণায় ভেঙে ডান দিকে উড়িয়ে দেয়
 * (ডান থেকে বাঁ দিকে sweep হয়ে), শেষে [onFinished] কল করে।
 * onFinished-এ লিস্ট থেকে আইটেম মুছো, তাহলে নিচের আইটেমগুলো নিজে থেকে উঠে আসবে।
 *
 * দরকার: Compose UI/Foundation 1.7+ (BOM 2024.09.00 বা নতুন)
 */
@Composable
fun DisintegrateBox(
    dissolve: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1450,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<Particles?>(null) }
    val density = LocalDensity.current.density
    val done by rememberUpdatedState(onFinished)
    val paint = remember { AndroidPaint() }

    LaunchedEffect(dissolve) {
        if (!dissolve) return@LaunchedEffect

        // Attempt capturing layer bitmap safely
        var bitmap: ImageBitmap? = null
        for (attempt in 0..2) {
            try {
                val bmp = layer.toImageBitmap()
                if (bmp.width > 0 && bmp.height > 0) {
                    bitmap = bmp
                    break
                }
            } catch (_: Exception) {}
            delay(16L)
        }

        if (bitmap == null) {
            done()
            return@LaunchedEffect
        }

        particles = withContext(Dispatchers.Default) {
            buildParticles(bitmap, density)
        }
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        done()
    }

    Box(
        modifier = modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            val p = particles
            if (p == null) {
                drawLayer(layer)
            } else {
                drawParticles(p, progress.value, paint)
            }
        },
        content = content,
    )
}

/**
 * Manages active disintegrating messages so they remain visually present in layout
 * while the particle animation plays out, before being safely deleted from state.
 */
@Stable
class DisintegrateManager(private val coroutineScope: CoroutineScope) {
    private val _dissolvingMessages = mutableStateMapOf<String, ChatMessage>()
    private val _activeDissolveIds = mutableStateMapOf<String, Boolean>()

    var dissolvingCount by mutableIntStateOf(0)
        private set

    fun isDissolving(messageId: String): Boolean {
        return _activeDissolveIds.containsKey(messageId) || _dissolvingMessages.containsKey(messageId)
    }

    fun getDissolvingMessages(): List<ChatMessage> {
        return _dissolvingMessages.values.toList()
    }

    fun startDissolve(
        messages: Collection<ChatMessage>,
        durationMillis: Int = 1450,
        onComplete: (() -> Unit)? = null
    ) {
        if (messages.isEmpty()) {
            onComplete?.invoke()
            return
        }
        val list = messages.toList()
        for (m in list) {
            _dissolvingMessages[m.id] = m
            _activeDissolveIds[m.id] = true
        }
        dissolvingCount++

        // Safeguard fallback: ensure messages are removed even if component unmounts prematurely
        coroutineScope.launch {
            delay(durationMillis + 500L)
            var changed = false
            for (m in list) {
                if (_dissolvingMessages.remove(m.id) != null) changed = true
                _activeDissolveIds.remove(m.id)
            }
            if (changed) dissolvingCount++
            onComplete?.invoke()
        }
    }

    fun finishDissolve(messageIds: Collection<String>) {
        var changed = false
        for (id in messageIds) {
            if (_dissolvingMessages.remove(id) != null) changed = true
            _activeDissolveIds.remove(id)
        }
        if (changed) dissolvingCount++
    }

    fun finishDissolve(messageId: String) {
        finishDissolve(listOf(messageId))
    }
}

@Composable
fun rememberDisintegrateManager(): DisintegrateManager {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { DisintegrateManager(coroutineScope) }
}

private class Particles(
    val n: Int,
    val x: FloatArray,
    val y: FloatArray,
    val delay: FloatArray,
    val vx: FloatArray,
    val vy: FloatArray,
    val argb: IntArray,
    val size: Float,
    val gravity: Float,
)

private const val LIFE = 0.38f // মোট সময়ের কত অংশ একটা কণা উড়বে

private fun buildParticles(bmp: ImageBitmap, density: Float): Particles {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) {
        return Particles(0, FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0), IntArray(0), 0f, 0f)
    }
    val step = (2.5f * density).roundToInt().coerceAtLeast(2)
    val pixels = IntArray(w * h)
    bmp.readPixels(pixels)

    val max = ((w + step - 1) / step) * ((h + step - 1) / step)
    val x = FloatArray(max); val y = FloatArray(max); val d = FloatArray(max)
    val vx = FloatArray(max); val vy = FloatArray(max); val c = IntArray(max)
    val rnd = Random(System.nanoTime())
    var n = 0
    for (gy in 0 until h step step) {
        for (gx in 0 until w step step) {
            val px = pixels[min(gy + step / 2, h - 1) * w + min(gx + step / 2, w - 1)]
            if ((px ushr 24) < 10) continue
            x[n] = gx.toFloat(); y[n] = gy.toFloat()
            d[n] = (1f - gx / w.toFloat()) * 0.38f + rnd.nextFloat() * 0.17f
            vx[n] = (10f + rnd.nextFloat() * 70f) * density
            vy[n] = (-50f + rnd.nextFloat() * 30f) * density
            c[n] = px
            n++
        }
    }
    return Particles(n, x, y, d, vx, vy, c, step.toFloat(), 30f * density)
}

private fun DrawScope.drawParticles(p: Particles, prog: Float, paint: AndroidPaint) {
    drawIntoCanvas { canvas ->
        val cv = canvas.nativeCanvas
        for (i in 0 until p.n) {
            val k = (prog - p.delay[i]) / LIFE
            if (k >= 1f) continue
            var x = p.x[i]
            var y = p.y[i]
            var a = p.argb[i] ushr 24
            var s = p.size
            if (k > 0f) {
                val e = k * k * (3f - 2f * k)
                x += p.vx[i] * e
                y += p.vy[i] * e + p.gravity * e * e
                a = (a * (1f - k)).toInt()
                s *= 1f - 0.4f * k
            }
            paint.color = (a shl 24) or (p.argb[i] and 0xFFFFFF)
            cv.drawRect(x, y, x + s, y + s, paint)
        }
    }
}
