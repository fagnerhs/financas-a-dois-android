alter table public.expenses
  add column kind text not null default 'expense';

alter table public.expenses
  add constraint expenses_kind_check check (kind in ('expense', 'income')),
  drop constraint if exists expenses_category_check,
  add constraint expenses_category_check check (
    (kind = 'expense' and category in ('Alimentação','Mercado','Moradia','Transporte','Lazer','Saúde','Educação','Delivery','Outros'))
    or (kind = 'income' and char_length(btrim(category)) between 1 and 60)
  ),
  add constraint expenses_income_visibility_check check (kind <> 'income' or visibility <> 'balances_only');
