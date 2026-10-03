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
- Preferences he stated (October 2026); follow them:
  - Animations: polished, fast and smooth, never choppy. He likes the mist that dissipates, and wants every "goes away" animation to match it (page delete is the reference).
  - Shapes must update live while dragged or resized. Snapped arrows and lines locking to angles is fine.
  - The shapes panel stays simple: one tile per shape (no % chips for Part circle). Amounts are set on the drawn shape.
  - Fills keep exactly the chosen colour, on dark paper too.
  - Page orientation is chosen when a notebook is made, not as a screen-rotation lock.
  - When he mentions "Emil's design skill", he means Emil Kowalski's `emil-design-eng` skill. It isn't installed in cloud sessions; a public copy is at github.com/vercel-labs/open-agents (`.agents/skills/emil-design-eng/SKILL.md`). Key rules: ask how often an animation is seen, keep UI motion under ~300 ms, exits faster than entries, strong ease-out, nothing from scale 0, origin-aware, blur to mask crossfades.
- In a local session, build and install the debug APK on his connected tablet yourself (`./gradlew installDebug`, or `adb install -r`) and launch it, so he can try changes straight away. Only cloud sessions (no USB, no emulator) have to ask him to pull and run it himself. That applies to his own tablet only; never Tali's.
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
- Updates only install over an app signed with the same key. (Tali's copy is currently on a cloud debug key; see the hand-off note under Open items.) Lose the key and she has to uninstall, which wipes her notes unless she restores a backup.
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
- Everything from the 2 Oct 2026 session (items 7-21 below, including the Ink line-style brushes) was tested by the owner on his tablet and works. Items 22-28 (3 Oct) are not yet seen on a device: especially the first-stroke fix, the square highlighter brush, the laser and lock.
- Session hand-off (2 Oct 2026): everything is merged: `master` and the working branch `claude/vigilant-fermat-8fu4pj` are identical.
- **Tali's signing changed (2 Oct 2026):** the owner chose to have Tali uninstall (losing her notes, which he said was fine) and install a release APK built in a cloud session, signed with that session's throwaway debug key. So her installed app is **no longer** signed with the `keystore/` release key, and no future build (cloud or PC) will install over it: each one again needs an uninstall, which wipes her notes unless she backs up first and restores (restore merges). To get her back onto one stable key, the next update should be built on the owner's PC with `keystore/` (`assembleRelease`), after a backup and one last uninstall; from then on updates install over it. Remind the owner of this before handing her any APK.

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
15. Smooth editing: only ink and the eraser call `requestUnbufferedDispatch`. Unbuffered input for handle drags rebuilt the shape's Ink strokes many times per frame and starved drawing, so shapes only updated on lift. A finger that lands on a shape's dots or on a selection edits it (`grabsEdit`) instead of panning.
16. Fill tool (`Tool.FILL`, `FillPanel`, `AppSettings.fillColor`/`fillOpacity`, where a null colour means "take fills away"). Tapping inside a closed `ShapeItem` sets its vector `fill` (stored as an extra "fill" key in the shape's JSON payload, drawn under the outline). Otherwise `canvas/AreaFill.kt` floods the area the writing encloses: the ink in a window around the tap is drawn into a mask on a background thread, ~1 mm gaps are bridged, and it gives up if the area reaches the window or page edge. The result is saved as a `fill-AARRGGBB-*.png` picture behind the writing. Fills (shape and area) keep exactly the chosen colour on dark paper; the owner wants them to stay as chosen. New items whose strokes aren't built yet are built when they're added (`buildNew`), because tiles skip unbuilt strokes and the warm-up only runs on open. `ShapeItem.withZ` and `filled` share the already-built strokes. Fill pictures under the tap are replaced or removed.
17. Arc and part-circle ends wrap (`positiveTurn` in `model/Shapes.kt`): 361° is 1°, so an end can be dragged round either way. The arc's start dot used to wind past a full turn and stay drawn as a whole circle. A whole part circle only snaps shut within 1.8°. `drawMeasure` in `NoteCanvasView` shows one badge for lines (beside the middle, or past the fixed end when short), arcs (degrees at the centre) and part circles (% · ° at the centre), while drawing, dragging or with dots showing.
18. Tapping a shape picks it up: a quick pen tap (< `PEN_TAP_MS`, no movement) on a shape's outline selects it instead of leaving a dot (`shapeTappedByPen`), and so does a tap with the Shapes tool. Finger taps already did.
19. Line styles (`LineStyle`: Solid, Dotted, Long dash, Short dash, Dot and dash, Mixed dash; `pattern` = visible lengths in multiples of the line's thickness). The owner asked for them in the pen settings instead of a separate Dashed pen.
    - Ink: `Brushes.lineFamily` builds an Ink particle brush per style (round pieces spaced along the stroke, turning with it; two-piece patterns are two coats, the second pushed forward with `POSITION_OFFSET_FORWARD`), so the pattern draws live. Confirmed working on the owner's tablet. If Ink ever rejects the family it falls back to the stock dashed line (and the style reads back as solid). `clientBrushFamilyId` "tali:line:STYLE" identifies it; a styled stroke is stored with pen column "PEN/STYLE".
    - Shapes: `ShapeItem.line` (extra "line" key in the shape JSON), cut geometrically by `StrokeFactory.dashes` (short paths such as arrow barbs stay solid).
    - `PenType.DASHED` is retired: kept so old strokes load and look the same; `AppSettings.migrated()` turns Dashed pens into fineliners with Short dash. `PenType.offered` is what the UI lists.
    - The selection menu's palette button is now *Style* (`SelectionStyle`): colour, thickness (applied on release, one undo step) and line style, via `NoteCanvasView.restyleSelection` (`InkItem.restyled`, `ShapeItem.restyled`).
    - `LineStylePicker` (`ui/notebook/LineStyles.kt`) is the 3×2 tile grid with the gliding highlight; `PenGlyph(line =)` shows the pattern down the pen. `LineStylePickerTest` saves frames to `app/build/line-styles/`.
20. Quick colours: `PenSlot.quick`/`recent`, `PenSlot.pick()`. Three dots after the pen-settings arrow while a pen is active (gliding ring). Palette taps and the picker's save go through `pickPenColor`; the picker's live drag doesn't, so dragging doesn't churn the dots.
21. Paper line weight: `AppSettings.paperLineWeight` (0.5..3), slider under the papers in the ⋯ paper menu. It's app-wide (one setting, not per notebook), applied through `PaperRenderer.lineWeight`, which `SettingsStore` keeps in step so the canvas, thumbnails and PDF export all match.
22. Fixes after Tali's second round (3 Oct 2026):
    - The eraser circle shows only while the eraser touches the page (`drawEraserCursor`). It used to follow the pen's hover after lifting, which looked like a mark left behind.
    - First stroke lag: `InProgressStrokesView.eagerInit()` on attach (Ink otherwise sets up its front-buffered surface on the first stroke), and `warmPens()` builds a tiny stroke with each pen in the background so the native code and brushes are ready.
    - Saved custom shapes looked choppy: ink was simplified into straight bits drawn with sharp joins. Now `PolyShape.smooth` draws a Catmull-Rom curve through the points (`Geo.smooth`, keeping corners sharper than 55°), simplification is finer, and `CustomShape.smoothInk` applies it to shapes saved before. Smooth polylines resize from their bounding-box corners instead of a dot per point.
23. A shape stays selected after it's drawn (shape tool or hold-to-shape) or after a dot is dragged. It's a "soft" selection (`softSelection`): a pen landing inside it writes (and deselects) instead of moving it, so writing in a just-drawn box works; a finger still moves it; dots, corner and rotate handles still work.
24. One triangle (`TriangleShape`; `RIGHT_TRIANGLE` was removed). Each corner moves on its own in whole degrees; `snapCorner` pulls (within 3°) to a right angle at any corner or two equal angles, and `lockAngle` locks each angle to the 15° family within 2.5° and to every 5° within 1° (the owner wanted free movement that locks on to popular angles, like the straight line). The recogniser and the item decoder turn three-point closed `PolyShape`s into `TriangleShape`s (`ShapeRecognizer.asTriangle`). `drawMeasure` shows each corner's angle while drawing or dragging.
25. Highlighter: `PenSlot.squareTip` (a copy of the stock highlighter with `cornerRounding = 0`, family id "tali:highlighter-square", stored as pen column "HIGHLIGHTER/SQUARE") and `PenSlot.freehand` (default off: `Mode.STRAIGHT` draws a straight line from where the pen landed, snapping level/upright within 4°, committed as a normal highlighter `InkItem`). Set with two `Segmented` switches in the pen panel.
26. Laser (`Tool.LASER`, `TaliIcons.Laser`, `Mode.LASER`, `drawLaser`): a red glow plus bright core. The stroke being drawn stays fully lit; when the pen lifts, the whole stroke fades and thins together over `LASER_FADE_MS` (the owner's call). Nothing is stored. No panel.
27. Lock (`CanvasItem.locked`, `withLocked`): stored in the saved data (ink pen column tag "LOCKED", a "locked" key in shape JSON, `locked` in the picture and text payloads), so no schema change. Locked items are skipped by `hitTest` (unless asked for), the lasso loop, the eraser and shape fill. Lock from the selection menu (`lockSelection`); unlock by holding a finger on it (`longPress`, `LONG_PRESS_MS`) or *Unlock all* in the ⋯ menu (`unlockAll`). All are undoable.
28. Page delete asks first (`PagesOverview`, `confirming`). Thickness sliders use `StepSlider` (`ui/components/StepSlider.kt`: − and + in 0.1 steps, hold to repeat faster, `onChangeFinished` once per change). Changing a pen's type keeps its thickness (clamped to the new pen's range) instead of resetting to the type's default.

Working setup notes:
- On a fresh Windows PC the system JDK 17 works without Android Studio. Gradle downloads SDK platform 37 by itself once `local.properties` points at the SDK.
- The `medium_tablet` AVD may need creating. Copy another AVD's `config.ini` with 1334×800 @160dpi, landscape.
- Python edits on Windows: write files in text mode so they keep CRLF.
- Compose UI tests also run on the JVM with Robolectric: `SlidingSelectionTest` and `CloudPuffTest` save animation frames (to `app/build/sliding-frames/`, `app/build/puff-frames/`; stitch them into strips or GIFs with Pillow to review motion and show the owner), `LandscapeUpgradeTest` runs the real SQLite upgrade. Pure-geometry tests cover `Lasso`, `SectorShape`, shape resizing and `AreaFill`. Anything that renders Ink strokes (the canvas, area fill) cannot run on the JVM. Use `application = android.app.Application::class`, because the real app loads Ink's native library. After changing state from the test thread under a paused clock, call `Snapshot.sendApplyNotifications()`. If Robolectric can't download `android-all-instrumented`, put the jar in `~/.m2/repository` by hand.
- Cloud sessions (Linux, no KVM, so no emulator): install the SDK command-line tools from dl.google.com into `~/android-sdk`, then `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"`, write `local.properties`, and build. Maven Central sometimes answers 429; retry with a pause. Built APKs there are signed with a throwaway debug key, so they won't install over a debug build made on the owner's PC.
