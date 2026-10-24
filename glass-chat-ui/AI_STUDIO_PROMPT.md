# Prompt to paste into Google AI Studio (together with the .kt files)

I am replacing the chat screen UI of my Android app (Kotlin + Jetpack Compose) with a new
liquid-glass design. The new UI files are attached (package `com.example.app.ui.glasschat`).

Do exactly this:

1. Add the dependencies from `dependencies-snippet.txt` to `app/build.gradle.kts`
   (Haze 1.6.10 + material-icons-extended). Keep minSdk as it is.
2. Copy all attached `.kt` files into my project, in a `ui/glasschat` package. Change the
   package line in each file to match my real package name.
3. In MainActivity (or wherever the window is set up) enable edge-to-edge
   (`enableEdgeToEdge()` or `WindowCompat.setDecorFitsSystemWindows(window, false)`).
4. Find the OLD chat screen composable (the one currently shown for a conversation) and replace
   its UI with `GlassChatScreen(...)`. Delete the old chat UI composables that are no longer used
   (old top bar, old message bubbles, old input bar). Do NOT delete anything that is still used elsewhere.
5. Create ONE mapper file (`ChatUiMapper.kt`) that converts my existing message and user models
   into `ChatItem` / `MessageItem` / `ChatHeaderState`:
   - text -> `MessageContent.Text`, image -> `MessageContent.Image(url)`,
     video -> `MessageContent.Video(thumbUrl, duration)`, voice -> `MessageContent.Voice(duration)`
   - call messages (missed / rejected / outgoing) can stay as text lines
   - group messages by day and insert `DateItem("Today")`, `DateItem("Yesterday")`, etc.
   - sent / delivered / seen -> `DeliveryStatus`
   - peer typing flag -> `ChatHeaderState.isTyping`
   - online / last seen text -> `ChatHeaderState.status`
6. Wire `ChatActions` to my existing ViewModel functions: send, audio call, video call, reply,
   copy, delete, react, attach, voice record. Use my existing call-start functions for
   `onAudioCall` / `onVideoCall`.
7. Pass `mediaLoader` using my existing image library (for example Coil `AsyncImage` with
   `ContentScale.Crop`) so avatars, photos and video thumbnails show real images.

HARD RULES:
- This is a UI-only change. Do NOT modify any repository, realtime, presence, call-signaling or
  `sendMessage()` logic (for example FirebaseChatRepository.kt, startRealtimeMessageSync,
  createMessagingRealtimeChannel). Only touch UI files and the one mapper.
- Do not rename or remove existing ViewModel functions; only call them.
- Keep everything compiling. If something in my project is unclear, tell me which file you need
  instead of guessing.
