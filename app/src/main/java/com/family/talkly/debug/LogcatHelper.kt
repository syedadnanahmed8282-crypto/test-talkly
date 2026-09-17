package com.family.talkly.debug

import java.io.BufferedReader
import java.io.InputStreamReader

enum class LogCategory(
    val id: String,
    val displayName: String,
    val icon: String,
    val exportHeader: String
) {
    CRASH("crash", "Crash", "🔴", "=== CRASH ==="),
    CALLS("calls", "Calls", "📞", "=== CALLS ==="),
    MESSAGES("messages", "Messages", "💬", "=== MESSAGES ==="),
    NOTIFICATIONS("notifications", "Notifications", "🔔", "=== NOTIFICATIONS ==="),
    AUTH("auth", "Authentication", "🔐", "=== AUTHENTICATION ==="),
    SUPABASE("supabase", "Supabase", "🗄", "=== SUPABASE ==="),
    MEDIA("media", "Cloudinary / Media", "☁️", "=== CLOUDINARY / MEDIA ==="),
    UI("ui", "App / UI", "🎨", "=== APP / UI ==="),
    SYSTEM("system", "System", "⚙️", "=== SYSTEM ===")
}

data class CategorizedLogs(
    val categorized: Map<LogCategory, List<String>>,
    val rawLogFallback: String? = null,
    val captureTime: Long = System.currentTimeMillis()
)

object LogcatHelper {
    private const val DEFAULT_CATEGORY_LIMIT = 120

    private val CALL_KEYWORDS = listOf("zego", "agora", "call", "rtc", "calling")
    private val MESSAGE_KEYWORDS = listOf("firebasechat", "message", "chat", "typing", "reaction")
    private val NOTIFICATION_KEYWORDS = listOf("fcm", "firebase messaging", "notification", "push", "badtoken")
    private val AUTH_KEYWORDS = listOf("auth", "login", "logout", "signin", "signup", "profile")
    private val SUPABASE_KEYWORDS = listOf("supabase", "postgrest", "realtime", "edge function", "socialservice")
    private val MEDIA_KEYWORDS = listOf("cloudinary", "upload", "media", "gallery", "image", "video")
    private val UI_KEYWORDS = listOf("ui", "compose", "screen", "dialog", "lazycolumn")
    private val CRASH_KEYWORDS = listOf(
        "androidruntime: fatal", "fatal exception", "uncaughtexception",
        "crashhandler", "sigsegv", "fatal"
    )
    private val SYSTEM_KEYWORDS = listOf(
        "androidruntime", "exception", "system", "dalvik",
        "art", "activitymanager", "windowmanager"
    )

    fun captureCategorizedLogs(maxPerCategory: Int = DEFAULT_CATEGORY_LIMIT): CategorizedLogs {
        val categorizedMap = mutableMapOf<LogCategory, MutableList<String>>()
        LogCategory.values().forEach { category ->
            categorizedMap[category] = mutableListOf()
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val allLines = ArrayList<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val nonNullLine = line ?: continue
                allLines.add(nonNullLine)
            }
            reader.close()

            var anyCategorized = false

            for (logLine in allLines) {
                val lower = logLine.lowercase()
                var matched = false

                if (CRASH_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.CRASH]?.add(logLine)
                    matched = true
                }
                if (CALL_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.CALLS]?.add(logLine)
                    matched = true
                }
                if (MESSAGE_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.MESSAGES]?.add(logLine)
                    matched = true
                }
                if (NOTIFICATION_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.NOTIFICATIONS]?.add(logLine)
                    matched = true
                }
                if (AUTH_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.AUTH]?.add(logLine)
                    matched = true
                }
                if (SUPABASE_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.SUPABASE]?.add(logLine)
                    matched = true
                }
                if (MEDIA_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.MEDIA]?.add(logLine)
                    matched = true
                }
                if (UI_KEYWORDS.any { lower.contains(it) }) {
                    categorizedMap[LogCategory.UI]?.add(logLine)
                    matched = true
                }

                if (SYSTEM_KEYWORDS.any { lower.contains(it) } ||
                    (!matched && (lower.contains(" e ") || lower.contains(" w ") || lower.contains("error") || lower.contains("fail")))
                ) {
                    categorizedMap[LogCategory.SYSTEM]?.add(logLine)
                    matched = true
                }

                if (matched) {
                    anyCategorized = true
                }
            }

            val finalMap = categorizedMap.mapValues { (_, list) ->
                if (list.size > maxPerCategory) list.takeLast(maxPerCategory) else list
            }

            val rawFallback = if (!anyCategorized && allLines.isNotEmpty()) {
                allLines.takeLast(maxPerCategory).joinToString("\n")
            } else null

            // If nothing categorized, populate System with raw log fallback
            if (!anyCategorized && allLines.isNotEmpty()) {
                val sysList = allLines.takeLast(maxPerCategory)
                CategorizedLogs(
                    categorized = finalMap.toMutableMap().apply {
                        put(LogCategory.SYSTEM, sysList)
                    },
                    rawLogFallback = rawFallback
                )
            } else {
                CategorizedLogs(
                    categorized = finalMap,
                    rawLogFallback = rawFallback
                )
            }
        } catch (e: Exception) {
            val failureMessage = "Logcat capture failed: ${e.localizedMessage}\n(On some devices, logcat access may be restricted. Check persistent CrashHandler reports.)"
            val fallbackMap = categorizedMap.mapValues { (cat, _) ->
                if (cat == LogCategory.SYSTEM) listOf(failureMessage) else emptyList()
            }
            CategorizedLogs(
                categorized = fallbackMap,
                rawLogFallback = failureMessage
            )
        }
    }

    /**
     * Backward-compatible helper returning a single combined log string.
     */
    fun captureRecentLogs(maxLines: Int = 500): String {
        val catLogs = captureCategorizedLogs(maxLines / LogCategory.values().size)
        val sb = StringBuilder()
        catLogs.categorized.forEach { (cat, lines) ->
            if (lines.isNotEmpty()) {
                sb.append("${cat.exportHeader}\n")
                sb.append(lines.joinToString("\n"))
                sb.append("\n\n")
            }
        }
        return if (sb.isNotEmpty()) sb.toString().trim() else catLogs.rawLogFallback ?: "No logs available"
    }
}
