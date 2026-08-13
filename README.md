# Expiry Date Reminder

Android app for tracking expiry dates on food, medicine, warranties and anything
else with a date printed on it. Point the camera at a label and it reads the date
off it. Published on Google Play as `com.anish.expirydatereminder`.

Version 2.0 is a rewrite. The 2019-2022 app was Java, Activities and raw SQLite;
this is Kotlin Multiplatform, Compose and SQLDelight, shipped as an update to the
same listing. That history is still in this repo, below the rewrite commit.

## Stack

- Kotlin 2.4, Compose Multiplatform, Material 3 Expressive
- SQLDelight for the database, Koin for DI, kotlinx-datetime
- ML Kit Text Recognition, with the GenAI Prompt API on devices that have it
- Glance for the home screen widget, WorkManager for reminders
- minSdk 31, targetSdk 36, JVM 21

## Layout

    shared/       domain model, repositories, SQLDelight schema, most of the tests
    composeApp/   Android app: UI, widget, notifications, camera, migration
    config/       detekt configuration
    store-assets/ Play Store icon, feature graphic and screenshots, with generators

The `shared` module is multiplatform-shaped but only Android is wired up. Nothing
in it depends on Android outside `androidMain`.

## Building

    ./gradlew :composeApp:assembleDebug

Release builds are signed only if `keystore.properties` exists in the project
root, which is gitignored and must stay that way. Without it the build still
succeeds and produces an unsigned artifact rather than failing configuration:

    storeFile=/path/to/upload-key.jks
    storePassword=...
    keyAlias=...
    keyPassword=...

Then `./gradlew :composeApp:bundleRelease`. Upload `mapping.txt` alongside the
bundle or Play Console crash reports will be unreadable.

## Checks

    ./gradlew ciCheck          # ktlint, detekt, unit tests, lint, assembleDebug
    ./gradlew ciCheckDevice    # the above plus instrumented tests

`ciCheck` is what GitHub Actions runs. ktlint is wired through the CLI rather than
the Gradle plugin, because AGP 9 supplies its own Kotlin plugin and ktlint-gradle
registers no source-set tasks in that setup.

## Migrating from 1.5

`LegacyImporter` reads the old app's SQLite database and image cache on first
launch and imports both. It runs once, is idempotent, and leaves the old data in
place. This is the part of the app most worth being careful with: it is the only
code path where a bug loses data that users cannot get back.

## License

MIT, see `LICENSE`. Privacy policy in `PrivacyPolicy.md`.
