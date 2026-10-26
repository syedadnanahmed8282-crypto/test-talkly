package com.talkly.designui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.components.ArtPairs
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette

private class EmojiCat(val label: String, val icon: String, val list: List<String>)

private val EmojiCats = listOf(
    EmojiCat("SMILEYS", "😀", listOf("😀","😃","😄","😁","😆","😅","😂","🤣","🥲","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡")),
    EmojiCat("GESTURES", "👍", listOf("👍","👎","👊","✊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✌️","🤞","🤟","🤘","👌","🤌","🤏","👈","👉","👆","👇","☝️","✋","🤚","🖐️","🖖","👋","🤙","💪","🙋","🤷")),
    EmojiCat("HEARTS", "❤️", listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","♥️","😻","💑","💏","🌹")),
    EmojiCat("ANIMALS", "🐶", listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦋","🐝","🐢","🐍","🐬","🐳","🦈")),
    EmojiCat("FOOD", "🍔", listOf("🍎","🍊","🍋","🍌","🍉","🍇","🍓","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🌽","🥕","🍔","🍟","🍕","🌭","🥪","🌮","🍜","🍣","🍩","🍪","🎂","🍫","☕","🍵","🥤")),
    EmojiCat("ACTIVITY", "⚽", listOf("⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓","🏸","🥊","🎯","🎮","🎲","🎸","🎹","🎤","🎧","🎬","🎨","🏆","🥇","🎉","🎊","🎁","🎈","✈️","🚗","🏍️","🚀","🌈","☀️","🌙","⭐")),
)

/** Emoji panel: recent row, category title, scrolling grid and a capsule tab bar at the bottom. */
@Composable
fun EmojiPicker(recents: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var cat by remember { mutableIntStateOf(0) }
    Column(modifier.glass(RoundedCornerShape(28.dp), strong = true).padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)) {
        if (recents.isNotEmpty()) {
            Text("RECENT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Mid, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                recents.take(8).forEach { e -> EmojiCell(e, Modifier.weight(1f)) { onPick(e) } }
                repeat(8 - recents.take(8).size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(EmojiCats[cat].label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Mid, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(168.dp)) {
            items(EmojiCats[cat].list) { e -> EmojiCell(e, Modifier) { onPick(e) } }
        }
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.55f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EmojiCats.forEachIndexed { i, c ->
                val on = i == cat
                val s by animateFloatAsState(if (on) 1f else 0.85f, spring(0.5f, 400f), label = "tab")
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.Transparent)
                        .clickable { cat = i }.graphicsLayer { alpha = if (on) 1f else 0.6f; scaleX = s; scaleY = s },
                    contentAlignment = Alignment.Center,
                ) { Text(c.icon, fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun EmojiCell(e: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) }
}

/** 3x2 attachment sheet. Wire each callback to your own picker / camera / file intent. */
@Composable
fun AttachSheet(
    onGallery: () -> Unit, onCamera: () -> Unit, onDocument: () -> Unit,
    onAudio: () -> Unit, onLocation: () -> Unit, onContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf<Triple<String, ImageVector, () -> Unit>>(
        Triple("Gallery", Icons.Rounded.Image, onGallery), Triple("Camera", Icons.Rounded.PhotoCamera, onCamera),
        Triple("Document", Icons.Rounded.InsertDriveFile, onDocument), Triple("Audio", Icons.Rounded.Headphones, onAudio),
        Triple("Location", Icons.Rounded.LocationOn, onLocation), Triple("Contact", Icons.Rounded.Person, onContact),
    )
    val colors = listOf(Color(0xFF0369A1), Color(0xFF0E7490), Color(0xFF1D4ED8), Color(0xFF0F766E), Color(0xFF155E75), Color(0xFF1E40AF))
    Column(modifier.glass(RoundedCornerShape(28.dp), strong = true).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, (label, icon, click) ->
                    Column(Modifier.weight(1f).bounceClick(onClick = click), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(colors[r * 3 + c], Color(0xFF0C4A6E)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp)) }
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.Dark)
                    }
                }
            }
        }
    }
}

private val ReactionEmojis = listOf("👍","❤️","😂","😮","😢","🙏","🔥","👏","🎉","😍","🤔","💯","😡","🤝")

/** Floating reaction bar (scrolls sideways for more emojis; each emoji rises in with a small delay). */
@Composable
fun ReactionBar(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0.5f, spring(0.55f, 320f), label = "bar")
    Row(
        modifier.widthIn(max = 290.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }
            .glass(RoundedCornerShape(50), strong = true).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        ReactionEmojis.forEachIndexed { i, e ->
            var up by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(50L + i * 30L); up = true }
            val s by animateFloatAsState(if (up) 1f else 0.4f, spring(0.5f, 400f), label = "e$i")
            Box(Modifier.size(36.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f); translationY = (1f - s) * 14f }.bounceClick { onPick(e) }, contentAlignment = Alignment.Center) {
                Text(e, fontSize = 22.sp)
            }
        }
    }
}
