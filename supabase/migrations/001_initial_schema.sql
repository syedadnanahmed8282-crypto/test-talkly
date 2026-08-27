-- ============================================================================
-- TALKLY SUPABASE DATABASE SCHEMA MIGRATION
-- Migration: 001_initial_schema.sql
-- Description: Optimized Relational Schema, RLS & Realtime for Talkly
-- Target Backend: Supabase (Project: Family-calling-app)
-- ============================================================================

-- Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. PROFILES TABLE (Linked with Supabase Auth)
-- ============================================================================
-- Note: Live online/offline presence is handled in-memory via Supabase Realtime
-- Presence channels to avoid frequent, quota-heavy database heartbeat writes.
-- last_seen_at is recorded only during explicit session disconnects/app exit.
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    phone TEXT UNIQUE NOT NULL,
    phone_suffix TEXT NOT NULL,
    name TEXT NOT NULL,
    avatar_url TEXT DEFAULT '',
    cover_photo_url TEXT DEFAULT '',
    bio TEXT DEFAULT 'Available on Talkly 💬',
    last_seen_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Profiles Indexes (Fast lookups for contact sync & phone matching)
CREATE INDEX IF NOT EXISTS idx_profiles_phone ON public.profiles(phone);
CREATE INDEX IF NOT EXISTS idx_profiles_phone_suffix ON public.profiles(phone_suffix);

-- ============================================================================
-- 2. USER CONTACTS & FAMILY RELATIONSHIPS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    contact_user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    contact_name TEXT NOT NULL,
    contact_phone TEXT NOT NULL,
    contact_phone_suffix TEXT DEFAULT '',
    relation TEXT DEFAULT 'Contact',
    is_pinned BOOLEAN DEFAULT false,
    is_mutual BOOLEAN DEFAULT false,
    status TEXT DEFAULT 'ACCEPTED',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_user_contact UNIQUE (user_id, contact_phone)
);

-- Contacts Indexes
CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON public.contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_user_id ON public.contacts(contact_user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_phone_suffix ON public.contacts(contact_phone_suffix);
CREATE INDEX IF NOT EXISTS idx_contacts_user_status ON public.contacts(user_id, status);

-- ============================================================================
-- 3. CONVERSATIONS (1-on-1 Chats with strictly unique pairs)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant1_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    participant2_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    last_message_id TEXT,
    last_message_text TEXT DEFAULT '',
    last_message_time TIMESTAMPTZ DEFAULT now(),
    last_message_sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT distinct_conversation_participants CHECK (participant1_id <> participant2_id)
);

-- Prevent duplicate 1-to-1 conversations between the same two users regardless of participant order
CREATE UNIQUE INDEX IF NOT EXISTS unique_conversation_pair 
    ON public.conversations (LEAST(participant1_id, participant2_id), GREATEST(participant1_id, participant2_id));

-- Conversations Indexes
CREATE INDEX IF NOT EXISTS idx_conversations_p1 ON public.conversations(participant1_id);
CREATE INDEX IF NOT EXISTS idx_conversations_p2 ON public.conversations(participant2_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_msg_time ON public.conversations(last_message_time DESC);

-- ============================================================================
-- 4. CHAT MESSAGES
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.messages (
    id TEXT PRIMARY KEY DEFAULT ('msg_' || extract(epoch from now())::bigint || '_' || substr(md5(random()::text), 1, 8)),
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    message_type TEXT NOT NULL DEFAULT 'TEXT' CHECK (message_type IN ('TEXT', 'IMAGE', 'VIDEO', 'VOICE_NOTE', 'CALL_LOG')),
    text_content TEXT DEFAULT '',
    media_url TEXT,
    call_type TEXT CHECK (call_type IS NULL OR call_type IN ('AUDIO', 'VIDEO')),
    call_duration_sec INTEGER DEFAULT 0,
    is_delivered BOOLEAN DEFAULT false,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMPTZ,
    reaction TEXT, -- Serialized JSON array of reactions or single emoji
    is_starred BOOLEAN DEFAULT false,
    is_pinned BOOLEAN DEFAULT false,
    pinned_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reply_to_message_id TEXT,
    reply_to_sender_name TEXT,
    reply_to_text TEXT,
    is_edited BOOLEAN DEFAULT false,
    is_deleted_for_everyone BOOLEAN DEFAULT false,
    deleted_for_users TEXT[] DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Messages Indexes (Fast message history paging, receipts & sync queries)
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON public.messages(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON public.messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_id ON public.messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON public.messages(created_at DESC);

-- ============================================================================
-- 5. MESSAGE REQUESTS (Connecting Stranger Users)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.message_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    sender_phone TEXT NOT NULL,
    sender_phone_suffix TEXT DEFAULT '',
    sender_name TEXT NOT NULL,
    sender_avatar TEXT DEFAULT '',
    receiver_phone TEXT NOT NULL,
    receiver_phone_suffix TEXT DEFAULT '',
    receiver_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'BLOCKED')),
    initial_message TEXT DEFAULT 'Hello, I would like to connect on Talkly!',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Message Requests Indexes
CREATE INDEX IF NOT EXISTS idx_message_requests_receiver ON public.message_requests(receiver_id, status);
CREATE INDEX IF NOT EXISTS idx_message_requests_sender ON public.message_requests(sender_id, status);

-- ============================================================================
-- 6. 24-HOUR EPHEMERAL STATUSES (Stories)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.statuses (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    user_name TEXT NOT NULL,
    user_avatar_url TEXT,
    text_content TEXT,
    photo_url TEXT, -- Cloudinary Media URL
    is_video BOOLEAN DEFAULT false,
    background_color_hex TEXT DEFAULT '#321C3B',
    created_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ DEFAULT (now() + INTERVAL '24 hours')
);

-- Statuses Indexes (Valid immutable PostgreSQL indexes)
CREATE INDEX IF NOT EXISTS idx_statuses_user_expires ON public.statuses(user_id, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_statuses_expires_at ON public.statuses(expires_at DESC);

-- ============================================================================
-- 7. STATUS VIEWERS & LIKES
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.status_viewers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id TEXT NOT NULL REFERENCES public.statuses(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    viewer_name TEXT NOT NULL,
    viewer_avatar_url TEXT,
    viewed_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_status_viewer UNIQUE (status_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_status_viewers_status_id ON public.status_viewers(status_id);

CREATE TABLE IF NOT EXISTS public.status_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id TEXT NOT NULL REFERENCES public.statuses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    user_name TEXT NOT NULL,
    user_avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_status_like UNIQUE (status_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_status_likes_status_id ON public.status_likes(status_id);

-- ============================================================================
-- 8. ACTIVE CALL SIGNALING (Real-Time ZEGO / Call State Signaling)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.active_calls (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    caller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    caller_name TEXT NOT NULL,
    caller_phone TEXT NOT NULL,
    caller_suffix TEXT NOT NULL,
    caller_avatar_url TEXT DEFAULT '',
    receiver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_phone TEXT NOT NULL,
    receiver_suffix TEXT NOT NULL,
    call_type TEXT NOT NULL CHECK (call_type IN ('AUDIO', 'VIDEO')),
    status TEXT NOT NULL DEFAULT 'CALLING' CHECK (status IN ('CALLING', 'RINGING', 'ACCEPTED', 'REJECTED', 'BUSY', 'MISSED', 'ENDED', 'TIMEOUT')),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Active Calls Indexes
CREATE INDEX IF NOT EXISTS idx_active_calls_room_id ON public.active_calls(room_id);
CREATE INDEX IF NOT EXISTS idx_active_calls_receiver ON public.active_calls(receiver_id, status);
CREATE INDEX IF NOT EXISTS idx_active_calls_receiver_suffix ON public.active_calls(receiver_suffix, status);
CREATE INDEX IF NOT EXISTS idx_active_calls_caller ON public.active_calls(caller_id, status);

-- ============================================================================
-- 9. CALL LOGS (Call History)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.call_logs (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    peer_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    peer_name TEXT NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('INCOMING', 'OUTGOING', 'MISSED')),
    call_type TEXT NOT NULL CHECK (call_type IN ('AUDIO', 'VIDEO')),
    duration_seconds INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Call Logs Indexes
CREATE INDEX IF NOT EXISTS idx_call_logs_user_id ON public.call_logs(user_id, created_at DESC);

-- ============================================================================
-- 10. DATABASE TRIGGERS & AUTOMATION FUNCTIONS
-- ============================================================================

-- A. Auto-update 'updated_at' column timestamp
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger across mutable tables
DROP TRIGGER IF EXISTS set_profiles_updated_at ON public.profiles;
CREATE TRIGGER set_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS set_contacts_updated_at ON public.contacts;
CREATE TRIGGER set_contacts_updated_at
    BEFORE UPDATE ON public.contacts
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS set_conversations_updated_at ON public.conversations;
CREATE TRIGGER set_conversations_updated_at
    BEFORE UPDATE ON public.conversations
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS set_active_calls_updated_at ON public.active_calls;
CREATE TRIGGER set_active_calls_updated_at
    BEFORE UPDATE ON public.active_calls
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS set_message_requests_updated_at ON public.message_requests;
CREATE TRIGGER set_message_requests_updated_at
    BEFORE UPDATE ON public.message_requests
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- B. Auto-update Conversation 'last_message' snippet upon new Message insert
CREATE OR REPLACE FUNCTION public.handle_conversation_last_message()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.conversation_id IS NOT NULL THEN
        UPDATE public.conversations
        SET last_message_id = NEW.id,
            last_message_text = CASE 
                WHEN NEW.message_type = 'IMAGE' THEN '📷 Photo'
                WHEN NEW.message_type = 'VIDEO' THEN '🎥 Video'
                WHEN NEW.message_type = 'VOICE_NOTE' THEN '🎤 Voice message'
                WHEN NEW.message_type = 'CALL_LOG' THEN '📞 Call'
                ELSE COALESCE(NEW.text_content, '')
            END,
            last_message_time = NEW.created_at,
            last_message_sender_id = NEW.sender_id,
            updated_at = now()
        WHERE id = NEW.conversation_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_conversation_last_message_trigger ON public.messages;
CREATE TRIGGER update_conversation_last_message_trigger
    AFTER INSERT ON public.messages
    FOR EACH ROW EXECUTE FUNCTION public.handle_conversation_last_message();

-- C. Protect Message Core Fields from Tampering on Updates
CREATE OR REPLACE FUNCTION public.handle_message_update_security()
RETURNS TRIGGER AS $$
BEGIN
    -- Prevent altering immutable routing and creation timestamps
    IF NEW.id <> OLD.id OR NEW.conversation_id <> OLD.conversation_id OR NEW.sender_id <> OLD.sender_id OR NEW.receiver_id <> OLD.receiver_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Immutable message fields cannot be modified';
    END IF;

    -- When updated by receiver, only receipts, reactions, star/pin, and deletion flags are mutable
    IF auth.uid() = OLD.receiver_id AND auth.uid() <> OLD.sender_id THEN
        IF NEW.text_content <> OLD.text_content OR NEW.media_url IS DISTINCT FROM OLD.media_url OR NEW.message_type <> OLD.message_type OR NEW.is_edited <> OLD.is_edited OR NEW.is_deleted_for_everyone <> OLD.is_deleted_for_everyone THEN
            RAISE EXCEPTION 'Receiver is not permitted to alter message content or edit metadata';
        END IF;
    END IF;

    -- Senders cannot edit text once deleted for everyone
    IF OLD.is_deleted_for_everyone AND NEW.text_content <> OLD.text_content THEN
        RAISE EXCEPTION 'Cannot edit message after deletion for everyone';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS enforce_message_update_security_trigger ON public.messages;
CREATE TRIGGER enforce_message_update_security_trigger
    BEFORE UPDATE ON public.messages
    FOR EACH ROW EXECUTE FUNCTION public.handle_message_update_security();

-- D. Auto-provision User Profile on Supabase Auth Sign Up
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER AS $$
DECLARE
    user_phone TEXT;
    raw_suffix TEXT;
    user_name TEXT;
BEGIN
    user_phone := COALESCE(NEW.phone, (NEW.raw_user_meta_data->>'phone'), '');
    raw_suffix := RIGHT(REGEXP_REPLACE(user_phone, '[^0-9]', '', 'g'), 10);
    user_name := COALESCE(NEW.raw_user_meta_data->>'name', 'Talkly User');

    INSERT INTO public.profiles (
        id,
        phone,
        phone_suffix,
        name,
        avatar_url,
        cover_photo_url,
        bio,
        last_seen_at,
        created_at,
        updated_at
    ) VALUES (
        NEW.id,
        user_phone,
        raw_suffix,
        user_name,
        COALESCE(NEW.raw_user_meta_data->>'avatar_url', ''),
        COALESCE(NEW.raw_user_meta_data->>'cover_photo_url', ''),
        COALESCE(NEW.raw_user_meta_data->>'bio', 'Available on Talkly 💬'),
        now(),
        now(),
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        phone = EXCLUDED.phone,
        phone_suffix = EXCLUDED.phone_suffix,
        name = COALESCE(NULLIF(EXCLUDED.name, ''), public.profiles.name),
        updated_at = now();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- ============================================================================
-- 11. ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.status_viewers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.status_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.active_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_logs ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- PROFILES POLICIES
-- ----------------------------------------------------------------------------
-- Authenticated users can discover profiles by phone/suffix for chat creation & contact sync
CREATE POLICY "Profiles are viewable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

-- Users can only insert their own profile
CREATE POLICY "Users can insert their own profile"
    ON public.profiles FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = id);

-- Users can only update their own profile
CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- CONTACTS POLICIES
-- ----------------------------------------------------------------------------
-- Users can only manage their own contacts list
CREATE POLICY "Users can view their own contacts"
    ON public.contacts FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert contacts into their list"
    ON public.contacts FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own contacts"
    ON public.contacts FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own contacts"
    ON public.contacts FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- CONVERSATIONS POLICIES
-- ----------------------------------------------------------------------------
-- Conversations are private to the 2 participants
CREATE POLICY "Users can view conversations they participate in"
    ON public.conversations FOR SELECT
    TO authenticated
    USING (auth.uid() = participant1_id OR auth.uid() = participant2_id);

CREATE POLICY "Users can create conversations they participate in"
    ON public.conversations FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = participant1_id OR auth.uid() = participant2_id);

CREATE POLICY "Participants can update conversation metadata"
    ON public.conversations FOR UPDATE
    TO authenticated
    USING (auth.uid() = participant1_id OR auth.uid() = participant2_id);

-- ----------------------------------------------------------------------------
-- MESSAGES POLICIES
-- ----------------------------------------------------------------------------
-- Only sender or receiver can view messages
CREATE POLICY "Users can view messages sent or received by them"
    ON public.messages FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Senders can insert messages
CREATE POLICY "Users can send messages"
    ON public.messages FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = sender_id);

-- Sender or receiver can update message state (delivery/read receipts, reactions, edits validated by trigger)
CREATE POLICY "Participants can update message receipts and state"
    ON public.messages FOR UPDATE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id)
    WITH CHECK (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Only message sender can hard delete
CREATE POLICY "Senders can delete messages"
    ON public.messages FOR DELETE
    TO authenticated
    USING (auth.uid() = sender_id);

-- ----------------------------------------------------------------------------
-- MESSAGE REQUESTS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view message requests involving them"
    ON public.message_requests FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "Users can create message requests"
    ON public.message_requests FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = sender_id);

CREATE POLICY "Participants can update message request status"
    ON public.message_requests FOR UPDATE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "Participants can delete message requests"
    ON public.message_requests FOR DELETE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- ----------------------------------------------------------------------------
-- STATUSES (Stories) POLICIES
-- ----------------------------------------------------------------------------
-- Privacy-protected: Users can view their own statuses OR statuses of mutual/accepted contacts
CREATE POLICY "Users can view their own or contacts statuses"
    ON public.statuses FOR SELECT
    TO authenticated
    USING (
        auth.uid() = user_id
        OR EXISTS (
            SELECT 1 FROM public.contacts
            WHERE public.contacts.user_id = auth.uid()
              AND public.contacts.contact_user_id = public.statuses.user_id
              AND public.contacts.status = 'ACCEPTED'
        )
    );

CREATE POLICY "Users can post their own status"
    ON public.statuses FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own status"
    ON public.statuses FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- STATUS VIEWERS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Status authors and viewers can view status viewers"
    ON public.status_viewers FOR SELECT
    TO authenticated
    USING (
        auth.uid() = viewer_id
        OR EXISTS (
            SELECT 1 FROM public.statuses
            WHERE public.statuses.id = public.status_viewers.status_id
              AND public.statuses.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can record their own status view"
    ON public.status_viewers FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = viewer_id);

-- ----------------------------------------------------------------------------
-- STATUS LIKES POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Status authors and likers can view status likes"
    ON public.status_likes FOR SELECT
    TO authenticated
    USING (
        auth.uid() = user_id
        OR EXISTS (
            SELECT 1 FROM public.statuses
            WHERE public.statuses.id = public.status_likes.status_id
              AND public.statuses.user_id = auth.uid()
        )
    );

CREATE POLICY "Users can like a status"
    ON public.status_likes FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can unlike a status"
    ON public.status_likes FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- ACTIVE CALLS POLICIES (Signaling)
-- ----------------------------------------------------------------------------
CREATE POLICY "Participants can view active call signals"
    ON public.active_calls FOR SELECT
    TO authenticated
    USING (auth.uid() = caller_id OR auth.uid() = receiver_id);

CREATE POLICY "Callers can initiate active call signals"
    ON public.active_calls FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = caller_id);

CREATE POLICY "Participants can update active call state"
    ON public.active_calls FOR UPDATE
    TO authenticated
    USING (auth.uid() = caller_id OR auth.uid() = receiver_id);

CREATE POLICY "Participants can delete active call records"
    ON public.active_calls FOR DELETE
    TO authenticated
    USING (auth.uid() = caller_id OR auth.uid() = receiver_id);

-- ----------------------------------------------------------------------------
-- CALL LOGS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view their own call logs"
    ON public.call_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own call logs"
    ON public.call_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own call logs"
    ON public.call_logs FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ============================================================================
-- 12. SUPABASE REALTIME PUBLICATION ENABLEMENT
-- ============================================================================
-- Enable Realtime on tables that require instant push events.
-- Realtime Presence (in-memory WebSocket) handles online/offline status.
DO $$
BEGIN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.messages';
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.message_requests';
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.active_calls';
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.statuses';
EXCEPTION WHEN duplicate_object THEN
    NULL;
END $$;
