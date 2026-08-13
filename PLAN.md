# ExpiryDateReminder 2.0 — Implementation Plan

Rewrite of the Java/XML/raw-SQLite app at `../ExpiryDateReminder` as Kotlin
Multiplatform + Compose, shipping as an update to the existing Play listing.

Status: approved, not yet started. Last revised 2026-08-06.

---

## 1. Non-negotiable constraints

Everything in this plan bends around these.

| Constraint | Value |
|---|---|
| applicationId / namespace | `com.anish.expirydatereminder` (unchanged) |
| versionCode | ≥ 7 (live is 6, versionName 1.5) |
| Signing cert SHA-1 | `1C:5B:45:F0:FB:57:60:70:CC:CF:29:21:17:E7:87:F6:1E:BF:EC:14` (confirmed working) |
| targetSdk | 36 (Play requires it from 2026-08-31) |
| minSdk | 31, raised from 26 |
| Data | Existing users lose nothing, or migration fails cleanly and stays recoverable |

Raising minSdk to 31 drops Android 8–11 users from future updates. They keep the
working v1.5 build and lose no data. This is a deliberate, accepted tradeoff, made
so Material You dynamic color needs no fallback branching.

## 2. Stack

- Kotlin 2.4.0, AGP 8.13.0, Gradle 8.14.3, JVM target 21
- Compose Multiplatform 1.11.0; AndroidX Navigation Compose
- **SQLDelight 2.2.1** for persistence
- Koin 4.2.2, kotlinx-datetime 0.7.1 (pin exactly — 0.7.0 removed `Instant`/`Clock`),
  WorkManager 2.11.2
- ML Kit Text Recognition v2 `16.0.1` (unbundled), ML Kit GenAI Prompt API `1.0.0-beta4`
- Coil (images), Glance (widget), Turbine (Flow tests)

Verify every version resolves during Phase 1 rather than trusting this table. The
current stable `androidx.compose.material3` version and whether Material 3
Expressive components are stable as of Aug 2026 both need confirming on day one.

### Why SQLDelight over Room 3.0

Room computes an identity hash and validates it against a stamp Room itself wrote
into the database at creation. The legacy database was written by hand-rolled
`SQLiteOpenHelper` subclasses and carries no such stamp, so Room either rejects it
or forces you through `createFromFile`, which is built for read-only seed databases
rather than live read-write ones. SQLDelight's driver opens whatever file you point
it at and leaves `onCreate`/`onUpgrade` under your control.

Room 3.0 also only reached stable on 2026-07-01. Rejected alternative: Room with a
custom `SupportSQLiteOpenHelper.Factory` bypassing Room's own open logic, which is
more code to arrive where SQLDelight already is.

## 3. Module layout

```
ExpiryDateReminder2/
  gradle/libs.versions.toml
  build-logic/                      convention plugins
  shared/
    src/commonMain/                 domain models, use cases, DateParser, repositories
    src/commonMain/sqldelight/      Item.sq, Category.sq, Settings.sq
    src/androidMain/                DriverFactory, ImageStore, LegacyImporter
    src/commonTest/                 parser, use-case, repository tests
  composeApp/
    src/androidMain/kotlin/com/anish/expirydatereminder/
      MainActivity.kt  EdrApplication.kt
      ui/{items,settings,scan,help,theme}/
      camera/  notifications/  widget/  shortcuts/  di/
```

`shared/domain` has zero Android imports, structurally enforced by being a KMP
source set. UI stays in `composeApp` for now — moving finished screens into
`commonMain` later is mechanical, whereas getting the data layer wrong is not.

---

## Phase 0 — De-risk before building

**0.1 Signing.** CONFIRMED RESOLVED. No longer blocking.

**0.2 OCR viability spike.** Throwaway module outside the repo. ML Kit + CameraX
against 30+ real package photos spanning inkjet print, embossed plastic, and
dot-matrix blister packs. Because the extracted date lands in an editable field,
the bar is *"does prefill save more taps than it wastes"*, not *"is it
trustworthy"*. Measure the rate of confidently-**wrong** extractions specifically —
a silently wrong date the user doesn't notice is the only genuinely harmful
outcome. Assumed acceptable per user direction.

**0.3 Capture a real legacy database.** Install the current release build, use it
properly (items across several categories, images attached, a custom category, a
deleted item, changed settings), then:

```
adb pull /data/data/com.anish.expirydatereminder/databases/itemsDatabase
```

Pull the `-wal` and `-shm` sidecars too if present. Also craft an empty fixture and
a corrupt one with a dangling category reference. This real file is worth more as
test input than any synthetic fixture.

**0.4 Verify library minSdk floors.** Confirm ML Kit Text Recognition, ML Kit
GenAI, CameraX and Glance don't declare a minSdk above 31, which would fail the
manifest merge. Remedy is `tools:overrideLibrary` plus the runtime availability
gate. Cheap now, disruptive to discover in Phase 6.

---

## Phase 1 — Skeleton and engineering baseline

Version catalog, convention plugins, both modules building, blank Compose activity
installing under the correct applicationId.

- ktlint and detekt with committed configs, wired to fail the build
- GitHub Actions running build, tests, lint, detekt, dependency review
- `signingConfig` from day one reading a gitignored `keystore.properties`, never
  Android Studio's signed-bundle dialog like the old project
- **i18n conventions established now, not retrofitted**: string catalog structure,
  `plurals` discipline, and a lint rule failing the build on hardcoded UI strings.
  Retrofitting this across finished screens is exactly the sweep worth avoiding.

**Done when** `./gradlew :composeApp:assembleDebug` produces an installable debug
APK and the static analysis gates work.

---

## Phase 2 — Database and migration

The highest-stakes phase. A migration bug that ships cannot be fixed retroactively
for anyone who already ran it.

### 2.1 Schema

```sql
CREATE TABLE item (
  id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,
  categoryId   INTEGER NOT NULL REFERENCES category(id) ON DELETE CASCADE,
  expiryDay    INTEGER NOT NULL,
  expiryMonth  INTEGER NOT NULL,
  expiryYear   INTEGER NOT NULL,
  imagePath    TEXT,
  createdAt    INTEGER NOT NULL,
  updatedAt    INTEGER NOT NULL
);
CREATE INDEX item_category_idx ON item(categoryId);
CREATE INDEX item_expiry_idx   ON item(expiryYear, expiryMonth, expiryDay);

CREATE TABLE category (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  builtinKey  TEXT UNIQUE,   -- 'grocery', 'frozen', ... NULL for user categories
  isBuiltin   INTEGER NOT NULL DEFAULT 0
);
-- uniqueness applies only to user-created names; a user category may legitimately
-- collide with a *translated* built-in name
CREATE UNIQUE INDEX category_user_name_idx ON category(name) WHERE builtinKey IS NULL;

CREATE TABLE app_settings (
  id                    INTEGER NOT NULL PRIMARY KEY DEFAULT 0 CHECK (id = 0),
  dateFormat            INTEGER NOT NULL DEFAULT 1,
  notificationsEnabled  INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE migration_meta (
  id           INTEGER NOT NULL PRIMARY KEY DEFAULT 0 CHECK (id = 0),
  migratedAt   INTEGER NOT NULL,
  itemCount    INTEGER NOT NULL,
  orphanCount  INTEGER NOT NULL
);
```

Day/month/year stay as separate integers. Collapsing to an epoch timestamp invites
timezone bugs for what is semantically a calendar date with no time component.

`ON DELETE CASCADE` is chosen deliberately: it reproduces existing behavior, since
`SettingsDatabase.deleteCategory` already deletes the category *and* every item
under it. **`ON DELETE SET DEFAULT` would be broken here** — SQLite treats a column
with no `DEFAULT` clause as defaulting to `NULL`, which violates `NOT NULL` and
makes the delete fail outright.

**Foreign keys are OFF by default in SQLite.** Every connection needs
`PRAGMA foreign_keys = ON` via `setForeignKeyConstraintsEnabled(true)` in the
driver callback. Without it the constraint is decorative and cascades never fire.

Three legacy bugs are fixed structurally by this schema:

- `item.id` as a real key kills the name-only delete (`ItemsDatabase.java:124`
  deletes by `itemName=?` alone, wiping same-named items across all categories)
- `categoryId` as a foreign key means renaming a category no longer orphans items
- `imagePath` as a stored column means editing an item no longer orphans its image,
  which is what makes edit-item safe to add

### 2.2 Migration

Target a **separate new file** `edr_v2.db`. The legacy file is never adopted in
place.

**Read the legacy data from a copy, not the original.** Android enables
write-ahead logging by default for `SQLiteOpenHelper` databases, so
`itemsDatabase-wal` and `-shm` almost certainly exist on real devices. A read-only
connection to a WAL database needing recovery can fail with
`SQLITE_READONLY_RECOVERY`, or in the worse case read a database missing its most
recent committed transactions. So:

1. Copy `itemsDatabase` plus both sidecars to a scratch directory.
2. Open the **copy** read-write, letting SQLite recover and checkpoint normally.
3. Read all four tables into memory. Close. Delete the scratch copy.

The original file is never touched by anything, under any failure mode.

Then:

4. Open `edr_v2.db`, `BEGIN TRANSACTION`, insert everything **including the
   `migration_meta` row**, `COMMIT`.
5. Only after a successful commit, rename the legacy file to `itemsDatabase.bak`.

**On any failure:** roll back, leave `edr_v2.db` empty, and leave the legacy file
completely untouched and un-renamed. Surface a clear message plus a **Retry
import** action in Settings that re-runs against the still-present legacy file.
Retry must be safe to run repeatedly.

#### The guard must be the marker row, never file existence

SQLDelight creates `edr_v2.db` the moment it opens it, before any transaction
commits. A "skip if the file exists" guard would therefore see the file after a
rolled-back migration, conclude the work was already done, and skip forever —
leaving the user with an empty app and their data unread. Because the
`migration_meta` row is written **inside** the transaction, marker-present is
logically equivalent to transaction-committed. That is the only safe guard.

#### Restore-after-first-launch

If someone installs fresh, opens the app once, and *then* a device-to-device
transfer drops the legacy file into place, a "checked once, nothing there" marker
would mean it is never migrated. So: only write the marker when a migration
actually ran, and keep performing the cheap `File.exists()` check on every launch.

#### Translation details

- Preserve old row ids via explicit `id` inserts, which avoids needing a remap
  table. SQLite maintains `sqlite_sequence` against the largest rowid seen, so
  later autoincrements continue above the max correctly.
- Items whose `category` string matches no category row go to a fallback rather
  than being dropped. Count them and report in the completion summary.
- **Watch the notification remap**: legacy `1`=enabled / `2`=disabled becomes
  boolean `1`/`0`. Getting this backwards silently inverts every user's
  preference, so it needs an explicit test assertion.
- Built-in categories map from their English names onto stable `builtinKey`
  values. This is deterministic because the old app never allowed renaming a
  built-in, only adding and deleting `type=1` rows.

**Done when** migration tests pass against all three fixtures, including: a forced
mid-transaction failure leaves the legacy file un-renamed and the new database
without a marker; re-running after that failure succeeds; and running twice in a
row is a no-op.

---

## Phase 3 — Images

Move to `filesDir/images/` so Android can no longer evict them, with opaque
filenames (`item_<id>.jpg`) decoupled from item fields.

During the Phase 2 pass, compute the legacy filename using the exact old formula
from `FileUtils.java:11`:

```
"image_" + itemName + "." + date + "." + month + "." + year + "." + category + ".jpg"
```

(unpadded integers) and copy from `cacheDir/images/` when present.

Filesystem operations are not transactional, so **image copying is deliberately
best-effort and outside the database transaction**. A failed copy yields
`imagePath = null`, identical to an item that never had an image, and never aborts
the migration. The database stays the source of truth.

Update FileProvider from `cache-path` to `files-path`. Change backup rules to
include `files/images/` so images survive device migration, which they never did
before.

---

## Phase 4 — Domain and data

Pure-Kotlin domain models and use cases: add, **update** (new), delete-by-id,
duplicate detection (the legacy four-case `SearchResult` enum as a sealed type),
sorting, expiry threshold.

Repositories expose `Flow` via SQLDelight's coroutine extensions. Dispatchers are
injected so tests stay deterministic. Expected failures are sealed return types,
not exceptions — no blanket `catch (Exception e)` like `WakeUpReceiver`.

Edit-item needs deliberate handling for the case where an edit moves an item onto
an existing item's identity.

---

## Phase 5 — UI

Single activity, Compose Navigation. Screens: item list, item detail, add/edit
sheet, settings, help.

**Material You is the default path.** `dynamicLightColorScheme` /
`dynamicDarkColorScheme` unconditionally at minSdk 31; static fallback only for
genuine runtime unavailability. Full M3 beyond color: type scale, shape system,
`surfaceContainer*` roles rather than M2 elevation overlays, `enableEdgeToEdge()`.
Adaptive launcher icon with a monochrome layer, replacing the raw PNG the old
manifest points at. The old brand purple carries no obligation.

UX priorities, roughly by impact:

- **Urgency as the primary visual signal.** The old app rendered a flat
  `"<date> : <name>"` list with no indication of what is about to go bad, which is
  the biggest gap in the original. Color treatment and grouping for expired /
  expiring soon / fine, with relative phrasing ("expires in 3 days") beside the
  absolute date.
- Swipe-to-delete with an undo snackbar, replacing the blocking
  `setCancelable(false)` dialog fired on every single deletion. Explicit
  confirmation stays only for bulk-destructive operations.
- Inline validation as the user types, replacing the one-toast-at-a-time cascade
  that cleared fields on error. M3 `DatePicker` alongside manual entry.
- Shared element transitions between list and detail, `animateItem` on list
  changes, reduce-motion honored.
- Empty states distinguishing "nothing yet" from "nothing matches your filter".
  Snackbars replacing every Toast.
- Migration shows brief progress and a summary ("brought over 47 items and 3
  categories").

Search filters client-side over the loaded Flow; these data volumes do not justify
FTS. "All Items" becomes filter-only and is no longer assignable. The refresh FAB
is dropped, made obsolete by reactive lists.

---

## Phase 6 — Camera scan

`OcrEngine` and `DateEntityExtractor` are commonMain interfaces with androidMain
implementations bound through Koin. `DateParser` lives in commonMain as pure
Kotlin with no Android dependency, making it exhaustively JVM-testable.

The parser does regex matching across common formats, keyword proximity scoring,
plausibility filtering by date range and calendar validity, and DD/MM vs MM/DD
disambiguation using the user's own date-format setting as tiebreaker.

`ScanCoordinator` runs OCR, then the parser, and escalates to Gemini Nano **only**
when confidence is low, candidates are ambiguous, or no keyword anchored the match.
Nano receives the OCR **text**, never the image.

**UX.** A scan affordance sits next to the date field in the add/edit sheet.
Results populate the day/month/year fields in place and the user edits freely —
the form is the confirmation, there is no separate confirmation step. Multiple
candidates appear as tappable chips. Failure is a non-event: fields stay at
defaults, maybe a quiet inline hint, never an error dialog. A plausible product
name prefills the name field as a suggestion.

**Prefer prefilling nothing over prefilling a low-confidence guess.** That
threshold is a named, tunable constant.

**Device policy.** The scan button appears whenever a camera and Play Services
exist, so effectively everywhere. Nano silently improves results where present.
No "unsupported device" messaging anywhere, no user-visible tiering. The whole
policy sits behind one `ScanAvailability` type so restricting the feature later is
a single change. Handle the first-run case where the unbundled ML Kit model has not
downloaded yet.

CameraX `ImageCapture` at full resolution replaces the old thumbnail-only
`ACTION_IMAGE_CAPTURE`, which also improves OCR input quality.

---

## Phase 7 — Notifications

Replace `AlarmManager.setRepeating` with WorkManager, which fixes reboot
persistence for free (the old alarm was re-registered inline in
`MainActivity.onCreate` and died on reboot).

**Firing time needs a deliberate decision.** The old alarm anchored to 07:00.
WorkManager periodic work has a 15-minute floor and no precise firing time, so a
plain `PeriodicWorkRequest` will drift through the day. If a consistent morning
reminder matters, use a one-shot worker that reschedules itself for the next 07:00,
or `setExactAndAllowWhileIdle`.

Fix the threshold to "expiry between today and today + 14 days". The old logic had
no lower bound, so items that expired years ago kept inflating the count forever.

Request `POST_NOTIFICATIONS` at runtime when the user enables the notification
switch, tying the ask to clear intent. The old app declared and checked the
permission but never requested it, so notifications silently never fired on any
Android 13+ fresh install.

Keep channel id `edr_channel_1` deliberately — changing it discards existing users'
per-channel sound and importance settings. Replace the PNG small icon with a
monochrome vector.

Manifest cleanup: add `CAMERA` (requested at runtime today but never declared, so
the request silently failed), drop the two malformed permissions missing their
`android.permission.` prefix, and relax `android.hardware.camera` to
`required="false"` so the listing stops excluding camera-less devices.

---

## Phase 8 — Widget and shortcut

Both. A Glance widget showing the next N expiring items with the same urgency
treatment as the list, resizable, each row deep-linking to that item. Plus a
long-press launcher shortcut opening the add sheet directly.

---

## Phase 9 — Internationalization

Target locales: **de, fr, es, it, nl, pt, pl**. All Latin script, so ML Kit needs
no additional OCR models.

The legacy `strings.xml` contains exactly one string (`app_name`); everything else
is hardcoded in Java and XML. The catalog is therefore built from scratch
regardless, which is what makes adding locales nearly free now and expensive later.

- **Built-in category names** are stored as English text in the database. The
  `builtinKey` column resolves display names through string resources; user
  categories render `name` literally. Sorting and filtering must use the
  **resolved** display name, or German users get categories sorted by their
  English spelling.
- **Pluralization must be structural.** The old notification text is built by
  concatenation (`"You have " + n + " items expiring within 14 days!"`), which
  cannot be translated correctly — Polish has four plural forms. Use `plurals`
  resources everywhere a count appears.
- **Locale-aware date formatting.** Keep the MM/DD vs DD/MM toggle as an explicit
  override, defaulted from system locale for new installs, preserving the explicit
  choice for migrated users.
- **Per-app language picker** via `AppCompatDelegate.setApplicationLocales` plus
  `locales_config.xml`.
- **Multilingual OCR keywords**: German `MINDESTENS HALTBAR BIS` / `MHD` /
  `VERWENDBAR BIS`; French `À CONSOMMER DE PRÉFÉRENCE AVANT` / `DLC` / `DDM`;
  Spanish `CONSUMIR PREFERENTEMENTE ANTES DEL` / `CAD`; Italian `DA CONSUMARSI
  PREFERIBILMENTE ENTRO` / `SCAD`; Dutch `TEN MINSTE HOUDBAAR TOT` / `THT`; Polish
  `NAJLEPIEJ SPOŻYĆ PRZED`. Plus localized month abbreviations and European
  conventions (dotted `31.03.2027`, DD/MM dominant). DD/MM being the European
  default actually makes disambiguation easier once locale informs the tiebreaker.
- Localize the Play Store listing too, since that is what converts.
- Draft translations, then have native speakers review before shipping. For an app
  whose complaint is "it's English-only", shipping awkward German is a partial win
  at best.

---

## Phase 10 — Release

Enable R8 with real keep rules, add baseline profiles, verify Compose
strong-skipping.

**Prove publishability rather than assuming it:**

```
./gradlew :composeApp:bundleRelease
bundletool build-apks --mode=universal ...
apksigner verify --print-certs ...
```

Install the result **over the live v1.5 build without uninstalling** and confirm no
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` and no data loss. This doubles as the best
available end-to-end migration test.

Then the internal testing track on a device carrying real data, then staged
rollout at 5–10% held **48–72 hours** watching crash rate and any review mentioning
lost data, then step up through 25% / 50% / 100%.

---

## Engineering standards

Threaded through every phase, not bolted on at the end.

- **Architecture**: enforced layering. Domain is pure Kotlin with no framework
  dependencies; data implements domain interfaces; UI depends on domain, never on
  data implementations. Unidirectional data flow, immutable UI state, one state
  type per ViewModel. No god objects — the old `MainActivity` was 330 lines doing
  database access, sorting, alarm scheduling, notification channels, and image
  deletion.
- **Concurrency**: no main-thread I/O anywhere. The old app did every database
  operation synchronously on the main thread.
- **Testing**: the date parser and the migration are the two highest-value targets.
  Turbine for Flow tests, in-memory SQLDelight driver for repository tests. The old
  repo had only the two auto-generated stub tests.
- **Documentation**: README covering architecture and build, KDoc on public domain
  APIs, and ADR notes recording the significant decisions (SQLDelight over Room,
  minSdk 31, prefill-not-confirm scan UX, transactional migration, cascade delete)
  so the reasoning survives.
- **Accessibility**: content descriptions, touch target sizes, a TalkBack pass,
  dynamic type support.

---

## Risks

1. **Migration is one-way per device** and unrecoverable by a later update.
   Mitigated by the transaction, the marker-row guard, the untouched-on-failure
   legacy file, the retry path, and a slow staged rollout. This is why the real
   database from Phase 0.3 matters more than any other test input.
2. **ML Kit GenAI is beta** with no SLA and flagship-only device support. Enforced
   in code as enhancement-only, verified by testing the unavailable path on
   ordinary hardware.
3. **OCR is likely weak on dot-matrix and embossed print.** Minor rather than
   project-threatening, since the manual entry path is untouched and a degraded
   scan feature still ships.
4. **The Nano path ships under-tested** without a Pixel 9/10 or Galaxy S25/S26 on
   hand. Worth accepting consciously.

## Open items

- `versionName` for the release — "2.0" suggested
- Whether the notification permission ask belongs at first run instead of on the
  settings toggle
- Exact notification firing-time strategy (see Phase 7)
