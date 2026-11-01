# Supabase setup for two-phone sync

The main phone records. A second phone runs the Viewer app and shows everything read-only. Supabase is only a mailbox:
changes wait there until the second phone has taken them, and anything older than 7 days is deleted.

## Do this once

1. Go to supabase.com, make a free account and click **New project**. Pick the region **Mumbai (ap-south-1)** and a strong database password (keep it in your password manager; the app never needs it).
2. **Turn off sign-ups.** Authentication > Sign In / Providers > Email: switch **Allow new users to sign up** off. The key in the app is public, so this stops strangers from creating accounts.
3. **Add your one user.** Authentication > Users > Add user > Create new user. Email and password, with **Auto Confirm User** on. This is the account both phones use.
4. **Run the setup.** SQL Editor > New query, paste all of `supabase/sync-setup.sql`, press **Run**. It says "Success". It is safe to run again.
5. **Check it.** In the SQL editor run `select tablename, rowsecurity from pg_tables where schemaname = 'public' and tablename like 'sync_%';` and check both rows say `true`.
6. **Copy two values.** Project Settings > API: the **Project URL** and the **anon public** key. Never copy the `service_role` key anywhere.
7. In the GitHub repo: Settings > Secrets and variables > Actions > New repository secret, add:
   - `SUPABASE_URL` (the Project URL)
   - `SUPABASE_ANON_KEY` (the anon public key)
   - `SYNC_EMAIL` and `SYNC_PASSWORD` (the user from step 3)

The email and password end up inside the APKs, so keep the repo private and do not share the APK files.

## What is in the mailbox

| Table | What it holds |
| --- | --- |
| `sync_inbox` | One row per change: which table, which row, upsert or delete, and the row as JSON (raw GPS goes in compressed batches). Numbered in order by `seq`. |
| `sync_requests` | The second phone asking for a full copy again, for example after being off for more than 7 days. |

## Safety rules (Row Level Security)

- Both tables: a signed-in user can read, add and remove only rows whose `user_id` is theirs. Nothing can be edited in `sync_inbox`.
- The public key alone (not signed in) cannot read or write anything.
- `sync_backlog()` tells the main phone how much is waiting, so it pauses before the free 500 MB fills.
- `sync_cleanup()` runs every hour and deletes anything older than 7 days.
