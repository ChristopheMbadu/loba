import { randomBytes, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { hashToken, verifyPassword, validateLesson, sniffAudio, AUDIO_MAX_BYTES, fail } from './security.js';

async function body(req) {
  let size = 0; const chunks = [];
  for await (const chunk of req) { size += Buffer.byteLength(chunk); if (size > 200_000) throw fail(413, 'Request too large.'); chunks.push(Buffer.from(chunk)); }
  try { return JSON.parse(Buffer.concat(chunks).toString()); } catch { throw fail(400, 'Invalid JSON request.'); }
}
const LEARN_CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; media-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
const LEARN_MIME = {js:'text/javascript; charset=utf-8',css:'text/css; charset=utf-8',json:'application/json; charset=utf-8',woff2:'font/woff2',woff:'font/woff',png:'image/png',svg:'image/svg+xml',webmanifest:'application/manifest+json; charset=utf-8'};

// The learner app (learn/) is a built single-page app. Only hashed files in its assets
// folder and its manifest are served; every other extension-less /learn path gets index.html.
async function serveLearn(res, path, method, learnDir) {
  res.setHeader('Content-Security-Policy', LEARN_CSP);
  const asset = path.match(/^\/learn\/assets\/([A-Za-z0-9_-][A-Za-z0-9._-]{0,200})$/)?.[1];
  const ext = (asset || path).split('/').at(-1).includes('.') ? (asset || path).split('.').at(-1) : '';
  let file;
  if (asset && LEARN_MIME[ext]) file = 'assets/' + asset;
  else if (path === '/learn/manifest.webmanifest') file = 'manifest.webmanifest';
  else if (!ext) file = 'index.html';
  else throw fail(404, 'Page not found.');
  let data;
  try { data = await readFile(new URL(file, learnDir)); }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    throw file === 'index.html' ? fail(503, 'The learner app has not been built. Run npm run build:learn.') : fail(404, 'Page not found.');
  }
  // Hashed asset names change with their content, so they can be cached forever.
  if (asset) res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
  res.writeHead(200, {'Content-Type': file === 'index.html' ? 'text/html; charset=utf-8' : LEARN_MIME[file.split('.').at(-1)]});
  res.end(method === 'HEAD' ? undefined : data);
}

async function bytes(req, max) {
  let size = 0; const chunks = [];
  for await (const chunk of req) { size += chunk.length; if (size > max) throw fail(413, 'This recording is too large. Keep it under 1.5 MB.'); chunks.push(Buffer.from(chunk)); }
  return Buffer.concat(chunks);
}
// Safari only plays media served with byte-range support.
function sendAudio(req, res, {mime, data}) {
  const headers = {'Content-Type': mime, 'Accept-Ranges': 'bytes', 'Cache-Control': 'public, max-age=31536000, immutable'};
  const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || '');
  if (range && (range[1] || range[2])) {
    let start = range[1] ? Number(range[1]) : Math.max(0, data.length - Number(range[2]));
    let end = range[1] && range[2] ? Math.min(Number(range[2]), data.length - 1) : data.length - 1;
    if (start > end || start >= data.length) { res.writeHead(416, {'Content-Range': `bytes */${data.length}`}); return res.end(); }
    res.writeHead(206, {...headers, 'Content-Range': `bytes ${start}-${end}/${data.length}`, 'Content-Length': end - start + 1});
    return res.end(req.method === 'HEAD' ? undefined : data.subarray(start, end + 1));
  }
  res.writeHead(200, {...headers, 'Content-Length': data.length});
  res.end(req.method === 'HEAD' ? undefined : data);
}

export function createApp(store, {origin, production = false, learnDir = new URL('../learn/dist/', import.meta.url)}) {
  const cookieName = production ? '__Host-loba_session' : 'loba_session';
  const cookie = (token, age) => `${cookieName}=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${age}${production ? '; Secure' : ''}`;
  return async (req, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'same-origin');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Cache-Control', 'no-store');
    if (production) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
    const json = (status, data) => { res.writeHead(status, {'Content-Type':'application/json; charset=utf-8'}); res.end(JSON.stringify(data)); };
    try {
      const path = new URL(req.url, origin).pathname;
      const method = req.method;
      if (method === 'GET' && path === '/health') { await store.healthy(); return json(200, {status:'ok'}); }
      // Native clients need no cookies to read published content. Drafts are never returned here.
      if (method === 'GET' && path === '/api/v1/lessons') return json(200, {schemaVersion:1, lessons:await store.published()});
      // Teacher recordings are public once referenced by a lesson; ids are unguessable.
      const recording = path.match(/^\/api\/v1\/audio\/([0-9a-f-]{36})$/);
      if (recording && ['GET','HEAD'].includes(method)) {
        const audio = await store.audio(recording[1]);
        if (!audio) throw fail(404, 'Recording not found.');
        return sendAudio(req, res, audio);
      }
      if (path.startsWith('/api/')) {
        if (!['GET','HEAD'].includes(method)) {
          if (req.headers.origin !== origin) throw fail(403, 'Request origin is not allowed.');
          if (path !== '/api/admin/audio' && !(req.headers['content-type'] || '').startsWith('application/json')) throw fail(415, 'Use application/json.');
        }
        if (path === '/api/auth/signup' && method === 'POST') {
          const input=await body(req);
          if (!input || typeof input.email !== 'string' || input.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim()) || typeof input.password !== 'string' || input.password.length < 14 || input.password.length > 256) throw fail(400,'Enter a valid email and a password of 14–256 characters.');
          const email=input.email.trim().toLowerCase();
          const address=production ? (String(req.headers['x-forwarded-for'] || '').split(',').at(-1).trim() || req.socket.remoteAddress) : req.socket.remoteAddress;
          const allowed=await store.loginAllowed(hashToken(`signup-address:${address}`));
          const emailAllowed=await store.loginAllowed(hashToken(`signup-email:${email}`));
          if(!allowed || !emailAllowed) { res.setHeader('Retry-After','900'); throw fail(429,'Too many sign-up attempts. Try again in 15 minutes.'); }
          await store.register(email,input.password);
          return json(201,{message:'Account created. An administrator must approve your access before you can manage lessons.'});
        }
        if (path === '/api/auth/login' && method === 'POST') {
          const input = await body(req);
          if (!input || typeof input.email !== 'string' || input.email.length > 254 || typeof input.password !== 'string' || input.password.length > 256) throw fail(400, 'Enter your email and password.');
          const email = input.email.toLowerCase().trim();
          // Railway's last forwarded address is the trusted ingress address; never trust the first.
          const address = production ? (String(req.headers['x-forwarded-for'] || '').split(',').at(-1).trim() || req.socket.remoteAddress) : req.socket.remoteAddress;
          const allowed = await store.loginAllowed(hashToken(`email:${email}`));
          const ipAllowed = await store.loginAllowed(hashToken(`address:${address}`));
          if (!allowed || !ipAllowed) { res.setHeader('Retry-After','900'); throw fail(429, 'Too many sign-in attempts. Try again in 15 minutes.'); }
          const admin = await store.admin(email);
          const valid = await verifyPassword(input.password, admin?.password_hash);
          if (!admin || !valid) throw fail(401, 'Email or password is incorrect.');
          if (admin.approved === false) throw fail(403, 'Your account is awaiting administrator approval.');
          const token = randomBytes(32).toString('hex');
          await store.createSession(hashToken(token), admin.id);
          res.setHeader('Set-Cookie',cookie(token,43200));
          return json(200,{user:{email:admin.email}});
        }
        const token = String(req.headers.cookie || '').split(';').map(v=>v.trim()).find(v=>v.startsWith(cookieName+'='))?.slice(cookieName.length+1) || '';
        const tokenHash = hashToken(token);
        const user = token ? await store.session(tokenHash) : null;
        if (!user) throw fail(401, 'Sign in to continue.');
        if (path === '/api/auth/me' && method === 'GET') return json(200,{user});
        if (path === '/api/auth/logout' && method === 'POST') { await store.logout(tokenHash); res.setHeader('Set-Cookie',cookie('',0)); return json(200,{ok:true}); }
        if (path === '/api/admin/accounts' && method === 'GET') return json(200,{accounts:await store.pendingAccounts()});
        const approval=path.match(/^\/api\/admin\/accounts\/([0-9a-f-]{36})\/approve$/i);
        if (approval && method === 'POST') { await store.approveAccount(approval[1],user.email); return json(200,{ok:true}); }
        if (path === '/api/admin/audio' && method === 'POST') {
          const data = await bytes(req, AUDIO_MAX_BYTES);
          const mime = sniffAudio(data);
          if (!mime) throw fail(415, 'Upload an audio recording (MP3, M4A, WAV, OGG or WebM).');
          const id = randomUUID();
          await store.saveAudio({id, mime, data, email: user.email});
          return json(201, {id});
        }
        if (path === '/api/admin/lessons' && method === 'GET') return json(200,{lessons:await store.lessons(),activity:await store.activity()});
        if (path === '/api/admin/lessons' && method === 'POST') {
          const input = await body(req);
          return json(201,{lesson:await store.mutate({id:randomUUID(),content:validateLesson(input),action:'created',email:user.email})});
        }
        const match = path.match(/^\/api\/admin\/lessons\/([a-zA-Z0-9-]+)(?:\/(publish|unpublish))?$/);
        if (match && (method==='PUT' && !match[2] || method==='DELETE' && !match[2] || method==='POST' && match[2])) {
          const input = await body(req);
          const action = method==='PUT'?'saved':method==='DELETE'?'deleted':match[2]==='publish'?'published':'unpublished';
          if (!input || typeof input !== 'object') throw fail(400, 'Invalid lesson request.');
          const content = action==='saved'?validateLesson(input):undefined;
          return json(200,{lesson:await store.mutate({id:match[1],content,version:input.version,action,email:user.email})});
        }
        throw fail(404, 'Endpoint not found.');
      }
      if (method !== 'GET' && method !== 'HEAD') throw fail(405, 'Method not allowed.');
      if (path === '/learn' || path.startsWith('/learn/')) return await serveLearn(res, path, method, learnDir);
      const files = {'/admin/tabler.css':'../node_modules/@tabler/core/dist/css/tabler.min.css','/':'../admin/index.html','/admin':'../admin/index.html','/admin/':'../admin/index.html','/admin/app.js':'../admin/app.js','/admin/style.css':'../admin/style.css','/privacy.html':'../privacy.html','/manifest.webmanifest':'../manifest.webmanifest','/icons/icon-192.png':'../icons/icon-192.png','/icons/icon-512.png':'../icons/icon-512.png','/icons/apple-touch-icon.png':'../icons/apple-touch-icon.png'};
      const file = path === '/signup' || path === '/signup/' ? '../admin/index.html' : files[path]; if (!file) throw fail(404,'Page not found.');
      res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
      const ext = file.split('.').at(-1);
      const mime = {json:'application/json',html:'text/html',js:'text/javascript',css:'text/css',png:'image/png',webmanifest:'application/manifest+json'}[ext];
      const data = await readFile(new URL(file,import.meta.url));
      res.writeHead(200,{'Content-Type':mime + (ext==='png'?'':'; charset=utf-8')}); res.end(method==='HEAD'?undefined:data);
    } catch(error) {
      if (!error.status) console.error('Request failed:',error.code || error.name);
      json(error.status || 500,{error:error.status?error.message:'Something went wrong. Please try again.'});
    }
  };
}
