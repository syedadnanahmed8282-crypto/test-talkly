package com.example.app.ui.glasschat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun bubbleShape(mine: Boolean) = RoundedCornerShape(
    topStart = 18.dp, topEnd = 18.dp,
    bottomStart = if (mine) 18.dp else 6.dp,
    bottomEnd = if (mine) 6.dp else 18.dp,
)

@Composable
fun DateChip(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = GlassColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun MessageRow(
    item: MessageItem,
    loader: MediaLoader,
    onLongPress: ((MessageItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (item.isMine) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .then(
                    if (onLongPress != null)
                        Modifier.pointerInput(item.id) {
                            detectTapGestures(onLongPress = { onLongPress(item) })
                        }
                    else Modifier
                ),
        ) {
            when (val c = item.content) {
                is MessageContent.Text -> BubbleSurface(item.isMine) {
                    item.replyTo?.let { ReplyQuote(it, item.isMine); Spacer(Modifier.height(4.dp)) }
                    Text(
                        c.text,
                        color = if (item.isMine) Color.White else GlassColors.Ink,
                        fontSize = 16.sp,
                    )
                    MetaRow(item, if (item.isMine) Color.White.copy(0.88f) else GlassColors.Muted)
                }
                is MessageContent.Image -> MediaBubble(item, loader, c.url, isVideo = false, duration = null)
                is MessageContent.Video -> MediaBubble(item, loader, c.thumbUrl, isVideo = true, duration = c.duration)
                is MessageContent.Voice -> VoiceBubble(item, c)
            }
            item.reaction?.let {
                ReactionChip(it, Modifier.padding(top = 2.dp, start = 10.dp, end = 10.dp))
            }
        }
    }
}

@Composable
private fun BubbleSurface(
    mine: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = bubbleShape(mine)
    Column(
        modifier = modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .shadow(3.dp, shape, clip = false, ambientColor = GlassColors.Sky.copy(0.2f), spotColor = GlassColors.Sky.copy(0.25f))
            .clip(shape)
            .then(if (mine) Modifier.background(SkyBrush) else Modifier.background(Color.White))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End,
        content = content,
    )
}

@Composable
private fun ReplyQuote(reply: UiReply, mine: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(if (mine) Color.White.copy(0.22f) else GlassColors.SkyTint)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(if (mine) Color.White else GlassColors.SkyDeep)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(reply.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (mine) Color.White else GlassColors.SkyDeep)
            Text(reply.preview, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (mine) Color.White.copy(0.9f) else GlassColors.Muted)
        }
    }
}

/** time + delivery status (sending / sent / delivered / seen) */
@Composable
fun MetaRow(item: MessageItem, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(item.time, color = tint, fontSize = 11.sp)
        if (item.isMine && item.status != null) {
            val icon = when (item.status) {
                DeliveryStatus.Sending -> Icons.Rounded.Schedule
                DeliveryStatus.Sent -> Icons.Rounded.Done
                else -> Icons.Rounded.DoneAll
            }
            Icon(
                icon, item.status.name, tint = Color.White,
                modifier = Modifier.size(14.dp).alpha(if (item.status == DeliveryStatus.Seen) 1f else 0.65f),
            )
        }
    }
}

@Composable
private fun MediaBubble(
    item: MessageItem,
    loader: MediaLoader,
    url: String?,
    isVideo: Boolean,
    duration: String?,
) {
    val shape = bubbleShape(item.isMine)
    Box(
        Modifier
            .size(250.dp, 172.dp)
            .shadow(3.dp, shape, clip = false)
            .clip(shape)
    ) {
        loader(url, Modifier.fillMaxSize())
        if (isVideo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.3f))
                    .border(1.dp, Color.White.copy(0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            duration?.let {
                Text(
                    it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassColors.Ink.copy(0.35f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        MetaRow(
            item, Color.White,
            Modifier
                .align(Alignment.BottomEnd).padding(8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlassColors.Ink.copy(0.35f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private val waveHeights = listOf(8, 14, 10, 18, 24, 16, 12, 20, 26, 18, 10, 14, 22, 28, 20, 12, 8, 16, 22, 14, 10, 18, 12, 8, 14, 10)

@Composable
private fun VoiceBubble(item: MessageItem, c: MessageContent.Voice) {
    val mine = item.isMine
    BubbleSurface(mine) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .then(if (mine) Modifier.background(Color.White.copy(0.28f)) else Modifier.background(SkyBrush)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp)) }
            Column {
                val played = (waveHeights.size * c.progress).toInt().coerceAtLeast(10)
                Canvas(Modifier.width(128.dp).height(30.dp)) {
                    val w = 3.dp.toPx(); val gap = 2.dp.toPx()
                    waveHeights.forEachIndexed { i, h ->
                        val hh = h.dp.toPx()
                        val col = if (mine) Color.White.copy(if (i < played) 1f else 0.5f)
                        else (if (i < played) GlassColors.SkyDeep else GlassColors.Sky.copy(0.45f))
                        drawRoundRect(col, Offset(i * (w + gap), (size.height - hh) / 2), Size(w, hh), CornerRadius(w / 2))
                    }
                }
                Row(Modifier.width(128.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(c.duration, color = if (mine) Color.White.copy(0.9f) else GlassColors.Muted, fontSize = 11.sp)
                    MetaRow(item, if (mine) Color.White.copy(0.88f) else GlassColors.Muted)
                }
            }
        }
    }
}

@Composable
fun ReactionChip(r: UiReaction, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, GlassColors.SkyTint, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(r.emoji, fontSize = 13.sp)
        Text(r.count.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.SkyDeep)
    }
}

@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Row(
            Modifier
                .shadow(3.dp, bubbleShape(false), clip = false)
                .clip(bubbleShape(false))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val a by t.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(GlassColors.SkyDeep))
            }
        }
    }
}
