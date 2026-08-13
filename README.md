# Expiry Date Reminder

Keeps track of what's about to go off, so you find out before you open the fridge
and not after. Food, medicine, warranties, passports, anything with a date on it.

<p align="left">
<a href="https://play.google.com/store/apps/details?id=com.anish.expirydatereminder">
    <img alt="Get it on Google Play"
        height="80"
        src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
</a>
</p>

By Anish Sahoo, MIT licensed. Version 2.0 is a rewrite of the 2019-2022 Java app;
that history is still in this repo, below the rewrite commit.

## Features

- Scan the printed date off a label with the camera instead of typing it
- Items grouped by urgency, with a count of what needs attention
- Home screen widget, showing more detail the bigger you make it
- Notifications at a lead time and hour you choose
- Photos and notes per item, search, sorting, built-in and custom categories
- Date formats picked from your region, changeable in settings
- Eight languages, and colors that follow your wallpaper on Android 12+

Everything is stored on device. No account, and the camera is only used when you scan.

## Technicalities

- Kotlin 2.4, Compose Multiplatform, Material 3 Expressive
- SQLDelight for the local database, Koin for DI, kotlinx-datetime
- ML Kit Text Recognition, with the GenAI Prompt API where the device supports it
- Glance for the widget, WorkManager for reminders
- minSdk 31 (Android 12), targetSdk 36, JVM 21
- Item photos live in `filesDir`, readable only by the app

Layout:

    shared/       domain model, repositories, SQLDelight schema, most of the tests
    composeApp/   Android app: UI, widget, notifications, camera, migration
    config/       detekt configuration
    store-assets/ Play listing icon, feature graphic and screenshots, with generators

## Building

    ./gradlew :composeApp:assembleDebug
    ./gradlew ciCheck          # ktlint, detekt, Android Lint, unit tests, debug build
    ./gradlew ciCheckDevice    # the above plus instrumented tests

`ciCheck` is what GitHub Actions runs, one step per gate.

Release builds are signed only when a gitignored `keystore.properties` exists in the
project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it the
build still produces an unsigned artifact rather than failing. Upload `mapping.txt`
with the bundle or Play Console crash reports are unreadable.

## Migrating from 1.5

`LegacyImporter` reads the old app's SQLite database and image cache on first launch
and imports both. It runs once, is idempotent, and leaves the old data in place. It is
the only code path that can lose data users cannot get back, so it carries the most tests.

Privacy policy in [PrivacyPolicy.md](PrivacyPolicy.md).
