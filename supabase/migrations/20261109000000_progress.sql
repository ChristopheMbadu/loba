-- Sauvegarde automatique de la progression (une ligne par joueur).
-- Le joueur (compte anonyme ou e-mail) ne peut lire et écrire QUE sa propre ligne.

create table public.progress (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now(),
  -- garde-fou contre les abus de stockage : une progression pèse quelques Ko
  constraint progress_taille check (pg_column_size(data) < 200000),
  constraint progress_objet check (jsonb_typeof(data) = 'object')
);

alter table public.progress enable row level security;

create policy "Lire sa progression" on public.progress
  for select to authenticated using ((select auth.uid()) = user_id);

create policy "Créer sa progression" on public.progress
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy "Mettre à jour sa progression" on public.progress
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on public.progress from anon;
revoke delete, truncate on public.progress from authenticated;
