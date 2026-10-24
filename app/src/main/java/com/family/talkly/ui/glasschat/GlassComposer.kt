package com.family.talkly.ui.glasschat

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** Floating glass composer: [emoji] [input + attach] [mic -> send] */
@Composable
fun GlassComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttachClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    replyTo: UiReply? = null,
    onDismissReply: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(hazeState, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(replyTo.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.SkyDeep)
                    Text(replyTo.preview, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = GlassColors.Ink)
                }
                if (onDismissReply != null) {
                    IconButton(onClick = onDismissReply, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, "Cancel reply", tint = GlassColors.Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
        GlassCircleButton(hazeState, onClick = { /* emoji picker */ }) {
            Icon(Icons.Rounded.EmojiEmotions, "Emoji", tint = GlassColors.SkyDeep)
        }

        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .glass(hazeState, RoundedCornerShape(24.dp))
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = GlassColors.Ink),
                maxLines = 5,
                cursorBrush = SolidColor(GlassColors.SkyDeep),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Message", color = GlassColors.Muted.copy(alpha = 0.85f), fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Rounded.AttachFile, "Attach", tint = GlassColors.SkyDeep)
            }
        }

        val hasText = value.isNotBlank()
        Box(
            Modifier
                .size(48.dp)
                .shadow(10.dp, CircleShape, ambientColor = GlassColors.Sky.copy(0.4f), spotColor = GlassColors.Sky.copy(0.5f))
                .clip(CircleShape)
                .background(SkyBrush)
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable { if (hasText) onSend() else onVoice() },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = hasText, label = "micSend") { send ->
                Icon(
                    if (send) Icons.Rounded.ArrowUpward else Icons.Rounded.Mic,
                    if (send) "Send" else "Record voice",
                    tint = Color.White,
                )
            }
        }
    }
}
}
