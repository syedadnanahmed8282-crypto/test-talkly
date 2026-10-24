package com.example.app.ui.glasschat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val doodleIcons: List<ImageVector> = listOf(
    Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder, Icons.Outlined.Cloud,
    Icons.Outlined.EmojiEmotions, Icons.Outlined.CameraAlt, Icons.Outlined.Image,
    Icons.Outlined.Call, Icons.Outlined.Videocam, Icons.Outlined.MusicNote,
    Icons.Outlined.AttachFile, Icons.Outlined.Mic, Icons.Outlined.LocationOn, Icons.Outlined.Schedule,
)

/** Sky-blue gradient + soft light blobs + faint doodle pattern (Telegram-like). */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFFEAF6FF), Color(0xFFD5ECFC), Color(0xFFBDE1FA))
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun blob(cx: Float, cy: Float, r: Float, c: Color) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            blob(size.width * 0.05f, size.height * 0.40f, size.width * 0.7f, Color(0x8C73C7FA))
            blob(size.width * 0.90f, size.height * 0.80f, size.width * 0.6f, Color(0xCCFFFFFF))
            blob(size.width * 0.90f, size.height * 0.10f, size.width * 0.5f, Color(0x808CD1FF))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rnd = remember { Random(7) }
            val spots = remember {
                List(45) { i ->
                    val col = i % 5
                    val row = i / 5
                    Triple(
                        Offset(col * 80f + rnd.nextFloat() * 30f - 5f, row * 96f + rnd.nextFloat() * 40f),
                        (22 + rnd.nextInt(10)),
                        (rnd.nextFloat() - 0.5f) * 40f,
                    )
                }
            }
            spots.forEachIndexed { i, (pos, sz, rot) ->
                Icon(
                    imageVector = doodleIcons[i % doodleIcons.size],
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .offset(pos.x.dp, pos.y.dp)
                        .size(sz.dp)
                        .rotate(rot),
                )
            }
        }
    }
}
