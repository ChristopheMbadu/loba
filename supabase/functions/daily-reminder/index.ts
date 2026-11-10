// Loba — rappel quotidien.
// Appelée chaque heure par pg_cron. Pour chaque abonné dont c'est l'heure choisie (dans son fuseau)
// et qui n'a pas encore pratiqué aujourd'hui, envoie une notification Web Push (RFC 8030 / 8291 / 8292).
// Secrets : VAPID_JWK (clé privée ES256 au format JWK), VAPID_SUBJECT (mailto:…), CRON_SECRET.

const enc = new TextEncoder();
const b64u = (b: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const unb64u = (s: string) => {
  const b = atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4));
  return Uint8Array.from(b, (c) => c.charCodeAt(0));
};
const cat = (...a: Uint8Array[]) => {
  const out = new Uint8Array(a.reduce((n, x) => n + x.length, 0));
  let o = 0;
  for (const x of a) { out.set(x, o); o += x.length; }
  return out;
};

async function hkdf(salt: Uint8Array, ikm: Uint8Array, info: Uint8Array, len: number) {
  const k = await crypto.subtle.importKey("raw", ikm, "HKDF", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt, info }, k, len * 8));
}

// Chiffrement du message (aes128gcm, RFC 8291).
export async function encryptPayload(p256dh: string, auth: string, payload: string) {
  const uaPub = unb64u(p256dh), authSecret = unb64u(auth);
  const as = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const asPub = new Uint8Array(await crypto.subtle.exportKey("raw", as.publicKey));
  const uaKey = await crypto.subtle.importKey("raw", uaPub, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: uaKey }, as.privateKey, 256));
  const ikm = await hkdf(authSecret, shared, cat(enc.encode("WebPush: info\0"), uaPub, asPub), 32);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const cek = await hkdf(salt, ikm, enc.encode("Content-Encoding: aes128gcm\0"), 16);
  const nonce = await hkdf(salt, ikm, enc.encode("Content-Encoding: nonce\0"), 12);
  const key = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["encrypt"]);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, key, cat(enc.encode(payload), new Uint8Array([2]))));
  const rs = new Uint8Array([0, 0, 16, 0]); // 4096
  return cat(salt, rs, new Uint8Array([asPub.length]), asPub, ct);
}

// En-tête d'autorisation VAPID (JWT ES256, RFC 8292).
export async function vapidHeader(endpoint: string, jwk: JsonWebKey, subject: string) {
  const aud = new URL(endpoint).origin;
  const h = b64u(enc.encode(JSON.stringify({ typ: "JWT", alg: "ES256" })));
  const c = b64u(enc.encode(JSON.stringify({ aud, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: subject })));
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, enc.encode(`${h}.${c}`));
  const pub = cat(new Uint8Array([4]), unb64u(jwk.x!), unb64u(jwk.y!));
  return `vapid t=${h}.${c}.${b64u(sig)}, k=${b64u(pub)}`;
}

// Même format que new Date().toDateString() côté appli, mais dans le fuseau de l'utilisateur.
export function localDay(tz: string, d = new Date()) {
  const p = Object.fromEntries(new Intl.DateTimeFormat("en-US", { timeZone: tz, weekday: "short", month: "short", day: "2-digit", year: "numeric" })
    .formatToParts(d).map((x) => [x.type, x.value]));
  return `${p.weekday} ${p.month} ${p.day} ${p.year}`;
}
export function localHour(tz: string, d = new Date()) {
  return Number(new Intl.DateTimeFormat("en-US", { timeZone: tz, hour: "numeric", hourCycle: "h23" }).format(d));
}
export function yesterdayDay(tz: string) { return localDay(tz, new Date(Date.now() - 864e5)); }

const NAMES: Record<string, Record<string, string>> = { ln: { fr: "lingala", en: "Lingala" }, sw: { fr: "swahili", en: "Swahili" } };
export function message(ui: string, lang: string, streak: number, name: string) {
  const en = ui === "en", l = (NAMES[lang] || NAMES.ln)[en ? "en" : "fr"], hi = lang === "sw" ? "Jambo" : "Mbote";
  const who = name ? ` ${name}` : "";
  if (streak > 0) return {
    title: en ? `🔥 Your ${streak}-day streak is waiting!` : `🔥 Ta série de ${streak} jour${streak > 1 ? "s" : ""} t'attend !`,
    body: en ? `${hi}${who}! 5 minutes of ${l} to keep it alive.` : `${hi}${who} ! 5 minutes de ${l} pour la garder.`,
  };
  return {
    title: en ? `${hi}${who}! 🦜` : `${hi}${who} ! 🦜`,
    body: en ? `Your ${l} lesson of the day is ready. 5 minutes is enough!` : `Ta leçon de ${l} du jour t'attend. 5 minutes suffisent !`,
  };
}

const ALLOWED = /^https:\/\/([a-z0-9-]+\.)*(fcm\.googleapis\.com|push\.services\.mozilla\.com|push\.apple\.com|notify\.windows\.com|android\.googleapis\.com)\//;

Deno.serve(async (req) => {
  if (req.headers.get("x-cron-secret") !== Deno.env.get("CRON_SECRET")) return new Response("forbidden", { status: 403 });
  const url = Deno.env.get("SUPABASE_URL")!, key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const jwk = JSON.parse(Deno.env.get("VAPID_JWK")!), subject = Deno.env.get("VAPID_SUBJECT") || "mailto:contact@example.com";
  const rest = (path: string, init: RequestInit = {}) =>
    fetch(`${url}/rest/v1/${path}`, { ...init, headers: { apikey: key, Authorization: `Bearer ${key}`, "Content-Type": "application/json", ...(init.headers || {}) } });

  const subs: any[] = await (await rest("push_subs?select=endpoint,user_id,p256dh,auth,hour,tz,ui,last_sent")).json();
  const users = [...new Set(subs.map((s) => s.user_id))];
  const prog: Record<string, any> = {};
  for (let i = 0; i < users.length; i += 100) {
    const ids = users.slice(i, i + 100).join(",");
    const rows: any[] = await (await rest(`progress?select=user_id,data&user_id=in.(${ids})`)).json();
    for (const r of rows) prog[r.user_id] = r.data || {};
  }
  let sent = 0, gone = 0, skipped = 0;
  for (const s of subs) {
    let tz = "UTC";
    try { localHour(s.tz); tz = s.tz; } catch { /* fuseau invalide */ }
    const day = localDay(tz), d = prog[s.user_id] || {};
    if (localHour(tz) !== s.hour || s.last_sent === day || d.lastDay === day || !ALLOWED.test(s.endpoint)) { skipped++; continue; }
    const streak = d.lastDay === yesterdayDay(tz) ? Number(d.streak) || 0 : 0;
    const name = typeof d.prof?.name === "string" ? d.prof.name.slice(0, 20) : "";
    const body = await encryptPayload(s.p256dh, s.auth, JSON.stringify(message(s.ui, d.lang, streak, name)));
    const r = await fetch(s.endpoint, {
      method: "POST",
      headers: { Authorization: await vapidHeader(s.endpoint, jwk, subject), "Content-Encoding": "aes128gcm", "Content-Type": "application/octet-stream", TTL: "43200", Urgency: "normal" },
      body,
    });
    const ep = encodeURIComponent(s.endpoint);
    if (r.status === 404 || r.status === 410) { await rest(`push_subs?endpoint=eq.${ep}`, { method: "DELETE" }); gone++; }
    else if (r.ok) { await rest(`push_subs?endpoint=eq.${ep}`, { method: "PATCH", body: JSON.stringify({ last_sent: day }) }); sent++; }
  }
  return Response.json({ sent, gone, skipped, total: subs.length });
});
