-- Ligues hebdomadaires et amis.
-- Les tables ne sont pas lisibles directement : tout passe par des fonctions (security definer)
-- qui ne renvoient que pseudo, avatar, série et XP. Aucun e-mail, aucun identifiant de compte inutile.

create table if not exists public.public_profiles (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  name       text not null default '' check (length(name) <= 20),
  av         text not null default '🦜' check (length(av) <= 16),
  col        smallint not null default 0 check (col between 0 and 7),
  streak     int not null default 0 check (streak between 0 and 100000),
  xp         int not null default 0 check (xp between 0 and 100000000),
  tier       smallint not null default 0 check (tier between 0 and 5),
  result     text check (result in ('up','down','stay')),
  code       text not null unique default upper(substr(md5(gen_random_uuid()::text), 1, 6)),
  updated_at timestamptz not null default now()
);

create table if not exists public.league_entries (
  week    text not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  tier    smallint not null check (tier between 0 and 5),
  cohort  int not null default 0,
  xp      int not null default 0 check (xp between 0 and 100000),
  primary key (week, user_id)
);
create index if not exists league_entries_board on public.league_entries (week, tier, cohort, xp desc);

create table if not exists public.follows (
  follower   uuid not null references auth.users(id) on delete cascade,
  followee   uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower, followee),
  check (follower <> followee)
);

alter table public.public_profiles enable row level security;
alter table public.league_entries  enable row level security;
alter table public.follows         enable row level security;
revoke all on public.public_profiles, public.league_entries, public.follows from anon, authenticated;
-- Chacun peut voir et retirer ses propres abonnements (amis suivis).
grant select, delete on public.follows to authenticated;
create policy "follows own select" on public.follows for select to authenticated using (follower = auth.uid());
create policy "follows own delete" on public.follows for delete to authenticated using (follower = auth.uid());

create or replace function public.loba_week() returns text language sql stable
as $$ select to_char(now() at time zone 'utc', 'IYYY-"W"IW') $$;

-- Met à jour le profil public et l'entrée de ligue de la semaine ; à la première synchro
-- d'une nouvelle semaine, calcule la montée / descente d'après le classement de la semaine passée.
create or replace function public.sync_profile(p_name text, p_av text, p_col int, p_streak int, p_xp int, p_week text, p_week_xp int)
returns jsonb language plpgsql security definer set search_path = public
as $$
declare
  uid uuid := auth.uid();
  wk text := public.loba_week();
  pr public.public_profiles;
  prev public.league_entries;
  rk int; sz int; newtier int; res text := null;
  e public.league_entries;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  p_name := left(regexp_replace(coalesce(p_name, ''), '[<>[:cntrl:]]', '', 'g'), 20);
  p_av := left(coalesce(p_av, '🦜'), 16);
  p_col := least(greatest(coalesce(p_col, 0), 0), 7);
  p_streak := least(greatest(coalesce(p_streak, 0), 0), 100000);
  p_xp := least(greatest(coalesce(p_xp, 0), 0), 100000000);
  p_week_xp := case when p_week = wk then least(greatest(coalesce(p_week_xp, 0), 0), 100000) else 0 end;

  select * into pr from public.public_profiles where user_id = uid for update;
  if not found then
    insert into public.public_profiles (user_id, name, av, col, streak, xp) values (uid, p_name, p_av, p_col, p_streak, p_xp)
    returning * into pr;
  else
    update public.public_profiles set name = p_name, av = p_av, col = p_col, streak = p_streak, xp = greatest(pr.xp, p_xp), updated_at = now()
    where user_id = uid returning * into pr;
  end if;

  select * into e from public.league_entries where week = wk and user_id = uid;
  if not found then
    -- Résultat de la dernière semaine jouée.
    select * into prev from public.league_entries where user_id = uid and week < wk order by week desc limit 1;
    newtier := pr.tier;
    if found then
      select count(*) + 1 into rk from public.league_entries where week = prev.week and tier = prev.tier and cohort = prev.cohort and xp > prev.xp;
      select count(*) into sz from public.league_entries where week = prev.week and tier = prev.tier and cohort = prev.cohort;
      if rk <= 7 and prev.xp >= 30 and prev.tier < 5 then newtier := prev.tier + 1; res := 'up';
      elsif prev.tier > 0 and ((sz >= 10 and rk > sz - 5) or prev.xp = 0) then newtier := prev.tier - 1; res := 'down';
      else newtier := prev.tier; res := 'stay'; end if;
      update public.public_profiles set tier = newtier, result = res where user_id = uid returning * into pr;
    end if;
    -- Groupes de 30 joueurs maximum par ligue.
    insert into public.league_entries (week, user_id, tier, cohort, xp)
    values (wk, uid, pr.tier, (select count(*) / 30 from public.league_entries where week = wk and tier = pr.tier), p_week_xp)
    on conflict (week, user_id) do nothing;
  elsif p_week_xp > e.xp then
    update public.league_entries set xp = p_week_xp where week = wk and user_id = uid;
  end if;

  return jsonb_build_object('tier', pr.tier, 'code', pr.code, 'result', pr.result, 'week', wk);
end $$;

-- Classement de mon groupe de ligue pour la semaine en cours.
create or replace function public.league_board() returns jsonb language sql stable security definer set search_path = public
as $$
  with me as (select * from public.league_entries where week = public.loba_week() and user_id = auth.uid())
  select jsonb_build_object('tier', (select tier from me), 'rows', coalesce((
    select jsonb_agg(jsonb_build_object('name', p.name, 'av', p.av, 'col', p.col, 'xp', l.xp, 'me', l.user_id = auth.uid()) order by l.xp desc, l.user_id = auth.uid() desc)
    from public.league_entries l join public.public_profiles p on p.user_id = l.user_id, me
    where l.week = me.week and l.tier = me.tier and l.cohort = me.cohort), '[]'::jsonb))
$$;

create or replace function public.follow_by_code(p_code text) returns jsonb language plpgsql security definer set search_path = public
as $$
declare uid uuid := auth.uid(); f public.public_profiles; n int;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  select count(*) into n from public.follows where follower = uid;
  if n >= 200 then raise exception 'too many friends'; end if;
  select * into f from public.public_profiles where code = upper(left(p_code, 8));
  if not found or f.user_id = uid then raise exception 'code not found'; end if;
  insert into public.follows (follower, followee) values (uid, f.user_id) on conflict do nothing;
  return jsonb_build_object('name', f.name);
end $$;

create or replace function public.friends_list() returns jsonb language sql stable security definer set search_path = public
as $$
  select coalesce(jsonb_agg(jsonb_build_object('user_id', p.user_id, 'name', p.name, 'av', p.av, 'col', p.col, 'streak', p.streak,
    'week_xp', coalesce(l.xp, 0)) order by coalesce(l.xp, 0) desc), '[]'::jsonb)
  from public.follows f join public.public_profiles p on p.user_id = f.followee
  left join public.league_entries l on l.user_id = p.user_id and l.week = public.loba_week()
  where f.follower = auth.uid()
$$;

revoke all on function public.sync_profile(text,text,int,int,int,text,int), public.league_board(), public.follow_by_code(text), public.friends_list() from public, anon;
grant execute on function public.sync_profile(text,text,int,int,int,text,int), public.league_board(), public.follow_by_code(text), public.friends_list() to authenticated;
