package com.talkly.designui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** One selectable chat theme (the same 6 themes as the design prototype). */
data class ChatPalette(
    val name: String,
    val bgTop: Color, val bgMid: Color, val bgBottom: Color,
    val bubble1: Color, val bubble2: Color,
    val blobA: Color, val blobB: Color,
    val selection: Color,
)

val ChatPalettes = listOf(
    ChatPalette("Sky Blue", Color(0xFF9ED8F8), Color(0xFFC9ECFC), Color(0xFFEAF7FE), Color(0xFF0369A1), Color(0xFF0A78B5), Color(0xFF5CC0F2), Color(0xFF7FCDF6), Color(0x380369A1)),
    ChatPalette("Ocean", Color(0xFF99E0DA), Color(0xFFC6F1EC), Color(0xFFE8FAF8), Color(0xFF0F766E), Color(0xFF12857A), Color(0xFF4FD1C5), Color(0xFF7FE0D6), Color(0x380F766E)),
    ChatPalette("Lavender", Color(0xFFD1C4F7), Color(0xFFE6DEFB), Color(0xFFF5F1FE), Color(0xFF6D28D9), Color(0xFF7C3AED), Color(0xFFB79CF5), Color(0xFFC9B6F8), Color(0x336D28D9)),
    ChatPalette("Rose", Color(0xFFF9C0D2), Color(0xFFFBDCE6), Color(0xFFFEF1F5), Color(0xFFBE185D), Color(0xFFDB2777), Color(0xFFF58BB0), Color(0xFFF9A8C4), Color(0x33BE185D)),
    ChatPalette("Sunset", Color(0xFFFDCB9E), Color(0xFFFDE2C7), Color(0xFFFFF3E8), Color(0xFFB93A0C), Color(0xFFD4470F), Color(0xFFFBA05A), Color(0xFFFDB98A), Color(0x33B93A0C)),
    ChatPalette("Forest", Color(0xFFB7E8C4), Color(0xFFD6F3DD), Color(0xFFEEFAF1), Color(0xFF166534), Color(0xFF15803D), Color(0xFF6FD08A), Color(0xFF97DDAA), Color(0x33166534)),
)

object Ink {
    val Dark = Color(0xFF0C2A40)
    val Deep = Color(0xFF0C4A6E)
    val Mid = Color(0xFF3D5F78)
    val Danger = Color(0xFFB3261E)
}

val LocalChatPalette = compositionLocalOf { ChatPalettes[0] }

@Composable
fun TalklyTheme(palette: ChatPalette = ChatPalettes[0], content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChatPalette provides palette) {
        MaterialTheme(colorScheme = lightColorScheme(primary = palette.bubble1), content = content)
    }
}
