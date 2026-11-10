// Service worker de Loba : l'appli fonctionne hors ligne (Kinshasa, coupures réseau…).
// Change VERSION à chaque mise en ligne pour forcer la mise à jour du cache.
const VERSION = 'loba-v8-0';
const SHELL = ['./', 'index.html', 'privacy.html', 'manifest.webmanifest',
  'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png', 'icons/apple-touch-icon.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // Ne jamais mettre en cache Supabase (données personnelles, authentification).
  if (url.hostname.endsWith('supabase.co')) return;

  // Pages : réseau d'abord (pour avoir la dernière version), cache si hors ligne.
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).then((res) => {
      const copy = res.clone();
      caches.open(VERSION).then((c) => c.put('index.html', copy));
      return res;
    }).catch(() => caches.match('index.html')));
    return;
  }
  // Fichiers statiques et polices : cache d'abord.
  e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
    if (res.ok && (url.origin === location.origin || url.hostname.includes('fonts.') || url.hostname === 'cdn.jsdelivr.net')) {
      const copy = res.clone();
      caches.open(VERSION).then((c) => c.put(req, copy));
    }
    return res;
  })));
});

// Rappel quotidien : la notification envoyée par le serveur (fonction daily-reminder).
self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (err) { d = { body: e.data && e.data.text() }; }
  const title = typeof d.title === 'string' ? d.title.slice(0, 80) : 'Loba';
  e.waitUntil(self.registration.showNotification(title, {
    body: typeof d.body === 'string' ? d.body.slice(0, 200) : '',
    icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', tag: 'loba-daily', renotify: true,
    data: { url: './' },
  }));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    for (const c of list) if ('focus' in c) return c.focus();
    return self.clients.openWindow('./');
  }));
});
