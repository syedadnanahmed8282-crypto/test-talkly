package com.family.talkly.data.models

data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val avatarUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val status: String = "Available for video call",
    val phone: String,
    val isOnline: Boolean = true,
    val isTyping: Boolean = false,
    val lastSeen: String = "Just now",
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isRegisteredOnTalkly: Boolean = false,
    val firebaseUid: String? = null
) {
    fun isRecentlyActive(maxInactiveMs: Long = 2 * 60 * 1000L): Boolean {
        if (!isOnline) return false
        val now = System.currentTimeMillis()
        if (lastActiveTimestamp <= 0L) return isOnline
        return (now - lastActiveTimestamp) <= maxInactiveMs
    }

    val displayLastSeen: String
        get() {
            if (lastActiveTimestamp > 0L) {
                val formatted = com.family.talkly.util.PhoneUtils.formatLastSeenTime(lastActiveTimestamp)
                if (!formatted.equals("Online", ignoreCase = true) && formatted.isNotBlank()) {
                    return formatted
                }
            }
            if (lastSeen.equals("Online", ignoreCase = true) || lastSeen.isBlank()) {
                return "Recently"
            }
            return lastSeen
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
