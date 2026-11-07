# xrnwallet

A local-first personal money tracker for Android: ledgers, accounts, categories, budgets, recurring
bills, savings plans, reports, reminders, CSV and backup files, and sync to a server of your own.
Your data lives on the phone. Only two things use the network: the sync, if you connect a server, and
the exchange-rate refresh, which sends the base currency code and runs only when you press the
refresh button.

Kotlin and Jetpack Compose, with Room for storage. Minimum Android 8.0.

## Build and test

```sh
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

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
