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
- Session hand-off (3 Oct 2026): everything through item 32 is merged; item 33 is on the working branch only, not yet merged to master: `master` and the working branch `claude/vigilant-fermat-8fu4pj` are identical. A cloud-built (debug-key) release APK of it was handed to the owner.
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
8. Cloud puff (`ui/components/CloudPuff.kt`). An element leaving turns into a puff of mist that dissipates: soft Gaussian wisps on a grid over its whole area (any shape: row, card, tall page) rise, swell and thin out on their own clocks. There is no offscreen layer and no blur on the mist, so it's cheap. Progress is read in the draw phase (`CloudPuff(progress = { ... })`). Dark mode uses one pale tone at low opacity. Two looks: `CloudPuff(vivid = false)` is the subtle original (white wisps, lilac hint, `SUBTLE_PUFF_MS` 520, element out in `ELEMENT_LEAVE_MS` 200): the toolbars' hide/show and the sky clouds, which the owner wants quiet. Deleting (`PuffBox` leaving) is `vivid`: faintly lilac with a deeper lilac belly, holding its density for the first ~18%, a touch smaller than the subtle one (less lift and swell, smaller wisps), lasting `PUFF_LEAVE_MS` (600, which callers wait for); the element lifts 10dp, shrinks 7%, fades and blurs out in 220ms. The owner tuned this: 700 ms and full size was a bit much. Deletion mist is drawn by a `PuffLayerHost` (the library screen's root and the pages dialog's root) above all their content, so it isn't cut off by the grid, the sidebar or the header; `PuffBox` hands its bounds to `LocalPuffLayer` and only draws its own mist when there is no host. The host draws puffs in their own top-left-aligned box: the pages dialog centres its content, which once threw the page's puff off-screen (`deletionMistStaysOnItsPageInACentredWindow`). Arrivals use a lighter puff. Toolbars: no puff on open; bars slide in from their top edge with a 40ms stagger; hide/show plays a puff at bar bounds recorded in a plain map (state writes mid-slide made it choppy). A leaving `PuffBox` lifts its zIndex so neighbours don't clip the mist. A `PuffBox` fades and blurs through `layerWithRoom` (an offscreen layer with room around it), so card shadows don't get clipped mid-fade. Toolbar bars fade with `CompositingStrategy.ModulateAlpha` via the visibility's own transition, because an offscreen layer clipped the cloud bumps until fully shown. Library sky clouds burst into the same mist when tapped (`Cloud(burstable = true)`) and re-form after 4 s. `CloudPuffTest` saves frames to `app/build/puff-frames/`.
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
21. Paper line weight: `AppSettings.paperLineWeight` (0.5..3), *Line thickness* choice under the papers in the ⋯ paper menu. It's app-wide (one setting, not per notebook), applied through `PaperRenderer.lineWeight`, which `SettingsStore` keeps in step so the canvas, thumbnails and PDF export all match.
22. Fixes after Tali's second round (3 Oct 2026):
    - The eraser circle shows only while the eraser touches the page (`drawEraserCursor`). It used to follow the pen's hover after lifting, which looked like a mark left behind.
    - First stroke lag: `InProgressStrokesView.eagerInit()` on attach (Ink otherwise sets up its front-buffered surface on the first stroke), and `warmPens()` builds a tiny stroke with each pen in the background so the native code and brushes are ready.
    - Saved custom shapes looked choppy: ink was simplified into straight bits drawn with sharp joins. Now `PolyShape.smooth` draws a Catmull-Rom curve through the points (`Geo.smooth`, keeping corners sharper than 55°), simplification is finer, and `CustomShape.smoothInk` applies it to shapes saved before. Smooth polylines resize from their bounding-box corners instead of a dot per point.
23. A shape stays selected after it's drawn (shape tool or hold-to-shape) or after a dot is dragged. It's a "soft" selection (`softSelection`): a pen landing inside it writes (and deselects) instead of moving it, so writing in a just-drawn box works; a finger still moves it; dots, corner and rotate handles still work.
24. One triangle (`TriangleShape`; `RIGHT_TRIANGLE` was removed). Each corner moves on its own in whole degrees; `snapCorner` pulls (within 3°) to a right angle at any corner or two equal angles, and `lockAngle` locks each angle to the 15° family within 2.5° and to every 5° within 1° (the owner wanted free movement that locks on to popular angles, like the straight line). The recogniser and the item decoder turn three-point closed `PolyShape`s into `TriangleShape`s (`ShapeRecognizer.asTriangle`). `drawMeasure` shows each corner's angle while drawing or dragging.
25. Highlighter: `PenSlot.squareTip` (a copy of the stock highlighter with `cornerRounding = 0`, family id "tali:highlighter-square", stored as pen column "HIGHLIGHTER/SQUARE") and `PenSlot.freehand` (default off: `Mode.STRAIGHT` draws a straight line from where the pen landed, snapping level/upright within 4°, committed as a normal highlighter `InkItem`). Set with two `Segmented` switches in the pen panel.
26. Laser (`Tool.LASER`, `TaliIcons.Laser`, `Mode.LASER`, `drawLaser`): a red glow plus bright core. Every stroke stays fully lit while she keeps using it; once the laser has been off the page for `LASER_IDLE_MS` (700 ms), everything drawn fades and thins together over `LASER_FADE_MS` (450 ms). The owner asked for this, so several strokes can be drawn before any fade. Nothing is stored. No panel.
27. Lock (`CanvasItem.locked`, `withLocked`): stored in the saved data (ink pen column tag "LOCKED", a "locked" key in shape JSON, `locked` in the picture and text payloads), so no schema change. Locked items are skipped by `hitTest` (unless asked for), the lasso loop, the eraser and shape fill. Lock from the selection menu (`lockSelection`); unlock by holding a finger on it (`longPress`, `LONG_PRESS_MS`) or *Unlock all* in the ⋯ menu (`unlockAll`). All are undoable.
28. Page delete asks first (`PagesOverview`, `confirming`). Thickness sliders use `StepSlider` (`ui/components/StepSlider.kt`: − and + in 0.1 steps, hold to repeat faster, `onChangeFinished` once per change). Changing a pen's type keeps its thickness (clamped to the new pen's range) instead of resetting to the type's default.

29. Top bar: back, the notebook's name, then undo/redo beside it (`TitlePill`); the eye then ⋯ in the top-right corner (⋯ always last). Undo/redo used to sit next to the eye and Tali kept hiding the toolbars by accident. *Hide navigation bar* in the ⋯ menu (`AppSettings.hideNavBar`, app-wide) hides the system navigation bar in every notebook (swipe up shows it for a moment), re-applied on resume, and shown again in the library.

30. Toolbar colours: `AppSettings.toolbarColors` (0-6, app-wide, a `Segmented` under the colours in the pen panel) is how many of her saved colours sit beside the pens; the toolbar shows the first N of `savedColors` in her order (this replaced the per-pen recent colours; `PenSlot.quick`/`recent` are now unused). `ReorderableColors` in `Panels.kt`: hold a colour and drag to move it (live reorder, keyed by colour so the drag survives the move; saved on release), hold without moving for *Remove colour*; a thin line after the last toolbar colour sets them apart (a dot under each collided with the dot and its ring). The toolbar's colour strip eases to its new width (`animateContentSize`, 220 ms). The wide top bar is a small custom `Layout`: the corner (eye, ⋯) and tools are measured first, the title side gets what's left (the notebook name shortens with …), and the tools stay centred unless that would overlap, then shift over.
31. Confirmation dialogs (`ConfirmDialog`, and the Recently deleted choice) add no tint of their own: `DialogCard(dim = false)` clears the window's `FLAG_DIM_BEHIND`. (Setting a dim amount of 0 instead also removed the tint of the pages overview underneath, because the system keeps a single dim layer behind the top window that asks for one; the owner wants the overview's tint kept.)

32. The add-page sign below the last page shows only while fingers are pulling (`pulling`), measured from where the pull began (`effectivePull`, `pullOffset`): at rest there's always `REST_DP` of room there, and zoomed out far more, which used to leave the sign on screen (and could add a page on any lift when zoomed out). The paper line weight is a `Segmented` (Thin, Standard, Bold, Bolder = 0.6, 1, 1.6, 2.4) labelled *Line thickness*: the bare slider read as a stray white line under the papers. Paper swatches stop their pattern half a step short of the edge (float drift drew a line on the Grid swatch's edge).

33. Tali's third round (3 Oct 2026):
    - Pens: every type goes down to 0.5 (`PenType` min sizes), so changing type keeps her thickness; changing type no longer turns the colour yellow. Thickness steps are 0.5. Three thicknesses per pen (`PenSlot.sizes`, `sizeSlot`, `presetSizes`, `withSize`/`pickSize`/`setPreset`): dots after the toolbar colours (`QuickSizes` in `Toolbar.kt`; tap the one in use to set it), and a three-way choice above the pen panel's size slider, which sets the chosen one.
    - The highlighter has its own palette (`AppSettings.highlighterColors`, `paletteFor`); palette edits go to whichever the active pen uses.
    - Highlighter tips are built in `Brushes.highlighterFamily` (circle vs upright square, self-overlap discarded). The stock highlighter is a slanted chisel, so squaring its corners looked the same as before.
    - Paper line thickness adds Bold 2, Bolder 3.5, Heavy 5 (widths are screen pixels, so 2.4 was still thin on her tablet).
    - *Full screen* in the ⋯ menu (was Hide navigation bar, same `hideNavBar` setting) hides the status bar too.
    - Laser: putting the laser down again relights everything still fading; idle wait 1 s.
    - Lasso panel: *Pick up locked items* (`lassoLocked`); the selection menu's lock button unlocks when everything picked up is locked.
    - Finger taps no longer pick things up (only with the lasso tool); a pen tap on a shape's outline still does.
    - Lines snap only to every 45° (within 6°), when drawn, when an end is dragged, and when the whole line is turned (by its own direction). The end-drag direction lock is down to 2°.
    - Rectangles and squares get a dot in the middle of each side (`PolyShape.isRect`, `withSide`) to stretch one way, snapping to a square within 4%; a rectangle's corner snaps to square too.
    - New notebooks and folders are suggested as "New notebook" / "New folder"; Done on the keyboard in the new-notebook card only hides the keyboard.
    - Paper swatches are clipped to their rounded corners (white leaked past them in dark mode: the "little white line").
    - Highlighter tips after the owner tried them: Round is the built circle tip (he likes it); Square went back to the stock slanted chisel with sharp corners ("at an angle like before").
    - A thin line separates the toolbar's quick colours from its three thicknesses. The notebook's toolbars sit 8 dp lower (top padding 20 dp; the show-toolbars eye 26 dp).
    - The wide top bar centres the title side and the corner (eye, ⋯) on the main toolbar's middle line (`MAIN_TOOLBAR_HEIGHT`, 60 dp), measured from the bar itself because the tools part grows down when a panel opens. On narrow screens the title and undo line is centred too.
    - Unlocking by holding (`longPress`): 1.5 s (`LONG_PRESS_MS`), messages just say "hold". Guarded against palms and stray touches: only a single fingertip-sized touch (`fingertip`, contact under `PALM_MM` 14 mm) that started on the locked item, with no S Pen touch or hover in the last `PEN_NEARBY_MS` (1.5 s), that doesn't move (half the touch slop), grow, or get a second finger; the system's palm-rejection cancel also stops it.
    - Padlock cue (`LockGlyph`, `drawLockCues`, `LockCue`; frames from `LockGlyphShotTest` in `app/build/lock-frames/`): a small padlock in a frosted chip. Locking or unlocking from the selection menu plays it over the selection's middle: in from 90% with a strong ease-out, the shackle clicks shut (or swings open) from 120 ms, then it rises 4 dp and fades from 560 ms, gone at 720 ms. While a finger holds a locked item, the padlock shows 64 dp above the fingertip after 500 ms and its shackle opens gradually with the hold (to `HOLD_OPEN_MAX` 0.8); at 1.5 s it pops open and leaves; let go early and it fades in 120 ms. Retuned after the owner tried it ("I want to see it happening"): 1.3× larger, the shackle snaps with a small overshoot (260-560 ms) and the chip pulses as it clicks, leaving from 1050, gone at 1300. (It replaced a progress ring, at the owner's request, using Emil's design rules.)
    - Area fills and their outlines act as one object (`withFills`, `selectGroup`): the lasso (loop or tap), lock and hold-to-unlock take the fill picture together with every stroke or shape reaching its edge (writing inside the filled area isn't included), and the outline's other strokes with it. Only the eraser treats them apart.
    - The gliding highlight (`SlidingSelection`'s `Glide.from`/`to`) keeps its brush in state: as plain fields, switching the theme or dark paper left the highlight drawn in the old theme's colour (white or black) until something moved it. (`ThemeFlipTest` can't reproduce it under Robolectric, which redraws everything.)
    - Held upright (narrow top bar), the title line and the tools line are 18 dp apart (was 10). The empty library's example page is just the engineering paper (the support and load drawings were removed).
    - Starred pens (`PenSlot.starred`, the star beside the pen's name in its panel, `StarToggle`): a double press of the S Pen button goes to the next starred pen, a triple press back (`NotebookViewModel.cyclePen`, round and round; from another tool it starts at the first or last). The canvas counts quick presses (`penButton`: ≤ `PEN_PRESS_MAX_MS` 350 ms each, `PEN_PRESS_GAP_MS` 320 ms to wait for another) from `ACTION_BUTTON_PRESS/RELEASE` and the hover's button state (`PEN_BUTTONS` = stylus primary or secondary). Android only reports the button while the pen hovers or touches, so it works with the pen near the screen; holding the button still erases. Hover and button events come from `MainActivity.dispatchGenericMotionEvent` through `canvas/PenEvents.kt` (`NoteCanvasView.penHover`): Compose doesn't pass hover events down to the canvas, so the first version never saw a press (and the palm guard's "pen nearby" never saw hovering). The star is offered only on tablets with a pen (`NotebookViewModel.hasStylus`); starred pens wear a small gold star in the toolbar. `AppSettings.migrated()` gives duplicate pen ids their own. Starring shows a hint explaining it; with toolbars hidden, cycling names the pen. Nothing is starred by default, and with nothing starred the presses do nothing. On Samsung, a single press while hovering may also open Air command if that's on in the tablet's S Pen settings.
    - Pages overview: hold a page (finger or pen, `detectDragGesturesAfterLongPress`) and drag it; the others make way live (`order`), the grid scrolls near its edges, and on release `NoteCanvasView.movePage` moves everything on it there and shifts the pages between (`Pages.movedIndex`, one undo step). Thumbnails follow their pages (`thumbs`) and placement animation is off for the settle (`settling`), so nothing flashes or slides back after a drop.
    - Ink stays on A4 pages (`clipToPages`, `inkAreas`, pure cutting in `geometry/PageClip.kt` with `PageClipTest`): finished strokes (and the straight highlighter) are cut where they leave, inset by half the pen's width so the round end stops at the edge; pieces keep their pressure, tilt and timing (`sampleAt`). Never past the left or right side. *Write between pages* in the ⋯ menu (`AppSettings.inkBetweenPages`, off by default) lets a stroke carry on across the gap (and start in it); off, a stroke stays on the page it started on (`strokeStartPages`, `pageAt`): anything past that page's edge, including onto the next page, isn't kept (the owner's meaning, corrected after the first version only cut the gap). While writing, `InProgressStrokesView.maskPath` hides ink outside the same area so the live stroke matches what's kept. Layout comes from `pageLayout`, so portrait and landscape both work; endless canvases are untouched. Older ink already off the page is left alone.
    - Pictures stay on pages (`pageNudge`): a selection with pictures, moved or resized off the page, is nudged on release (same undo step) so its pictures sit in the nearest place on the page they overlap most; writing picked up with them moves the same way. New pictures land on a page too.
    - Carrying a selection to an edge scrolls that way (`edgeStep`): 100 dp zones (the top one reaches 20 dp below the toolbars), after resting 120 ms there (the owner found 56 dp / 250 ms started too late), speed easing up with depth to 900 dp/s; the item stays under the finger or pen.
    - The toolbar's tools (eraser to keyboard) page only when they don't fit (`ToolStrip` in `Toolbar.kt`, measured with `BoxWithConstraints` from the room the bar's row leaves): a chevron at the end slides to the next page (and turns round on the last); a tool chosen elsewhere brings its page into view. Pens, colours and thicknesses never page. `ToolbarFitShotTest` renders it upright and wide (`app/build/toolbar-fit/`).
    - Shape dots only change a shape's form; resizing is the resize chip's (`drawResizeChip`, `resizeChip`): a white round chip with a diagonal two-way arrow 14 dp out from the selection's bottom-right corner, for a just-drawn or tapped shape and for lasso selections alike, checked before the shape's own dots (it replaced the plain corner dot). So: every polygon except a rectangle moves the dragged corner on its own (`PolyShape.withHandle`); a rectangle or square has only its side dots (stretch one way, square snap; its corner dots went); circles, saved hand-drawn shapes (`smooth`) and `GroupShape` have no dots; ellipses, lines, arcs, part circles and triangles keep theirs. (This replaced the corner resizing of item 14.)
    - `DialogCard` content is a `ColumnScope`: a long card's scrolling part takes `weight(1f, fill = false)` so the buttons under it always fit (the new-notebook card's Cancel/Create were squashed once the card could be as tall as the screen; the folder-move list too). The cover colours flow across the full width (no 9-per-row cap), and `PaperPicker` shows as many tiles across as fit (`fit`, up to `perRow`); folded, that's Blank, Lines, Grid, then the other papers and hers, beside More.
    - `QuietButton` is a soft lilac pill (`accentSoft`, accent text; a warning colour gets a faint wash of itself), no outline, pressing in to 96%: the thin grey border all but vanished on the sky and on cards. The library's pick bar keeps its three action icons in one frosted pill. `ButtonsShotTest` renders them (`app/build/buttons/`).
    - Dialogs keep full screen (`ui/components/FullScreen.kt`: `LocalFullScreen`, provided by the notebook screen, and `KeepFullScreen()` in `DialogCard` and the pages overview): a dialog is its own window, and Android showed the system bars for it.
    - No hover highlight anywhere (`LocalRippleConfiguration` in `theme/Theme.kt`, hovered alpha 0): with the S Pen, a menu row could stay lit after pressing it and moving the pen away. Presses still show.
    - Panels (`PanelColumn`) show a soft fade at the bottom while there's more to scroll to, and the first two panels opened after the app starts peek down 36 dp and settle back (`panelPeeks`).
    - Picking several pages in the pages overview: *Select* (with 2+ pages) beside Done; while picking, taps tick pages (check circle, accent outline), dragging and peeking are off, and the header becomes "N selected" with Select all/none, Duplicate (copies right after each page) and Delete (asks once; all pages = clear, one empty page stays; they puff away together). Both go through `NoteCanvasView.rearrangePages(order)`: the new order of old page numbers, a page left out is deleted, a page listed again is copied with new ids (`Pages.withoutPages`, `Pages.withCopies`), one undo step.
    - Picking several notebooks in the library (`picked` in `LibraryScreen`, `PickBar`): *Select* beside the search box, or *Select* first in a notebook's hold menu. While picking, taps (and holds) tick notebooks, each card shows a check circle and a picked one an accent outline, slightly pressed in; the header becomes "N selected" with Select all/none, Move to folder, Duplicate, Delete (one confirmation, then each puffs away) and Done. Back, Done, changing folder or unticking the last one ends it. Not offered in Recently deleted.
    - The fade between the library and a notebook is 420 ms (`SCREEN_FADE_MS` in `Navigation.kt`; Navigation 3's default was 700).
34. Smooth size changes (3 Oct 2026): `cloudSurface` used to paint each size into a full-size bitmap with a software blur, so anything on it changing size (the new-notebook card folding away Orientation, the toolbar easing wider, panels) rebuilt a multi-megabyte blurred bitmap every frame and stuttered. It now draws the outline straight to the canvas with a GPU shadow layer (`Paint.setShadowLayer`), and the puffs join the outline in one `Path.op` union (about 3× faster than one per puff). `DialogCard`'s window fills the screen and centres the card itself (tap outside closes; `safeDrawing` keeps it above the keyboard), so an animating card isn't a system window resizing every frame. Keep both in mind for any new animation of a card's or bar's size. `CloudSurfaceShotTest` renders a bar and a card (Robolectric draws no shadow layer; check shadows on a device).

Working setup notes:
- On a fresh Windows PC the system JDK 17 works without Android Studio. Gradle downloads SDK platform 37 by itself once `local.properties` points at the SDK.
- The `medium_tablet` AVD may need creating. Copy another AVD's `config.ini` with 1334×800 @160dpi, landscape.
- Python edits on Windows: write files in text mode so they keep CRLF.
- Compose UI tests also run on the JVM with Robolectric: `SlidingSelectionTest` and `CloudPuffTest` save animation frames (to `app/build/sliding-frames/`, `app/build/puff-frames/`; stitch them into strips or GIFs with Pillow to review motion and show the owner), `LandscapeUpgradeTest` runs the real SQLite upgrade. Pure-geometry tests cover `Lasso`, `SectorShape`, shape resizing and `AreaFill`. Anything that renders Ink strokes (the canvas, area fill) cannot run on the JVM. Use `application = android.app.Application::class`, because the real app loads Ink's native library. After changing state from the test thread under a paused clock, call `Snapshot.sendApplyNotifications()`. If Robolectric can't download `android-all-instrumented`, put the jar in `~/.m2/repository` by hand.
- Cloud sessions (Linux, no KVM, so no emulator): install the SDK command-line tools from dl.google.com into `~/android-sdk`, then `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"`, write `local.properties`, and build. Maven Central sometimes answers 429; retry with a pause. Built APKs there are signed with a throwaway debug key, so they won't install over a debug build made on the owner's PC.
