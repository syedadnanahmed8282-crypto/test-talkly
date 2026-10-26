package com.family.talkly.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.talkly.designui.components.ArtBox
import com.talkly.designui.profile.ProfileActions
import com.talkly.designui.profile.ProfileScreen
import com.talkly.designui.profile.presetPalette

@Composable
fun ContactProfileDetailsDialog(
    member: FamilyMember,
    onDismiss: () -> Unit,
    onStartChat: (FamilyMember) -> Unit,
    onStartCall: (FamilyMember, CallType) -> Unit,
    onDeleteContact: ((String) -> Unit)? = null,
    isMutualContact: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ProfileScreen(
            name = member.name.ifBlank { "User" },
            lastSeen = if (member.isOnline) "Active now" else member.displayLastSeen.ifBlank { "offline" },
            about = member.phone.ifBlank { "Talkly Contact" },
            muted = false,
            blocked = false,
            mediaCount = 0,
            palette = presetPalette(member.id.hashCode().mod(8)),
            actions = ProfileActions(
                onBack = onDismiss,
                onAudio = {
                    onDismiss()
                    onStartCall(member, CallType.AUDIO)
                },
                onVideo = {
                    onDismiss()
                    onStartCall(member, CallType.VIDEO)
                },
                onSearch = {
                    onDismiss()
                    onStartChat(member)
                },
                onBlock = {
                    onDeleteContact?.invoke(member.id)
                    onDismiss()
                }
            ),
            cover = { modifier ->
                if (!member.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = member.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = modifier
                    )
                } else {
                    ArtBox(member.id.hashCode().mod(8), modifier)
                }
            }
        )
    }
}
