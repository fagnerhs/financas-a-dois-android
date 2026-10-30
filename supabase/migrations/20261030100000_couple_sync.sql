create extension if not exists pgcrypto with schema extensions;

create table public.couple_spaces (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 80),
  invite_code uuid not null unique default gen_random_uuid(),
  invite_expires_at timestamptz not null default (now() + interval '48 hours'),
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table public.couple_members (
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 60),
  role text not null check (role in ('owner','partner')),
  joined_at timestamptz not null default now(),
  primary key (space_id, user_id)
);
create index couple_members_user_idx on public.couple_members(user_id);

create table public.expenses (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  amount numeric(12,2) not null check (amount > 0),
  category text not null check (category in ('Alimentação','Mercado','Moradia','Transporte','Lazer','Saúde','Educação','Delivery','Outros')),
  payer text not null default 'Não informado' check (char_length(payer) <= 60),
  description text not null default '' check (char_length(description) <= 240),
  expense_date date not null default current_date,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index expenses_space_date_idx on public.expenses(space_id, expense_date desc);

create table public.category_budgets (
  id uuid primary key default gen_random_uuid(),
  space_id uuid not null references public.couple_spaces(id) on delete cascade,
  category text not null check (category in ('Alimentação','Mercado','Moradia','Transporte','Lazer','Saúde','Educação','Delivery','Outros')),
  monthly_limit numeric(12,2) not null check (monthly_limit >= 0),
  updated_at timestamptz not null default now(),
  unique (space_id, category)
);

create table public.couple_settings (
  space_id uuid primary key references public.couple_spaces(id) on delete cascade,
  emergency_goal numeric(12,2) not null default 10000 check (emergency_goal >= 0),
  emergency_saved numeric(12,2) not null default 0 check (emergency_saved >= 0),
  monthly_contribution numeric(12,2) not null default 500 check (monthly_contribution >= 0),
  updated_at timestamptz not null default now()
);

create or replace function public.is_couple_member(p_space_id uuid)
returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.couple_members m
    where m.space_id = p_space_id and m.user_id = auth.uid()
  );
$$;

create or replace function public.create_couple_space(p_name text, p_display_name text)
returns jsonb
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_space uuid;
  v_code uuid;
begin
  if auth.uid() is null then raise exception 'authentication_required'; end if;
  if nullif(trim(p_name), '') is null or nullif(trim(p_display_name), '') is null then
    raise exception 'name_required';
  end if;
  v_code := gen_random_uuid();
  insert into public.couple_spaces(name, invite_code, created_by)
    values (left(trim(p_name), 80), v_code, auth.uid()) returning id into v_space;
  insert into public.couple_members(space_id, user_id, display_name, role)
    values (v_space, auth.uid(), left(trim(p_display_name), 60), 'owner');
  insert into public.couple_settings(space_id) values (v_space);
  return jsonb_build_object('space_id', v_space, 'invite_code', v_code);
end;
$$;

create or replace function public.join_couple_space(p_invite_code uuid, p_display_name text)
returns uuid
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
  v_space uuid;
  v_member_count integer;
begin
  if auth.uid() is null then raise exception 'authentication_required'; end if;
  if nullif(trim(p_display_name), '') is null then raise exception 'name_required'; end if;
  select id into v_space from public.couple_spaces
    where invite_code = p_invite_code and invite_expires_at > now()
    for update;
  if v_space is null then raise exception 'invite_invalid_or_expired'; end if;
  if exists (select 1 from public.couple_members where space_id=v_space and user_id=auth.uid()) then
    return v_space;
  end if;
  select count(*) into v_member_count from public.couple_members where space_id=v_space;
  if v_member_count >= 2 then raise exception 'space_full'; end if;
  insert into public.couple_members(space_id, user_id, display_name, role)
    values (v_space, auth.uid(), left(trim(p_display_name), 60), 'partner');
  update public.couple_spaces set invite_code=gen_random_uuid(), invite_expires_at=now()+interval '48 hours'
    where id=v_space;
  return v_space;
end;
$$;

create or replace function public.refresh_couple_invite(p_space_id uuid)
returns jsonb
language plpgsql security definer
set search_path = public, pg_temp
as $$
declare v_code uuid;
begin
  if not public.is_couple_member(p_space_id) then raise exception 'not_a_member'; end if;
  update public.couple_spaces set invite_code=gen_random_uuid(), invite_expires_at=now()+interval '48 hours'
    where id=p_space_id returning invite_code into v_code;
  return jsonb_build_object('invite_code', v_code, 'expires_at', now()+interval '48 hours');
end;
$$;

alter table public.couple_spaces enable row level security;
alter table public.couple_members enable row level security;
alter table public.expenses enable row level security;
alter table public.category_budgets enable row level security;
alter table public.couple_settings enable row level security;

create policy couple_spaces_member_select on public.couple_spaces
  for select to authenticated using (public.is_couple_member(id));
create policy couple_members_group_select on public.couple_members
  for select to authenticated using (public.is_couple_member(space_id));
create policy expenses_member_select on public.expenses
  for select to authenticated using (public.is_couple_member(space_id));
create policy expenses_member_insert on public.expenses
  for insert to authenticated with check (public.is_couple_member(space_id) and created_by=auth.uid());
create policy expenses_member_update on public.expenses
  for update to authenticated using (public.is_couple_member(space_id))
  with check (public.is_couple_member(space_id) and created_by=auth.uid());
create policy expenses_member_delete on public.expenses
  for delete to authenticated using (public.is_couple_member(space_id));
create policy budgets_member_all on public.category_budgets
  for all to authenticated using (public.is_couple_member(space_id))
  with check (public.is_couple_member(space_id));
create policy settings_member_all on public.couple_settings
  for all to authenticated using (public.is_couple_member(space_id))
  with check (public.is_couple_member(space_id));

revoke all on public.couple_spaces, public.couple_members, public.expenses,
  public.category_budgets, public.couple_settings from anon;
grant usage on schema public to authenticated;
grant select on public.couple_spaces, public.couple_members, public.expenses,
  public.category_budgets, public.couple_settings to authenticated;
grant insert, update, delete on public.expenses, public.category_budgets,
  public.couple_settings to authenticated;
revoke all on function public.is_couple_member(uuid) from public, anon;
revoke all on function public.create_couple_space(text,text) from public, anon;
revoke all on function public.join_couple_space(uuid,text) from public, anon;
revoke all on function public.refresh_couple_invite(uuid) from public, anon;
grant execute on function public.is_couple_member(uuid) to authenticated;
grant execute on function public.create_couple_space(text,text) to authenticated;
grant execute on function public.join_couple_space(uuid,text) to authenticated;
grant execute on function public.refresh_couple_invite(uuid) to authenticated;

alter table public.expenses replica identity full;
do $$
declare t text;
begin
  if not exists (select 1 from pg_publication where pubname='supabase_realtime') then
    create publication supabase_realtime;
  end if;
  foreach t in array array['couple_spaces','couple_members','expenses','category_budgets','couple_settings'] loop
    if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end;
$$;
