package com.family.talkly.data.models

data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val avatarUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val status: String = "Available for video call",
    val phone: String,
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val lastSeen: String = "Offline",
    val lastActiveTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isRegisteredOnTalkly: Boolean = false,
    val firebaseUid: String? = null
) {
    fun isRecentlyActive(maxInactiveMs: Long = 5 * 60 * 1000L): Boolean {
        if (!isRegisteredOnTalkly) return false
        if (isOnline) return true
        val now = System.currentTimeMillis()
        if (lastActiveTimestamp > 0L) {
            val diff = now - lastActiveTimestamp
            return diff in 0..maxInactiveMs
        }
        return false
    }

    /**
     * True if the user is not actively in the foreground (isOnline == false)
     * but was active within the last 5 minutes (background state).
     */
    fun isBackgroundRecentlyActive(maxInactiveMs: Long = 5 * 60 * 1000L): Boolean {
        if (!isRegisteredOnTalkly) return false
        if (isOnline) return false
        val now = System.currentTimeMillis()
        if (lastActiveTimestamp > 0L) {
            val diff = now - lastActiveTimestamp
            return diff in 0..maxInactiveMs
        }
        return false
    }

    val displayLastSeen: String
        get() {
            if (!isRegisteredOnTalkly) return "Not on Talkly"
            if (isOnline) {
                return "Online"
            }
            if (lastActiveTimestamp > 0L) {
                val formatted = com.family.talkly.util.PhoneUtils.formatLastSeenTime(lastActiveTimestamp)
                if (formatted.isNotBlank() && !formatted.equals("Online", ignoreCase = true) && !formatted.equals("Offline", ignoreCase = true)) {
                    return formatted
                }
            }
            if (lastSeen.isNotBlank() && !lastSeen.equals("Online", ignoreCase = true) && !lastSeen.equals("Offline", ignoreCase = true)) {
                return lastSeen
            }
            return "Recently"
        }

    val firstName: String
        get() {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return ""
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size > 1) {
                val firstUpper = parts[0].uppercase().removeSuffix(".")
                val honorifics = listOf("MD", "SK", "DR", "MR", "MRS", "MS", "PROF", "ADV", "ENG", "ENGR")
                if (honorifics.contains(firstUpper)) {
                    val rest = parts.drop(1)
                    if (rest.all { it.length == 1 && it[0].isLetter() }) {
                        val joined = rest.joinToString("").lowercase().replaceFirstChar { it.uppercase() }
                        return joined
                    }
                    val candidate = rest.firstOrNull()?.replace(Regex("[^\\p{L}\\p{N}]"), "")
                    if (!candidate.isNullOrBlank()) return candidate
                }
            }
            val firstWord = parts[0].replace(Regex("[^\\p{L}\\p{N}]"), "")
            return if (firstWord.isNotBlank()) firstWord else parts[0]
        }
}

val DEFAULT_FAMILY_MEMBERS = emptyList<FamilyMember>()
