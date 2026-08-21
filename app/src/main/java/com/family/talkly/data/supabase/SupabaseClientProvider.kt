package com.family.talkly.data.supabase

import android.util.Log
import com.family.talkly.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

/**
 * Provides a singleton instance of the configured Supabase client.
 * Uses confirmed project URL and publishable key via BuildConfig (.env / .env.example).
 */
object SupabaseClientProvider {
    private const val TAG = "SupabaseClientProvider"

    val supabaseUrl: String by lazy {
        val url = try {
            BuildConfig.SUPABASE_URL
        } catch (e: Exception) {
            ""
        }
        if (url.isNullOrBlank()) "https://rqfarogdanryxfanadjl.supabase.co" else url
    }

    val supabasePublishableKey: String by lazy {
        val key = try {
            BuildConfig.SUPABASE_PUBLISHABLE_KEY
        } catch (e: Exception) {
            ""
        }
        if (key.isNullOrBlank()) "sb_publishable_cuBoppGikbwArqkC-XSNxw_6Sw2MCk6" else key
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabasePublishableKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    val auth: Auth
        get() = client.auth

    val postgrest: Postgrest
        get() = client.postgrest

    val realtime: Realtime
        get() = client.realtime
}
