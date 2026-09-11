import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface DeleteMediaRequest {
  message_id?: string;
  messageId?: string;
  media_url?: string;
  mediaUrl?: string;
}

// SHA-1 helper using Web Crypto API
async function sha1Hex(message: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-1", msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Extract public_id and resource_type from Cloudinary URL
function parseCloudinaryUrl(urlStr: string): { publicId: string; resourceType: string } | null {
  try {
    const url = new URL(urlStr);
    const parts = url.pathname.split("/").filter((p) => p.length > 0);
    // Path looks like: /<cloud_name>/<resource_type>/upload/v<version>/<public_id>.<ext>
    // or: /<cloud_name>/<resource_type>/upload/<public_id>.<ext>
    const uploadIndex = parts.indexOf("upload");
    if (uploadIndex === -1) return null;

    const resourceType = uploadIndex > 0 ? parts[uploadIndex - 1] : "image";
    const postUploadParts = parts.slice(uploadIndex + 1);

    // Skip version prefix (e.g., v1712345678)
    const relevantParts = postUploadParts[0]?.match(/^v\d+$/)
      ? postUploadParts.slice(1)
      : postUploadParts;

    if (relevantParts.length === 0) return null;

    const fullFileName = relevantParts.join("/");
    // Strip file extension if present
    const lastDotIndex = fullFileName.lastIndexOf(".");
    const publicId = lastDotIndex !== -1 ? fullFileName.substring(0, lastDotIndex) : fullFileName;

    return { publicId, resourceType };
  } catch (_e) {
    return null;
  }
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
    const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey);

    const { data: { user }, error: userAuthError } = await supabaseAdmin.auth.getUser(userToken);
    if (userAuthError || !user || !user.id) {
      return new Response(
        JSON.stringify({ success: false, error: "Unauthorized: Invalid or expired authentication token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const body: DeleteMediaRequest = await req.json().catch(() => ({}));
    const messageId = body.message_id || body.messageId;
    let targetMediaUrl = body.media_url || body.mediaUrl;

    // 2. Reject requests without a valid messageId (do NOT trust arbitrary client-provided URLs)
    if (!messageId) {
      return new Response(
        JSON.stringify({ success: false, error: "Missing message_id parameter to authorize deletion" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Verify message exists and caller is authorized
    const { data: messageRecord, error: msgError } = await supabaseAdmin
      .from("messages")
      .select("id, sender_id, receiver_id, media_url, is_deleted_for_everyone")
      .eq("id", messageId)
      .maybeSingle();

    if (msgError || !messageRecord) {
      return new Response(
        JSON.stringify({ success: false, error: "Message not found" }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Caller must be sender or receiver of the message
    if (messageRecord.sender_id !== user.id && messageRecord.receiver_id !== user.id) {
      return new Response(
        JSON.stringify({ success: false, error: "Forbidden: Not authorized to delete media for this message" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // If media_url was not provided, use the message's recorded media_url
    if (!targetMediaUrl && messageRecord.media_url) {
      targetMediaUrl = messageRecord.media_url;
    }

    // If message already had its media URL cleared or was already deleted, and targetMediaUrl is provided,
    // verify it matches what was recorded if recorded media_url is still present
    if (messageRecord.media_url && targetMediaUrl && messageRecord.media_url !== targetMediaUrl) {
      return new Response(
        JSON.stringify({ success: false, error: "Target media URL does not match message record" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!targetMediaUrl) {
      // Message has no media to delete
      return new Response(
        JSON.stringify({ success: true, message: "No media associated with this message" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const cloudName = Deno.env.get("CLOUDINARY_CLOUD_NAME") || "tsnijtq5";
    const apiKey = Deno.env.get("CLOUDINARY_API_KEY");
    const apiSecret = Deno.env.get("CLOUDINARY_API_SECRET");

    if (!apiKey || !apiSecret) {
      console.warn("CLOUDINARY_API_KEY or CLOUDINARY_API_SECRET not configured on server");
      return new Response(
        JSON.stringify({ success: false, error: "Cloudinary credentials not configured on server" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const parsed = parseCloudinaryUrl(targetMediaUrl);
    if (!parsed || !parsed.publicId) {
      return new Response(
        JSON.stringify({ success: false, error: "Could not parse Cloudinary public_id from media URL" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const targetPublicId = parsed.publicId;
    const resourceType = parsed.resourceType || "image";

    // 4. Compute signature with invalidate=true in alphabetical parameter order
    const timestamp = Math.floor(Date.now() / 1000).toString();
    const stringToSign = `invalidate=true&public_id=${targetPublicId}&timestamp=${timestamp}${apiSecret}`;
    const signature = await sha1Hex(stringToSign);

    const formData = new URLSearchParams();
    formData.append("public_id", targetPublicId);
    formData.append("timestamp", timestamp);
    formData.append("api_key", apiKey);
    formData.append("invalidate", "true");
    formData.append("signature", signature);

    // Call Cloudinary destroy API: /v1_1/<cloud_name>/<resource_type>/destroy
    const destroyUrl = `https://api.cloudinary.com/v1_1/${cloudName}/${resourceType}/destroy`;
    const response = await fetch(destroyUrl, {
      method: "POST",
      body: formData,
    });

    const result = await response.json();
    console.log(`Cloudinary destroy result for message ${messageId} (${targetPublicId}):`, result);

    return new Response(
      JSON.stringify({ success: true, result }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: any) {
    console.error("Error in delete-cloudinary-media Edge Function:", err);
    return new Response(
      JSON.stringify({ success: false, error: err.message || "Unknown error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
