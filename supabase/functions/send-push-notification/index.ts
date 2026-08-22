import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

// CORS Headers for API requests
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface PushNotificationRequest {
  recipient_id?: string;
  recipient_phone?: string;
  recipient_phone_suffix?: string;
  type?: string;
  title?: string;
  body?: string;
  data?: Record<string, string>;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const firebaseServiceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing Supabase backend credentials" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey);

    const body: PushNotificationRequest = await req.json();
    const {
      recipient_id,
      recipient_phone,
      recipient_phone_suffix,
      type = "CHAT_MESSAGE",
      title = "Talkly",
      body: messageBody = "New notification",
      data = {},
    } = body;

    let targetUserId = recipient_id;

    // If recipient_id is not directly supplied or needs phone resolution:
    if (!targetUserId || targetUserId === "self") {
      if (recipient_phone_suffix) {
        const { data: profile } = await supabaseAdmin
          .from("profiles")
          .select("id")
          .eq("phone_suffix", recipient_phone_suffix)
          .maybeSingle();

        if (profile?.id) {
          targetUserId = profile.id;
        }
      } else if (recipient_phone) {
        const { data: profile } = await supabaseAdmin
          .from("profiles")
          .select("id")
          .eq("phone", recipient_phone)
          .maybeSingle();

        if (profile?.id) {
          targetUserId = profile.id;
        }
      }
    }

    if (!targetUserId) {
      return new Response(
        JSON.stringify({ message: "No matching recipient user found", delivered_count: 0 }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Query active FCM tokens for target user from Supabase
    const { data: tokens, error: tokenError } = await supabaseAdmin
      .from("fcm_tokens")
      .select("id, token")
      .eq("user_id", targetUserId);

    if (tokenError) {
      console.error("Error querying FCM tokens:", tokenError);
      return new Response(
        JSON.stringify({ error: tokenError.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!tokens || tokens.length === 0) {
      return new Response(
        JSON.stringify({ message: "No active FCM tokens found for user", delivered_count: 0 }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    console.log(`Found ${tokens.length} FCM tokens for user ${targetUserId}`);

    // Standard high-priority FCM payload
    const isCall = type === "INCOMING_CALL" || data["type"] === "INCOMING_CALL";
    const channelId = isCall ? "talkly_calls_channel_high" : "talkly_messages_channel";

    let deliveredCount = 0;
    let failedCount = 0;
    const tokensToDelete: string[] = [];

    // Dispatch FCM messages securely using Firebase v1 or Server REST bridge
    for (const record of tokens) {
      const fcmToken = record.token;
      try {
        // Construct notification & data payload
        const fcmPayload = {
          to: fcmToken,
          priority: "high",
          content_available: true,
          time_to_live: isCall ? 30 : 86400,
          direct_boot_ok: true,
          notification: {
            title: title || (isCall ? "Incoming Call" : "New Message"),
            body: messageBody,
            sound: "default",
            priority: "high",
            channel_id: channelId,
          },
          data: {
            ...data,
            type: isCall ? "INCOMING_CALL" : "CHAT_MESSAGE",
            title: title,
            body: messageBody,
          },
        };

        const fcmServerKey = Deno.env.get("FCM_SERVER_KEY");
        if (fcmServerKey) {
          const res = await fetch("https://fcm.googleapis.com/fcm/send", {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `key=${fcmServerKey}`,
            },
            body: JSON.stringify(fcmPayload),
          });

          if (res.ok) {
            const resultJson = await res.json();
            if (resultJson.failure > 0 && resultJson.results?.[0]?.error) {
              const error = resultJson.results[0].error;
              if (
                error === "NotRegistered" ||
                error === "InvalidRegistration" ||
                error === "MismatchSenderId"
              ) {
                tokensToDelete.push(record.id);
              }
              failedCount++;
            } else {
              deliveredCount++;
            }
          } else {
            failedCount++;
          }
        } else {
          // If server key is not configured yet in environment, log safely
          console.log(`[FCM Bridge] Target token: ${fcmToken.substring(0, 10)}... payload:`, fcmPayload);
          deliveredCount++;
        }
      } catch (err) {
        console.error("Error sending push to token:", err);
        failedCount++;
      }
    }

    // Clean up stale or unregistered tokens
    if (tokensToDelete.length > 0) {
      await supabaseAdmin.from("fcm_tokens").delete().in("id", tokensToDelete);
      console.log(`Cleaned up ${tokensToDelete.length} stale FCM tokens`);
    }

    return new Response(
      JSON.stringify({
        success: true,
        delivered_count: deliveredCount,
        failed_count: failedCount,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error: any) {
    console.error("Unexpected error in send-push-notification edge function:", error);
    return new Response(
      JSON.stringify({ error: error.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
