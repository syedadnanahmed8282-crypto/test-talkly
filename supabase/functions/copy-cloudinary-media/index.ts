import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface CopyMediaRequest {
  media_url?: string;
  mediaUrl?: string;
  message_id?: string;
  messageId?: string;
  resource_type?: string;
  resourceType?: string;
}

// SHA-1 helper using Web Crypto API
async function sha1Hex(message: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-1", msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // 1. Authenticate caller using Supabase session token
    const authHeader = req.headers.get("Authorization") ?? "";
    const userToken = authHeader.replace(/^Bearer\s+/i, "").trim();

    if (!userToken) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: Missing authentication token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!supabaseUrl || !serviceRoleKey) {
      return new Response(
        JSON.stringify({ success: false, error: "Missing Supabase server configuration" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey);
    const { data: { user }, error: userAuthError } = await supabaseAdmin.auth.getUser(userToken);
    if (userAuthError || !user || !user.id) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: Invalid or expired authentication token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const body: CopyMediaRequest = await req.json().catch(() => ({}));
    const targetMediaUrl = body.media_url || body.mediaUrl;

    if (!targetMediaUrl || typeof targetMediaUrl !== "string") {
      return new Response(
        JSON.stringify({ success: false, ok: false, error: "Missing media_url parameter" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const cloudName = Deno.env.get("CLOUDINARY_CLOUD_NAME") || "tsnijtq5";
    const apiKey = Deno.env.get("CLOUDINARY_API_KEY");
    const apiSecret = Deno.env.get("CLOUDINARY_API_SECRET");

    if (!apiKey || !apiSecret) {
      console.warn("CLOUDINARY_API_KEY or CLOUDINARY_API_SECRET not configured on server");
      return new Response(
        JSON.stringify({
          success: false,
          ok: false,
          error: "Cloudinary credentials not configured on server (CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET missing in Supabase Edge Function Secrets)"
        }),
        { status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 2. Perform signed Cloudinary upload by remote URL to create an independent asset copy
    const timestamp = Math.floor(Date.now() / 1000).toString();
    const folder = "talkly_media/forwarded";
    
    // Sort parameters alphabetically for signing: folder, timestamp
    const stringToSign = `folder=${folder}&timestamp=${timestamp}${apiSecret}`;
    const signature = await sha1Hex(stringToSign);

    const formData = new FormData();
    formData.append("file", targetMediaUrl);
    formData.append("folder", folder);
    formData.append("timestamp", timestamp);
    formData.append("api_key", apiKey);
    formData.append("signature", signature);

    const uploadEndpoint = `https://api.cloudinary.com/v1_1/${cloudName}/auto/upload`;
    const response = await fetch(uploadEndpoint, {
      method: "POST",
      body: formData,
    });

    const result = await response.json().catch(() => ({}));
    console.log(`Cloudinary copy upload response status=${response.status}, ok=${response.ok}`);

    const secureUrl = result?.secure_url || result?.url;
    if (!response.ok || !secureUrl) {
      const errorMsg = result?.error?.message || `HTTP ${response.status}`;
      return new Response(
        JSON.stringify({
          success: false,
          ok: false,
          error: `Cloudinary copy failed: ${errorMsg}`,
          result,
        }),
        { status: response.ok ? 400 : response.status, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        ok: true,
        secure_url: secureUrl,
        public_id: result?.public_id,
        resource_type: result?.resource_type,
        bytes: result?.bytes,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    console.error("Error in copy-cloudinary-media Edge Function:", err);
    return new Response(
      JSON.stringify({ success: false, error: err.message || "Unknown error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
