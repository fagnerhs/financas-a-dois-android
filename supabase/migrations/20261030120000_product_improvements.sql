-- Product improvements: shared/private expenses, manual recurring bills,
-- shared savings goals, review status, and a client-read-only event history.

-- Users without a verified MFA factor keep normal access. Once TOTP is verified,
-- all membership-backed table/RPC access requires a fresh AAL2 session.
create or replace function private.mfa_gate_pass()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null and (
    (select auth.jwt() ->> 'aal') = 'aal2'
    or not exists (
      select 1 from auth.mfa_factors f
      where f.user_id = (select auth.uid()) and f.status = 'verified'
    )
  );
$$;
revoke all on function private.mfa_gate_pass() from public, anon, authenticated;

create or replace function private.is_couple_member(p_space_id uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select (select private.mfa_gate_pass()) and exists (
    select 1 from public.couple_members m
    where m.space_id = p_space_id and m.user_id = (select auth.uid())
  );
$$;
revoke all on function private.is_couple_member(uuid) from public, anon;
grant execute on function private.is_couple_member(uuid) to authenticated;

alter table public.expenses
  add column visibility text not null default 'shared',
  add column creator_share_amount numeric(12,2) default null,
  add column review_status text not null default 'reviewed',
  add column reviewed_by uuid references auth.users(id) on delete set null,
  add column reviewed_at timestamptz;
update public.expenses set creator_share_amount = amount;
alter table public.expenses
  alter column creator_share_amount set not null,
  add constraint expenses_visibility_check check (visibility in ('shared', 'private', 'balances_only')),
  add constraint expenses_creator_share_range check (creator_share_amount >= 0 and creator_share_amount <= amount),
  add constraint expenses_private_full_share check (visibility <> 'private' or creator_share_amount = amount),
  add constraint expenses_review_status_check check (review_status in ('pending','reviewed','legacy'));
alter table public.expenses alter column review_status set default 'pending';
create index if not exists expenses_space_visibility_date_idx
  on public.expenses(space_id, visibility, expense_date desc);
create index if not exists expenses_review_queue_idx on public.expenses(space_id, review_status, expense_date desc);
comment on column public.expenses.creator_share_amount is
  'Portion assigned to the creator; the partner portion is amount minus this value. Couple budgets sum amount only for shared expenses; private expenses are excluded.';

create or replace function private.default_expense_creator_share()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.creator_share_amount is null then
    new.creator_share_amount := new.amount;
  end if;
  return new;
end;
$$;
revoke all on function private.default_expense_creator_share() from public, anon, authenticated;
create trigger expenses_default_creator_share
  before insert or update on public.expenses
  for each row execute function private.default_expense_creator_share();

create or replace function public.mark_expense_reviewed(p_expense_id uuid, p_space_id uuid)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
declare v_rows integer;
begin
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member_or_mfa_required'; end if;
  update public.expenses set review_status='reviewed',reviewed_by=(select auth.uid()),reviewed_at=now()
  where id=p_expense_id and space_id=p_space_id and visibility='shared';
  get diagnostics v_rows = row_count;
  if v_rows=0 then raise exception 'shared_expense_not_found'; end if;
  return true;
end;
$$;
revoke all on function public.mark_expense_reviewed(uuid,uuid) from public, anon;
grant execute on function public.mark_expense_reviewed(uuid,uuid) to authenticated;

-- Recurring bills are manual plans only; there are no bank connections or imports.
create table public.recurring_bills (
  id uuid primary key default extensions.gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  amount numeric(12,2) not null check (amount > 0),
  category text not null check (category in ('Alimentação','Mercado','Moradia','Transporte','Lazer','Saúde','Educação','Delivery','Outros')),
  payer text not null default 'Não informado' check (char_length(payer) <= 60),
  description text not null default '' check (char_length(description) <= 240),
  next_date date not null,
  frequency text not null check (frequency in ('weekly','biweekly','monthly','yearly')),
  is_active boolean not null default true,
  visibility text not null default 'shared' check (visibility in ('shared','private')),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index recurring_bills_space_next_date_idx on public.recurring_bills(space_id, next_date) where is_active;
create index recurring_bills_creator_idx on public.recurring_bills(created_by);
alter table public.recurring_bills enable row level security;
create policy recurring_bills_select on public.recurring_bills for select to authenticated
  using ((select private.is_couple_member(space_id)) and (visibility = 'shared' or created_by = (select auth.uid())));
create policy recurring_bills_insert on public.recurring_bills for insert to authenticated
  with check ((select private.is_couple_member(space_id)) and created_by = (select auth.uid()));
create policy recurring_bills_update on public.recurring_bills for update to authenticated
  using ((select private.is_couple_member(space_id)) and created_by = (select auth.uid()))
  with check ((select private.is_couple_member(space_id)) and created_by = (select auth.uid()));
create policy recurring_bills_delete on public.recurring_bills for delete to authenticated
  using ((select private.is_couple_member(space_id)) and created_by = (select auth.uid()));

alter table public.couple_settings
  add column monthly_income numeric(12,2) not null default 0 check (monthly_income >= 0),
  add column available_balance numeric(12,2) not null default 0,
  add column balance_as_of date not null default current_date,
  add column next_payday date;

create table public.savings_goals (
  id uuid primary key default extensions.gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  target_amount numeric(12,2) not null check (target_amount > 0),
  saved_amount numeric(12,2) not null default 0 check (saved_amount >= 0),
  monthly_contribution numeric(12,2) not null default 0 check (monthly_contribution >= 0),
  target_date date,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index savings_goals_space_idx on public.savings_goals(space_id);
alter table public.savings_goals enable row level security;
create policy savings_goals_member_select on public.savings_goals for select to authenticated
  using ((select private.is_couple_member(space_id)));
create policy savings_goals_member_insert on public.savings_goals for insert to authenticated
  with check ((select private.is_couple_member(space_id)) and created_by = (select auth.uid()));
create policy savings_goals_member_update on public.savings_goals for update to authenticated
  using ((select private.is_couple_member(space_id)))
  with check ((select private.is_couple_member(space_id)));
create policy savings_goals_member_delete on public.savings_goals for delete to authenticated
  using ((select private.is_couple_member(space_id)));

create or replace function private.guard_savings_goal_author()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' and new.created_by is distinct from (select auth.uid()) then
    raise exception 'goal_author_must_match_current_user';
  end if;
  if tg_op = 'UPDATE' and new.created_by is distinct from old.created_by then
    raise exception 'goal_author_is_immutable';
  end if;
  return new;
end;
$$;
revoke all on function private.guard_savings_goal_author() from public, anon, authenticated;
create trigger savings_goals_author_guard
  before insert or update on public.savings_goals
  for each row execute function private.guard_savings_goal_author();

-- Shared goal updates go through this RPC so a partner can edit values while
-- created_by remains immutable and accurately records the original author.
create or replace function public.save_savings_goal(
  p_id uuid, p_space_id uuid, p_name text, p_target_amount numeric,
  p_saved_amount numeric, p_monthly_contribution numeric, p_target_date date
)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare v_rows integer;
begin
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member_or_mfa_required'; end if;
  insert into public.savings_goals(id,space_id,name,target_amount,saved_amount,monthly_contribution,target_date,created_by)
    values (p_id,p_space_id,p_name,p_target_amount,p_saved_amount,p_monthly_contribution,p_target_date,(select auth.uid()))
  on conflict (id) do update set
    name=excluded.name, target_amount=excluded.target_amount, saved_amount=excluded.saved_amount,
    monthly_contribution=excluded.monthly_contribution, target_date=excluded.target_date, updated_at=now()
  where public.savings_goals.space_id=p_space_id;
  get diagnostics v_rows = row_count;
  if v_rows = 0 then raise exception 'goal_not_found_or_wrong_space'; end if;
  return p_id;
end;
$$;
revoke all on function public.save_savings_goal(uuid,uuid,text,numeric,numeric,numeric,date) from public, anon;
grant execute on function public.save_savings_goal(uuid,uuid,text,numeric,numeric,numeric,date) to authenticated;

-- Event snapshots survive expense deletion. A partner can see shared history
-- only while the current expense is still shared; authors retain their own history.
create table public.expense_events (
  id bigint generated always as identity primary key,
  expense_id uuid not null,
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in ('INSERT','UPDATE','DELETE')),
  visibility text not null check (visibility in ('shared','private','balances_only')),
  snapshot jsonb not null check (jsonb_typeof(snapshot) = 'object'),
  occurred_at timestamptz not null default now()
);
create index expense_events_space_time_idx on public.expense_events(space_id, occurred_at desc);
create index expense_events_expense_idx on public.expense_events(expense_id, occurred_at desc);
alter table public.expense_events enable row level security;
create policy expense_events_member_select on public.expense_events for select to authenticated
  using ((select private.is_couple_member(space_id)) and (
    actor_id = (select auth.uid())
    or (visibility = 'shared' and exists (
      select 1 from public.expenses e
      where e.id = expense_events.expense_id and e.space_id = expense_events.space_id and e.visibility = 'shared'
    ))
  ));

create or replace function private.capture_expense_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'DELETE' then
    insert into public.expense_events(expense_id, space_id, actor_id, event_type, visibility, snapshot)
    values (old.id, old.space_id, (select auth.uid()), 'DELETE', old.visibility, to_jsonb(old));
    return old;
  elsif tg_op = 'UPDATE' then
    insert into public.expense_events(expense_id, space_id, actor_id, event_type, visibility, snapshot)
    values (new.id, new.space_id, (select auth.uid()), 'UPDATE', new.visibility, to_jsonb(new));
    return new;
  else
    insert into public.expense_events(expense_id, space_id, actor_id, event_type, visibility, snapshot)
    values (new.id, new.space_id, (select auth.uid()), 'INSERT', new.visibility, to_jsonb(new));
    return new;
  end if;
end;
$$;
revoke all on function private.capture_expense_event() from public, anon, authenticated;
create trigger expenses_capture_event
  after insert or update or delete on public.expenses
  for each row execute function private.capture_expense_event();

-- Expense visibility is row-level, while writes remain restricted to authors.
drop policy expenses_member_select on public.expenses;
create policy expenses_member_select on public.expenses for select to authenticated
  using ((select private.is_couple_member(space_id)) and (visibility = 'shared' or created_by = (select auth.uid())));

-- A separate narrow projection supports “show only the balance” without
-- exposing the transaction's category, description, payer, or split.
create table public.expense_balances (
  expense_id uuid primary key references public.expenses(id) on delete cascade,
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  amount numeric(12,2) not null check (amount > 0),
  expense_date date not null,
  created_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now()
);
create index expense_balances_space_date_idx on public.expense_balances(space_id, expense_date desc);
alter table public.expense_balances enable row level security;
create policy expense_balances_member_select on public.expense_balances for select to authenticated
  using ((select private.is_couple_member(space_id)));

create or replace function private.sync_expense_balance_projection()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'DELETE' then
    delete from public.expense_balances where expense_id = old.id;
    return old;
  end if;
  if new.visibility = 'balances_only' then
    insert into public.expense_balances(expense_id,space_id,amount,expense_date,created_by,updated_at)
      values (new.id,new.space_id,new.amount,new.expense_date,new.created_by,now())
    on conflict (expense_id) do update set
      space_id=excluded.space_id,amount=excluded.amount,expense_date=excluded.expense_date,
      created_by=excluded.created_by,updated_at=now();
  else
    delete from public.expense_balances where expense_id = new.id;
  end if;
  return new;
end;
$$;
revoke all on function private.sync_expense_balance_projection() from public, anon, authenticated;
create trigger expenses_sync_balance_projection
  after insert or update or delete on public.expenses
  for each row execute function private.sync_expense_balance_projection();

revoke all privileges on public.recurring_bills, public.savings_goals, public.expense_events, public.expense_balances from anon, authenticated;
grant select, insert, update, delete on public.recurring_bills to authenticated;
grant select, delete on public.savings_goals to authenticated;
grant select on public.expense_events, public.expense_balances to authenticated;
revoke all on sequence public.expense_events_id_seq from public, anon, authenticated;
revoke insert, update, delete on public.expense_events from public, anon, authenticated;

-- Add new client-synced tables if the standard Supabase publication exists.
do $$
declare t text;
begin
  if exists (select 1 from pg_catalog.pg_publication where pubname = 'supabase_realtime') then
    foreach t in array array['recurring_bills','savings_goals','expense_events','expense_balances'] loop
      if not exists (select 1 from pg_catalog.pg_publication_tables
        where pubname='supabase_realtime' and schemaname='public' and tablename=t) then
        execute pg_catalog.format('alter publication supabase_realtime add table public.%I', t);
      end if;
    end loop;
  end if;
end;
$$;
