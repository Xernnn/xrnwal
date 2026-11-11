<p align="center">
  <img src="docs/icon.svg" width="72" height="72" alt="">
</p>

<h1 align="center">xrnwallet</h1>

A local-first personal money tracker for Android: ledgers, accounts, categories, budgets, recurring
bills, savings plans, reports, reminders, CSV and backup files, and sync to a server of your own.
Your data lives on the phone. Only two things use the network: the sync, if you connect a server, and
the exchange-rate refresh, which sends the base currency code and runs only when you press the
refresh button.

Kotlin and Jetpack Compose, with Room for storage. Minimum Android 8.0.

## What it does

- **Bills:** expenses, income and transfers between accounts, with fees, tags, notes, photos and
  future dates. The keypad adds up sums as you type. Hold a bill to edit it, add it again today or
  delete it, and Undo brings a deleted bill back for a few seconds.
- **Home and Calendar:** expenses, income and balance for one ledger or all of them, by week, month,
  year or a range of your own, with search, a budget card and checklists. Calendar shows each day's
  totals.
- **Reports:** by week, month, year or range, with categories ranked and the bills behind each row.
- **Accounts:** each balance in its own currency, converted for net assets, and amounts hidden when
  you'd rather not show them.
- **Planning:** a monthly budget and budgets per category, recurring bills that record themselves
  (and ask what to do about dates already past), and savings plans that are fixed, growing or open.
- **Currencies:** a base currency, others with their rates, and a converter. A bill keeps the rate it
  was recorded at, so history doesn't move when rates change.
- **Made to fit:** ledgers, categories and subcategories, tags, light and dark with colour accents,
  your own start of month, number formats, reminders, six home-screen widgets and eight icon colours.
- **Lock:** PIN, pattern or biometric. While a lock is set, widgets show dots instead of amounts and
  Recents keeps no picture of the app.
- **Files:** CSV export and import, in its own format or a general template, and backup files that
  hold everything but the app lock, photos included.

Every gesture has a button that does the same, and nothing is ever deleted by a swipe.

## Sync

Optional. The phone sends its whole wallet to your server as one document a few seconds after a
change and when the app opens, retries by itself when the server can't be reached, and checks every
6 hours. A change made on another phone comes down the same way.

Nothing is overwritten without asking. When both sides changed, when a new phone meets a server that
already has a copy, when this phone was restored from a file or had all its data deleted, or when the
server went backwards, the app asks which copy to keep, and files the other on the server first. The server keeps every copy it replaces, the last 20 and the first of each
day for 60 days, so Earlier copies can undo a choice.

The app lock never leaves the phone: taking the server's copy, or restoring a backup file, keeps this
phone's own lock. The login is stored encrypted with a key in the phone's keystore, and the release
build only accepts `https://` addresses.

## Build and test

```sh
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Use the release build day to day. A debug build responds about three times slower, because Android
can't precompile it. Both are signed with the build machine's debug key, so either installs over the
other and keeps the app's data. That key isn't in the repo and must never be added to it.

## Sync server

`server/xrnwallet-sync.py` keeps a copy of the wallet on a home server. It is one Python file, needing
only `bcrypt` beyond the standard library.

```sh
python3 -m unittest server/test_xrnwallet_sync.py   # server tests
server/deploy.sh [ssh-host]                         # install or update it (Termux, runit, Funnel)
```

In the app: Mine, Data synchronization, then the server's address ending in `/xrnwallet`.

## Credits

Uses [MiSans](https://hyperos.mi.com/font) by Xiaomi.

## Docs

- [Feature spec](docs/feature-spec.md): what every screen does.
- [Status](docs/status.md): what has been tested, and where the app differs from the spec's reference.
