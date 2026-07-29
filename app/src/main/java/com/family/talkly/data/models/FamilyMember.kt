package com.family.talkly.data.models

data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val avatarUrl: String? = null,
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
    fun isRecentlyActive(maxInactiveMs: Long = 45 * 60 * 1000L): Boolean {
        if (!isOnline) return false
        val now = System.currentTimeMillis()
        if (lastActiveTimestamp <= 0L) return isOnline
        return (now - lastActiveTimestamp) <= maxInactiveMs
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

val DEFAULT_FAMILY_MEMBERS = listOf(
    FamilyMember(
        id = "safwan",
        name = "Safwan",
        relation = "Friend",
        status = "আচ্ছা সকালে কথা হবে",
        phone = "+880 1700-000001",
        isOnline = true,
        isTyping = false,
        lastSeen = "6:30 PM",
        unreadCount = 0
    ),
    FamilyMember(
        id = "israfel",
        name = "Md Israfel Hosen",
        relation = "Contact",
        status = "Tap to view",
        phone = "+880 1700-000002",
        isOnline = false,
        isTyping = false,
        lastSeen = "Sat",
        unreadCount = 0
    ),
    FamilyMember(
        id = "jolil",
        name = "Jolil",
        relation = "Contact",
        status = "Missed Video Call",
        phone = "+880 1700-000003",
        isOnline = false,
        isTyping = false,
        lastSeen = "Fri",
        unreadCount = 0
    ),
    FamilyMember(
        id = "samim",
        name = "সামিম",
        relation = "Contact",
        status = "Missed Audio Call",
        phone = "+880 1700-000004",
        isOnline = false,
        isTyping = false,
        lastSeen = "Mon",
        unreadCount = 0
    ),
    FamilyMember(
        id = "akhter",
        name = "md Akhter Høssain° •:...",
        relation = "Contact",
        status = "Tap to view",
        phone = "+880 1700-000005",
        isOnline = false,
        isTyping = false,
        lastSeen = "Jul 10",
        unreadCount = 0
    ),
    FamilyMember(
        id = "osman",
        name = "Osman Vi",
        relation = "Contact",
        status = "Missed Audio Call",
        phone = "+880 1700-000006",
        isOnline = false,
        isTyping = false,
        lastSeen = "Jun 30",
        unreadCount = 0
    ),
    FamilyMember(
        id = "mohammad_raiu",
        name = "Mohammad Raiu Mha...",
        relation = "Contact",
        status = "Tap to view",
        phone = "+880 1700-000007",
        isOnline = false,
        isTyping = false,
        lastSeen = "Jun 29",
        unreadCount = 0
    ),
    FamilyMember(
        id = "dr_rashed",
        name = "Dr. Rashed",
        relation = "Doctor",
        status = "Medical updates",
        phone = "+880 1700-000008",
        isOnline = true,
        unreadCount = 3
    ),
    FamilyMember(
        id = "monju",
        name = "Monju",
        relation = "Friend",
        status = "Available",
        phone = "+880 1700-000009",
        isOnline = true,
        unreadCount = 1
    ),
    FamilyMember(
        id = "sk_farid",
        name = "Sk F A R I D",
        relation = "Friend",
        status = "At work",
        phone = "+880 1700-000010",
        isOnline = true,
        unreadCount = 2
    )
)
