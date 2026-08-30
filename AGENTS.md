# Project Guidelines & Rules

## 1. Step-by-Step Code Generation
- Make precise, modular, and complete changes.
- Do not write incomplete code, placeholder methods, or experimental APIs (e.g., experimental Kotlin foundation APIs) without proper annotations or clean stable implementations.
- Ensure all brackets, syntax, missing imports, and variables are fully resolved before saving.

## 2. Production-Ready Logic (No Fake/Mock Data)
- Never inject mock users, automated fake responses, or auto-answering call logic.
- Always query and validate actual Firebase Firestore collections/documents before treating a user or phone number as active or registered.

## 3. Error Prevention & Type Safety
- Ensure native Android configurations (`build.gradle.kts`, `AndroidManifest.xml`) use standard production/debug signing without missing local keystores or missing `google-services.json` paths.
- Always initialize native services (Firebase, ZEGOCloud) inside application startup safely with error handling to avoid startup crashes.
- Verify camera, microphone, and internet permissions at runtime before executing call/media functions.

## 4. Self-Verification Before Push
- Double-check that all updated Kotlin files compile cleanly without any syntax or build errors (e.g., verify `ChatListScreen` and main build tasks).
- Keep code backward-compatible and stable.

## 5. Non-Destructive Code & UI Preservation (Injection Only)
- **Preserve Existing Code & Layouts**: Never rewrite or modify existing core layout wrappers, custom styling, colors, or existing input/output functionalities unless explicitly requested.
- **Additive / Injection Only**: Treat new feature requests strictly as incremental additions or injections into their designated places.
- **No Unsolicited Redesigns**: Do not redesign existing components, alter layout positions, or remove existing features during updates.

## 6. Critical Core Features Preservation Rule (MANDATORY)
- **Protected Core Modules**:
  1. Real-time Messaging (Text, Photos, Videos, Documents, File Sharing).
  2. Voice Messages / Audio Recordings & Playback.
  3. Real-time Audio & Video Calling (ZEGOCloud / Supabase Call Service).
  4. Real-time Typing Indicator & Online Presence (real-time typing signals, debounce, and active indicators).
  5. Real-time Channel Architecture: Independent channel separation (`messages-user-$uid`, `calls-user-$uid`, `statuses-user-$uid`) must NEVER be merged or altered without explicit user request.
- **Pre-Update Impact Verification & Consent**:
  - Never modify, refactor, or touch any of the above core pipelines during unrelated updates or feature additions.
  - If a requested update could in any way impact, modify, or break any part of these core communication/media pipelines, you MUST explain the exact technical reason to the user and obtain explicit permission BEFORE making any code changes.

## 7. Protected Feature: Online Presence / Last Seen (DO NOT BREAK)

The following presence system is now fully working correctly and must be treated with the same protection level as messaging, calling, and media sharing. Any future task — even unrelated ones — must NOT modify, refactor, or remove any of the following unless a task explicitly and specifically asks to change presence behavior:

- `presenceScope` (independent CoroutineScope in FirebaseChatRepository.kt) — must remain fully separate from `repositoryScope`, `callScope`, `messageSyncJob`, and `callSyncJob`.
- `startRealtimePresenceSync()` in FirebaseChatRepository.kt — including its idempotency guard (checks `presenceJob?.isActive == true && currentPresenceUserId == userId` before relaunching).
- `presenceHeartbeatJob` — runs every 15 seconds, calls BOTH `socialService.retrackPresence(userId, userName, avatarUrl)` AND `socialService.updateLastSeenTimestamp(userId, force = true)`. Do not remove either call or change the interval without explicit instruction.
- `connectPresence()` in SupabaseSocialService.kt — including the check that skips calling `realtime.connect()` if `realtime.status.value` is already CONNECTED or CONNECTING.
- `retrackPresence()` in SupabaseSocialService.kt — reuses the existing `presenceChannel` instance and re-calls `.track(payload)`; must not be removed or merged into a channel-recreation flow.
- The `DisposableEffect(lifecycleOwner, currentUserProfile?.uid)` block in MainScreen.kt that manages presence lifecycle (ON_START/ON_RESUME connects, ON_PAUSE/ON_STOP disconnects) — including its initial call on entry.
- The `profiles` table's Realtime publication setting on Supabase (`ALTER PUBLICATION supabase_realtime ADD TABLE profiles` + `REPLICA IDENTITY FULL`) — do not assume this is unnecessary or suggest removing it.

**Current correct behavior (for reference, to verify nothing has regressed):**
- Opening the app shows the user as "Online"/"Active now" to their contacts immediately.
- Staying in the foreground keeps the user "Online" continuously (no flipping back to "Last seen" after 10-20 seconds).
- Backgrounding the app immediately and correctly shows "Last seen [time]" to contacts.
- Reopening the app immediately shows "Online" again.

If any future change (even one seemingly unrelated to presence) touches FirebaseChatRepository.kt, SupabaseSocialService.kt, or MainScreen.kt, explicitly verify presence still behaves as described above before considering the task complete.

