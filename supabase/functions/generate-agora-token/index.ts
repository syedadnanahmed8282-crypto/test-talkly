import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

// CORS headers matching project standard
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface TokenRequest {
  channelId: string;
  uid: number | string;
  role?: "publisher" | "subscriber";
  expireSeconds?: number;
}

// CRC32 table initialization for IEEE 802.3 polynomial (0xEDB88320)
function makeCrcTable(): Uint32Array {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[i] = c;
  }
  return table;
}

const crcTable = makeCrcTable();

function crc32(str: string): number {
  const bytes = new TextEncoder().encode(str);
  let crc = 0 ^ (-1);
  for (let i = 0; i < bytes.length; i++) {
    crc = (crc >>> 8) ^ crcTable[(crc ^ bytes[i]) & 0xFF];
  }
  return (crc ^ (-1)) >>> 0;
}

/**
 * Byte buffer writer implementing Little-Endian serialization for Agora 006 Token Format.
 */
class ByteBuf {
  private buffer: Uint8Array;
  private view: DataView;
  private position: number;

  constructor(initialCapacity = 1024) {
    this.buffer = new Uint8Array(initialCapacity);
    this.view = new DataView(this.buffer.buffer);
    this.position = 0;
  }

  private ensureCapacity(bytesNeeded: number) {
    if (this.position + bytesNeeded > this.buffer.length) {
      const newBuffer = new Uint8Array(Math.max(this.buffer.length * 2, this.position + bytesNeeded));
      newBuffer.set(this.buffer);
      this.buffer = newBuffer;
      this.view = new DataView(this.buffer.buffer);
    }
  }

  putUint16(v: number): this {
    this.ensureCapacity(2);
    this.view.setUint16(this.position, v, true); // Little Endian
    this.position += 2;
    return this;
  }

  putUint32(v: number): this {
    this.ensureCapacity(4);
    this.view.setUint32(this.position, v, true); // Little Endian
    this.position += 4;
    return this;
  }

  putBytes(bytes: Uint8Array): this {
    this.putUint16(bytes.length);
    this.ensureCapacity(bytes.length);
    this.buffer.set(bytes, this.position);
    this.position += bytes.length;
    return this;
  }

  putString(str: string): this {
    return this.putBytes(new TextEncoder().encode(str));
  }

  putTreeMap(map: Record<number, number>): this {
    const keys = Object.keys(map).map(Number).sort((a, b) => a - b);
    this.putUint16(keys.length);
    for (const k of keys) {
      this.putUint16(k);
      this.putUint32(map[k]);
    }
    return this;
  }

  pack(): Uint8Array {
    return this.buffer.subarray(0, this.position);
  }
}

/**
 * Computes HMAC-SHA256 using standard Web Crypto API.
 */
async function hmacSha256(key: string, data: Uint8Array): Promise<Uint8Array> {
  const keyBytes = new TextEncoder().encode(key);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", cryptoKey, data);
  return new Uint8Array(signature);
}

/**
 * Converts a Uint8Array into a Base64-encoded string.
 */
function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = "";
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// Agora Privileges constants
const Privileges = {
  kJoinChannel: 1,
  kPublishAudioStream: 2,
  kPublishVideoStream: 3,
  kPublishDataStream: 4,
};

/**
 * Generates an Agora RTC v006 dynamic token.
 */
async function buildAgoraRtcToken(
  appId: string,
  appCertificate: string,
  channelId: string,
  uid: number | string,
  role: "publisher" | "subscriber",
  privilegeExpireTs: number
): Promise<string> {
  const version = "006";
  const uidStr = (!uid || uid === 0 || uid === "0") ? "" : String(uid);
  const encoder = new TextEncoder();

  // Salt and signing timestamp
  const salt = Math.floor(Math.random() * 0xFFFFFFFF);
  const currentTs = Math.floor(Date.now() / 1000);

  const messagesBuf = new ByteBuf();
  messagesBuf.putUint32(salt);
  messagesBuf.putUint32(currentTs);

  const privileges: Record<number, number> = {};
  privileges[Privileges.kJoinChannel] = privilegeExpireTs;
  if (role === "publisher") {
    privileges[Privileges.kPublishAudioStream] = privilegeExpireTs;
    privileges[Privileges.kPublishVideoStream] = privilegeExpireTs;
    privileges[Privileges.kPublishDataStream] = privilegeExpireTs;
  } else {
    privileges[Privileges.kPublishAudioStream] = 0;
    privileges[Privileges.kPublishVideoStream] = 0;
    privileges[Privileges.kPublishDataStream] = 0;
  }
  messagesBuf.putTreeMap(privileges);
  const messageBytes = messagesBuf.pack();

  // Prepare payload for HMAC-SHA256 signature
  const appIdBytes = encoder.encode(appId);
  const channelBytes = encoder.encode(channelId);
  const uidBytes = encoder.encode(uidStr);

  const toSign = new Uint8Array(
    appIdBytes.length + channelBytes.length + uidBytes.length + messageBytes.length
  );
  let offset = 0;
  toSign.set(appIdBytes, offset); offset += appIdBytes.length;
  toSign.set(channelBytes, offset); offset += channelBytes.length;
  toSign.set(uidBytes, offset); offset += uidBytes.length;
  toSign.set(messageBytes, offset);

  const signature = await hmacSha256(appCertificate, toSign);

  // Compute CRC32 for channel name and uid
  const crcChannelBuf = new ByteBuf();
  crcChannelBuf.putUint32(crc32(channelId));
  const crcChannelBytes = crcChannelBuf.pack();

  const crcUidBuf = new ByteBuf();
  crcUidBuf.putUint32(crc32(uidStr));
  const crcUidBytes = crcUidBuf.pack();

  // Pack final token content: signature + crcChannel + crcUid + messageBytes
  const content = new Uint8Array(
    signature.length + crcChannelBytes.length + crcUidBytes.length + messageBytes.length
  );
  let cOffset = 0;
  content.set(signature, cOffset); cOffset += signature.length;
  content.set(crcChannelBytes, cOffset); cOffset += crcChannelBytes.length;
  content.set(crcUidBytes, cOffset); cOffset += crcUidBytes.length;
  content.set(messageBytes, cOffset);

  return `${version}${appId}${uint8ArrayToBase64(content)}`;
}

serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing Supabase backend configuration" }),
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

    // ========================================================================
    // 2. PARSE REQUEST BODY & ENVIRONMENT SECRETS
    // ========================================================================
    const body: TokenRequest = await req.json();
    const { channelId, uid, role = "publisher", expireSeconds = 3600 } = body;

    if (!channelId || typeof channelId !== "string" || channelId.trim() === "") {
      return new Response(
        JSON.stringify({ error: "Missing or invalid 'channelId' in request body" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (uid === undefined || uid === null) {
      return new Response(
        JSON.stringify({ error: "Missing 'uid' in request body" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const agoraAppId = Deno.env.get("AGORA_APP_ID")?.trim() ?? "";
    const agoraAppCertificate = Deno.env.get("AGORA_APP_CERTIFICATE")?.trim() ?? "";

    if (!agoraAppId || !agoraAppCertificate) {
      return new Response(
        JSON.stringify({ error: "Server missing AGORA_APP_ID or AGORA_APP_CERTIFICATE environment secrets" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ========================================================================
    // 3. GENERATE AGORA RTC TOKEN
    // ========================================================================
    const nowSec = Math.floor(Date.now() / 1000);
    const expiresAt = nowSec + expireSeconds;

    const token = await buildAgoraRtcToken(
      agoraAppId,
      agoraAppCertificate,
      channelId.trim(),
      uid,
      role,
      expiresAt
    );

    return new Response(
      JSON.stringify({
        token,
        expiresAt,
        channelId: channelId.trim(),
        uid,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    return new Response(
      JSON.stringify({ error: `Internal Server Error: ${errorMsg}` }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
