# Progress

## How to pick this up

1. Read `BRIEF.md`, `PLAN.md`, this file, `OWNER_TODO.md`.
2. **Since 25 Nov 2026 this app lives in `ios/` of `arielmz1234/Talis-Notes`** (the Android repo,
   branch `master`), with all its history; `Talis-Notes-iOS` is archived. See `ios/CLAUDE.md` and
   the top of `../CLAUDE.md`. Look at the last run of the *iOS* workflow and its screenshots
   (`gh run list -w iOS -L 3`, `gh run download <id> -D <scratch dir>`, where the GitHub CLI is
   installed; otherwise the public API and links, see `ios/CLAUDE.md`). Landscape iPad screenshots
   come out sideways (rotate them when reviewing).
3. There is no Swift on the Windows PC; every compile happens in CI. The quick run takes about 15
   minutes; the full UI suite runs only when asked (see `ios/CLAUDE.md`). Batch changes, and read
   compile errors with `gh run view <id> --log-failed | grep error:`.
4. **Rule: never change what a basic tap, hold or touch does without the owner asking.** (A Pencil
   tap that picked shapes up was added unasked and had to be taken out; see 24 Nov below.)

## The owner's list of 28 Nov 2026 (Windows, no Swift: compiled and checked in CI only)

Nothing here was run on an iPad: the owner tries the `.ipa`. The core tests (new ones included) and
the quick CI's iPad screenshot pass are what checked it.

- **Bring to front / Send to back redrew the whole page**: setting PencilKit's drawing (the covered
  writing moving between PencilKit and the page) makes it draw all its writing again, and the page's
  tiles take a moment too. `holdTheLook` (Selection.swift) keeps a picture of the canvas as it was
  over it while the change is drawn (until PencilKit says it has rendered and the tiles have had
  0.16 s), then fades over to it in 0.1 s, so a press is one clean switch.
- **Writing sent back didn't go under a picture sent back earlier** (pictures behind the writing are
  drawn under everything, so a line over one could never go under it, and a second press said
  "already at the back"). `Arrange.plan`: sent to the back, writing, shapes and text go under the
  pictures they lie on too: those pictures come forward into the stack (`front`), just above what
  was sent back and below everything else, so nothing else changes. Area fills and an imported PDF's
  pages are paper and never count (a PDF page at z 0 used to make every Send to back "work").
  `ArrangeTests` has the owner's case. (Android has the same rule as the iPad had; not changed there.)
- **The name bar filled its room with blank space**: `.frame(maxWidth: 200)` takes all the room
  it's offered. `UpTo(200)` (NotebookView.swift) is as wide as the name, at most 200, shortened only
  when the row is short, so with room to spare the gaps between the bars grow, as on Android.
- **A fast stroke showed a tail past its end for a moment after lifting**: the live stroke was drawn
  through the system's predicted touches as they came. `PredictionGuard` (TaliCore, Android's, with
  its tests ported) only draws a guess that carries on the pen's way (within 50�), at most 1.2 times
  the distance of the last five samples and 2.5 mm on screen, none when the pen is nearly still, and
  none while the pen is lifting (its pressure under 75% of its firmest of the last eight samples
  and still falling; iOS only, Android's Ink ends the stroke on the lift event itself).
- **The stroke changed as the pen lifted**: the predicted samples were smoothed and modelled
  (`InkModel`'s 20 ms window) together with the real ones, so the stroke's last few millimetres were
  bent toward the guess, and moved back once it was kept without it. Now the live stroke is the
  stroke exactly as it would be kept if the pen lifted now, with the tamed guess drawn on from its
  end at the end's width (`showLive(tail:)`, `pkStroke(mask:drawn:)`). And the live picture is placed
  on whole device pixels of the page (it was on whole world units, between pixels at most zooms, so
  it was resampled a touch soft and seemed to sharpen when the canvas took over).

## Joined into the Android repo; shorter CI (25 Nov 2026, Windows)

The owner merged the two repos: this app is now `ios/` in `arielmz1234/Talis-Notes` (Android at
the root, branch `master`). Done by one commit moving every file into `ios/` and a merge with
unrelated histories, so every old commit and id is kept (`git log --follow` traces files back).
The Android files are unchanged. CI moved to `../.github/workflows/ios.yml` and only runs when
`ios/` changes. It is quick by default (core tests, a build of the app and the UI tests, the iPad
screenshot set, the `.ipa`); the full UI suite, unchanged, runs only on request (*Run workflow*
with *full*, or a `full-ci` label), since the owner tries the rest on a real iPad. The tools that
read the Android app (`import_strings`, `import_palettes`, `make_icon`, `check_shape_port`) default
to `../`. Whoever pushes from the Mac (`idanlo`) needs write access to `Talis-Notes` now.

## The owner's second round of 25 Nov 2026 (26 Nov, Mac without Xcode, with the Android emulator)

Written on the Mac with only the command line tools (Xcode isn't installed): the core tests pass
and the app type-checks for Mac Catalyst; the full UI suite runs in CI on this branch. The Android
app was built and run in an emulator (a Pixel Tablet image, resized with `adb shell wm size` to
each iPad's size in points) to see what Android does.

- **Stroke smoothness**: Android's handwriting goes through two stages, PenSmoother (the same One
  Euro filter) and then Ink's `SlidingWindowModel(20 ms, 360 Hz)` (`Brushes.WRITING_MODEL`): the
  path averaged over a sliding 20 ms window and filled in at 360 points a second. The iPad only had
  the first. `TaliCore/InkModel.swift` is the second, applied when a stroke is drawn
  (`InkStroke.drawnPoints`, used by `pkStroke`, the highlighter layer and the PDF), never to what's
  stored. Sparse samples (shapes, a sample every 37 ms) are left as they are, as Android keeps the
  default model for shapes. PenSmoother now also eases pressure (Android's 18 ms) and no longer
  pulls the stroke's last samples back onto the pen (Android doesn't; it brought the jitter back at
  the end). A lasso move now carries the stored samples (`InkStroke.transformed`) instead of
  reading PencilKit's points back, which are the drawn ones.
- **The highlighter still flickered** (video v4: gone mid-drag, darker after drawing). PencilKit
  never says exactly when it has drawn, so any hand-over between it and an overlay showed the
  see-through ink twice or not at all. The highlighter is now drawn by the notebook
  (`HighlightInk.swift`: a vector layer over the page, under PencilKit's writing), changed in the
  same frame as everything else; PencilKit keeps the strokes hidden (the buried-ink mask) so the
  lasso, eraser and undo are unchanged. Pictures of the page (thumbnails, overview, screenshot
  lasso, the fill's walls) use `visibleDrawing`. New test: `SelectingTests.testHighlighterKeepsItsStrengthWhenMoved`.
- **The name showed as "…"** on the one-row toolbar, and the two-line fallback tried next made
  the bars' height change. The owner's tablet (Tab S6 Lite) is 1333 dp across at ~149 dp an inch;
  an 11-inch iPad is 1180 points at 132 an inch: the same width (~22.6 cm), but a point is 13%
  bigger than a dp there, so the same bars took 13% more of the screen (the emulator showed Android
  giving "…" too at 1180 dp). Now on an iPad the notebook's bars and panels are drawn at 132/149 of
  their size (`NotebookScale`, 0.95 on an iPad mini), as big as on his tablet. Any iPad held
  sideways (and a 13-inch upright) has one line; an 11-inch upright uses Android's upright layout.
  Decided by the screen only, so the height never changes. The name keeps at least 170 units
  ("Microeconomics L…", Android's bar holds at most 200 of text): when the row is short the tools
  fold into pages with the arrow (Android's ToolStrip) instead (`RowWidths.stripRoom`).
  `ScreenshotTests.testNotebookNameOnTheToolbarRow` checks one line and a long name's start.
- **Endless zoom didn't slow down**: the scroll view turns its own pinch back on when its zoom limits
  are set, and that pinch zooms straight past the resistance; `NoteCanvasView` now refuses it on an
  endless canvas (`ownPinchAllowed`). What's written is measured as Android does (with the pen's width).
- **Auto finger drawing** with an older Apple Pencil: Android's Auto is "no stylus on this device".
  The iPad can't list a Pencil, so a Pencil touch is remembered (`PencilSeen`) and from then on Auto
  means Pencil only.
- **The iOS edit menu** (Select All / Insert Space) that came up on a held touch is removed from the
  canvas (`quietSystemMenus`).
- **Library**: the select bar keeps one width for Select all / Select none (it wrapped); a duplicate
  copies the preview; a hold that picks a notebook up isn't also taken as a tap (`HoldTracker`).
- **Back, undo, redo** use Android's Material icons.
- **Bring to front / Send to back on pictures**: not reproduced. The full UI suite's
  `testPicturesInTheLasso` (a line over a picture covered by Bring to front, shown again by Send to
  back, checked with pixels) passes on the iPad Pro 13 simulator; it had never run before (the full
  run of 25 Nov was cancelled).
- **Full UI suite on this branch (26 Nov)**: everything passes except `PenTests.testEraser`'s check
  that the precise eraser cuts a shape (made by holding the pen) into two pieces of ink: it stays
  whole. The same test fails the same way on master without this branch's changes (checked with a
  one-test run), so it's older than this work and still open. `testTypedTextInTheLasso` failed on the
  11-inch iPad only because the page scrolls back as the keyboard goes now the tools are on their own
  line; the test now finds the text where it's drawn, and passes.

## The owner's videos of 25 Nov 2026, evening (Mac without Xcode)

Written on a Mac with only the command line tools: the core tests pass and the whole app
type-checks for Mac Catalyst (see the entry below for how); nothing was run on a simulator or an
iPad here. The full UI suite runs in CI on this branch's pull request.

- **Ink thickened when resized** (his video: the pen's writing grew ~2.5 times thicker for a
  1.4 times bigger box, and stayed thick when shrunk). PencilKit doesn't draw a point in
  proportion to its size: a point P across is drawn 2P − 4 wide by the monoline (1.75P − 0.4 the
  pencil; the fountain pen's nib reaches P − 2), measured again on a Mac at every zoom and
  matching InkOutline's simulator numbers. `pkStroke` gave points the pen's size times a fixed
  factor, so widths weren't proportional. Now `InkWidth.pointSize(drawing:)` works the point size
  out from the width wanted: every stroke is drawn as wide as its size (× 0.55…1.45 for pressure
  pens), as Android's brushes are, and a resize scales it exactly. Note: solid lines now draw at
  the pen's size (a 2.4 fineliner was about 1.6 wide), the same as dashed lines and Android.
- **The highlighter flashed** at every pick-up and drop: the carried copy and the canvas both
  showed it for a moment, and see-through ink drawn twice looks darker. `liftInk` shows see-through
  ink in the overlay only once the canvas has drawn the page without it (`afterRender`), and
  `dropShapes` takes carried ink away as soon as the canvas has drawn it put down.
- **Endless zoom jumped** (a small pinch out went to 24% and down-right, the writing gone):
  changing the zoom from inside the scroll view's own pinch fought it. On an endless canvas the
  scroll view's pinch is now off and `endlessPinched` (Endless.swift) zooms and places the page
  itself, Android's `PinchResist`: out along `ZoomResist`, in following the fingers, the page under
  the fingers' middle kept under it, the measure taken again when a finger lifts or lands.
- **A page picked up in the overview jumped**: the hold ended before the drag gave the finger's
  place, so the grab was taken as the page's middle; now it's taken at the first place the finger
  is reported (`PagesOverview.grab`).
- **Selection menu**: Bring to front / Send to back use Android's Material icons (`FlipToFront`,
  `FlipToBack`, `MaterialIcons.swift`, read by Folders.swift's `SVGPath`). Pictures behind the
  writing are now drawn in their stack order, so Send to back puts one behind another picture.
  `SelectingTests.testPicturesInTheLasso` writes a line over a picture and checks Bring to front
  covers it and Send to back shows it again (the owner said pictures didn't react).
- **Top row as Android**: `TopBarLayout` is Android's `Layout` exactly: the name and the eye and ⋯
  are one corner measured with the room the title and tools leave (the name shortens, never moves
  under the bar), the tools in the middle sliding between them. The row is no longer drawn smaller
  for the name (that was today's earlier try).

## Auto arrow, and the owner's list of 25 Nov 2026 (a second Mac, without Xcode)

**Nothing from this session has been run on a simulator or an iPad yet**: this Mac has only Apple's
command line tools. What was checked here:
- The core package builds and all its tests pass (`sh scripts/core-test.sh`), Android's
  `AutoArrowTest` ported as it is (`AndroidAutoArrowTests`) and `ZoomResistTests`.
- The whole app target type-checks in Swift 6 mode with no errors, built for Mac Catalyst (the
  command line tools' macOS SDK has UIKit, PencilKit, PDFKit and SwiftUI under
  `System/iOSSupport`): `swiftc -emit-module` of TaliCore for `arm64-apple-ios17.0-macabi`, then
  `swiftc -emit-sil -wmo -parse-as-library -target arm64-apple-ios17.0-macabi -sdk <MacOSX.sdk>
  -Fsystem <sdk>/System/iOSSupport/System/Library/Frameworks -swift-version 6 -I <module dir>` over
  `App/Sources`. Nine older files need small edits in a scratch copy for this older toolchain (the
  `@Entry` macro, `glassEffect`, a few slow expressions); none are files changed here.
- `UITests/` was only read. CI runs them (the PR for this branch), with a new run on an 11-inch iPad.

Auto arrow (Android 1.0.34), checked against the Kotlin (`geometry/AutoArrow.kt`,
`NoteCanvasView.autoArrow`):
- `TaliCore/AutoArrow.swift` is a port of the Kotlin, same order and thresholds. A line shape has
  a sixth part, `head` (JSON `"head"`, left out when 0, scaled with the shape, kept by end dots).
- 1.0.34 is more forgiving: rounded and nearly-U heads, slightly bowed lines, and the head may come
  first (`AutoArrow.pair`). Android's `AutoArrowTest` is ported as it is (`AndroidAutoArrowTests`).
- App (`AutoArrow.swift`): runs from `OwnInk.hand` once the stroke is kept (so the strokes are their
  own undo steps and the swap one more); looks at the stroke as kept (smoothed), alone first, then
  with the stroke before it (any stroke of at most 2000 points, same pen slot and colour, lifted
  less than 3 s before this one). As 1.0.34, the arrow isn't picked up and there's no haptic.
- The setting `autoArrow` and the strings `auto_arrow`, `auto_arrow_detail` are Android's names (the
  strings are also in `tools/ios_strings.json` until they're imported from Android; then they come
  out of that file, or the import stops with a clash).

The list:
- **Lines across page edges** (Write between pages off): strokes keep all their points (as Android,
  item 40 of its notes). Kept still, a stroke is drawn only on the page it starts on; carried with
  the lasso it's drawn whole (`liftInk`, locked ink in `liftShapes`); put down, it's drawn on the
  page it now starts on, so moved onto that page it shows in full. An earlier version on this
  branch cut strokes at the edge when kept; that was taken back.
- **Carrying things is choppy**: `SelectionChromeView` is only as big as what it draws
  (`SelectionChrome.extent`), not the whole screen redrawn at every step of a drag.
- **A filled circle flickers or changes opacity when moved**: `PagePatch.swift`. `PaperUnderlay`
  is plain paper under the tiles; `coverPage` draws a picture of that part of the page as it now is
  over tiles being drawn again (0.4 s); `dropShapes(pageShows:)` takes what the page draws out of
  the overlay as soon as the page shows it. Also when a shape is drawn or an arrow made.
- **PDFs load square by square**: `SharpPage.Way.picture` draws a PDF page once as a picture at the
  tiles' sharpness (up to 12 megapixels, 192 MB kept) and the tiles are cut from it.
- **Text boxes open under the keyboard**: `showForTyping` (keyboard notifications, the cursor's
  line, room under the page while typing; also for a box opened from the page).
- **Endless canvas zoom-out**: no hard limit. `ZoomResist` (Android 1.0.34 has the same,
  `canvas/ZoomResist.kt`): down to where everything written fits (at most 100%), the fingers' zoom;
  past it each step out counts for less, never beyond 2.5 times further out. Pinching in follows
  the fingers from where the pinch began (`pinched`); out carries on along the resistance with no
  jump. Done in `scrollViewDidZoom` (`resistZoom`) from the fingers' spread against when the pinch
  was last measured from (again when a finger lifts or lands, or the zoom is held at its closest),
  keeping the page under their middle. Pages keep their 20% floor.
- **A page being moved leaves the finger** (pages overview): the carried pages float at the finger
  (`PagesOverview.floating`) instead of being their grid tiles pushed over.
- **The notebook's name under the bar**: e9ec94c keeps it beside the tools while it gets 110 pt
  (`TopBarLayout.nameMin`). On an 11-inch iPad on its side that isn't left (CI's iPad Air 11: the
  name went under the bar, and at 110 pt it showed as "New n…"). The owner's Android pictures show
  the whole name in the row, so `topRowScale` draws the row just small enough for the whole name
  (measured with a hidden copy of the name bar; never under 0.86); it does nothing where it fits.
  `ScreenshotTests.testNotebookNameOnTheToolbarRow` shoots it both ways (`-noTopRowScale`) on CI's
  11-inch iPad (`ipad11` job). The quick-open button has Android's icon (`RecentNotebookIcon`, a
  port of `TaliIcons.RecentNotebook`). Android's pictures had 2 pens and 5 colours: that tablet's
  own settings; both apps start with the same 4 pens and 3 toolbar colours.
- **Lasso makes text far too big**: not found. The iPad uses Android's rule exactly (scale = the
  corner's distance from the top-left corner; `TextRow.transformed` = `TextItem.transformed`,
  `InkStroke.transformed` = `StrokeFactory.transform`; same chip offsets). The owner's Android video
  shows handwriting resized with the box's corner following the finger. On CI's iPad Air 11,
  `testTypedTextInTheLasso` grew text by less than 1.3 for a 1.15 drag (passed). Needs a recording
  from the iPad.

## Curve with add dots, lines lock within 3° (25 Nov 2026, Windows)

As Android 1.0.32 (the owner's reference video). The Shapes tool draws a Curve as one S-shaped
wave from pen-down to lift (`ShapeKind.curveBetween`: four points, a; a + 0.3d + n·w;
a + 0.7d − n·w; b, with w = `curveWave` 0.17 of its length, up first on the left of the way it's
drawn). Its dots (`handles()`): point, add dot, point, …; the add dot sits on the curve halfway
along each stretch (`curveMiddle`, Catmull-Rom at t = ½). `withHandle` moves a point for an even
index and inserts one for an odd index (`addsPoint`); the dot drag rebuilds from the shape it began
with, so it inserts once. Add dots are drawn smaller (`addDotScale` 0.7, blue with a white rim,
`SelectionChromeView.addDot`) and hide, taking no touch, with less than `addDotRoom` (16 pt) clear of
the points either side (`SelectionHandles.shown`). Curves saved before keep their points and gain
add dots; hold-to-shape curves are unchanged. Lines lock onto 45° steps only within 3°
(`ShapeRecognizer.lineSnapDeg`, was 6°): drawing, end dots, hold-to-shape, a load's ends. Turning a
selection still locks within 5°. Tests in `ShapeHandleTests`; not yet tried with a Pencil.

## A Pencil tap only writes (24 Nov 2026, Windows)

At the owner's request (Tali kept picking shapes up by accident while writing), as Android 1.0.30:
a tap with the pen no longer picks up a shape under it (`shapeTappedByPen` and `penTap` are gone);
it leaves its dot like any tap. Shapes are picked up with the lasso or a Shapes-tool tap
(`ShapeTool.swift`), both unchanged, as are circle to select, scribble to delete and text boxes
(finger taps only). The shape-dots tip (`feat_dots_body`) now says to use the lasso or the Shapes
tool. `ShapesMoreTests.testATapWithThePenOnlyWrites` checks all three taps.

## Scribble to delete (23 Nov 2026, Windows)

Owner's request: a toggle in the eraser panel (`scribbleDelete`, off by default) so a pen scribble
over writing deletes it; Apple Pencil has no button to hold, and the 1st-gen and USB-C Pencils
have no gesture at all. `TaliCore/ScratchOut.swift` tells a scribble (at least 4 turns, legs across
most of it, legs close together, at least 16 pt on screen) and what it mostly covers (60% of a
thing's line inside its hull). `ScribbleDelete.swift` runs from `holdEnded` before circle to select:
not with a highlighter, only what the eraser's Erases filter takes, nothing locked, no text or
pictures; the scribble is dropped (`dropInk`) and the rest goes in one `commit`, with fills they
outline. A scribble over nothing stays ink. As Android 1.0.28: `ScratchOut.takes` also takes a thing with 15% under the
scribble when it lies within the scribble's box grown by its larger side (tall letters sticking out),
and the scribble and what it took vanish at once (no fade).
As Android 1.0.29 (ported from `geometry/ScratchOut.kt` and its tests): legs are looked for in 12
directions (every 15°, from the main one) with legs reaching half the scribble, so slanted loops
count; `ScratchOut.scribble` keeps its path and a reach (¾ of the legs' gap, at least 6 pt), and
`takes` needs 60% of what's under it to be within that reach of the path (circling a word leaves
it). `ScribbleKindsTests` is Android's rate bench with a SplitMix generator; a Python run of it gave
scrub 99, zigzag 72, loops 100, tangle 99, in place 100; word 7, runs 28 (limit 30), spring 22, resistor,
star and wave 0, spiral 1. UI tests can only drag straight, so `ScratchOutTests`
checks the shapes and `PenTests.testScribbleToDeleteSetting` the toggle. Try it on the iPad.

## Owner's list of 24 Nov 2026 (Mac, after testing on the iPad)

One commit per fix. What changed and what to know:
- `OwnInk.swift`: every pen is drawn live by the notebook (PencilKit's drawing gesture is off for pens). The live picture is PencilKit's own renderer (`PKDrawing.image`, about 2–3 ms) of the very `InkStroke.pkStroke(mask:)` that's kept, smoothed live with `PenSmoother` and masked to its page; on lift it stays until `canvasViewDidFinishRendering` (`afterRender`). This fixed the flicker on lift (the old smooth-on-lift swap), live smoothing and strokes showing past the page edge. `PageEdgeCover` is gone. Strokes kept from here are remembered (`own.kept`) so a scribble or a caught loop can take them back (`dropInk`).
- Selection put-down: `endDrag` no longer draws the old drawing back; `inkBase` holds it for `commit`, `restoreLifted(redraw: false)` leaves the overlay where it was, and `dropShapes` waits for the canvas to render.
- `InkStack.swift`: the page layer draws shapes, text, front pictures and locked or covered ink in one z order; ink covered by something above it is hidden in PencilKit with a far-off 1 pt mask (an empty mask is ignored) and drawn by the page instead (`restack()` after every change).
- `CircleCatch`: Android's close-in animation (`drawCircleCatch`, `framePoints`).
- Text boxes keep the height they grew to while typing. Dark paper's toolbar puff uses the dark theme's mist.
- First open: the canvas is hidden until its saved zoom and place are set (`firstPlacing`).
- Pages overview: the tap ending a hold is ignored; carried tiles share the held tile's offset.
- Endless: `makeRoom` moves everything into 6000 units of room (and again near the left/top edge when a scroll ends); the shift is kept per notebook in UserDefaults (`endlessShift-<id>`) and taken back first when going to pages. Undo is cleared on the switch.
- Toolbar tools 40 pt wide and pens 32 pt; the name stays beside the tools down to 110 pt.

## Owner's list of 23 Nov 2026 (Mac)

One commit per fix. What changed and what to know:
- Tour slides 28 pt and is clipped to the card. Camera preview follows `RotationCoordinator`'s preview angle (KVO). Quick open shows thumbnails.
- `StrokePreview.shownSize`: the sample maps the pen's range onto 1.6…10 world units, so the card is never filled.
- `LaserIcon` (Android's `TaliIcons.Laser`). `CircleSelect.swift`: Android's circle to select, `PenSlot.circleSelect`, toggle in the pen panel.
- `TopBarLayout` has a `.name` role: the name sits beside the corner while it gets ≥150 pt, else under it.
- Selection drags carry picked ink as an image in the overlay (`liftInk`); PencilKit's drawing isn't reset every step.
- The pen settings arrow takes the bar's whole height and the padding beside it, never a pen's area (SelectingTests checks taps on neighbours).
- Library cards and page tiles: no system context menu / drag (`HoldMenu.swift`, `HoldPress`; the pages overview's own hold-and-drag with `PageFrames`).
- `KeyboardDismiss`: a window tap recognizer ends editing of a `UITextField` (not the notebook's text boxes).
- The page layer (CATiledLayer) is only invalidated where things changed: `updateContent` redraws only on a size change (it used to redraw every tile each zoom frame), `putShape`/fills redraw their rects, `load` redraws all. Pictures and fills decode off the main thread in `load` (`decodeLater`), reusing decoded ones.
- `PenSmoother` (TaliCore, Android's One Euro filter) smooths each new stroke in `sync`; the stroke is swapped in PencilKit. PencilKit's undo of a stroke checks the drawing it made, so `undoKeepingSwaps` puts PencilKit's own drawing back (`pkSwaps`) before an undo/redo and `sync` swaps the forms back (`swappedForm`). This also fixed undo for strokes masked to their page.
- `PageEdgeCover`: with Write between pages off, a snapshot covers everything around the stroke's page once the pen leaves it.
- Not reproduced in the simulator: repeated bounce at the bottom (one normal bounce seen); per-frame SwiftUI updates while scrolling were cut instead. Check on the iPad.

## Thickness in whole numbers (17 Nov 2026, Windows, from Android 1.0.24)

Android's df39537 (its CLAUDE.md item 91a): pen, shape-style and selection thickness step by 0.25
(`thicknessStep` in `Theme/StepSlider.swift`) and show four times the stored width
(`thicknessLabel`: 0.5 shows as 2, 1 as 4); stored sizes are unchanged and the eraser and text
size keep their own steps. Changed: the pen panel (presets and slider), the toolbar's quick sizes,
the Shapes Style tab and the selection menu's thickness. `PenTests` taps "16" (was "4.0") and
holds + for 8 s to reach "160". Not built here (no Swift on Windows): CI checks it. Item 91b
(Back from a file opened from another app goes to the library) needs nothing here: `RootView`
has no stack, only one notebook over the library, and a file from another app replaces it
(`open = n`), so Back always lands in the library. 91c (the stroke flicker) is Android Ink's own.

## Windows sessions 4–5 (11–12 Nov 2026), merged without their code

While the Mac ran the parity work, the scheduled task on the owner's Windows PC kept going and
pushed 15 commits to `main` (shape dots, snapping, resize/move chips and rotate handle, the Shapes
Style tab, soft selection). The Mac's `main` had all of these in fuller form, built and tested with
the rest, so they were merged with `git merge -s ours` (commit "Merge the Windows sessions 4–5"):
their history is kept, their code isn't used. Turn that scheduled task off (OWNER_TODO.md) so the
two machines don't build the same things again.

## Parity run (session 3, 11–12 Nov 2026)

Goal: parity with Android except backups, iCloud and Drive. Six areas were built by agents in
their own worktrees and merged into `main` one by one, each checked by the full UI suite on the
iPad Pro 13 simulator (see PLAN.md's "Parity with Android" for what each left). The phone pass and
the leftovers in PLAN.md went in on 12 Nov; on 17 Nov every UI suite passed on the iPad Pro 13, and
the selection and phone suites on an iPhone, with 396 core tests.

How things fit together now (read before changing the notebook):
- `TopBarLayout` places the wide top row by role (title, tools, corner) as Android's
  `NotebookScreen` Layout: the tools shift over and the notebook's name shortens before anything
  overlaps.
- PencilKit draws only when `inkOn` (a pen, not just looking, not a pen the notebook draws itself:
  dashed lines and the highlighter are `InkLook`/`OwnInk` on our own layer). Set
  `drawingGestureRecognizer.isEnabled` after `canvas.tool`: setting the tool switches it back on.
- `draws(t)` says whether a touch writes; `claim(t)` says a touch is editing the selection (chips,
  dots). Every tool's watcher checks both.
- Dashed shapes are cut into pieces (`ShapeRow.strokes`, Android's `StrokeFactory.dashes`) and drawn
  in `ShapeRow.inkColor`, everywhere: page, lifted selection, live Shapes tool, fill mask, previews.
- `ShapeGeometry` carries Android's extra fields: poly `form`, ellipse `stretchable`, group and
  triangle `turn`, and `param` shapes with joints (subject packs).
- The selection menu is drawn under the bars and panels (it belongs to the page, as on Android); its
  trash is `selection-delete`.
- Pictures and memos are typed rows (`PictureRow`, `MemoRow`); one `contentBounds` (TaliCore `Rect`).
- Colours live in `Palettes.shared` (observable); pen helpers (`presetSizes`, `withSize`) in TaliCore.
- `CrashLog` (Android's): uncaught exceptions, fatal signals (async-signal-safe, turned into a
  report on the next start), MetricKit crash and hang reports, and an on-screen marker for a close
  nothing saw. The library's `CrashBanner` offers the report by Mail (the share sheet without an
  account). UI test runs (`-uitest`) start clean; `-crashReport` makes a test report.
- The canvas ignores PencilKit's "drawing changed" for a drawing it set itself (`ownDrawing`), not
  only while `loading`: on CI's iOS the call came late, and a lasso move's live drawing was stored
  part way (the outline stayed behind while its fill went on).
- `NoteCanvasView.onResize` refits and redraws chrome; `PagesLayer.paint(in:scale:print:)` is the one
  drawing path for screen, page pictures and PDF (`drawsPaper` off when export paints one sheet).

Testing:
- One UI test class per area (`UITests/*Tests.swift` on `ShotCase`). `ONLY=<Class>` or
  `ONLY=<Class>/<test>` with `scripts/local-shots.sh`; `scripts/one-test.sh` also stops a single test.
- Full suite on the iPad Pro 13: about 50 minutes for 59 tests, no known failures. The overview's
  "Duplicate opens Delete" was not the simulator: a button with a rounded-rectangle `contentShape`
  answered taps up to 22 pt to its left (the right half of Undo fired Redo). Every button uses a
  plain `Rectangle` content shape now.
- CI splits the UI suite over five jobs (`scripts/ci-shots.sh`, one matrix entry each): all of it
  once on the iPad in the light; the dark, Hebrew and iPhone runs take ScreenshotTests and the phone
  classes. One job for all of it ran past GitHub's hour. Failing tests are listed, not fatal.
  CI's Macs are slow virtual machines: a failed test is retried (three tries), and the tests get
  `CI=1`. There PencilKit can be slow to redraw ink moved in code (the cut-outline test reopens the
  notebook and checks what's stored; it was right every time). Its "iPhone 16" was an iPhone 16e
  until `sim.sh` preferred exact names.
- After a page drag in the overview, the drag's window stays in XCTest's tree and the tiles read as
  not hittable, though touches reach them: tap them by coordinates.
- A new notebook takes the last paper used, so a test choosing a paper must pick one that isn't
  current (tapping the current one opens its card).
- A shape just made stays picked up (Android's soft selection): tests that count pixels or tap
  "Delete" must put it down first or tell the menu's trash apart.
- Test-only launch arguments: `-uitest -seed`, `-cameraPicture`, `-fakePickers`, `-fakeDrop`,
  `-openURL <file>`, `-keep-pens`, `-fingerDraws 0`. Settings persist on a simulator between runs.
- Never run two `local-shots.sh` on one simulator at once; a killed run can leave the test runner
  alive (the script now stops it first).
- The Mac is short on disk: each booted simulator is ~2.3 GB. Keep few, delete them after use.

Device: the owner's iPad Air 11 (M2) installs with the free personal team, bundle id
`com.tali.notes.idan` (build-time override only), when unlocked and plugged in:
`xcodebuild … -destination generic/platform=iOS DEVELOPMENT_TEAM=R38D6WY53B PRODUCT_BUNDLE_IDENTIFIER=com.tali.notes.idan`
then `xcrun devicectl device install app --device <udid> <app>`.

Pushing: `idanlo` has write access to `arielmz1234/Talis-Notes-iOS` since 12 Nov; `main` is pushed.

## On the Mac (session 3, 11 Nov 2026)

Building and checking locally now (Xcode 27, iOS 27 simulators; the code is unchanged for it):

```bash
brew install xcodegen && xcodegen generate
swift test --package-path Packages/TaliCore                      # 82 core tests
sh scripts/local-shots.sh "iPad Pro 13" ipad-light 0 ""           # build + the screenshot test
NO_BUILD=1 sh scripts/local-shots.sh "iPhone 17 Pro" iphone-dark 1 ""
python3 scripts/contact-sheet.py out.png shots/iphone-light-4*.png   # several shots in one image
```

A run takes about 1.5 minutes per device. `local-shots.sh` stops `xcodebuild` once the test summary is
out (on Xcode 27 it sat forever after the last test). Landscape iPad shots come out upright locally.

Checked on the simulators this session (iPad Pro 13" M5, iPhone 17 Pro), light, dark and Hebrew:
- Typing in a text box works on iPad and iPhone. The box opens below the writing, scrolls under the
  toolbars, keeps its text when finished and after reopening.
- The text box was invisible to accessibility (PencilKit's canvas hides its subviews), so VoiceOver
  couldn't reach it and the UI test could never type ("Neither element nor any descendant has
  keyboard focus" in CI). The canvas is now `NoteCanvasView`, which offers the box and A−/A+ while
  one is open. The test types into whatever has the keyboard.
- Opening fitted: the first fit waited for an unrelated scroll event (about 2 s after opening a PDF,
  so the screenshot showed 100%). Nothing told the canvas it had a size. `NoteCanvasView.onResize`
  now calls `updateContent` when the size changes, so the fit comes 0.2 s after the tap. The 0.35 s
  second fit and the scroll hook were removed: the zoom was never being reset, only fitted late.
- `topClearance` from Android (`NotebookScreen`: 196 narrow, 124 wide), as 164 / 104 points
  here since the canvas starts below the status bar: the first page now starts below the phone's
  second toolbar row, and the text box's A−/A+ is no longer hidden behind it.
- The phone's folded tools, A−/A+, the lasso (2 picked) and the shapes panel look right on iPhone.
- Typed text in Hebrew (or Arabic) UI was right-aligned even when written in English. Now each
  paragraph runs the way its first letters do, as Android's `FIRSTSTRONG_LTR` (`TaliCore/TextDirection`,
  with tests), on the page and in the editor. Seen in `ipad-he-4g/4h`.
- The test-build diagnostics (ink widths, lasso hints) were removed after confirming live and
  reloaded ink match (3.31 pt both).

There is no Simulator.app in this Xcode 27 install and T3's device panel is off, so runs are
headless; review the shots in `shots/`.

Same as Android, left as is: on a phone at 30% typed text is small (Android doesn't zoom for typing);
a page nearly full puts a new box 30 mm above the page's bottom, over what's there.

## Moving to a Mac (11 Nov 2026, end of session 2)

The owner is continuing on a MacBook. There, build and test locally instead of waiting ~35 min for CI:

```bash
git clone https://github.com/arielmz1234/Talis-Notes-iOS && cd Talis-Notes-iOS
brew install xcodegen && xcodegen generate          # makes TalisNotes.xcodeproj (never hand-edit it)
swift test --package-path Packages/TaliCore          # 79 core tests
open TalisNotes.xcodeproj                            # run on an iPad simulator, or a USB iPad
```

- Needs Xcode 26 (CI uses Xcode 26.6 on macos-26, iOS 26 simulators); the app targets iOS 17+.
- UI screenshot tests: `xcodebuild test -scheme TalisNotes -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M4)'`
  with env `TEST_RUNNER_SHOT_DIR=$PWD/shots` (see `.github/workflows`).
- Running on a real iPad from Xcode with a free Apple ID works (Signing & Capabilities → your
  team); it's re-signed every 7 days. iCloud stays off without a paid account.
- The Android app (`Talis-Notes`, the spec) can be cloned beside it for reference.
- Test-build-only diagnostics still in the code, to remove once seen: the ink hint in
  `NoteCanvas.sync`/`load` ("live tool … | loaded …"), the fit note in `fitWidth`'s caller
  ("fit … got …"), the lasso hints in `Selection.finishLoop`. All only under `-uitest`.

**Last CI run checked: 43062180296 (green).** Pushed after it and not yet seen in CI: keyboard
avoidance off for the canvas (text box scrolled below the toolbars), refit while the canvas size
settles (iPhone page fit), the phone's folded tools, the test's tap-inside-the-box fix, A−/A+.
First thing on the Mac: run the app and check those (typing a text box on iPad and iPhone, the
iPhone opening fitted, the phone tools button).

## Session 1 (10 Nov 2026, the owner's Windows PC)

Environment: Windows, no Swift or Xcode locally, Docker not running. CI on GitHub Actions
(`macos-15`, Xcode 16.4). The repo is public, so its macOS minutes are free.

Done:
- Repository set up: BRIEF, PLAN, OWNER_TODO, XcodeGen `project.yml`, `Packages/TaliCore`, CI
  (core tests, build, screenshots on iPad light/dark/Hebrew and iPhone light/dark, unsigned .ipa).
- TaliCore (pure Swift, tested):
  - `Pt`, `Rect`, `Affine` (Android's field names and `then` order), `Geo`, `Pages`
    (A4 layout, clip rules, page reorder helpers).
  - The library model: `NotebookMeta`, `Folder`, `DocKind`, `PaperStyle`, covers, sort,
    natural order, numbered names, greeting.
  - `NotesDb`: SQLite3 with the Android schema VERSION 8 and its upgrade steps.
  - `InkCodec`: androidx.ink's stroke storage (gzip'd protobuf, delta-coded runs), worked out
    from a real Android stroke (fixture in the tests) and written the same way.
  - `Gzip`, `CRC32`, `Deflate` (Apple's Compression), and `Zip` reader/writer.
  - `BackupArchive`: write/restore the Android backup .zip, with merge (newer wins, nothing
    deleted, older schemas, names kept apart).
  - `Lasso` geometry.
  - Ported tests: MovePageTest, FreeNameTest, LassoTest, FreeSelectTest, plus page layout,
    database, ink, zip and backup round trips.
- App:
  - Theme: lilac light/dark, variable fonts (Figtree, Bricolage, Heebo, Vazirmatn with weight
    axes), the cloud surface (bumps ported exactly), sky clouds, papers (all eight built-in ones).
  - Library: sidebar, phone drawer, search, sort, cards, folders, Recently deleted, context menu,
    new-notebook card, Backups card (save a backup file, restore files).
  - Notebook: `NoteCanvas` is a PKCanvasView. PencilKit draws the live stroke; each finished stroke
    is stored as an Android ink row. Strokes that run off their page are hidden past it with
    PencilKit's stroke mask. Pages are drawn by a CATiledLayer underneath. Writing on the last page
    adds a page. Pens (fineliner, fountain, pencil, calligraphy, highlighter) with a panel for type,
    colour and thickness; PencilKit's eraser and lasso; undo and redo.
- Strings: 827 keys in six languages generated from Android.
- App icon from `tools/icon-source.png`.

Verified by looking at CI screenshots:
- Run 42853258304: the library on iPad (landscape, portrait, dark, Hebrew mirrored with the
  sidebar on the right) and iPhone (light, dark).
- Run 42863082146 (all green, core tests included):
  - Fonts load: Figtree for text, Bricolage for headings, Heebo in Hebrew.
  - Paper previews show their lines.
  - The new-notebook card fits iPad and iPhone.
  - Backups and Settings cards look right.
  - Writing works: two finger strokes drawn in the UI test show on the page in a new notebook,
    on iPad and iPhone.
  - The notebook keeps the English layout in Hebrew.
- Found in that run and fixed in the next commit (run 42870297252):
  - Dark mode lightened black ink on the white page (PencilKit adapts ink to the interface style).
  - The bars should be the mist colours.
  - The page started under the toolbar.
  - Also added in that commit: iPhone view-only with Write.

Not verified yet: anything about real Apple Pencil feel (pressure, latency) — needs a device.
Restoring a real Android backup on iOS is covered by unit tests (ink fixture, schema 7 database),
not yet by opening one in the app.

- Run 42883014643 (green): the library and notebook bars redone to match the Android app's
  current screenshots (`C:/dev/Talis-Notes/tools/emulator/shots\lib.png`, `nb.png`, 9 Nov 2026):
  - one continuous sky behind the sidebar and the main area;
  - the wordmark with its highlighter swipe;
  - the 52 pt heading with a count and date line;
  - plain white search and sort boxes (no cloud), the + as a 48 pt gradient square;
  - the ruler, the RECENT label, and landscape cards (1.32) with a stitched spine;
  - in the notebook: back on a three-puff cloud, undo and redo, the name pill with a pencil, the eye.
  Checked in screenshots: iPad light, dark, portrait, iPhone.
- Dialog cards (new notebook, backups, settings) lost their cloud bumps to match Android's
  `DialogCard` (radius 24, shadow 12). Pushed at the end of session 1, not yet seen in a screenshot.

**Owner's rule (10 Nov 2026): the app must look exactly like Android.** Before any design
decision, read the Android composable and look at its screenshots in
`C:/dev/Talis-Notes/tools/emulator/shots` (newest first: `ls -t`). Copy crowns (`CloudCrown`), radii,
sizes and type styles (`TaliType` mirrors Android's Material names) rather than guessing.

Later in session 1 (owner's requests, 10 Nov 2026 evening):
- CI moved to `macos-26` (Xcode 26.6, iOS 26 simulators) for Liquid Glass. Screenshot runs take
  about 25–35 minutes there.
- The owner allows iOS-only touches that are clearly better (BRIEF.md, "Look"); behaviour and logic
  stay exactly as on Android. Apple design rules: `docs/apple-design-SKILL.md`.
- Done and seen in screenshots (run 42891226523):
  - glass thickness slider (`Theme/StepSlider.swift`, native Slider plus glass − / + on iOS 26);
  - Settings laid out like Android (Language row, colour tiles, Theme | Style segmented);
  - Android's `Segmented` with the gliding white pill (`Theme/Segmented.swift`).
- Done, build green, not yet seen in screenshots:
  - sky clouds burst into mist when tapped and re-form after 4 s (`Theme/CloudPuff.swift` ports
    `CloudPuff.kt`: same wisp grid, timing, easing);
  - cards shown over a dimmed screen (`Theme/DialogOverlay.swift`) instead of sheets (iOS 26 drew a
    panel behind them);
  - Select mode in the library (PickBar);
  - tap the notebook's name to rename it;
  - the main toolbar like Android's (`Notebook/Toolbar.swift`): `PenGlyph` ported exactly, pens
    standing in a tray with the active one lifted, the arrow to pen settings, toolbar colours,
    `QuickSizes`, eraser and lasso with the blue gliding highlight;
  - `PenPanel` (types, sample stroke, three sizes, Size slider, colours, how many in the toolbar);
  - `EraserPanel` (whole strokes / precise, size);
  - page and zoom chips (go to page, tap zoom to fit).

Night of 10–11 Nov 2026 (same session, continuing):
- Seen in screenshots:
  - the toolbar and pen panel match Android on iPad, light and dark (run 42901014073);
  - the pen panel fits an iPhone (run 42906901522);
  - the ⋯ menu (`Notebook/MoreMenu.swift`), readable in dark mode once popovers got the app's
    surface colour (run 42913922426);
  - quick open beside Back (`Notebook/QuickOpen.swift`);
  - the new-notebook card on iPad (`Theme/PaperPicker.swift`, Android's paper picker with More);
  - library cards showing the first page with its writing (`Notebook/Thumbnail.swift`, saved as
    `thumbs/<id>.png` when a notebook closes).
- Built, not yet seen in a screenshot (run 42919352781 has them):
  - our own lasso (`Notebook/Selection.swift`): a dashed loop picks up whatever it touches
    (`Lasso.touchesPath`), a dashed frame, drag inside to move;
  - the selection menu (`Notebook/SelectionMenu.swift`): Copy, Cut, Duplicate, Box around, Delete;
  - hold 0.6 s on the page with something copied → Paste bubble;
  - the hint line;
  - the new-notebook card fitting an iPhone.
- Fixed: undo bringing back erased or deleted strokes stored them with the pen in use (wrong colour
  or type). The coordinator now keeps `archive` (every stroke seen, by key) and `pending` (strokes
  the app adds itself), and `sync` uses them.
- Ported into the ⋯ menu: Whole page, Paper, Dark paper, Draw with finger (Auto/On/Off → PencilKit
  drawingPolicy), Tap 2 fingers to undo and 3 to redo, Write between pages, Full screen, View only
  (tablets keep a `viewOnly` list, as Android's `AppSettings.viewOnly`).

Next (in this order):
- Check run 42919352781's screenshots: `4d-lasso` (selection frame and menu), iPhone new notebook.
- Selection menu's ⋯ (Colour, Thickness, to front/back, mirror, lock); the resize chip, the move
  chip and the rotate handle on the selection frame; the lasso panel (Lasso / Free form,
  Screenshot, Pick up locked items).
- Pages overview (⋯ menu › Pages), Endless canvas switch, Export PDF.
- Fill, laser, shapes (with hold-to-shape), picture/camera/PDF insert, voice memo, typed text; line
  styles (dashes); the highlighter's straight lines and square tip.
- Shapes and pictures from Android show under the writing; typed text and "param" designs don't yet.
- iCloud (switched off in the free-signing build), then Google Drive.

Decisions:
- The bundle id is `com.tali.notes`, the same as Android (Apple's ids are separate).
- Swift Testing (`import Testing`) for TaliCore tests.
- Ink: PencilKit for live drawing and display (Apple Pencil latency, zoom, tiling for free), with
  the Android ink format as the stored truth. A PKStroke's control points are stored as the
  Android inputs. Pressure = force / 2; tilt = π/2 − altitude; orientation = azimuth.
  Dashed line styles will need our own rendering later (PencilKit has no dashes).
- World units = points at zoom 1 (Android's dp).

Known problems:
- The lasso pan is allowed to begin but never begins (run 42989992216; see Session 2's "Found").
- Shapes (from Android or hold-to-shape) can't be erased or lassoed yet.
- After a lasso move, the moved strokes are swapped for their stored form, so the lasso selection
  ends (PencilKit's lasso). Our own lasso will replace it.
- New strokes are kept as PencilKit drew them until reopened (control points ≈ samples); strokes
  loaded from Android go through PencilKit's spline, so they look slightly smoother than on Android.

## Session 2 (11 Nov 2026, scheduled run, owner's Windows PC)

Found on start: the night session's last commit (Export PDF, 11ba273) was never pushed, and its
notes stopped before its last three commits. Those commits, all built green in CI before Export:
- the selection menu's ⋯ (colour, thickness, to front/back, mirror) — 61cbdef;
- the lasso running alongside PencilKit's touch handling, simulators booted before tests — 314a6a1;
- the pages overview from the ⋯ menu (every page, go to, delete, add) — e9b3906;
- Export PDF from the ⋯ menu (Best / Balanced / Light with the real size, Save or Share) — 11ba273,
  pushed this session; it builds in the same run as hold-to-shape below.

Seen in run 42930179315's screenshots: the lasso still picks nothing up in the UI test (no loop,
frame or menu in `4d-lasso` on iPad or iPhone; a tiny mark appears in the iPad's bottom-right
corner). Not solved yet: see Known problems.

Done this session:
- `TaliCore/ShapeRecognizer.swift`: Android's `ShapeRecognizer.kt` ported line for line (lines
  snapping to 45°, arrows, arcs with the half-circle snap and hook trimming, curves, L/Z polylines,
  circles, ellipses, triangles incl. right-angle and isosceles snaps, rectangles/squares, diamonds,
  regular pentagons/hexagons, stars), plus `Geo.resample/simplify/meanDistToPolyline/centroid`.
  `ShapeRecognizerTests` ports all of `ShapeRecognizerTest.kt` (a seeded SplitMix generator
  replaces Kotlin's `Random(7)`, so the wobble differs; the thresholds are the same).
- Shapes are written the Android way: `ShapeGeometry.json` (kotlinx's form: "type" key, defaults
  left out), `ShapeRow.row(id:z:)` (kind 1, extra "fill"/"locked"/"line"), `bounds`, `transformed`.
  `.poly` gained `rotation` (Android stores it for rectangles).
- App: hold-to-shape (`Notebook/HoldToShape.swift`). A `StrokeWatcher` gesture recogniser watches
  the writing touch without ever recognising (PencilKit keeps the stroke). After 0.45 s resting
  (7 pt tolerance, stroke ≥ 24 pt on screen) the stroke is recognised; PencilKit's live stroke is
  cancelled by switching its drawing recogniser off and on (and `sync` drops it if it arrives anyway,
  `isSnappedStroke`); the live shape is drawn in a CAShapeLayer and follows the pen (a line's end
  snaps to 45°, others scale round their middle, 0.15–8×); on lift it's stored as a shape row,
  drawn by the page layer, one undo step (redo works too). Medium haptic on the snap.
- UI test: a sloping line held at its end, which should come out exactly level in `4-notebook`.

Decisions:
- Hold-to-shape is always on (Android's default); the switch lives in the Shapes panel's Style tab,
  which isn't ported yet.
- Shapes keep being drawn by the page layer under the writing. So shapes made here can't yet be
  erased or picked up with the lasso (PencilKit only knows ink). Needs our own item model for
  shapes in the eraser and lasso — next big step.
- Not done from Android's hold-to-shape: snapping a line's start/end to other shapes (`Snap`),
  custom and subject-pack shapes, the measuring badge, the square/circle diagonal hint.

Verified (run 42989992216, green):
- All 73 TaliCore tests pass, the ported `ShapeRecognizerTests` included. (The first run,
  42989607121, failed: Swift couldn't type-check a `max { }` closure in `fitCurve` in time; it's now
  plain loops.)
- Hold-to-shape in the app: in `ipad-he-4-notebook` the sloping line held at its end came out
  exactly level, drawn by the page layer. On the iPhone the same drag is ~7° off level (narrow
  screen), past the 6° snap, so it stays at its angle but is a clean shape (thin, straight).
- Only ipad-he and iPhone screenshots came out: the ipad-light run couldn't find its simulator, and
  ipad-dark timed out setting the orientation. CI flakiness; rerun before reading anything into it.

Found:
- A shape draws thinner than PencilKit's ink at the same pen size (PencilKit's monoline widens the
  width it's given). Android draws both with the same brush. Match them, e.g. scale PencilKit
  widths, or draw shapes with the factor PencilKit uses. Check against Android shots.
- Lasso: the test build now shows `gestureRecognizerShouldBegin` returning true for the lasso pan
  ("lasso should begin"), but the pan's `.began` never arrives ("lasso began" never shows). So
  another recogniser wins after it's allowed to start, probably one inside PKCanvasView. Try:
  `canvas.gestureRecognizers?.forEach { if $0 !== lasso.pan { $0.require(toFail:) … } }` for
  PencilKit's own ones while the lasso is on, or watch raw touches like `StrokeWatcher` does.

Next:
- Lasso UI test picking nothing up (see above).
- Eraser and lasso for shapes; the Shapes tool and panel; snapping.

### Session 2, continued (owner said keep going)

- Lasso fixed (run 42997378616, seen in `ipad-light-4d-lasso`): it now follows its own touches with a
  `StrokeWatcher` (like hold-to-shape) instead of a `UIPanGestureRecognizer`, which PKCanvasView let
  begin but never began. 191 loop points, both strokes picked, frame and menu showing. A second
  finger cancels the loop (pinch/scroll).
- The UI test now reopens the notebook (`5b-reopened`). It showed two bugs:
  1. **Reloaded ink is a hairline** (~1 px) while live ink was ~4.5 pt for a 2.5 pt fineliner. So
     `InkStroke.pkStroke` point sizes don't render like PencilKit's live tool width. The test build
     now shows "ink tool W point P max M n N" for each new stroke (hint in `4-notebook`) to
     measure the relation. Then make live and reloaded match Android's width.
  2. The lasso's own drag was stored as an invisible ink stroke (PencilKit kept drawing in the
     lasso's clear ink). Strokes arriving while the lasso is the tool are now dropped in `sync`.
     PencilKit drawing during the lasso should also be truly off (`drawingGestureRecognizer`
     disabled evidently isn't enough on iOS 26).
- Shapes in the lasso and eraser (`Notebook/ShapeSelection.swift`): the lasso picks up shapes it
  touches (unlocked); move (carried in an overlay layer, page tiles redrawn once), copy, cut,
  duplicate, paste, recolour (keeps the shape's alpha), thickness, mirror and delete include them,
  and undo restores shapes with the drawing (`record(_:shapes:)` → `setShapes`, which saves only
  what changed). The eraser takes whole shapes it passes over (its own watcher; one undo step for
  the shapes). Arrange (front/back) is ink only: shapes are always under the writing here.
- Shapes tool (`Notebook/ShapeTool.swift`, `ShapesPanel.swift`, `TaliCore/ShapeKinds.swift` with
  `ShapeKindTests`): seen in run 43004155595 — the panel (`4e-shapes-panel`: 16 standard tiles and the
  7 civil-engineering symbols, Rectangle chosen in blue) and an ellipse dragged out
  (`4f-shape-drawn`), the toolbar button showing the ellipse. The engineering symbols are in the
  panel under "Civil Engineering" (Android has them in the Civil subject of More shapes; the
  subject packs aren't ported). No Style tab yet: shapes take the last pen's colour and size.
- Same run, reopened (`5b-reopened`): the lasso's drag is no longer saved. Reloaded ink was still
  a hairline, probably because a finger's stroke stores pressure 0 and was reloaded with force
  0.01. Pens without pressure now reload at force 1 (`InkStroke.force`). The test build shows
  "live … | loaded …" numbers in `5b-reopened` to confirm.
- The lasso's menu stayed up after switching tools: `clearSelection` from inside `updateUIView`
  changed SwiftUI state mid-update. It's now deferred.
- PDF and picture import (`Data/Imports.swift`, the + is now a system menu: Notebook / File;
  `KindBadge` on cards). Seen in run 43011336605: the menu (`2b-add-menu`), and a seeded two-slide
  PDF opening just for looking with Write (`5c-pdf`). On a tablet a PDF/picture opens for looking
  until its first Write (`writeOn`, as Android). Found there: the slide wasn't fitted to the screen
  (zoom 100%), and the page chip said 1 / 1. Both came from state changed during the canvas's first
  update (`reportUndo`, first fit), now deferred. Not seen yet.
- Ink width, measured (`5b-reopened` hint): a monoline tool of width 2.4 gives live points 4.40
  wide (1.83×); reloaded strokes use 2.4 exactly (Android's width), hence "thin after reopening". Now
  the tool gets size ÷ factor (`InkWidth`, 1.83 for monoline at first, learned per ink from strokes
  without pressure), so live and reloaded match Android. Not seen yet. Pressure inks (fountain,
  pencil, calligraphy) keep factor 1 until measured.
- Not done for PDFs yet: sharp pages at zoom (`SharpPdf`), the Adding… pill, Photo and Camera in
  the + menu, Save-as-draft PDFs from other apps.
- Run 43023666787: the page chip now says 1 / 2 for the two-slide PDF; tapping the zoom chip fits
  the slide (71%) and the slide text is crisp (pages drawn from the PDF itself as vectors,
  `SharpPage` in NoteCanvas.swift, in the tiled page layer; the JPEG stays as the fallback).
  The first fit on opening was still undone right after (100%); it's now fitted again 0.35 s later
  unless she has pinched (`fitWidth`, `zoomedByHand`). Live ink now measures ~2.2 pt for a 2.4 pen
  (was 4.5). Reloaded points of 2.4 still render ~1.2 pt: suspected PencilKit's `secondaryScale`
  left unset by the old point initialiser; now set to 1 (not yet seen).
- Reloaded ink matches live ink exactly (run 43039738075, `ipad-he` profiles identical): rebuilt
  points copy live points' size per pen size and `secondaryScale` (`InkWidth.pointStyle`, learned
  from each live stroke of an ink without pressure; monoline defaults 1.17 and 0.36).
- Added, not yet seen: Photo and Camera in the + menu (PhotosPicker up to 30; the system camera via
  `CameraPicker`, shown only where there's a camera; `NSCameraUsageDescription` in project.yml,
  English only for now, to be localised with an InfoPlist string catalog); opening where she left
  off (saved viewport restored, else the page's width, with the animated zoom PKCanvasView honours);
  typed text (`Notebook/TextBoxes.swift`, `TextRow` in TaliCore with a payload test): keyboard
  button, Android's placement, finger tap on a box to type in it, tool change or tap elsewhere
  finishes, undo, Android's text boxes drawn by the page layer.
- Not yet for text: lasso and eraser on text boxes, A−/A+ and the side dots, Android's two-step tap
  (pick up, then type).
- Remember to keep `RUNNING` fresh in long sessions (a stale time lets the next scheduled session in).
- Typed text, debugging in CI (runs 43056814643, 43062180296): the first box didn't take the
  keyboard ("neither element nor any descendant has keyboard focus", which also stopped the rest of
  the test run); fixed with a second `becomeFirstResponder` once it's on screen. Then the test's tap
  inside the box finished it (the canvas's text tap saw it): the tap recogniser now ignores touches
  on the box. And SwiftUI's keyboard avoidance pushed the whole canvas up: the canvas now
  `.ignoresSafeArea(.keyboard)` and scrolls the box below the toolbars itself. Being checked in
  run 43068434218.
- The page didn't fit on opening (iPhone at 100%, page far wider than the screen): the canvas
  refits whenever its size changes until she zooms by hand (`fittedSize`); min zoom 0.2.
- Phone toolbar: the tools fold into one button (`compactTools`, Android's `MainToolbarPhone`),
  dropping a bar of eraser, lasso, shapes, keyboard. The UI test opens it (`showTool`).
- CI now prints the failure lines of each screenshot test run (the element, the reason).
