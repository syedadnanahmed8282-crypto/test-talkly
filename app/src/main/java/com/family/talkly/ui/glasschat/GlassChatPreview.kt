package com.family.talkly.ui.glasschat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

/** Sample data so you can see the design in Android Studio's preview pane. Safe to delete. */
private val sample = listOf(
    DateItem("Yesterday"),
    MessageItem("1", false, "9:02 AM", MessageContent.Text("Good morning!")),
    DateItem("Today"),
    MessageItem("2", false, "10:51 PM", MessageContent.Text("Hey! Did you see the new design?")),
    MessageItem("3", true, "10:52 PM", MessageContent.Text("Yes! Looks amazing, love the glass look"), DeliveryStatus.Seen),
    MessageItem("4", false, "10:52 PM", MessageContent.Image(null)),
    MessageItem("5", true, "10:53 PM", MessageContent.Voice("0:14", 0.4f), DeliveryStatus.Seen),
    MessageItem("6", false, "10:56 PM", MessageContent.Video(null, "0:24")),
    MessageItem(
        "7", true, "10:57 PM", MessageContent.Text("Haha that is so cool!"), DeliveryStatus.Delivered,
        reaction = UiReaction("❤️", 2), replyTo = UiReply("Rafi Khan", "Video · 0:24"),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GlassChatPreview() {
    var text by remember { mutableStateOf("") }
    GlassChatScreen(
        header = ChatHeaderState("Rafi Khan", "last seen recently"),
        items = sample, input = text, onInputChange = { text = it },
        actions = ChatActions(onBack = {}, onAudioCall = {}, onVideoCall = {}, onSend = {}),
        unreadCount = 3,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GlassChatEmptyPreview() {
    GlassChatScreen(
        header = ChatHeaderState("Rafi Khan", "online"),
        items = emptyList(), input = "", onInputChange = {},
        actions = ChatActions(onBack = {}, onAudioCall = {}, onVideoCall = {}, onSend = {}),
    )
}
