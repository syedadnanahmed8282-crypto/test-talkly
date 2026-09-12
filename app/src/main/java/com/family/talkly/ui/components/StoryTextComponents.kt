package com.family.talkly.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.data.models.StoryTextMetadata
import com.family.talkly.data.models.StoryTextStyle

val StoryTalklyCyan = Color(0xFF22D3EE)
val StoryTalklyAqua = Color(0xFF0EA5A4)
val StoryTalklyMint = Color(0xFF5EEAD4)
val StoryTalklySkyBlue = Color(0xFF38BDF8)
val StoryTalklyDeepCyan = Color(0xFF0C2B3A)
val StoryTalklyRoyalNavy = Color(0xFF0B3056)
val StoryTalklyMidnight = Color(0xFF080B10)

val TALKLY_STORY_TEXT_COLORS = listOf(
    "#FFFFFF", // Crisp White
    "#22D3EE", // Talkly Cyan
    "#38BDF8", // Sky Blue
    "#5EEAD4", // Mint Cyan
    "#BAE6FD", // Ice Blue
    "#93C5FD", // Soft Blue
    "#0C2B3A", // Deep Cyan Dark
    "#0B3056", // Royal Navy
    "#080B10"  // True Black
)

val TALKLY_STORY_BG_COLORS = listOf(
    "#0C2B3A", // Deep Cyan Dark
    "#080B10", // Talkly True Black
    "#0E3838", // Deep Emerald Aqua
    "#1B2430", // Slate Elevated
    "#0B3056", // Royal Navy Blue
    "#132238", // Dark Slate Blue
    "#16334D"  // Ice Blue Tint
)

/**
 * Renders story text according to its StoryTextStyle and color.
 * Displays selection border and handles when isSelected is true.
 */
@Composable
fun StoryTextRender(
    metadata: StoryTextMetadata,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textColor = try {
        Color(android.graphics.Color.parseColor(metadata.colorHex))
    } catch (_: Exception) {
        Color.White
    }

    val style = metadata.style

    // Base text style according to preset
    val textStyle = when (style) {
        StoryTextStyle.CLASSIC -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = Offset(2f, 2f),
                blurRadius = 8f
            )
        )
        StoryTextStyle.MODERN -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 25.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(2f, 3f),
                blurRadius = 10f
            )
        )
        StoryTextStyle.ELEGANT -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.7f),
                offset = Offset(1f, 2f),
                blurRadius = 6f
            )
        )
        StoryTextStyle.STRONG -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            textAlign = TextAlign.Center
        )
        StoryTextStyle.TYPEWRITER -> TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
        StoryTextStyle.BUBBLE -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            textAlign = TextAlign.Center
        )
        StoryTextStyle.HIGHLIGHT -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        StoryTextStyle.MINIMAL -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.6f),
                offset = Offset(1f, 1f),
                blurRadius = 4f
            )
        )
    }

    // Box modifiers for container backgrounds (Strong, Typewriter, Bubble, Highlight)
    val containerModifier = when (style) {
        StoryTextStyle.STRONG -> Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
        StoryTextStyle.TYPEWRITER -> Modifier
            .background(Color(0xFF0C2B3A).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .border(1.dp, StoryTalklyCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
        StoryTextStyle.BUBBLE -> Modifier
            .background(Color(0xFF0C2B3A).copy(alpha = 0.9f), RoundedCornerShape(24.dp))
            .border(1.5.dp, StoryTalklyCyan, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
        StoryTextStyle.HIGHLIGHT -> Modifier
            .background(StoryTalklyCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
        else -> Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = containerModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = metadata.text,
                color = textColor,
                style = textStyle,
                textAlign = TextAlign.Center
            )
        }

        // Selection Border & Handles
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        BorderStroke(1.5.dp, StoryTalklyCyan),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                // Top-left handle
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StoryTalklyCyan)
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                )
                // Top-right handle
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StoryTalklyCyan)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
                // Bottom-left handle
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StoryTalklyCyan)
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 4.dp)
                )
                // Bottom-right handle
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StoryTalklyCyan)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                )
            }
        }
    }
}

/**
 * Smart contrast analyzer that evaluates the background behind text (normalized X/Y)
 * and recommends the highest-contrast readable color from the Talkly blue palette.
 */
object StoryContrastAnalyzer {
    fun getRecommendedColor(
        context: Context,
        mediaUri: String?,
        backgroundColorHex: String,
        normX: Float,
        normY: Float
    ): String {
        if (mediaUri.isNullOrBlank()) {
            return try {
                val colorInt = android.graphics.Color.parseColor(backgroundColorHex)
                val r = android.graphics.Color.red(colorInt)
                val g = android.graphics.Color.green(colorInt)
                val b = android.graphics.Color.blue(colorInt)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                if (luminance < 0.45) "#FFFFFF" else "#080B10"
            } catch (_: Exception) {
                "#FFFFFF"
            }
        }

        return try {
            val uri = Uri.parse(mediaUri)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 16 // Downscale for super-fast off-thread reading
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                if (bitmap != null) {
                    val targetX = (normX * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val targetY = (normY * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)

                    // Average 3x3 local neighborhood
                    var totalLum = 0.0
                    var samples = 0
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val px = (targetX + dx).coerceIn(0, bitmap.width - 1)
                            val py = (targetY + dy).coerceIn(0, bitmap.height - 1)
                            val pixel = bitmap.getPixel(px, py)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            totalLum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                            samples++
                        }
                    }
                    bitmap.recycle()

                    val avgLum = if (samples > 0) totalLum / samples else 0.0
                    if (avgLum < 0.45) "#FFFFFF" else "#080B10"
                } else {
                    "#FFFFFF"
                }
            } else {
                "#FFFFFF"
            }
        } catch (_: Exception) {
            "#FFFFFF"
        }
    }
}
