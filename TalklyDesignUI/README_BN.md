# Talkly Design UI (Jetpack Compose)

আমাদের কথোপকথনের ডিজাইন প্রোটোটাইপের Kotlin / Jetpack Compose রূপ। এটা **শুধু UI**: কোনো Supabase, Cloudinary,
Agora, Room বা সিঙ্ক কোড এখানে নেই। আপনার ডেটা ও ইভেন্ট কলব্যাকের মাধ্যমে ঢোকে।

> **সৎ কথা:** এই কোড আমি কম্পাইল করে টেস্ট করতে পারিনি (এখানে Android কম্পাইলার নেই)। আশা করুন ২-১টা ছোট কম্পাইল এরর
> আসবে (ইম্পোর্ট বা ভার্সনের গোলমাল)। এরর টেক্সটটা AI-কে কপি করে দিন, সে ঠিক করে দেবে। ডেমো দিয়ে আগে সবকিছু চালিয়ে দেখুন।

## ফোল্ডারে কী আছে
| ফোল্ডার / ফাইল | কাজ |
|---|---|
| `theme/Theme.kt` | ৬টা চ্যাট থিম (Sky Blue, Ocean, Lavender, Rose, Sunset, Forest) |
| `components/Glass.kt` | গ্লাস ইফেক্ট, প্রেস-বাউন্স, ব্যাকগ্রাউন্ড, ওয়েভফর্ম |
| `chat/UiModels.kt` | `UiMessage`, `MediaItem` ইত্যাদি (আপনার মেসেজ এখানে ম্যাপ করবেন) |
| `chat/Bubbles.kt` | টেক্সট, ভয়েস, অডিও, ভিডিও, একাধিক মিডিয়া বাবল, ডাউনলোড ওভারলে, স্লাইড-রিপ্লাই, পপ-ইন অ্যানিমেশন |
| `chat/Composer.kt` | টাইপিং বার, হোল্ড-টু-রেকর্ড, স্লাইড-ক্যান্সেল (২৩০dp), লক (১৯০dp) |
| `chat/Pickers.kt` | ইমোজি পিকার, অ্যাটাচ শিট, রিঅ্যাকশন বার |
| `chat/Headers.kt` | হেডার, ড্রপডাউন, সিলেকশন বার, সার্চ, পিন ব্যানার |
| `chat/ChatScreen.kt` | মূল স্ক্রিন: সবকিছু জোড়া লাগানো + `ChatCallbacks` |
| `gallery/GalleryPicker.kt` | গ্যালারি পিকার + প্রিভিউ (জুমসহ) |
| `viewer/MediaViewer.kt` | ছবি/ভিডিও ভিউয়ার (পিঞ্চ, ডাবল-ট্যাপ জুম), অডিও প্লেয়ার |
| `call/CallScreen.kt` | ইনকামিং/আউটগোয়িং/অ্যাক্টিভ কল, মিনিমাইজ, স্প্লিট, নিজের ভিউ টেনে সরানো |
| `profile/ProfileScreen.kt` | কভার ছবির রঙে প্রোফাইল |
| `demo/DemoApp.kt` | নকল ডেটায় সবকিছু চালানোর ডেমো (`TalklyDemoActivity`) |

## ধাপ ১: ফাইল কপি
1. জিপ খুলুন।
2. `src/main/java/com/talkly/designui` ফোল্ডারটা পুরো আপনার প্রজেক্টের `app/src/main/java/com/talkly/designui` এ কপি করুন
   (প্যাকেজ নাম আলাদা হলে ফোল্ডার যেখানেই রাখুন, ফাইলের ভেতরের `package com.talkly.designui...` যেমন আছে তেমন থাকলেই চলবে)।

## ধাপ ২: dependencies
`dependencies.gradle.kts.txt` ফাইলের লাইনগুলো আপনার `app/build.gradle(.kts)` এ যোগ করুন। Sync করুন।
(আপনার অ্যাপ যদি আগে থেকে Compose ব্যবহার না করে, তাহলে `buildFeatures { compose = true }` আর কম্পাইলার প্লাগইন লাগবেই।)

## ধাপ ৩: ডেমো চালানো
1. `AndroidManifest.snippet.xml` এর `<activity>` অংশটা `<application>` এর ভেতরে বসান।
2. `TalklyDemoActivity` চালান। সব ফিচার নকল ডেটায় কাজ করবে।
3. (এজ-টু-এজ কীবোর্ড ঠিকমতো কাজ করার জন্য Activity তে `enableEdgeToEdge()` লাগে, ডেমোতে দেওয়া আছে।)

## ধাপ ৪: আপনার আসল ডেটার সাথে যুক্ত করা
আপনার অ্যাক্টিভিটিতে (`setContent { ... }` এর ভেতরে):
```kotlin
TalklyTheme {
    ChatScreen(
        contactName = "Farabi Ahmed",
        subtitle = "online",
        messages = uiMessages,            // List<UiMessage>, আপনার মেসেজ থেকে ম্যাপ করা
        callbacks = object : ChatCallbacks() {
            override fun onSendText(text: String, replyToId: String?) { /* আপনার সেন্ড ফাংশন কল */ }
            override fun onDelete(ids: List<String>, forEveryone: Boolean) { /* ... */ }
            // শুধু যেগুলো দরকার সেগুলোই override করুন
        },
    )
}
```
**AI-কে দেওয়ার প্রম্পট (কপি করে দিন):**
> এই Compose UI প্যাকেজটা (`com.talkly.designui`) আমার চ্যাট স্ক্রিনে ব্যবহার করতে চাই। আমার মেসেজ মডেল থেকে `UiMessage` এ
> ম্যাপ করার একটা mapper ফাংশন লেখো এবং `ChatCallbacks` এর প্রতিটা ফাংশন আমার বিদ্যমান ফাংশনের সাথে যুক্ত করো।
> **কঠিন নিয়ম: `startRealtimeMessageSync`, Messaging RealtimeChannel তৈরি, আর `FirebaseChatRepository.kt` এর `sendMessage()` এর
> মূল লজিকে হাত দেবে না। শুধু UI ফাইল বদলাবে।** মেসেজ লিস্টে প্রতিটা `UiMessage` এর `id` স্থির রাখবে (ফ্লিকার এড়াতে)।

**ফ্লিকার এড়াতে:** `UiMessage.id` প্রতিবার একই থাকতে হবে (যেমন ডাটাবেসের আইডি)। প্রতিবার নতুন আইডি দিলে লিস্ট ঝলকাবে।
নতুন মেসেজে শুধু ওই একবার `fresh = true` দিন, যাতে পপ-ইন অ্যানিমেশন চলে।

## ছবি লোড করা (Cloudinary → Coil)
গ্রেডিয়েন্ট প্লেসহোল্ডারের জায়গায় আসল ছবি:
```kotlin
ChatScreen(
    ...,
    imageContent = { item, modifier ->
        if (item.url != null) AsyncImage(model = item.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        else ArtBox(item.art, modifier)
    },
)
```
গ্যালারি পিকারের জন্য একই ভাবে `imageContent` স্লট আছে (`GalleryItem.uri` দিয়ে)।

## কল স্ক্রিন (Agora / Zego)
`CallScreen` শুধু চেহারা। ভিডিও আসে স্লট থেকে। `CallUiState` আপনার SDK ইভেন্ট থেকে আপডেট করবেন (phase, seconds, muted, quality)।
```kotlin
CallScreen(
    state = callState, local = rememberCallLocalState(), callbacks = myCallCallbacks,
    remoteVideo = { m -> AndroidView(modifier = m, factory = { ctx -> SurfaceView(ctx).also { /* engine.setupRemoteVideo(VideoCanvas(it, RENDER_MODE_HIDDEN, remoteUid)) */ } }) },
    localVideo  = { m -> AndroidView(modifier = m, factory = { ctx -> SurfaceView(ctx).also { /* engine.setupLocalVideo(VideoCanvas(it, RENDER_MODE_HIDDEN, 0)) */ } }) },
)
```
(কমেন্টের Agora কোড আপনার SDK ভার্সন অনুযায়ী মিলিয়ে নিন।) কলের কথা, ক্যামেরা, মিউট, কল শেষ হওয়া: সব আপনার SDK-র কাজ। UI শুধু `CallCallbacks` দিয়ে জানায়।

## প্রোফাইলে কভারের রং
কভার ছবির প্রধান রং `Palette.from(bitmap).generate().getVibrantColor(...)` দিয়ে বের করে `paletteFrom(Color(তা))` এ দিন। ডেমোতে প্রিসেট রং আছে।

## আপনার অ্যাপ যদি পুরনো XML লেআউটে হয়
একটা `ComposeView` দিয়ে ভেতরে বসাতে পারবেন:
```kotlin
findViewById<ComposeView>(R.id.compose_view).setContent { TalklyTheme { ChatScreen(...) } }
```

## জানা সীমাবদ্ধতা
- **গ্লাস (আপডেট):** এখন ডিফল্ট `GlassMode.Frosted`, যা **সব Android ভার্সনে** একই রকম পরিষ্কার দেখায়:
  থিমের রঙে ছোঁয়া দেওয়া প্রায়-অস্বচ্ছ ফ্রস্টেড গ্লাস, ওপরে হালকা চকচকে আভা, গ্রেডিয়েন্ট কিনারা।
  মেসেজ লিস্ট হেডার/কম্পোজারের কাছে গিয়ে নিজে ফিকে হয়ে মিলিয়ে যায় (`Modifier.fadeEdges`), তাই গ্লাসের নিচে এলোমেলো লেখা দেখা যায় না।
  এটা আসল ব্লার নয়: গ্লাসের নিচে মেসেজ ঝাপসা হয়ে দেখা যায় না, মিলিয়ে যায়।
  আসল ব্লারের জন্য Haze লাইব্রেরি যোগ করলে (শুধু Android 12+ এ ব্লার হয়) `TalklyTheme(glassMode = GlassMode.Clear)` দিন
  এবং শুধু ১২+ ফোনে, নিচের ফোনে `Frosted` রাখুন:
  `TalklyTheme(glassMode = if (Build.VERSION.SDK_INT >= 31) GlassMode.Clear else GlassMode.Frosted) { ... }`
  (`Clear` শুধু আসল ব্লারের সাথেই ব্যবহার করুন, নইলে নিচের লেখা ঝাপসা না হয়ে সোজা দেখা যায়।)
- **প্লেব্যাক:** ভয়েস/অডিও/ভিডিও আসল শব্দ বাজায় না, শুধু প্রোগ্রেস দেখায়। ExoPlayer/MediaPlayer আপনাকে যুক্ত করতে হবে (`PlaybackState` আপডেট করে)।
- **ডেমোর** ছবি, ভিডিও, ফাইলের সাইজ, নাম সব নকল।
- ডেমোতে কল, রিপ্লাই, ডাউনলোড সব নকল টাইমারে চলে।
- নিজের কোড এখনও কম্পাইল-টেস্ট করা হয়নি।
