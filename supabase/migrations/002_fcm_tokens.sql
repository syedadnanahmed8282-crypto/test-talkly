-- ============================================================================
-- TALKLY SUPABASE DATABASE SCHEMA MIGRATION
-- Migration: 002_fcm_tokens.sql
-- Description: Multi-device FCM Push Token Registry & Secure RLS
-- Target Backend: Supabase (Project: Family-calling-app)
-- ============================================================================

-- ============================================================================
-- 1. FCM TOKENS TABLE (Multi-device Push Registry)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.fcm_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    device_id TEXT NOT NULL DEFAULT '',
    platform TEXT NOT NULL DEFAULT 'android',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_user_fcm_token UNIQUE (user_id, token)
);

-- Indexes for lightning fast token lookups and recipient fan-outs
CREATE INDEX IF NOT EXISTS idx_fcm_tokens_user_id ON public.fcm_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_fcm_tokens_token ON public.fcm_tokens(token);
CREATE INDEX IF NOT EXISTS idx_fcm_tokens_device ON public.fcm_tokens(user_id, device_id);

-- Auto-update updated_at timestamp trigger
DROP TRIGGER IF EXISTS set_fcm_tokens_updated_at ON public.fcm_tokens;
CREATE TRIGGER set_fcm_tokens_updated_at
    BEFORE UPDATE ON public.fcm_tokens
    FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 2. ROW LEVEL SECURITY (RLS) POLICIES FOR FCM TOKENS
-- ============================================================================
ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their own FCM tokens"
    ON public.fcm_tokens FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own FCM tokens"
    ON public.fcm_tokens FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own FCM tokens"
    ON public.fcm_tokens FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own FCM tokens"
    ON public.fcm_tokens FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);
