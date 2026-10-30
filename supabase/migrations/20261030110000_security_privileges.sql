-- Exact table grants: spaces and memberships are read-only to app clients;
-- shared budgets/settings and expenses require authentication plus RLS.
revoke all privileges on public.couple_spaces, public.couple_members from anon, authenticated;
grant select on public.couple_spaces, public.couple_members to authenticated;
revoke all privileges on public.expenses, public.category_budgets, public.couple_settings from anon, authenticated;
grant select, insert, update, delete on public.expenses, public.category_budgets, public.couple_settings to authenticated;

 drop policy if exists expenses_member_update on public.expenses;
create policy expenses_member_update on public.expenses
  for update to authenticated
  using ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()))
  with check ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()));

drop policy if exists expenses_member_delete on public.expenses;
create policy expenses_member_delete on public.expenses
  for delete to authenticated
  using ((select private.is_couple_member(space_id)) and created_by=(select auth.uid()));
