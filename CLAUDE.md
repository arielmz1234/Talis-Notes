# Tali's Notes: notes for Claude

A handwriting notes app for Android tablets, made for **one person**: Tali, a civil-engineering student, writing with an S Pen.
- It is sideloaded as an APK and never goes to the Play Store.
- It replaces Notewise for her.
- Devices: Galaxy Tab S6 Lite and newer (she has a Tab S9 FE).

[README.md](README.md) lists the features and has a code map, so read it first. [BACKUP_SETUP.md](BACKUP_SETUP.md) covers signing and Google Drive.

## Working with the owner

- The owner (Ariel) is often away from the computer and asks for long, unattended runs. When asked to keep going, keep going:
  - don't stop to ask questions;
  - make sensible calls and report them at the end.
- **Taste over feature count.** Add features the way a good notes app (Notewise, GoodNotes, Samsung Notes) would, and only when they clearly help a student. Ideas deliberately held back for now: protractor, handwriting search. (Typed text was added on request: `TextItem`, `ui/notebook/TextEditor.kt`.)
- After a chunk of work: build, test it in the emulator, review the diff yourself, then commit with a short plain-English message describing what changed for her (see `git log`).
- Don't leave the emulator running when you finish, and stop any background tasks you started.

## Look and feel

- Lilac and light-blue "sky" theme, with soft 3D cartoon clouds.
  - Clouds sit at the top of the screens only.
  - Toolbars, dialogs and cards have subtle cloud bumps along their edges (`ui/components/CloudSurface.kt`).
  - Big clouds are painted into software bitmaps off the main thread (`ui/components/Clouds.kt`).
  - Earlier feedback: cloud circles must not look like visible circles, and toolbar bumps should be many and subtle.
- Light and dark mode both matter. Check screenshots in both.
- The UI is LTR even on a Hebrew-locale tablet: `supportsRtl="false"`, and dates are formatted with `Locale.UK`.
- Layouts must work in landscape and portrait. Narrow screens (< 900dp) get a two-line notebook top bar.

## Stack

- Kotlin and Jetpack Compose.
- AGP 9.4.1 and Gradle 9.8.0 (wrapper), JDK 17.
- compileSdk 37, targetSdk 36, minSdk 29.
- Package `com.tali.notes`.
- Ink: `androidx.ink` (low-latency strokes).
- Canvas: one custom `View` (`canvas/NoteCanvasView.kt`) with RenderNode tiles (`canvas/TileCache.kt`, 768-unit tiles).
  - The renderer drops the display list of a tile that leaves the screen, so tiles re-record when `hasDisplayList()` is false. Without that, writing vanishes after scrolling away and back.
  - Paged notebooks (`notebooks.pages` not NULL) put A4 pages in one column from the origin (`canvas/Pages.kt`). Older notebooks keep NULL, which means an endless canvas.
  - Dark paper (`notebooks.dark`) is display-only. Strokes are stored in their real colour and drawn through `Brushes.onDarkPaper`.
  - World units are dp.
  - A4 is 1322 × 1871 world units.
- Storage: plain SQLite (`data/NotesDb.kt`, schema VERSION 5: pages, dark paper, `folders.parent_id`, `notebooks.landscape`).
  - Merge-restore accepts backups from older schemas (missing columns get defaults).
  - All writes go through a single writer thread in `NotesRepository`.
  - Deleting a notebook is a soft delete into *Recently deleted* (kept 30 days).
- Strokes decode lazily (`InkItem.deferred`) and warm up in the background, nearest to the viewport first. A 5000-stroke notebook opens in about 50ms; keep it that way.
- Pictures: `data/ImageStore.kt` (memory-capped LRU, loaded on worker threads).
- Settings: `data/SettingsStore.kt` (`AppSettings` serialized with kotlinx.serialization).
  - Custom "hold shapes" are stored here as `customShapes`.
  - They are matched by the $P recogniser in `geometry/ShapeMatcher.kt`.

## Building

On Windows, use Android Studio's bundled JDK:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"   # adjust per machine
./gradlew --no-daemon -q assembleDebug
./gradlew --no-daemon -q assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew --no-daemon -q testDebugUnitTest
./gradlew --no-daemon -q connectedDebugAndroidTest   # needs a device/emulator
```

- `local.properties` (the SDK path) is not in git. Android Studio creates it, or write `sdk.dir=...` yourself.
- The Android SDK needs platform 37 and build-tools installed.

## Release signing: important

- The release key lives in `keystore/` (`talis-release.jks` plus `keystore.properties`). It is **gitignored and must never be committed**.
- Tali's installed app is signed with this key, so updates only install over it if they are signed with the same key. Lose the key and she has to uninstall, which wipes her notes unless she restores a backup.
- On a new computer, copy the `keystore/` folder over by hand (USB or a password manager, not git).
- Without the key, `assembleRelease` falls back to the debug key. That APK **will not** update her installed app, so don't hand it to her as an update.
- Release SHA-1: `B9:89:A0:5A:05:74:6D:3D:AD:20:3C:F2:A0:74:51:BB:08:13:CE:08` (used by Drive OAuth).

## Testing in the emulator

- AVD `medium_tablet` (1334×800 @160dpi, landscape), run headless.
- Helpers are in `tools/emulator/`:

```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd medium_tablet -no-window -no-audio -no-boot-anim -no-snapshot-save &
sh tools/emulator/boot.sh                                  # wait for boot, install debug APK, launch
sh tools/emulator/boot.sh app/build/outputs/apk/release/app-release.apk
python tools/emulator/drive.py tap 600 400 wait 1 shot library   # see the docstring for all commands
python tools/emulator/drive.py hold 300,300 500,300        # stylus line then hold → snaps to a shape
python tools/emulator/drive.py log                         # crashes / errors since last call
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" emu kill   # when done
```

- Screenshots go to `tools/emulator/shots/`, which is gitignored.
- Gotchas:
  - `connectedDebugAndroidTest` installs the debug-signed build. If a release build is installed, the signature mismatch makes Gradle **uninstall it and its data**. On the emulator that's fine. Never run it against Tali's tablet.
  - `StressNotebookTest` runs only with `-Pandroid.testInstrumentationRunnerArguments.stress=true`. It writes a big-notebook backup zip to Downloads for performance testing.
  - Never reinstall or force-stop the app on a real device someone is using.

## Backups

Backups come in three layers:
1. **Backup file.** A `.zip` (`backup/BackupArchive.kt`) that holds the DB snapshot, images, settings and a manifest. Restoring it **merges** into the existing notes and never wipes them.
2. **Android's backup** (`res/xml/` rules).
3. **Google Drive auto-backup** (`backup/DriveBackup.kt`). This is fully written but switched off with `buildConfigField DRIVE_BACKUP false`. It needs the owner's own Google Cloud OAuth client (Android type, package `com.tali.notes`, the release SHA-1 above). Steps are in BACKUP_SETUP.md.

## Open items

- Copy `keystore/` somewhere safe (it is not in git).
- Google Drive setup, then set `DRIVE_BACKUP=true`.
- Try two-finger undo and three-finger redo on the real tablet. They are tested only in instrumented tests and the emulator.
- The app icon comes from `tools/icon-source.png` via `python tools/make_icon.py` (Pillow, numpy, scipy). It writes `mipmap-*/ic_launcher_art.webp` (the whole picture, used as the adaptive icon's background) and `ic_launcher_mono.webp` (themed icons). The foreground layer is empty. The owner should check it on his launcher.
- The owner reviews visual changes on his own Galaxy Tab S9 FE (debug builds over USB). He asked to review: the frosted eye "show toolbars" button, the back-cloud plus title-with-pencil layout, and how scrolling feels after the glide fix.

## Recent work (October 2026)

In order, see `git log`:
1. A4 pages by default (`canvas/Pages.kt`), page overview popup (`ui/notebook/PagesOverview.kt`), Samsung-style "Drag for a new page / Release to add a page" at the end of the last page.
2. Dark paper, her own paper backgrounds (`model/CustomPaper.kt`, `ui/components/Papers.kt`), Cornell and Ruled papers, grey-on-white paper colours.
3. Folders inside folders, with a colour per folder.
4. PDF export qualities (Best, Balanced, Light, One long page) with the real file size shown.
5. Cloud-puff animations (`ui/components/CloudPuff.kt`), hide-toolbars mode, gliding scroll.
6. Typed text (`TextItem`, `ui/notebook/TextEditor.kt`).
7. Gliding selection (`ui/components/SlidingSelection.kt`). One highlight springs from option to option and stretches as it travels. It is used in every one-of-many choice: segmented controls, the eraser/lasso/shapes tools, pen types, colour rings, paper tiles, the library sidebar and cover swatches. Options with a gliding highlight have no ripple. Shape tiles (cards of their own) and on/off chips fade their colours instead.
8. Cloud puff (`ui/components/CloudPuff.kt`). An element leaving turns into a puff of mist that dissipates: soft Gaussian wisps on a grid over its whole area (any shape: row, card, tall page) rise, swell and thin out on their own clocks. There is no offscreen layer and no blur on the mist, so it's cheap. Progress is read in the draw phase (`CloudPuff(progress = { ... })`). Dark mode uses one pale tone at low opacity. The element itself fades and blurs out in 200ms; the mist lasts `PUFF_LEAVE_MS` (520), which callers wait for. Arrivals use a lighter puff. Toolbars: no puff on open; bars slide in from their top edge with a 40ms stagger; hide/show plays a puff at bar bounds recorded in a plain map (state writes mid-slide made it choppy). A leaving `PuffBox` lifts its zIndex so neighbours don't clip the mist. A `PuffBox` fades and blurs through `layerWithRoom` (an offscreen layer with room around it), so card shadows don't get clipped mid-fade. Toolbar bars fade with `CompositingStrategy.ModulateAlpha` via the visibility's own transition, because an offscreen layer clipped the cloud bumps until fully shown. Library sky clouds burst into the same mist when tapped (`Cloud(burstable = true)`) and re-form after 4 s. `CloudPuffTest` saves frames to `app/build/puff-frames/`.
9. Her own paper has an opacity (`CustomPaper.opacity`, 15-100%) for the lines/dots/margin, or for the picture. Applied in `PaperRenderer`, the swatch and export.
10. Page orientation, chosen in the new-notebook dialog (Portrait / Landscape, shown for A4 pages; remembered as `defaultLandscape`). It is stored per notebook in `notebooks.landscape` (schema VERSION 5, older notebooks are portrait; merge-restore and duplicate carry it). `canvas/Pages.kt` is now a layout (`Pages.PORTRAIT` / `Pages.LANDSCAPE`, `Pages.of(landscape)`) that the canvas (`pageLayout`), view model, export and page overview take from the notebook. An endless canvas keeps the portrait A4 grid. (An earlier app-wide screen-rotation lock was removed: the owner wanted the page's orientation, not the screen's.)
11. Arrange: the selection menu's layers button offers Bring to front / Send to back (`NoteCanvasView.arrangeSelection`, `CanvasItem.withZ`). Draw order (`ItemRenderer.drawAll`): pictures with `front=false` (the default and every older picture) behind everything, then highlighter, then writing and `front` pictures by z.
12. Lasso picks up whatever it touches (`geometry/Lasso.kt`). Exceptions: imported PDF pages still need to be mostly inside, and a loop drawn wholly inside a picture leaves the picture.
13. Part circle shape (`SectorShape`, `ShapeKind.PART_CIRCLE`). It is a single tile in the shapes panel (no % choices there, at the owner's request). Drag from the centre outwards; it starts as a half and shows its dots straight away. End dots change the amount (snapping to quarters and 5% steps, with a live "% · °" bubble); the middle dot resizes and turns it.
14. Shape dots resize instead of distorting: `PolyShape.withHandle` stretches from the opposite side in the shape's own frame (`PolyShape.rotation`, recorded when it is turned with the lasso or recognised tilted). It scales evenly for squares, regular polygons and stars, and clamps so it can't invert. Circles stay circles. The canvas keeps the grab offset and picks the nearest dot. After hold-to-shape snaps, moving on without lifting resizes the shape (lines still follow the pen tip): `liveBase` and `liveAnchor` in `NoteCanvasView`, with a touch-slop dead zone.

Working setup notes:
- On a fresh Windows PC the system JDK 17 works without Android Studio. Gradle downloads SDK platform 37 by itself once `local.properties` points at the SDK.
- The `medium_tablet` AVD may need creating. Copy another AVD's `config.ini` with 1334×800 @160dpi, landscape.
- Python edits on Windows: write files in text mode so they keep CRLF.
- Compose UI tests also run on the JVM with Robolectric (`app/src/test/.../ui/components/SlidingSelectionTest.kt`, which saves animation frames to `app/build/sliding-frames/`). Use `application = android.app.Application::class`, because the real app loads Ink's native library. After changing state from the test thread under a paused clock, call `Snapshot.sendApplyNotifications()`. If Robolectric can't download `android-all-instrumented`, put the jar in `~/.m2/repository` by hand.
- Cloud sessions (Linux, no KVM, so no emulator): install the SDK command-line tools from dl.google.com into `~/android-sdk`, then `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"`, write `local.properties`, and build. Maven Central sometimes answers 429; retry with a pause. Built APKs there are signed with a throwaway debug key, so they won't install over a debug build made on the owner's PC.
