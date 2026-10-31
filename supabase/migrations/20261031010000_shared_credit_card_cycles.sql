-- Shared couple credit card cycles and purchases.

create table public.credit_card_cycles (
  id uuid primary key default extensions.gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  opened_at timestamptz not null default pg_catalog.now(),
  opened_by uuid not null references auth.users(id),
  closed_at timestamptz,
  closed_by uuid references auth.users(id),
  statement_total numeric(12,2) not null default 0 check (statement_total >= 0),
  paid_at timestamptz,
  paid_by uuid references auth.users(id),
  constraint credit_card_cycles_paid_requires_closed check (paid_at is null or closed_at is not null)
);
create unique index credit_card_cycles_one_open_per_space_idx
  on public.credit_card_cycles(space_id) where closed_at is null;
create index credit_card_cycles_space_opened_idx
  on public.credit_card_cycles(space_id, opened_at desc);

create table public.credit_card_purchases (
  id uuid primary key default extensions.gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  cycle_id uuid not null references public.credit_card_cycles(id) on delete restrict,
  amount numeric(12,2) not null check (amount > 0),
  category text not null check (category in ('Alimentação','Mercado','Moradia','Transporte','Lazer','Saúde','Educação','Delivery','Outros')),
  description text not null default '' check (pg_catalog.char_length(description) <= 240),
  purchase_date date not null default current_date,
  payer text not null default 'Não informado' check (pg_catalog.char_length(payer) <= 60),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default pg_catalog.now()
);
create index credit_card_purchases_space_date_idx
  on public.credit_card_purchases(space_id, purchase_date desc);
create index credit_card_purchases_cycle_date_idx
  on public.credit_card_purchases(cycle_id, purchase_date desc);
create index credit_card_purchases_space_cycle_idx
  on public.credit_card_purchases(space_id, cycle_id);

alter table public.credit_card_cycles enable row level security;
alter table public.credit_card_purchases enable row level security;

create policy credit_card_cycles_select_member on public.credit_card_cycles
  for select to authenticated using ((select private.is_couple_member(space_id)));
create policy credit_card_purchases_select_member on public.credit_card_purchases
  for select to authenticated using ((select private.is_couple_member(space_id)));
create policy credit_card_purchases_insert_member_creator on public.credit_card_purchases
  for insert to authenticated with check (
    (select private.is_couple_member(space_id)) and created_by = (select auth.uid())
  );
create policy credit_card_purchases_update_member_creator on public.credit_card_purchases
  for update to authenticated using (
    (select private.is_couple_member(space_id)) and created_by = (select auth.uid())
  ) with check (
    (select private.is_couple_member(space_id)) and created_by = (select auth.uid())
  );
create policy credit_card_purchases_delete_member_creator on public.credit_card_purchases
  for delete to authenticated using (
    (select private.is_couple_member(space_id)) and created_by = (select auth.uid())
  );

revoke all on table public.credit_card_cycles from public, anon, authenticated;
revoke all on table public.credit_card_purchases from public, anon, authenticated;
grant select on table public.credit_card_cycles to authenticated;
grant select, insert, update, delete on table public.credit_card_purchases to authenticated;

create or replace function private.validate_credit_card_purchase_cycle()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_cycle public.credit_card_cycles%rowtype;
  v_cycle_id uuid;
begin
  if tg_op = 'DELETE' then
    v_cycle_id := old.cycle_id;
  else
    v_cycle_id := new.cycle_id;
  end if;

  if tg_op in ('UPDATE', 'DELETE') then
    select c.* into v_cycle
    from public.credit_card_cycles c
    where c.id = old.cycle_id and c.space_id = old.space_id
    for update;
    if not found then raise exception 'credit_card_cycle_not_found'; end if;
    if v_cycle.closed_at is not null then raise exception 'credit_card_cycle_closed'; end if;
  end if;

  if tg_op <> 'DELETE' then
    select c.* into v_cycle
    from public.credit_card_cycles c
    where c.id = new.cycle_id and c.space_id = new.space_id
    for update;
    if not found then raise exception 'credit_card_cycle_space_mismatch'; end if;
    if v_cycle.closed_at is not null then raise exception 'credit_card_cycle_closed'; end if;
    return new;
  end if;
  return old;
end;
$$;
revoke all on function private.validate_credit_card_purchase_cycle() from public, anon, authenticated;
create trigger credit_card_purchases_validate_cycle
  before insert or update or delete on public.credit_card_purchases
  for each row execute function private.validate_credit_card_purchase_cycle();

create or replace function public.ensure_open_credit_card_cycle(p_space_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_cycle public.credit_card_cycles%rowtype;
begin
  if (select auth.uid()) is null then raise exception 'authentication_required'; end if;
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member_or_mfa_required'; end if;
  perform 1 from public.couple_spaces s where s.id = p_space_id for update;
  if not found then raise exception 'space_not_found'; end if;
  select c.* into v_cycle from public.credit_card_cycles c
    where c.space_id = p_space_id and c.closed_at is null for update;
  if not found then
    insert into public.credit_card_cycles(space_id, opened_by)
      values (p_space_id, (select auth.uid())) returning * into v_cycle;
  end if;
  return pg_catalog.to_jsonb(v_cycle);
end;
$$;

create or replace function public.close_credit_card_cycle(
  p_space_id uuid, p_cycle_id uuid, p_next_cycle_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_cycle public.credit_card_cycles%rowtype;
  v_open public.credit_card_cycles%rowtype;
  v_total numeric(12,2);
begin
  if (select auth.uid()) is null then raise exception 'authentication_required'; end if;
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member_or_mfa_required'; end if;
  perform 1 from public.couple_spaces s where s.id = p_space_id for update;
  if not found then raise exception 'space_not_found'; end if;
  select c.* into v_cycle from public.credit_card_cycles c
    where c.id = p_cycle_id and c.space_id = p_space_id for update;
  if not found then raise exception 'credit_card_cycle_not_found'; end if;

  if v_cycle.closed_at is null then
    select coalesce(pg_catalog.sum(p.amount), 0)::numeric(12,2) into v_total
      from public.credit_card_purchases p where p.cycle_id = p_cycle_id and p.space_id = p_space_id;
    update public.credit_card_cycles c
      set statement_total = v_total, closed_at = pg_catalog.now(), closed_by = (select auth.uid())
      where c.id = p_cycle_id returning * into v_cycle;
  end if;

  select c.* into v_open from public.credit_card_cycles c
    where c.space_id = p_space_id and c.closed_at is null for update;
  if not found then
    insert into public.credit_card_cycles(id, space_id, opened_by)
      values (coalesce(p_next_cycle_id, extensions.gen_random_uuid()), p_space_id, (select auth.uid()))
      returning * into v_open;
  end if;
  return pg_catalog.jsonb_build_object('closed_cycle', pg_catalog.to_jsonb(v_cycle), 'open_cycle', pg_catalog.to_jsonb(v_open));
end;
$$;

create or replace function public.pay_credit_card_cycle(p_space_id uuid, p_cycle_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_cycle public.credit_card_cycles%rowtype;
begin
  if (select auth.uid()) is null then raise exception 'authentication_required'; end if;
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member_or_mfa_required'; end if;
  perform 1 from public.couple_spaces s where s.id = p_space_id for update;
  if not found then raise exception 'space_not_found'; end if;
  select c.* into v_cycle from public.credit_card_cycles c
    where c.id = p_cycle_id and c.space_id = p_space_id for update;
  if not found then raise exception 'credit_card_cycle_not_found'; end if;
  if v_cycle.closed_at is null then raise exception 'credit_card_cycle_not_closed'; end if;
  if v_cycle.paid_at is null then
    update public.credit_card_cycles c
      set paid_at = pg_catalog.now(), paid_by = (select auth.uid())
      where c.id = p_cycle_id returning * into v_cycle;
  end if;
  return pg_catalog.to_jsonb(v_cycle);
end;
$$;

revoke all on function public.ensure_open_credit_card_cycle(uuid) from public, anon, authenticated;
revoke all on function public.close_credit_card_cycle(uuid, uuid, uuid) from public, anon, authenticated;
revoke all on function public.pay_credit_card_cycle(uuid, uuid) from public, anon, authenticated;
grant execute on function public.ensure_open_credit_card_cycle(uuid) to authenticated;
grant execute on function public.close_credit_card_cycle(uuid, uuid, uuid) to authenticated;
grant execute on function public.pay_credit_card_cycle(uuid, uuid) to authenticated;

-- Add tables only when the optional realtime publication exists and lacks them.
do $$
declare
  t text;
begin
  if exists (select 1 from pg_catalog.pg_publication where pubname = 'supabase_realtime') then
    foreach t in array array['credit_card_cycles', 'credit_card_purchases'] loop
      if not exists (
        select 1 from pg_catalog.pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
      ) then
        execute pg_catalog.format('alter publication supabase_realtime add table public.%I', t);
      end if;
    end loop;
  end if;
end;
$$;
