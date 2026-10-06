# Life Tracker

A private, local-first Android app for logging your day, food, habits, money and studying, with stats on all of it.

Built with Kotlin and Jetpack Compose. All data stays on the phone.

## Status

Step 2 of the plan: a real on-phone database (Room/SQLite) and a working Log button. Timeline lets you pick a day, log an activity with start and end times (or just a moment), see daily study time against an 8h goal, and tap an entry to delete it. The Food, Habits, Money and Stats tabs are still placeholders.

## Installing on your phone

1. Open this repository's **Releases** page on GitHub, in your phone browser.
2. Open the newest release and download `life-tracker.apk`.
3. Tap the downloaded file. Android will ask once to allow installs from your browser or Files app. Allow it.
4. Google Play Protect may warn that the app is from outside the Play Store. That is expected for your own app.

Every push builds a new APK automatically (Actions tab, a few minutes). A new build installs over the old one and keeps your data, because every build is signed with the same key.

## About the signing key

`keystore/lifetracker.p12` is committed on purpose. It only exists so updates install over older versions. The password is in `app/build.gradle.kts`. Keep this repository private. If it is ever made public, make a new key and uninstall and reinstall the app once (export a backup first).
