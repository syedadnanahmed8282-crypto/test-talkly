package com.example

import com.family.talkly.util.TalklyNotificationHelper
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun generateNotificationId_isStableForSameMessageId() {
        val id1 = TalklyNotificationHelper.generateNotificationId(messageId = "msg_12345", chatMemberId = "user_a")
        val id2 = TalklyNotificationHelper.generateNotificationId(messageId = "msg_12345", chatMemberId = "user_a")
        assertEquals(id1, id2)
        assertTrue(id1 > 0)
        assertNotEquals(99999, id1)
    }

    @Test
    fun generateNotificationId_isDifferentForDifferentMessages() {
        val id1 = TalklyNotificationHelper.generateNotificationId(messageId = "msg_12345", chatMemberId = "user_a")
        val id2 = TalklyNotificationHelper.generateNotificationId(messageId = "msg_67890", chatMemberId = "user_a")
        assertNotEquals(id1, id2)
    }

    @Test
    fun generateNotificationId_fallbackIsDeterministicAndDistinct() {
        val id1 = TalklyNotificationHelper.generateNotificationId(messageId = "", chatMemberId = "user_a", messageText = "Hello", timestamp = 1000L)
        val id2 = TalklyNotificationHelper.generateNotificationId(messageId = "", chatMemberId = "user_a", messageText = "Hello", timestamp = 1000L)
        val id3 = TalklyNotificationHelper.generateNotificationId(messageId = "", chatMemberId = "user_a", messageText = "World", timestamp = 2000L)
        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
        assertTrue(id1 > 0)
        assertTrue(id3 > 0)
        assertNotEquals(99999, id1)
        assertNotEquals(99999, id3)
    }
}
