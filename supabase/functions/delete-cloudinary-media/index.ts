import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface DeleteMediaRequest {
  media_url?: string;
  public_id?: string;
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
    const body: DeleteMediaRequest = await req.json().catch(() => ({}));
    const { media_url, public_id } = body;

    if (!media_url && !public_id) {
      return new Response(
        JSON.stringify({ success: false, error: "Missing media_url or public_id parameter" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const cloudName = Deno.env.get("CLOUDINARY_CLOUD_NAME") || "tsnijtq5";
    const apiKey = Deno.env.get("CLOUDINARY_API_KEY");
    const apiSecret = Deno.env.get("CLOUDINARY_API_SECRET");

    if (!apiKey || !apiSecret) {
      console.warn("CLOUDINARY_API_KEY or CLOUDINARY_API_SECRET not set in environment");
      return new Response(
        JSON.stringify({ success: false, error: "Cloudinary credentials not configured on server" }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    let targetPublicId = public_id;
    let resourceType = "image";

    if (media_url) {
      const parsed = parseCloudinaryUrl(media_url);
      if (parsed) {
        targetPublicId = targetPublicId || parsed.publicId;
        resourceType = parsed.resourceType || "image";
      }
    }

    if (!targetPublicId) {
      return new Response(
        JSON.stringify({ success: false, error: "Could not resolve public_id from request" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const timestamp = Math.floor(Date.now() / 1000).toString();
    const stringToSign = `public_id=${targetPublicId}&timestamp=${timestamp}${apiSecret}`;
    const signature = await sha1Hex(stringToSign);

    const formData = new URLSearchParams();
    formData.append("public_id", targetPublicId);
    formData.append("timestamp", timestamp);
    formData.append("api_key", apiKey);
    formData.append("signature", signature);

    // Call Cloudinary destroy API: /v1_1/<cloud_name>/<resource_type>/destroy
    const destroyUrl = `https://api.cloudinary.com/v1_1/${cloudName}/${resourceType}/destroy`;
    const response = await fetch(destroyUrl, {
      method: "POST",
      body: formData,
    });

    const result = await response.json();
    console.log(`Cloudinary destroy result for ${targetPublicId} (${resourceType}):`, result);

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
