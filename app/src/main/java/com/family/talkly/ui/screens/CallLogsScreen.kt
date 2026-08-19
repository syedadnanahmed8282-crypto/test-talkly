package com.family.talkly.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember

// Talkly Signature Colors
private val BackgroundDark = Color(0xFF080B10)
private val SurfaceCard = Color(0xFF18212B)
private val SurfaceElevated = Color(0xFF202B36)
private val ElectricCyan = Color(0xFF22D3EE)
private val DeepAqua = Color(0xFF0EA5A4)
private val MintAccent = Color(0xFF5EEAD4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0BA)
private val TextMuted = Color(0xFF64748B)
private val DestructiveRed = Color(0xFFF43F5E)
private val BorderSubtle = Color(0xFF1E293B)
private val BorderElevated = Color(0xFF24303E)

@Composable
fun CallLogsScreen(
    callLogs: List<CallLog>,
    familyMembers: List<FamilyMember>,
    onStartCall: (FamilyMember, CallType) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Calls Dashboard Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Calls",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.6).sp
                    )
                    Text(
                        text = "Stay connected with voice & HD video",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // 2. Frequent / Quick Call Contacts Area (If contacts exist)
            if (familyMembers.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Quick Call",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(familyMembers.take(8), key = { "quick_${it.id}" }) { member ->
                                QuickCallContactCard(
                                    member = member,
                                    onStartAudio = { onStartCall(member, CallType.AUDIO) },
                                    onStartVideo = { onStartCall(member, CallType.VIDEO) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Call History Timeline
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent History",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (callLogs.isNotEmpty()) {
                        Text(
                            text = "${callLogs.size} calls",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (callLogs.isEmpty()) {
                item {
                    TalklyCallsEmptyState()
                }
            } else {
                items(callLogs, key = { it.id }) { log ->
                    val member = familyMembers.firstOrNull { it.id == log.memberId }
                    TalklyCallHistoryCard(
                        log = log,
                        member = member,
                        onAudioCall = {
                            if (member != null) onStartCall(member, CallType.AUDIO)
                        },
                        onVideoCall = {
                            if (member != null) onStartCall(member, CallType.VIDEO)
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// QUICK CALL CONTACT CARD (Horizontal Carousel)
// ==========================================
@Composable
private fun QuickCallContactCard(
    member: FamilyMember,
    onStartAudio: () -> Unit,
    onStartVideo: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(130.dp)
            .height(130.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (member.avatarUrl?.isNotBlank() == true) {
                    val mediaModel = remember(member.avatarUrl) {
                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.avatarUrl)
                    }
                    AsyncImage(
                        model = mediaModel,
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = member.name.take(2).uppercase(),
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = member.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Audio & Video action triggers
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.15f))
                        .clickable { onStartAudio() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Audio call",
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MintAccent.copy(alpha = 0.15f))
                        .clickable { onStartVideo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video call",
                        tint = MintAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// TALKLY CALL HISTORY CARD (Timeline Item)
// ==========================================
@Composable
private fun TalklyCallHistoryCard(
    log: CallLog,
    member: FamilyMember?,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "callCardScale"
    )

    val (directionIcon, directionColor, directionLabel) = when (log.direction) {
        CallDirection.INCOMING -> Triple(Icons.Default.CallReceived, ElectricCyan, "Incoming")
        CallDirection.OUTGOING -> Triple(Icons.Default.CallMade, MintAccent, "Outgoing")
        CallDirection.MISSED -> Triple(Icons.Default.CallMissed, DestructiveRed, "Missed")
    }

    val isVideo = log.callType == CallType.VIDEO

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .scale(cardScale),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (log.direction == CallDirection.MISSED) DestructiveRed.copy(alpha = 0.25f) else BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction Status Badge + Avatar
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderElevated, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (member?.avatarUrl?.isNotBlank() == true) {
                        val mediaModel = remember(member.avatarUrl) {
                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.avatarUrl)
                        }
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = log.memberName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = log.memberName.take(2).uppercase(),
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Call type indicator (Audio or Video mini dot)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(directionColor)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = Color(0xFF040E14),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.memberName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isVideo) "Video call" else "Voice call",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (log.direction == CallDirection.MISSED) DestructiveRed else TextSecondary
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = log.formattedTime,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    if (log.durationSeconds > 0) {
                        Text(
                            text = " (${log.formattedDuration})",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Quick Redial Trigger
            IconButton(
                onClick = if (isVideo) onVideoCall else onAudioCall,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Redial",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================
// CALLS EMPTY STATE
// ==========================================
@Composable
private fun TalklyCallsEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.5.dp, BorderElevated, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "No calls yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your recent voice and HD video calls will appear here.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
