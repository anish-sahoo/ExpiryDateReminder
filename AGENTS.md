# Working in this repo

Notes for anyone, human or otherwise, changing this codebase. Things the code
itself does not say.

## Layout

    shared/       domain model, repositories, SQLDelight schema, most of the tests
    composeApp/   Android app: UI, widget, notifications, camera, migration
    config/       detekt configuration
    store-assets/ Play listing assets, with the scripts that generate them

`shared` is multiplatform-shaped but only Android is wired up. Nothing outside
`androidMain` may depend on Android.

## Commands

    ./gradlew :composeApp:assembleDebug
    ./gradlew ciCheck          # ktlint, detekt, Android Lint, unit tests, debug build
    ./gradlew ciCheckDevice    # the above plus instrumented tests

`ciCheck` and `.github/workflows/ci.yml` must stay in step. CI runs one task per
job step so failures are attributable; adding a task to `ciCheck` means adding a
step there too.

Run the formatter and linter after changing code, and fix what they flag.

## Conventions

- ktlint is wired through the CLI, not the Gradle plugin. AGP 9 supplies its own
  Kotlin plugin, so `org.jetbrains.kotlin.android` is never applied and
  ktlint-gradle registers no source-set tasks. Style comes from `.editorconfig`.
- American English throughout, including comments and strings. The pre-2.0 code
  was British.
- Radii come from `EdrShapes`. No one-off `RoundedCornerShape` values.
- Comments explain why, not what. A rule or a workaround with no stated reason is
  one nobody can safely delete later.

## Things that have already gone wrong

- **`LegacyImporter`** is the only path that can lose data users cannot get back.
  It reads the 1.5 database and image cache on first launch, once, and leaves the
  original files alone. Treat changes here as the highest-risk edits in the repo.
- **R8 strips ML Kit's reflective constructors.** The failure is silent: the app
  starts, logs `NoSuchMethodException` at WARN, and scanning never works. Keep
  rules and their reasoning are in `composeApp/proguard-rules.pro`. A debug build
  cannot show this, so exercise `assembleRelease` before shipping.
- **Widget sizing** lives in `WidgetSizing.kt` as plain Kotlin, deliberately, so
  it is testable without Glance. It still leaves dead space and drops its overflow
  line at tall sizes.
- **Schema defaults and `AppSettings()` must agree.** They silently disagreed
  once; `SettingsDefaultsTest` is the guard.
- **Instrumented test method names cannot contain commas.** Dex will not represent
  them. JVM tests can keep backticked sentences.
- **Robolectric boots the real `EdrApplication`** and Koin then refuses to start
  twice. Use `@Config(application = Application::class)`.

## Signing

Release builds are signed only when `keystore.properties` exists in the project
root, holding `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. It is
gitignored, along with `*.jks` and `*.keystore`, and must stay that way. Without
it the build produces an unsigned artifact rather than failing configuration, so
CI needs no secrets.

Upload `mapping.txt` with every bundle or Play Console crash reports are
unreadable.

## Known gaps

- The eight translations are machine generated and unreviewed.
- OCR and the GenAI path have only ever run on an emulator.
- Install-over-1.5 has never been tested through a real Play install.
