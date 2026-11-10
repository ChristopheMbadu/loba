-- Rappels quotidiens : abonnements Web Push (un par appareil).
create table if not exists public.push_subs (
  endpoint   text primary key,
  user_id    uuid not null references auth.users(id) on delete cascade,
  p256dh     text not null,
  auth       text not null,
  hour       smallint not null default 19,
  tz         text not null default 'UTC',
  ui         text not null default 'fr',
  last_sent  text,
  created_at timestamptz not null default now(),
  -- Seuls les vrais services de notification sont acceptés (pas d'URL arbitraire).
  constraint push_endpoint check (endpoint ~ '^https://([a-z0-9-]+\.)*(fcm\.googleapis\.com|push\.services\.mozilla\.com|push\.apple\.com|notify\.windows\.com|android\.googleapis\.com)/' and length(endpoint) < 1000),
  constraint push_keys check (length(p256dh) between 80 and 100 and length(auth) between 16 and 32),
  constraint push_hour check (hour between 0 and 23),
  constraint push_tz check (length(tz) < 64),
  constraint push_ui check (ui in ('fr','en'))
);
create index if not exists push_subs_user on public.push_subs(user_id);

alter table public.push_subs enable row level security;
revoke all on public.push_subs from anon;
grant select, insert, delete on public.push_subs to authenticated;
-- last_sent n'est écrit que par le serveur : pas de droit de mise à jour dessus.
grant update (endpoint, user_id, p256dh, auth, hour, tz, ui) on public.push_subs to authenticated;
create policy "push own select" on public.push_subs for select to authenticated using (user_id = auth.uid());
create policy "push own insert" on public.push_subs for insert to authenticated with check (user_id = auth.uid());
create policy "push own update" on public.push_subs for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "push own delete" on public.push_subs for delete to authenticated using (user_id = auth.uid());

-- Appel horaire de la fonction daily-reminder (le secret est mis en place à part, pas dans ce fichier).
create extension if not exists pg_net;
create extension if not exists pg_cron;
