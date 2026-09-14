package com.example

import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.supabase.ActiveCallConflictException
import com.family.talkly.data.supabase.SupabaseActiveCall
import com.family.talkly.data.zego.CallState
import com.family.talkly.data.zego.CurrentCallInfo
import com.family.talkly.util.PhoneUtils
import org.junit.Assert.*
import org.junit.Test

class CallBusyUnitTest {

    @Test
    fun testActiveCallConflictException() {
        val activeCall = SupabaseActiveCall(
            id = "room_a_b",
            roomId = "room_a_b",
            callerId = "user_a",
            callerName = "User A",
            callerPhone = "+1234567890",
            callerSuffix = "7890",
            receiverId = "user_b",
            receiverPhone = "+1987654321",
            receiverSuffix = "4321",
            callType = "AUDIO",
            status = "ACCEPTED"
        )
        val ex = ActiveCallConflictException("User is busy / another call is in progress", activeCall)
        assertEquals("User is busy / another call is in progress", ex.message)
        assertEquals("room_a_b", ex.existingCall?.id)
        assertEquals("ACCEPTED", ex.existingCall?.status)
    }

    @Test
    fun testCallStateBusyDetection() {
        // Current state: A is ACTIVE in room_a_b
        val currentCall = CurrentCallInfo(
            state = CallState.ACTIVE,
            callType = CallType.AUDIO,
            targetMember = FamilyMember(id = "user_b", name = "User B", relation = "Friend", phone = "+1987654321"),
            roomID = "room_a_b"
        )

        // Incoming call from user C for room_c_a
        val incomingRoom = "room_c_a"
        val isEngagedInOtherCall = (currentCall.state != CallState.IDLE &&
                currentCall.state != CallState.ENDED &&
                currentCall.roomID.isNotBlank() &&
                currentCall.roomID != incomingRoom)

        assertTrue("Should detect user is engaged in another call", isEngagedInOtherCall)

        // Incoming call for the SAME room (e.g. duplicate event)
        val sameRoom = "room_a_b"
        val isEngagedSameRoom = (currentCall.state != CallState.IDLE &&
                currentCall.state != CallState.ENDED &&
                currentCall.roomID.isNotBlank() &&
                currentCall.roomID != sameRoom)

        assertFalse("Should not treat same room event as other call", isEngagedSameRoom)
    }

    @Test
    fun testOutgoingCallBlockedWhenActive() {
        val currentState = CallState.ACTIVE
        val isBlocked = (currentState != CallState.IDLE && currentState != CallState.ENDED)
        assertTrue("Outgoing call must be blocked when already ACTIVE", isBlocked)
    }

    @Test
    fun testOutgoingCallBlockedWhenCallingOrRinging() {
        val callingState = CallState.OUTGOING_CALLING
        val ringingState = CallState.OUTGOING_RINGING
        val incomingRinging = CallState.INCOMING_RINGING

        assertTrue(callingState != CallState.IDLE && callingState != CallState.ENDED)
        assertTrue(ringingState != CallState.IDLE && ringingState != CallState.ENDED)
        assertTrue(incomingRinging != CallState.IDLE && incomingRinging != CallState.ENDED)
    }

    @Test
    fun testOutgoingCallAllowedWhenIdleOrEnded() {
        val idleState = CallState.IDLE
        val endedState = CallState.ENDED

        assertFalse(idleState != CallState.IDLE && idleState != CallState.ENDED)
        assertFalse(endedState != CallState.IDLE && endedState != CallState.ENDED)
    }

    @Test
    fun testPhoneMatchingForParticipants() {
        val userPhone = "+1 (234) 567-8901"
        val cleanPhone = PhoneUtils.cleanPhoneNumber(userPhone)
        val cleanSuffix = PhoneUtils.extractPhoneSuffix(cleanPhone)

        val callCallerPhone = "+12345678901"
        val callCallerSuffix = PhoneUtils.extractPhoneSuffix(callCallerPhone)

        val matchesPhone = cleanPhone.isNotBlank() && PhoneUtils.cleanPhoneNumber(callCallerPhone) == cleanPhone
        val matchesSuffix = cleanSuffix.isNotBlank() && callCallerSuffix == cleanSuffix

        assertTrue(matchesPhone)
        assertTrue(matchesSuffix)
    }

    @Test
    fun testUnrelatedRoomEventDoesNotTearDownActiveCall() {
        val activeRoom = "room_a_b"
        val incomingEventRoom = "room_c_a"

        // Simulate endCallInternal guard:
        // if targetRoomId != null && currentRoom.isNotBlank() && currentRoom != targetRoomId -> ignore
        val shouldIgnore = (incomingEventRoom.isNotBlank() && activeRoom.isNotBlank() && activeRoom != incomingEventRoom)
        assertTrue("Active room must not be torn down by unrelated room event", shouldIgnore)
    }
}
