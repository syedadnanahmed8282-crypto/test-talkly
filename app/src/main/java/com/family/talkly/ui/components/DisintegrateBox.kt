package com.family.talkly.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Dispatchers
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

    LaunchedEffect(dissolve) {
        if (!dissolve) return@LaunchedEffect
        val bitmap = layer.toImageBitmap()
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
            if (p == null) drawLayer(layer) else drawParticles(p, progress.value)
        },
        content = content,
    )
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

private val paint = AndroidPaint()

private fun DrawScope.drawParticles(p: Particles, prog: Float) {
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
