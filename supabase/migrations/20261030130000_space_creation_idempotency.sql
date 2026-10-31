-- Repeated onboarding taps must not create one empty shared space per request.
-- Serialize creation per user, then return their existing membership when present.
create or replace function public.create_couple_space(p_name text, p_display_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_space uuid;
  v_code uuid;
begin
  if v_user is null then raise exception 'authentication_required'; end if;
  if nullif(trim(p_name), '') is null or nullif(trim(p_display_name), '') is null then
    raise exception 'name_required';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user::text, 0));

  select s.id, s.invite_code into v_space, v_code
  from public.couple_spaces s
  join public.couple_members m on m.space_id = s.id
  where m.user_id = v_user
  order by s.id
  limit 1
  for update of s;

  if v_space is not null then
    update public.couple_spaces
      set name = left(trim(p_name), 80)
      where id = v_space and created_by = v_user
      returning invite_code into v_code;
    if v_code is null then
      select invite_code into v_code from public.couple_spaces where id = v_space;
    end if;
    return jsonb_build_object('space_id', v_space, 'invite_code', v_code);
  end if;

  v_code := gen_random_uuid();
  insert into public.couple_spaces(name, invite_code, created_by)
    values (left(trim(p_name), 80), v_code, v_user)
    returning id into v_space;
  insert into public.couple_members(space_id, user_id, display_name, role)
    values (v_space, v_user, left(trim(p_display_name), 60), 'owner');
  insert into public.couple_settings(space_id) values (v_space);

  return jsonb_build_object('space_id', v_space, 'invite_code', v_code);
end;
$$;

revoke all on function public.create_couple_space(text, text) from public, anon;
grant execute on function public.create_couple_space(text, text) to authenticated;
