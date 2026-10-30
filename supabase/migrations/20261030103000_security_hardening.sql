create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.is_couple_member(p_space_id uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.couple_members m
    where m.space_id = p_space_id and m.user_id = (select auth.uid())
  );
$$;
revoke all on function private.is_couple_member(uuid) from public, anon;
grant execute on function private.is_couple_member(uuid) to authenticated;

create or replace function public.create_couple_space(p_name text, p_display_name text)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare v_space uuid; v_code uuid;
begin
  if (select auth.uid()) is null then raise exception 'authentication_required'; end if;
  if nullif(trim(p_name), '') is null or nullif(trim(p_display_name), '') is null then raise exception 'name_required'; end if;
  v_code := gen_random_uuid();
  insert into public.couple_spaces(name, invite_code, created_by)
    values (left(trim(p_name), 80), v_code, (select auth.uid())) returning id into v_space;
  insert into public.couple_members(space_id, user_id, display_name, role)
    values (v_space, (select auth.uid()), left(trim(p_display_name), 60), 'owner');
  insert into public.couple_settings(space_id) values (v_space);
  return jsonb_build_object('space_id', v_space, 'invite_code', v_code);
end;
$$;

create or replace function public.join_couple_space(p_invite_code uuid, p_display_name text)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare v_space uuid; v_member_count integer;
begin
  if (select auth.uid()) is null then raise exception 'authentication_required'; end if;
  if nullif(trim(p_display_name), '') is null then raise exception 'name_required'; end if;
  select id into v_space from public.couple_spaces
    where invite_code = p_invite_code and invite_expires_at > now() for update;
  if v_space is null then raise exception 'invite_invalid_or_expired'; end if;
  if exists (select 1 from public.couple_members where space_id=v_space and user_id=(select auth.uid())) then return v_space; end if;
  select count(*) into v_member_count from public.couple_members where space_id=v_space;
  if v_member_count >= 2 then raise exception 'space_full'; end if;
  insert into public.couple_members(space_id, user_id, display_name, role)
    values (v_space, (select auth.uid()), left(trim(p_display_name), 60), 'partner');
  update public.couple_spaces set invite_code=gen_random_uuid(), invite_expires_at=now()+interval '48 hours' where id=v_space;
  return v_space;
end;
$$;

create or replace function public.refresh_couple_invite(p_space_id uuid)
returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare v_code uuid; v_expiration timestamptz;
begin
  if not private.is_couple_member(p_space_id) then raise exception 'not_a_member'; end if;
  update public.couple_spaces set invite_code=gen_random_uuid(), invite_expires_at=now()+interval '48 hours'
    where id=p_space_id returning invite_code, invite_expires_at into v_code, v_expiration;
  return jsonb_build_object('invite_code', v_code, 'expires_at', v_expiration);
end;
$$;

-- Membership checks stay outside the exposed public API schema.
drop policy couple_spaces_member_select on public.couple_spaces;
drop policy couple_members_group_select on public.couple_members;
drop policy expenses_member_select on public.expenses;
drop policy expenses_member_insert on public.expenses;
drop policy expenses_member_update on public.expenses;
drop policy expenses_member_delete on public.expenses;
drop policy budgets_member_all on public.category_budgets;
drop policy settings_member_all on public.couple_settings;

create policy couple_spaces_member_select on public.couple_spaces
  for select to authenticated using ((select private.is_couple_member(id)));
create policy couple_members_group_select on public.couple_members
  for select to authenticated using ((select private.is_couple_member(space_id)));
create policy expenses_member_select on public.expenses
  for select to authenticated using ((select private.is_couple_member(space_id)));
create policy expenses_member_insert on public.expenses
  for insert to authenticated with check ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()));
create policy expenses_member_update on public.expenses
  for update to authenticated using ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()))
  with check ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()));
create policy expenses_member_delete on public.expenses
  for delete to authenticated using ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()));
create policy budgets_member_all on public.category_budgets
  for all to authenticated using ((select private.is_couple_member(space_id)))
  with check ((select private.is_couple_member(space_id)));
create policy settings_member_all on public.couple_settings
  for all to authenticated using ((select private.is_couple_member(space_id)))
  with check ((select private.is_couple_member(space_id)));

revoke insert, update, delete on public.couple_spaces, public.couple_members from authenticated;
revoke all on function public.is_couple_member(uuid) from public, anon, authenticated;
drop function public.is_couple_member(uuid);
create index if not exists couple_spaces_created_by_idx on public.couple_spaces(created_by);
create index if not exists expenses_created_by_idx on public.expenses(created_by);
