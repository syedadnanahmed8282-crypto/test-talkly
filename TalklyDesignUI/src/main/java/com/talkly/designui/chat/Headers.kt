package com.talkly.designui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette

@Composable
fun Avatar(initials: String, size: Dp, modifier: Modifier = Modifier, borderWidth: Dp = 2.dp) {
    val p = LocalChatPalette.current
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(p.blobA, p.bubble1))).border(borderWidth, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp) }
}

/**
 * Header = 3 floating glass pieces: back button, profile pill, call/dropdown bar.
 * First tap on the call icon reveals the video icon (the profile pill shrinks); the second tap places the call.
 */
@Composable
fun ChatHeader(
    name: String, subtitle: String, muted: Boolean,
    actionsExpanded: Boolean, menuOpen: Boolean,
    onBack: () -> Unit, onCallTap: () -> Unit, onVideoTap: () -> Unit, onMenuTap: () -> Unit, onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalChatPalette.current
    val rot by animateFloatAsState(if (menuOpen) 180f else 0f, spring(0.6f, 300f), label = "chev")
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassCircleButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
        Row(
            Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50)).clickable(onClick = onProfile).padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(name.take(2).uppercase(), 38.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (muted) Icon(Icons.Rounded.NotificationsOff, null, tint = Ink.Mid, modifier = Modifier.size(14.dp))
                }
                Text(subtitle, color = Ink.Mid, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.height(48.dp).glass(RoundedCornerShape(50)).padding(horizontal = 4.dp).animateContentSize(spring(0.7f, 300f)), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                actionsExpanded,
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, 400f), initialScale = 0.3f) + expandHorizontally() + fadeIn(),
                exit = scaleOut() + shrinkHorizontally() + fadeOut(),
            ) {
                Box(
                    Modifier.padding(end = 2.dp).size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.bubble2, p.bubble1))).bounceClick(onClick = onVideoTap),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Videocam, "Video call", tint = Color.White) }
            }
            Box(Modifier.size(40.dp).bounceClick(onClick = onCallTap), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Call, "Call", tint = Ink.Deep) }
            Box(Modifier.size(32.dp, 40.dp).bounceClick(onClick = onMenuTap), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.KeyboardArrowDown, "More", tint = Ink.Deep, modifier = Modifier.graphicsLayer { rotationZ = rot })
            }
        }
    }
}

class HeaderMenuActions(
    val onProfile: () -> Unit = {}, val onSearch: () -> Unit = {}, val onMute: () -> Unit = {},
    val onTheme: () -> Unit = {}, val onMedia: () -> Unit = {}, val onClear: () -> Unit = {}, val onBlock: () -> Unit = {},
)

@Composable
private fun MenuItem(icon: ImageVector, label: String, color: Color = Ink.Dark, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (color == Ink.Dark) Ink.Deep else color, modifier = Modifier.size(20.dp))
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun HeaderMenu(visible: Boolean, muted: Boolean, blocked: Boolean, actions: HeaderMenuActions, modifier: Modifier = Modifier) {
    val origin = TransformOrigin(1f, 0f)
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = scaleIn(spring(0.6f, 320f), initialScale = 0.85f, transformOrigin = origin) + fadeIn(),
        exit = scaleOut(targetScale = 0.85f, transformOrigin = origin) + fadeOut(),
    ) {
        Column(Modifier.width(232.dp).glass(RoundedCornerShape(24.dp), strong = true).padding(6.dp)) {
            MenuItem(Icons.Rounded.Person, "View profile", onClick = actions.onProfile)
            MenuItem(Icons.Rounded.Search, "Search", onClick = actions.onSearch)
            MenuItem(Icons.Rounded.NotificationsOff, if (muted) "Unmute notifications" else "Mute notifications", onClick = actions.onMute)
            MenuItem(Icons.Rounded.Palette, "Chat theme", onClick = actions.onTheme)
            MenuItem(Icons.Rounded.Image, "Media, links & docs", onClick = actions.onMedia)
            Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Ink.Deep.copy(alpha = 0.15f)))
            MenuItem(Icons.Rounded.DeleteSweep, "Clear chat", Ink.Danger, actions.onClear)
            MenuItem(Icons.Rounded.Block, if (blocked) "Unblock" else "Block", Ink.Danger, actions.onBlock)
        }
    }
}

/** Replaces the header while messages are selected: close - "N selected" - action bar. */
@Composable
fun SelectionBar(
    count: Int, canReply: Boolean, allStarred: Boolean,
    onClose: () -> Unit, onReply: () -> Unit, onStar: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit, onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassCircleButton(Icons.Rounded.Close, "Cancel selection", onClick = onClose)
        Box(Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50)).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text("$count selected", color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Row(Modifier.height(48.dp).glass(RoundedCornerShape(50)).padding(horizontal = 4.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            if (canReply) BarIcon(Icons.Rounded.Reply, "Reply", Ink.Deep, onReply)
            BarIcon(if (allStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Star", Ink.Deep, onStar)
            BarIcon(Icons.Rounded.ContentCopy, "Copy", Ink.Deep, onCopy)
            BarIcon(Icons.Rounded.Delete, "Delete", Ink.Danger, onDelete)
            Box(Modifier.size(30.dp, 40.dp).bounceClick(onClick = onMore), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.KeyboardArrowDown, "More", tint = Ink.Deep) }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp, 40.dp).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint) }
}

@Composable
fun SelectionMenu(visible: Boolean, canEdit: Boolean, canPin: Boolean, pinLabel: String, onEdit: () -> Unit, onForward: () -> Unit, onPin: () -> Unit, modifier: Modifier = Modifier) {
    val origin = TransformOrigin(1f, 0f)
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = scaleIn(spring(0.6f, 320f), initialScale = 0.85f, transformOrigin = origin) + fadeIn(),
        exit = scaleOut(targetScale = 0.85f, transformOrigin = origin) + fadeOut(),
    ) {
        Column(Modifier.width(208.dp).glass(RoundedCornerShape(24.dp), strong = true).padding(6.dp)) {
            if (canEdit) MenuItem(Icons.Rounded.Edit, "Edit message", onClick = onEdit)
            MenuItem(Icons.Rounded.Forward, "Forward", onClick = onForward)
            if (canPin) MenuItem(Icons.Rounded.PushPin, pinLabel, onClick = onPin)
        }
    }
}

@Composable
fun SearchHeader(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalChatPalette.current
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassCircleButton(Icons.Rounded.ArrowBack, "Close search", onClick = onClose)
        Row(
            Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50)).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = Ink.Deep, modifier = Modifier.size(20.dp))
            BasicTextField(
                query, onQuery, singleLine = true, cursorBrush = SolidColor(p.bubble1),
                textStyle = TextStyle(color = Ink.Dark, fontSize = 16.sp), modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search messages", color = Ink.Mid, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).bounceClick { onQuery("") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Clear", tint = Ink.Deep, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun PinnedBanner(text: String, onUnpin: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalChatPalette.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).height(46.dp).glass(RoundedCornerShape(50), strong = true).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.PushPin, null, tint = p.bubble1, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Pinned message", color = p.bubble1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Ink.Dark, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).bounceClick(onClick = onUnpin), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Unpin", tint = Ink.Deep, modifier = Modifier.size(18.dp)) }
    }
}

/** Reply / Edit preview above the composer. */
@Composable
fun ContextBar(icon: ImageVector, title: String, text: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalChatPalette.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(52.dp).glass(RoundedCornerShape(20.dp), strong = true).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(p.bubble1))
        Icon(icon, null, tint = p.bubble1, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.bubble1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Ink.Dark, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).bounceClick(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close", tint = Ink.Deep, modifier = Modifier.size(18.dp)) }
    }
}
