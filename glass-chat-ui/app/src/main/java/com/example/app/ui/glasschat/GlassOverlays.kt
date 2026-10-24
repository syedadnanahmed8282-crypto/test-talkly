package com.example.app.ui.glasschat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

enum class AttachType(val label: String, val icon: ImageVector) {
    Gallery("Gallery", Icons.Rounded.Image),
    Camera("Camera", Icons.Rounded.CameraAlt),
    File("File", Icons.Rounded.InsertDriveFile),
    Location("Location", Icons.Rounded.LocationOn),
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun AttachMenu(
    visible: Boolean,
    hazeState: HazeState,
    onPick: (AttachType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, modifier = modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)).noRippleClickable(onDismiss)) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = scaleIn(), exit = scaleOut(),
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(end = 12.dp, bottom = 84.dp)
                        .width(220.dp)
                        .glass(hazeState, RoundedCornerShape(26.dp), tint = 0.4f)
                        .padding(8.dp)
                ) {
                    AttachType.values().forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(SkyBrush), contentAlignment = Alignment.Center) {
                                Icon(t.icon, t.label, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(t.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlassColors.Ink)
                        }
                    }
                }
            }
        }
    }
}

private val reactionEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

/** Long-press menu: blurred backdrop, reaction bar, selected message, Reply / Copy / Delete. */
@Composable
fun MessageMenu(
    item: MessageItem?,
    hazeState: HazeState,
    loader: MediaLoader,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    Box(
        modifier
            .fillMaxSize()
            .hazeEffect(state = hazeState) {
                blurRadius = 18.dp
                tints = listOf(HazeTint(Color.White.copy(alpha = 0.45f)))
                fallbackTint = HazeTint(Color.White.copy(alpha = 0.82f))
                noiseFactor = 0f
            }
            .noRippleClickable(onDismiss)
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = if (item.isMine) Alignment.End else Alignment.Start,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp)
                    .glass(hazeState, CircleShape, tint = 0.5f)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                reactionEmojis.forEach { e ->
                    Text(e, fontSize = 26.sp, modifier = Modifier.noRippleClickable { onReact(e) })
                }
            }
            MessageRow(item, loader, onLongPress = null)
            Column(
                Modifier.padding(horizontal = 16.dp).width(210.dp)
                    .glass(hazeState, RoundedCornerShape(22.dp), tint = 0.5f)
                    .padding(vertical = 6.dp)
            ) {
                MenuItem("Reply", Icons.AutoMirrored.Rounded.Reply, GlassColors.Ink, GlassColors.SkyDeep, onReply)
                MenuItem("Copy", Icons.Rounded.ContentCopy, GlassColors.Ink, GlassColors.SkyDeep, onCopy)
                MenuItem("Delete", Icons.Rounded.Delete, GlassColors.Danger, GlassColors.Danger, onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, textColor: Color, iconColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Icon(icon, label, tint = iconColor, modifier = Modifier.size(20.dp))
    }
}

/** "Jump to latest" floating glass button with an optional unread badge. */
@Composable
fun ScrollDownButton(
    visible: Boolean,
    unread: Int,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        Box {
            GlassCircleButton(hazeState, onClick, size = 44.dp) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Latest messages", tint = GlassColors.SkyDeep)
            }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp)
                        .clip(CircleShape).background(SkyBrush).border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    name: String,
    hazeState: HazeState,
    onSayHi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(290.dp).glass(hazeState, RoundedCornerShape(30.dp), tint = 0.4f).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("👋", fontSize = 56.sp)
        Text("No messages here yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.Ink)
        Text("Send a message to say hi to $name", fontSize = 14.sp, color = GlassColors.Muted, textAlign = TextAlign.Center)
        Box(
            Modifier.padding(top = 6.dp).clip(CircleShape).background(SkyBrush).clickable(onClick = onSayHi)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) { Text("Say hi 👋", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}
