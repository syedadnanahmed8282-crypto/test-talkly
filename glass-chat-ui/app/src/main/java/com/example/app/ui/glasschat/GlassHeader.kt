package com.example.app.ui.glasschat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

/**
 * Floating glass header: [Back] [Profile tab] [Call tab]
 *
 * Call tab behaviour:
 *  - AudioOnly : only the audio-call icon is visible, profile tab is wide.
 *  - 1st tap   : call tab grows, video icon appears, profile tab shrinks (-> Expanded).
 *  - 2nd tap   : tapping audio/video icon starts the call.
 */
@Composable
fun GlassHeader(
    state: ChatHeaderState,
    callState: CallTabState,
    hazeState: HazeState,
    loader: MediaLoader,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onCallTabTap: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = callState == CallTabState.Expanded
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(hazeState, onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = GlassColors.SkyDeep)
        }

        // Profile tab (shrinks automatically because the call tab animates wider)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .glass(hazeState, CircleShape)
                .clickable { if (expanded) onCollapse() else onProfileClick() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatAvatar(state.avatarUrl, state.initials, 36.dp, loader)
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = state.name,
                    color = GlassColors.Ink,
                    fontSize = if (expanded) 14.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.isTyping) "typing…" else state.status,
                    color = if (state.isTyping) GlassColors.SkyDeep else GlassColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (state.isTyping) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Call tab
        val callWidth by animateDpAsState(
            targetValue = if (expanded) 100.dp else 56.dp,
            animationSpec = spring(dampingRatio = 0.8f),
            label = "callTabWidth",
        )
        val audioBg by animateColorAsState(
            targetValue = if (expanded) GlassColors.SkyDeep else Color.Transparent,
            label = "audioBg",
        )
        Row(
            modifier = Modifier
                .width(callWidth)
                .height(48.dp)
                .glass(hazeState, CircleShape)
                .clickable(enabled = !expanded) { onCallTabTap() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                        .clickable { onVideoCall() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Videocam, "Video call", tint = GlassColors.SkyDeep, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(audioBg)
                    .clickable(enabled = expanded) { onAudioCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Call, "Audio call",
                    tint = if (expanded) Color.White else GlassColors.SkyDeep,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
