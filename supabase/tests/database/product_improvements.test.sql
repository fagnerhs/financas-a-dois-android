begin;
select plan(37);
select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}', true);

-- Canonical Supabase pgTAP setup: insert auth.users fixtures as the test owner,
-- then simulate each client with role + request.jwt.claims.
insert into auth.users (instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
values
  ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated', 'owner-test@example.invalid', '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now()),
  ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated', 'partner-test@example.invalid', '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now()),
  ('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated', 'outsider-test@example.invalid', '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now()),
  ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated', 'mfa-test@example.invalid', '', now(), '{"provider":"email","providers":["email"]}', '{}', now(), now());

insert into public.couple_spaces(id, name, invite_code, created_by)
values ('20000000-0000-0000-0000-000000000001', 'Test space', gen_random_uuid(), '10000000-0000-0000-0000-000000000001');
insert into public.couple_members(space_id, user_id, display_name, role) values
 ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Owner', 'owner'),
 ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Partner', 'partner');
insert into public.expenses(id, space_id, amount, category, created_by, visibility, creator_share_amount, description)
values
 ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 80, 'Mercado', '10000000-0000-0000-0000-000000000001', 'shared', 60, 'shared item'),
 ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 25, 'Lazer', '10000000-0000-0000-0000-000000000001', 'private', 25, 'private item'),
 ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 9, 'Delivery', '10000000-0000-0000-0000-000000000001', 'shared', 9, 'creator item'),
 ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', 33, 'Saúde', '10000000-0000-0000-0000-000000000001', 'balances_only', 20, 'sensitive balance-only detail'),
 ('30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001', 15, 'Outros', '10000000-0000-0000-0000-000000000001', 'shared', 15, 'formerly shared item');
update public.expenses set visibility='private' where id='30000000-0000-0000-0000-000000000006';

insert into public.recurring_bills(id, space_id, amount, category, next_date, frequency, created_by, visibility)
values
 ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 40, 'Moradia', current_date, 'monthly', '10000000-0000-0000-0000-000000000001', 'shared'),
 ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 12, 'Lazer', current_date, 'weekly', '10000000-0000-0000-0000-000000000001', 'private');
insert into public.savings_goals(id, space_id, name, target_amount, saved_amount, created_by)
values ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Trip', 1000, 100, '10000000-0000-0000-0000-000000000001');

insert into public.couple_spaces(id, name, invite_code, created_by)
values ('20000000-0000-0000-0000-000000000002', 'MFA test space', gen_random_uuid(), '10000000-0000-0000-0000-000000000004');
insert into public.couple_members(space_id, user_id, display_name, role)
values ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004', 'MFA User', 'owner');
insert into public.expenses(id, space_id, amount, category, created_by, visibility, creator_share_amount)
values ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000002', 50, 'Outros', '10000000-0000-0000-0000-000000000004', 'shared', 50);
insert into auth.mfa_factors(id,user_id,factor_type,status,created_at,updated_at)
values ('60000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004','totp','verified',now(),now());

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is((select creator_share_amount from public.expenses where id='30000000-0000-0000-0000-000000000001'), 60::numeric, 'shared expense retains creator share');
select is((select count(*)::int from public.expense_events where expense_id='30000000-0000-0000-0000-000000000002' and event_type='INSERT'), 1, 'expense insert creates a private event snapshot');
select is((select count(*)::int from public.savings_goals where id='50000000-0000-0000-0000-000000000001'), 1, 'creator can see shared savings goal');
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000004'), 1, 'creator can see full balance-only expense');
select lives_ok($$select public.save_savings_goal('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','Trip updated',1000,150,50,null)$$, 'creator updates shared goal through RPC');
select is((select created_by::text from public.savings_goals where id='50000000-0000-0000-0000-000000000001'), '10000000-0000-0000-0000-000000000001', 'goal author remains immutable');
update public.expenses set description='creator edit' where id='30000000-0000-0000-0000-000000000003';
select is((select description from public.expenses where id='30000000-0000-0000-0000-000000000003'), 'creator edit', 'creator can edit own expense');
delete from public.expenses where id='30000000-0000-0000-0000-000000000003';
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000003'), 0, 'creator can delete own expense');

select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000001'), 1, 'partner sees shared expense');
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000002'), 0, 'partner cannot see private expense');
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000004'), 0, 'partner cannot see balance-only details');
select is((select amount from public.expense_balances where expense_id='30000000-0000-0000-0000-000000000004'), 33::numeric, 'partner sees balance-only amount projection');
select is((select count(*)::int from public.expense_balances where expense_id='30000000-0000-0000-0000-000000000002'), 0, 'private expense has no partner balance projection');
select is((select count(*)::int from public.expense_events where expense_id='30000000-0000-0000-0000-000000000002'), 0, 'partner cannot see private event snapshot');
select is((select count(*)::int from public.expense_events where expense_id='30000000-0000-0000-0000-000000000001'), 1, 'partner sees shared expense event');
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000006'), 0, 'partner cannot see formerly shared expense made private');
select is((select count(*)::int from public.expense_events where expense_id='30000000-0000-0000-0000-000000000006'), 0, 'partner cannot see old event snapshots after expense becomes private');
select lives_ok($$select public.mark_expense_reviewed('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001')$$, 'partner can confirm a shared expense review');
select is((select review_status from public.expenses where id='30000000-0000-0000-0000-000000000001'), 'reviewed', 'review status is persisted');
select is((select count(*)::int from public.recurring_bills where id='40000000-0000-0000-0000-000000000001'), 1, 'partner sees shared recurring bill');
select is((select count(*)::int from public.recurring_bills where id='40000000-0000-0000-0000-000000000002'), 0, 'partner cannot see private recurring bill');
select is((select count(*)::int from public.savings_goals where id='50000000-0000-0000-0000-000000000001'), 1, 'partner sees shared savings goal');
select lives_ok($$select public.save_savings_goal('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','Trip updated',1000,200,50,null)$$, 'partner can update shared goal through RPC');
select is((select created_by::text from public.savings_goals where id='50000000-0000-0000-0000-000000000001'), '10000000-0000-0000-0000-000000000001', 'partner update preserves creator');
update public.expenses set description='unauthorized' where id='30000000-0000-0000-0000-000000000001';
select is((select description from public.expenses where id='30000000-0000-0000-0000-000000000001'), 'shared item', 'partner cannot edit creator expense');
delete from public.expenses where id='30000000-0000-0000-0000-000000000001';
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000001'), 1, 'partner cannot delete creator expense');
update public.recurring_bills set description='unauthorized' where id='40000000-0000-0000-0000-000000000001';
select is((select description from public.recurring_bills where id='40000000-0000-0000-0000-000000000001'), '', 'partner cannot edit creator recurring bill');
delete from public.recurring_bills where id='40000000-0000-0000-0000-000000000001';
select is((select count(*)::int from public.recurring_bills where id='40000000-0000-0000-0000-000000000001'), 1, 'partner cannot delete creator recurring bill');
select throws_ok($$insert into public.expense_events(expense_id,space_id,actor_id,event_type,visibility,snapshot) values ('30000000-0000-0000-0000-000000000099','20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','INSERT','shared','{}')$$, '42501', null, 'app clients cannot insert expense events');

reset role;
select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*)::int from public.expenses), 0, 'outsider sees no expenses');
select is((select count(*)::int from public.savings_goals), 0, 'outsider sees no savings goals');
select is((select count(*)::int from public.expense_balances), 0, 'outsider sees no balance projections');
reset role;

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000004","role":"authenticated","aal":"aal1"}', true);
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000005'), 0, 'verified MFA user is blocked at AAL1');
select throws_ok($$select public.mark_expense_reviewed('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000002')$$, 'P0001', null, 'verified MFA user cannot call protected RPC at AAL1');
select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000004","role":"authenticated","aal":"aal2"}', true);
select is((select count(*)::int from public.expenses where id='30000000-0000-0000-0000-000000000005'), 1, 'verified MFA user can read at AAL2');
select lives_ok($$select public.mark_expense_reviewed('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000002')$$, 'verified MFA user can call protected RPC at AAL2');
reset role;
set local role anon;
select throws_ok($$select id from public.expenses limit 1$$, '42501', null, 'anonymous role is denied expense access');
reset role;

select * from finish();
rollback;
