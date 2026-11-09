-- Loba Premium : abonnements et paiements (CinetPay : Mobile Money et carte)
--
-- Principe de sécurité :
--   * le navigateur peut LIRE son propre abonnement et ses paiements (RLS) ;
--   * il ne peut RIEN écrire : seules les Edge Functions (clé service_role) créent
--     les paiements et activent les abonnements ;
--   * l'activation passe par settle_payment(), atomique et idempotente :
--     un même paiement ne peut prolonger l'abonnement qu'une seule fois.

create table public.payments (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  plan             text not null check (plan in ('month', 'year')),
  method           text not null check (method in ('mobile', 'card')),
  amount           numeric(10, 2) not null check (amount > 0),
  currency         text not null check (currency in ('USD', 'CDF')),
  phone_masked     text, -- inutilisé avec CinetPay (le numéro est saisi sur leur guichet)
  order_number     text unique,
  status           text not null default 'pending' check (status in ('pending', 'success', 'failed')),
  provider_payload jsonb,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index payments_user_created_idx on public.payments (user_id, created_at desc);
create index payments_pending_idx on public.payments (created_at) where status = 'pending';

create table public.subscriptions (
  user_id            uuid primary key references auth.users (id) on delete cascade,
  plan               text not null check (plan in ('month', 'year')),
  current_period_end timestamptz not null,
  last_payment_id    uuid references public.payments (id),
  updated_at         timestamptz not null default now()
);

alter table public.payments enable row level security;
alter table public.subscriptions enable row level security;

create policy "Lire ses propres paiements" on public.payments
  for select to authenticated using ((select auth.uid()) = user_id);

create policy "Lire son propre abonnement" on public.subscriptions
  for select to authenticated using ((select auth.uid()) = user_id);

-- Aucune policy insert/update/delete : seul service_role (qui contourne la RLS) écrit.
revoke insert, update, delete on public.payments, public.subscriptions from anon, authenticated;

-- Règle un paiement en attente et, s'il a réussi, prolonge l'abonnement.
-- Idempotent : si le paiement n'est plus "pending", rien ne change et l'état actuel est renvoyé.
create or replace function public.settle_payment(
  p_payment_id uuid,
  p_success    boolean,
  p_payload    jsonb default null
)
returns table (out_status text, out_period_end timestamptz)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pay      public.payments%rowtype;
  v_interval interval;
  v_end      timestamptz;
begin
  update public.payments p
     set status           = case when p_success then 'success' else 'failed' end,
         provider_payload = coalesce(p_payload, p.provider_payload),
         updated_at       = now()
   where p.id = p_payment_id
     and p.status = 'pending'
  returning p.* into v_pay;

  if not found then
    return query
      select p.status, s.current_period_end
        from public.payments p
        left join public.subscriptions s on s.user_id = p.user_id
       where p.id = p_payment_id;
    return;
  end if;

  if p_success then
    v_interval := case v_pay.plan when 'year' then interval '1 year' else interval '1 month' end;

    insert into public.subscriptions as s (user_id, plan, current_period_end, last_payment_id, updated_at)
    values (v_pay.user_id, v_pay.plan, now() + v_interval, v_pay.id, now())
    on conflict (user_id) do update
       set plan               = excluded.plan,
           -- un renouvellement anticipé s'ajoute à la période restante
           current_period_end = greatest(s.current_period_end, now()) + v_interval,
           last_payment_id    = excluded.last_payment_id,
           updated_at         = now()
    returning s.current_period_end into v_end;
  else
    select s.current_period_end into v_end
      from public.subscriptions s
     where s.user_id = v_pay.user_id;
  end if;

  return query select v_pay.status, v_end;
end;
$$;

revoke execute on function public.settle_payment(uuid, boolean, jsonb) from public, anon, authenticated;
grant execute on function public.settle_payment(uuid, boolean, jsonb) to service_role;
