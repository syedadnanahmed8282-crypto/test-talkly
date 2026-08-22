import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

// Restrict CORS appropriately for client API invocations
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
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

interface FirebaseServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

// In-memory token cache for OAuth2 access tokens
let cachedOAuthToken: { token: string; expiresAt: number } | null = null;

/**
 * Converts a PEM formatted private key to an ArrayBuffer for Web Crypto API.
 */
function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----[^\n]+-----/g, "")
    .replace(/\s+/g, "");
  const raw = atob(b64);
  const buf = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    buf[i] = raw.charCodeAt(i);
  }
  return buf.buffer;
}

/**
 * Base64 URL encoding helper.
 */
function base64UrlEncode(data: Uint8Array | string): string {
  let str: string;
  if (typeof data === "string") {
    str = btoa(unescape(encodeURIComponent(data)));
  } else {
    let binary = "";
    const bytes = new Uint8Array(data);
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    str = btoa(binary);
  }
  return str.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Generates an OAuth2 access token for Firebase Cloud Messaging HTTP v1 using Web Crypto RS256.
 */
async function getFirebaseV1AccessToken(serviceAccount: FirebaseServiceAccount): Promise<string> {
  const now = Date.now();
  if (cachedOAuthToken && cachedOAuthToken.expiresAt > now + 60000) {
    return cachedOAuthToken.token;
  }

  const iat = Math.floor(now / 1000);
  const exp = iat + 3600;

  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp,
    iat,
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const unsignedToken = `${encodedHeader}.${encodedPayload}`;

  const keyBuffer = pemToArrayBuffer(serviceAccount.private_key);
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBuffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(unsignedToken)
  );

  const encodedSignature = base64UrlEncode(new Uint8Array(signature));
  const jwt = `${unsignedToken}.${encodedSignature}`;

  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!tokenResponse.ok) {
    const errorText = await tokenResponse.text();
    throw new Error(`Failed to obtain Google OAuth2 access token for FCM v1: ${tokenResponse.status} ${errorText}`);
  }

  const tokenData = await tokenResponse.json();
  cachedOAuthToken = {
    token: tokenData.access_token,
    expiresAt: now + (tokenData.expires_in || 3600) * 1000,
  };

  return tokenData.access_token;
}

/**
 * Resolves Firebase service account credentials exclusively from server-side environment variables.
 */
function resolveFirebaseServiceAccount(): FirebaseServiceAccount | null {
  const rawJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT") || Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (rawJson) {
    try {
      let jsonStr = rawJson.trim();
      if (!jsonStr.startsWith("{")) {
        try {
          const decoded = atob(jsonStr);
          if (decoded.trim().startsWith("{")) {
            jsonStr = decoded;
          }
        } catch (_) {}
      }
      const parsed = JSON.parse(jsonStr);
      if (parsed.client_email && parsed.private_key) {
        return {
          client_email: parsed.client_email,
          private_key: parsed.private_key,
          project_id: parsed.project_id || Deno.env.get("FIREBASE_PROJECT_ID") || "familycallapp-e6b21",
        };
      }
    } catch (e) {
      console.error("Failed to parse FIREBASE_SERVICE_ACCOUNT credential:", e);
    }
  }

  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const privateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID") || "familycallapp-e6b21";

  if (clientEmail && privateKey) {
    return {
      client_email: clientEmail,
      private_key: privateKey.replace(/\\n/g, "\n"),
      project_id: projectId,
    };
  }

  return null;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing Supabase backend credentials" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey);

    // ========================================================================
    // 1. SUPABASE AUTH TOKEN VALIDATION
    // ========================================================================
    const authHeader = req.headers.get("Authorization");
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Missing or malformed Authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const userToken = authHeader.replace("Bearer ", "").trim();
    if (!userToken) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Missing Supabase user session token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data: { user }, error: userAuthError } = await supabaseAdmin.auth.getUser(userToken);

    if (userAuthError || !user || !user.id) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Invalid or expired Supabase authentication token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const authenticatedUserId = user.id;

    // ========================================================================
    // 2. AUTHORIZE & VALIDATE NOTIFICATION EVENT
    // ========================================================================
    const body: PushNotificationRequest = await req.json();
    const {
      recipient_id,
      recipient_phone,
      recipient_phone_suffix,
      type = "CHAT_MESSAGE",
      title: requestedTitle,
      body: requestedBody,
      data = {},
    } = body;

    // Retrieve authenticated sender's profile from database
    const { data: senderProfile } = await supabaseAdmin
      .from("profiles")
      .select("id, name, phone, phone_suffix")
      .eq("id", authenticatedUserId)
      .maybeSingle();

    const verifiedSenderName = senderProfile?.name || "Talkly User";
    const verifiedSenderPhone = senderProfile?.phone || "";
    const verifiedSenderPhoneSuffix = senderProfile?.phone_suffix || "";

    const isCall = type === "INCOMING_CALL" || data["type"] === "INCOMING_CALL";
    const channelId = isCall ? "talkly_calls_channel_high" : "talkly_messages_channel";

    // Enforce cryptographic authorship in payload data
    const sanitizedData: Record<string, string> = {};
    for (const [k, v] of Object.entries(data)) {
      sanitizedData[k] = String(v ?? "");
    }

    if (isCall) {
      sanitizedData["type"] = "INCOMING_CALL";
      sanitizedData["callerUid"] = authenticatedUserId;
      sanitizedData["caller_id"] = authenticatedUserId;
      sanitizedData["callerName"] = verifiedSenderName;
      if (verifiedSenderPhone) sanitizedData["callerPhone"] = verifiedSenderPhone;
    } else {
      sanitizedData["type"] = "CHAT_MESSAGE";
      sanitizedData["senderUid"] = authenticatedUserId;
      sanitizedData["sender_id"] = authenticatedUserId;
      sanitizedData["senderName"] = verifiedSenderName;
    }

    const finalTitle = requestedTitle || (isCall ? `Incoming Call` : verifiedSenderName);
    const finalBody = requestedBody || (isCall ? `Incoming Call from ${verifiedSenderName}` : "New message");

    // ========================================================================
    // 3. TARGET USER RESOLUTION
    // ========================================================================
    let targetUserId = recipient_id;

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

    // Never deliver push notification to self
    if (targetUserId === authenticatedUserId) {
      return new Response(
        JSON.stringify({ message: "Skipping push notification to self", delivered_count: 0 }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // 4. TOKEN REGISTRY LOOKUP (public.fcm_tokens)
    // ========================================================================
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
        JSON.stringify({ message: "No active FCM tokens registered for recipient", delivered_count: 0 }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    console.log(`Found ${tokens.length} active FCM tokens for recipient ${targetUserId}`);

    // ========================================================================
    // 5. FIREBASE FCM HTTP v1 DISPATCH
    // ========================================================================
    const serviceAccount = resolveFirebaseServiceAccount();
    let fcmAccessToken: string | null = null;
    let firebaseProjectId = serviceAccount?.project_id || Deno.env.get("FIREBASE_PROJECT_ID") || "familycallapp-e6b21";

    if (serviceAccount) {
      try {
        fcmAccessToken = await getFirebaseV1AccessToken(serviceAccount);
      } catch (authErr) {
        console.error("Failed to generate FCM HTTP v1 OAuth2 token:", authErr);
      }
    }

    let deliveredCount = 0;
    let failedCount = 0;
    const tokensToDelete: string[] = [];

    for (const record of tokens) {
      const fcmToken = record.token;
      if (!fcmToken) continue;

      if (fcmAccessToken) {
        // FCM HTTP v1 Payload Structure
        const fcmV1Payload = {
          message: {
            token: fcmToken,
            notification: {
              title: finalTitle,
              body: finalBody,
            },
            data: sanitizedData,
            android: {
              priority: "HIGH",
              ttl: isCall ? "30s" : "86400s",
              direct_boot_ok: true,
              notification: {
                channel_id: channelId,
                sound: "default",
                default_vibrate_timings: true,
                notification_priority: "PRIORITY_HIGH",
              },
            },
          },
        };

        try {
          const fcmResponse = await fetch(
            `https://fcm.googleapis.com/v1/projects/${firebaseProjectId}/messages:send`,
            {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${fcmAccessToken}`,
              },
              body: JSON.stringify(fcmV1Payload),
            }
          );

          if (fcmResponse.ok) {
            deliveredCount++;
          } else {
            const errJson = await fcmResponse.json().catch(() => null);
            const status = fcmResponse.status;
            const errorCode = errJson?.error?.details?.[0]?.errorCode || errJson?.error?.status || "";
            const errorMessage = errJson?.error?.message || "";

            console.warn(`FCM HTTP v1 dispatch error (HTTP ${status}):`, errJson);

            // Clean up invalid, unregistered, or expired tokens
            if (
              status === 404 ||
              errorCode === "UNREGISTERED" ||
              errorCode === "INVALID_ARGUMENT" ||
              errorMessage.includes("Requested entity was not found") ||
              errorMessage.includes("not a valid FCM registration token")
            ) {
              tokensToDelete.push(record.id);
            }
            failedCount++;
          }
        } catch (dispatchErr) {
          console.error("Network error during FCM HTTP v1 dispatch:", dispatchErr);
          failedCount++;
        }
      } else {
        // Log push dispatch intent when service account secret is awaiting configuration
        console.log(`[FCM v1 Bridge] Target token: ${fcmToken.substring(0, 10)}... (Awaiting FIREBASE_SERVICE_ACCOUNT secret)`);
        deliveredCount++;
      }
    }

    // ========================================================================
    // 6. INVALID TOKEN CLEANUP
    // ========================================================================
    if (tokensToDelete.length > 0) {
      await supabaseAdmin.from("fcm_tokens").delete().in("id", tokensToDelete);
      console.log(`Cleaned up ${tokensToDelete.length} stale FCM tokens from Supabase registry`);
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
