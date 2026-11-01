-- Kairos two-phone sync: the mailbox.
-- Paste this whole file into Supabase > SQL Editor > New query > Run. It is safe to run again.
--
-- How it works: the main phone puts changes into sync_inbox; the second phone reads them, applies them
-- and deletes them. Nothing is kept longer than 7 days. Row Level Security means a signed-in user can only
-- ever see their own rows, and the public (anon) key on its own can see nothing.

-- 1. The mailbox ------------------------------------------------------------------------------------------
create table if not exists public.sync_inbox (
    seq         bigint generated always as identity primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    device_id   text not null,
    table_name  text not null,
    row_key     text not null,
    op          text not null check (op in ('upsert', 'delete', 'batch', 'settings')),
    payload     jsonb,
    payload_gz  text,
    bytes       integer not null default 0 check (bytes between 0 and 6000000),
    created_at  timestamptz not null default now(),
    -- a delete carries no data; everything else carries one of the two payloads
    constraint sync_inbox_payload_ok check (
        (op = 'delete' and payload is null and payload_gz is null)
        or (op <> 'delete' and (payload is not null or payload_gz is not null))
    )
);

create index if not exists sync_inbox_user_seq on public.sync_inbox (user_id, seq);
create index if not exists sync_inbox_created on public.sync_inbox (created_at);

-- 2. Requests for a full copy (the second phone asks, the main phone answers) ---------------------------
create table if not exists public.sync_requests (
    id          bigint generated always as identity primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    device_id   text not null,
    reason      text not null default 'catch-up',
    created_at  timestamptz not null default now(),
    handled_at  timestamptz
);

create index if not exists sync_requests_user on public.sync_requests (user_id, created_at);

-- 3. Row Level Security: your rows only ------------------------------------------------------------------
alter table public.sync_inbox enable row level security;
alter table public.sync_requests enable row level security;

drop policy if exists "inbox select own" on public.sync_inbox;
drop policy if exists "inbox insert own" on public.sync_inbox;
drop policy if exists "inbox delete own" on public.sync_inbox;
create policy "inbox select own" on public.sync_inbox for select to authenticated using (user_id = auth.uid());
create policy "inbox insert own" on public.sync_inbox for insert to authenticated with check (user_id = auth.uid());
create policy "inbox delete own" on public.sync_inbox for delete to authenticated using (user_id = auth.uid());
-- no update policy on purpose: a change in the mailbox is never edited, only added and taken away

drop policy if exists "requests select own" on public.sync_requests;
drop policy if exists "requests insert own" on public.sync_requests;
drop policy if exists "requests update own" on public.sync_requests;
drop policy if exists "requests delete own" on public.sync_requests;
create policy "requests select own" on public.sync_requests for select to authenticated using (user_id = auth.uid());
create policy "requests insert own" on public.sync_requests for insert to authenticated with check (user_id = auth.uid());
create policy "requests update own" on public.sync_requests for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "requests delete own" on public.sync_requests for delete to authenticated using (user_id = auth.uid());

-- The public key gets nothing; a signed-in user gets only what the policies allow.
revoke all on public.sync_inbox from anon, public;
revoke all on public.sync_requests from anon, public;
grant select, insert, delete on public.sync_inbox to authenticated;
grant select, insert, update, delete on public.sync_requests to authenticated;

-- 4. How much is waiting (the main phone pauses uploads when this gets big) --------------------------
create or replace function public.sync_backlog()
returns table (waiting_rows bigint, waiting_bytes bigint, oldest timestamptz)
language sql
security invoker
stable
as $$
    select count(*), coalesce(sum(bytes), 0), min(created_at)
    from public.sync_inbox
    where user_id = auth.uid();
$$;
revoke all on function public.sync_backlog() from anon, public;
grant execute on function public.sync_backlog() to authenticated;

-- 5. Live updates (Realtime) ---------------------------------------------------------------------------------
do $$
begin
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'sync_inbox') then
        alter publication supabase_realtime add table public.sync_inbox;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'sync_requests') then
        alter publication supabase_realtime add table public.sync_requests;
    end if;
end $$;

-- 6. Nothing is kept longer than 7 days ------------------------------------------------------------------
create extension if not exists pg_cron with schema extensions;

create or replace function public.sync_cleanup()
returns void
language sql
security definer
set search_path = public
as $$
    delete from public.sync_inbox where created_at < now() - interval '7 days';
    delete from public.sync_requests where created_at < now() - interval '7 days';
$$;
revoke all on function public.sync_cleanup() from anon, authenticated, public;

select cron.unschedule(jobid) from cron.job where jobname = 'sync-cleanup';
select cron.schedule('sync-cleanup', '17 * * * *', $$select public.sync_cleanup();$$);
