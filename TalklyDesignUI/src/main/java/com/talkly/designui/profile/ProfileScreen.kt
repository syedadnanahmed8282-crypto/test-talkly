package com.talkly.designui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.chat.Avatar
import com.talkly.designui.components.ArtBox
import com.talkly.designui.components.ArtPairs
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink

/** Colours derived from the cover photo (Spotify "now playing" style). */
data class CoverPalette(val top: Color, val mid: Color, val bottom: Color, val accent: Color)

/**
 * Turn a dominant colour into the profile palette. Get the colour from a bitmap with androidx.palette:
 *   Palette.from(bitmap).generate().getVibrantColor(0xFF0369A1.toInt())  ->  paletteFrom(Color(thatInt))
 */
fun paletteFrom(dominant: Color): CoverPalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
    fun c(s: Float, v: Float, h: Float = hsv[0]) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    val s = hsv[1].coerceIn(0.35f, 0.8f)
    return CoverPalette(top = c(s, 0.55f), mid = c(s * 0.85f, 0.28f), bottom = c(s * 0.7f, 0.1f), accent = c(s.coerceAtLeast(0.55f), 0.95f))
}

/** Preset covers (art index -> dominant colour) so the demo works without images. */
fun presetPalette(art: Int): CoverPalette = paletteFrom(ArtPairs[art.mod(ArtPairs.size)].second)

class ProfileActions(
    val onBack: () -> Unit = {}, val onAudio: () -> Unit = {}, val onVideo: () -> Unit = {}, val onSearch: () -> Unit = {},
    val onToggleMute: () -> Unit = {}, val onTheme: () -> Unit = {}, val onMedia: () -> Unit = {},
    val onClear: () -> Unit = {}, val onBlock: () -> Unit = {}, val onChangeCover: () -> Unit = {},
    val onDemoIncomingVoice: (() -> Unit)? = null, val onDemoIncomingVideo: (() -> Unit)? = null,
)

/**
 * Contact info. [cover] is your image slot (Coil AsyncImage...). Pass [palette] computed from that image so the whole
 * screen takes the cover's colours.
 */
@Composable
fun ProfileScreen(
    name: String, lastSeen: String, about: String, muted: Boolean, blocked: Boolean, mediaCount: Int,
    palette: CoverPalette, actions: ProfileActions, modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit = { ArtBox(0, it) },
) {
    val top by animateColorAsState(palette.top, spring(), label = "t")
    val mid by animateColorAsState(palette.mid, spring(), label = "m")
    val bottom by animateColorAsState(palette.bottom, spring(), label = "b")
    val accent by animateColorAsState(palette.accent, spring(), label = "a")
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, mid, bottom)))) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            cover(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x4D000000), Color.Transparent, mid))))
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 14.dp, end = 14.dp, top = 228.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name.take(2).uppercase(), 104.dp, borderWidth = 3.dp)
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(lastSeen, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                QuickAction(Icons.Rounded.Call, "Audio", actions.onAudio)
                QuickAction(Icons.Rounded.Videocam, "Video", actions.onVideo)
                QuickAction(Icons.Rounded.Search, "Search", actions.onSearch)
            }
            DarkCard { Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { Text("About", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(about, color = Color.White, fontSize = 16.sp) } }
            DarkCard {
                InfoRow(icon = Icons.Rounded.NotificationsOff, label = "Mute notifications", onClick = actions.onToggleMute) {
                    Box(Modifier.size(50.dp, 30.dp).clip(RoundedCornerShape(15.dp)).background(if (muted) accent else Color.White.copy(alpha = 0.3f)).padding(3.dp), contentAlignment = if (muted) Alignment.CenterEnd else Alignment.CenterStart) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
                    }
                }
                InfoRow(icon = Icons.Rounded.Palette, label = "Chat theme", onClick = actions.onTheme) { Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.65f)) }
                InfoRow(icon = Icons.Rounded.Image, label = "Media, links & docs", onClick = actions.onMedia) {
                    Text(mediaCount.toString(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp); Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.65f))
                }
            }
            if (actions.onDemoIncomingVoice != null || actions.onDemoIncomingVideo != null) {
                DarkCard {
                    actions.onDemoIncomingVoice?.let { InfoRow(Icons.Rounded.Call, "Simulate incoming voice call", it) {} }
                    actions.onDemoIncomingVideo?.let { InfoRow(Icons.Rounded.Videocam, "Simulate incoming video call", it) {} }
                }
            }
            DarkCard {
                InfoRow(Icons.Rounded.DeleteSweep, "Clear chat", actions.onClear, danger = true) {}
                InfoRow(Icons.Rounded.Block, if (blocked) "Unblock $name" else "Block $name", actions.onBlock, danger = true) {}
            }
        }
        Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCircleButton(Icons.Rounded.ArrowBack, "Back", dark = true, tint = Color.White, onClick = actions.onBack)
            Box(Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50), dark = true).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                Text("Contact info", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 68.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.height(36.dp).glass(RoundedCornerShape(50), dark = true).bounceClick(onClick = actions.onChangeCover).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { Icon(Icons.Rounded.Image, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text("Change cover", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.bounceClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(56.dp).glass(CircleShape, dark = true), contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White) }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DarkCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0x4D101828)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)).padding(6.dp),
        content = content,
    )
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false, trailing: @Composable () -> Unit) {
    val c = if (danger) Color(0xFFFFB4AB) else Color.White
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = c)
        Text(label, color = c, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}
