package com.talkly.designui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.components.ArtBox
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette
import com.talkly.designui.util.formatClock
import com.talkly.designui.util.formatSize
import com.talkly.designui.viewer.ZoomableBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One local photo/video from MediaStore (map your cursor rows to this). */
data class GalleryItem(
    val id: String,
    val uri: String? = null,
    val art: Int = 0,
    val isVideo: Boolean = false,
    val durationSec: Int = 0,
    val sizeBytes: Long = 0,
    /** e.g. "Camera", "Screenshots", "Favorites" -> becomes an album chip */
    val album: String = "Camera",
)

private typealias GallerySlot = @Composable (GalleryItem, Modifier) -> Unit
private val DefaultGallerySlot: GallerySlot = { it, m -> ArtBox(it.art, m) }

/**
 * Bottom-sheet gallery picker: spring open, drag handle to dismiss, album chips, staggered tiles,
 * numbered multi-select, preview (long-press / expand icon) with zoom, caption + send bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryPicker(
    items: List<GalleryItem>,
    onDismiss: () -> Unit,
    onSend: (selected: List<GalleryItem>, caption: String) -> Unit,
    modifier: Modifier = Modifier,
    onCamera: () -> Unit = {},
    maxSelection: Int = 10,
    imageContent: GallerySlot = DefaultGallerySlot,
) {
    val p = LocalChatPalette.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val sheet = remember { Animatable(1f) }
    val drag = remember { Animatable(0f) }
    var album by remember { mutableStateOf("All") }
    var caption by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Pair<List<GalleryItem>, Int>?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { sheet.animateTo(0f, spring(0.85f, 280f)) }
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    fun close(after: () -> Unit = {}) {
        scope.launch { sheet.animateTo(1f, tween(320, easing = FastOutLinearInEasing)); onDismiss(); after() }
    }
    fun toggle(it: GalleryItem) {
        if (it.id in selectedIds) selectedIds.remove(it.id)
        else if (selectedIds.size >= maxSelection) toast = "You can select up to $maxSelection items"
        else { selectedIds.add(it.id); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    val albums = remember(items) { listOf("All", "Photos", "Videos") + items.map { it.album }.distinct() }
    val visible = remember(items, album) {
        when (album) {
            "All" -> items
            "Photos" -> items.filter { !it.isVideo }
            "Videos" -> items.filter { it.isVideo }
            else -> items.filter { it.album == album }
        }
    }
    val selectedItems = selectedIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    val gridBottom by animateDpAsState(if (selectedIds.isEmpty()) 24.dp else 200.dp, spring(0.8f, 300f), label = "pad")

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - sheet.value) * 0.9f }.background(Color(0x6B0C2A40)).clickable { close() })
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.93f)
                .graphicsLayer { translationY = sheet.value * size.height + drag.value }
                .glass(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), strong = true)
        ) {
            // drag handle
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(30.dp).zIndexTop()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { scope.launch { if (drag.value > 140.dp.toPx()) close() else drag.animateTo(0f, spring(0.7f, 300f)) } },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } },
                        ) { change, dy -> change.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Ink.Deep.copy(alpha = 0.32f))) }

            Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCircleButton(Icons.Rounded.Close, "Close gallery", size = 44.dp) { close() }
                    Box(Modifier.weight(1f).height(44.dp).glass(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Text(if (selectedIds.isEmpty()) "Gallery" else "${selectedIds.size} selected", color = Ink.Dark, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                    Spacer(Modifier.size(44.dp))
                }
                LazyRow(Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(albums) { _, a ->
                        val on = a == album
                        val s by animateFloatAsState(if (on) 1.04f else 1f, spring(0.5f, 400f), label = "chip")
                        Text(
                            a, color = if (on) Color.White else Ink.Deep, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(50))
                                .background(if (on) p.bubble1 else Color.White.copy(alpha = 0.7f)).clickable { album = a }.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize().padding(top = 8.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = gridBottom),
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (album == "All") {
                        item(key = "camera") {
                            Column(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF0C4A6E), p.bubble1))).clickable { close(onCamera) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, "Camera", tint = Color.White, modifier = Modifier.size(28.dp))
                                Text("Camera", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    itemsIndexed(visible, key = { _, it -> it.id }) { index, it ->
                        val number = selectedIds.indexOf(it.id) + 1
                        GalleryTile(
                            it, index, album, number, imageContent,
                            onTap = { toggle(it) },
                            onPreview = { preview = visible to index },
                        )
                    }
                }
            }

            AnimatedVisibility(
                selectedIds.isNotEmpty(), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            ) {
                Column(Modifier.glass(RoundedCornerShape(28.dp), strong = true).padding(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                        itemsIndexed(selectedItems, key = { _, it -> it.id }) { _, it ->
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White, RoundedCornerShape(14.dp)).clickable { selectedIds.remove(it.id) }) {
                                imageContent(it, Modifier.fillMaxSize())
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).bounceClick { preview = selectedItems to 0 },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.OpenInFull, "Preview selected", tint = Ink.Deep, modifier = Modifier.size(20.dp)) }
                        Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                caption, { caption = it }, singleLine = true, cursorBrush = SolidColor(p.bubble1),
                                textStyle = TextStyle(color = Ink.Dark, fontSize = 15.sp), modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner -> if (caption.isEmpty()) Text("Add a caption…", color = Ink.Mid, fontSize = 15.sp); inner() },
                            )
                        }
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bubble2, p.bubble1))).bounceClick { close { onSend(selectedItems, caption.trim()) } },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Text(
                                selectedIds.size.toString(), color = p.bubble1, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).clip(CircleShape).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(toast != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Text(toast ?: "", color = Ink.Dark, fontWeight = FontWeight.Bold, modifier = Modifier.glass(RoundedCornerShape(50), strong = true).padding(horizontal = 18.dp, vertical = 10.dp))
        }

        AnimatedVisibility(preview != null, enter = fadeIn(), exit = fadeOut()) {
            preview?.let { (list, start) ->
                GalleryPreview(
                    list = list, start = start, selectedIds = selectedIds, imageContent = imageContent,
                    onToggle = { toggle(it) }, onClose = { preview = null },
                )
            }
        }
    }
}

private fun Modifier.zIndexTop() = this.graphicsLayer { }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    item: GalleryItem, index: Int, album: String, number: Int, imageContent: GallerySlot,
    onTap: () -> Unit, onPreview: () -> Unit,
) {
    val p = LocalChatPalette.current
    val haptic = LocalHapticFeedback.current
    val selected = number > 0
    var shown by remember(album, item.id) { mutableStateOf(false) }
    LaunchedEffect(album, item.id) { delay((index * 30L).coerceAtMost(600L)); shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, spring(0.8f, 300f), label = "enter")
    val innerScale by animateFloatAsState(if (selected) 0.84f else 1f, spring(0.5f, 380f), label = "sel")
    val radius by animateDpAsState(if (selected) 18.dp else 12.dp, spring(0.7f, 400f), label = "rad")
    Box(
        Modifier.aspectRatio(1f)
            .graphicsLayer { alpha = enter; scaleX = 0.82f + 0.18f * enter; scaleY = scaleX; translationY = (1f - enter) * 26.dp.toPx() }
            .clip(RoundedCornerShape(12.dp)).background(if (selected) p.selection else Color.White.copy(alpha = 0.35f))
            .combinedClickable(onClick = onTap, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPreview() }),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = innerScale; scaleY = innerScale }.clip(RoundedCornerShape(radius))) {
            imageContent(item, Modifier.fillMaxSize())
        }
        // size chip (top-left)
        Text(
            formatSize(item.sizeBytes), color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x9E0C2A40)).padding(horizontal = 7.dp, vertical = 2.dp),
        )
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x9E0C2A40)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(formatClock(item.durationSec), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(Color(0x800C2A40)).clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.OpenInFull, "Preview", tint = Color.White, modifier = Modifier.size(13.dp)) }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            if (selected) {
                val pop by animateFloatAsState(1f, spring(0.4f, 500f), label = "pop")
                Text(
                    number.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop }.defaultMinSize(24.dp, 24.dp).clip(CircleShape).background(p.bubble1).border(2.dp, Color.White, CircleShape).padding(horizontal = 5.dp),
                )
            } else Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0x2E0C2A40)).border(2.dp, Color.White.copy(alpha = 0.95f), CircleShape))
        }
    }
}

/** Full-screen preview of the picked / tapped items: swipe, pinch + double-tap zoom, tap hides controls, select toggle. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPreview(
    list: List<GalleryItem>, start: Int, selectedIds: List<String>, imageContent: GallerySlot,
    onToggle: (GalleryItem) -> Unit, onClose: () -> Unit,
) {
    val p = LocalChatPalette.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, list.lastIndex)) { list.size }
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    val current = list[pager.currentPage]
    val number = selectedIds.indexOf(current.id) + 1
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF111B2E)))).clickable(enabled = false) {}) {
        HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 16.dp, userScrollEnabled = !zoomed) { page ->
            ZoomableBox(Modifier.fillMaxSize(), onZoomChange = { if (page == pager.currentPage) zoomed = it }, onTap = { chrome = !chrome }) {
                Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 78.dp, bottom = 176.dp)) {
                    imageContent(list[page], Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
                    if (list[page].isVideo) Box(Modifier.align(Alignment.Center).size(76.dp).glass(CircleShape, dark = true), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(Icons.Rounded.ArrowBack, "Back", dark = true, tint = Color.White, onClick = onClose)
                Box(Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50), dark = true), contentAlignment = Alignment.Center) {
                    Text("${pager.currentPage + 1} / ${list.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Box(Modifier.size(48.dp).glass(CircleShape, dark = true).bounceClick { onToggle(current) }, contentAlignment = Alignment.Center) {
                    if (number > 0) Text(number.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.defaultMinSize(30.dp, 30.dp).clip(CircleShape).background(p.bubble1).border(2.dp, Color.White, CircleShape).padding(horizontal = 6.dp))
                    else Box(Modifier.size(30.dp).border(2.5.dp, Color.White.copy(alpha = 0.95f), CircleShape))
                }
            }
        }
        AnimatedVisibility(chrome && !zoomed, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(list) { i, it ->
                        val on = i == pager.currentPage
                        val s by animateFloatAsState(if (on) 1.08f else 1f, spring(0.5f, 400f), label = "t")
                        Box(Modifier.size(52.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (on) 1f else 0.55f }.clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(14.dp)).clickable { scope.launch { pager.animateScrollToPage(i) } }) {
                            imageContent(it, Modifier.fillMaxSize())
                            if (it.id in selectedIds) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp))
                        }
                    }
                }
                Box(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(p.bubble2, p.bubble1))).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("Done" + if (selectedIds.isNotEmpty()) " · ${selectedIds.size} selected" else "", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            }
        }
    }
}
