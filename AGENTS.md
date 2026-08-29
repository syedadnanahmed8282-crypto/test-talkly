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
