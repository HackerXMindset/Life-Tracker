# Life Tracker

A private, local-first Android app for logging your day, food, habits, money and studying, with stats on all of it.

Built with Kotlin and Jetpack Compose. All data stays on the phone.

## Status

Step 1 of the plan: the app skeleton. Five tabs (Timeline, Food, Habits, Money, Stats), where Timeline shows sample entries and the others describe what is coming. The database and real logging are step 2.

## Installing on your phone

1. Open this repository's **Releases** page on GitHub, in your phone browser.
2. Open the newest release and download `life-tracker.apk`.
3. Tap the downloaded file. Android will ask once to allow installs from your browser or Files app. Allow it.
4. Google Play Protect may warn that the app is from outside the Play Store. That is expected for your own app.

Every push builds a new APK automatically (Actions tab, a few minutes). A new build installs over the old one and keeps your data, because every build is signed with the same key.

## About the signing key

`keystore/lifetracker.p12` is committed on purpose. It only exists so updates install over older versions. The password is in `app/build.gradle.kts`. Keep this repository private. If it is ever made public, make a new key and uninstall and reinstall the app once (export a backup first).
