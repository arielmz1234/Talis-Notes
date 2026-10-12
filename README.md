# Tali's Notes

A handwriting notes app for Android tablets with an S Pen (Galaxy Tab S6 Lite and newer), made for a civil-engineering student. It has an endless canvas per notebook, a lilac-and-sky look with cartoon clouds, and it runs entirely on the tablet.

It is sideloaded for now; a free, tablets-only release on the Play Store is planned (see *Release plan* in [CLAUDE.md](CLAUDE.md)).

## What it does

**Writing**
- Five pen types: fineliner, fountain pen, pencil, calligraphy and highlighter. She can keep up to 8 pens, and her saved colours are shared by all of them. Changing a pen's type keeps its thickness; sizes have − and + buttons for 0.1 steps (hold to repeat).
- The highlighter draws straight lines by default (level or upright when within 4°), or freehand; its tip can be round or square. Both are set in its pen settings.
- **Laser:** a pointer in the toolbar that leaves a glowing red trail; it stays lit while she keeps pointing (several strokes too) and fades about a second after she stops. Nothing is saved.
- **Line styles:** any pen (not the highlighter) can draw Solid, Dotted, Long dash, Short dash, Dot and dash or Mixed dash, chosen in the pen settings. The pattern draws live under the pen, and shapes drawn with that pen use it too (arrow barbs stay solid).
- **Quick colours:** the first few of her saved colours sit beside the pens (how many, 0-6, is set under the colours in the pen settings, for every notebook). Hold a colour in the pen settings and drag it to change the order.
- Low-latency inking (androidx.ink), with pressure.
- The S Pen side button erases while held.
- **Hold to perfect:** pause at the end of a stroke and it snaps into a clean shape: a line (with 0°, 30°, 45°, 60° and 90° snapping), arrow, circle, ellipse, triangle, rectangle, polygon or star. Keep moving without lifting to resize it (a line follows the pen tip).
- **My shapes:** lasso any drawing and tap *Save as shape*. Holding the pen at the end of a similar one-stroke drawing then snaps to it. Matching uses a point-cloud recognizer, `geometry/ShapeMatcher.kt`.
- Engineering shapes: pin, roller and fixed supports, point and distributed loads, moment and dimension lines. A dimension line is a line like the others (end dots, angle snapping, its length shown), with arrows and end marks. A distributed load's end dots lengthen it (arrows are added or taken away to keep their spacing) and its middle dot sets the arrows' length.
- **Shapes tool style:** the Shapes panel has two tabs, *Choose* (the shapes) and *Style*: the tool's own outline colour, thickness, opacity, line style, fill and fill opacity, apart from the pen she writes with.
- **Snapping:** shapes, pictures and text in view lock onto each other when moved, resized or drawn close: a line's end onto another line or corner (keeping its angle) holds firmly, with a small cross; edges and middles lining up hold more softly, with dashed guides. Pull something out of a lock and that lock lets go until you move well away, so things can sit close without touching. Handwriting picked up with the lasso lines up its middle with shapes; nothing snaps to handwriting (*Snap to other shapes* in the Style tab).
- Small shapes get smaller dots (none when tiny), and the middle of a shape always moves it. A selection has a resize chip at its bottom-right and a move chip at its bottom-left (handy for shapes that are mostly lines and dots). A tap with the Shapes tool draws nothing; slide to draw.
- Tap a shape (with a finger, a quick pen tap on its outline, or any tap with the Shapes tool) to get its dots and its menu; drag them with either, and the shape changes live. Dragging a line's end stretches it without changing its thickness, and shows the angle and printed length beside the line (not under the pen). Dragging a shape's corner dot resizes it from the opposite side and keeps its shape (rectangles stay rectangles, circles stay circles).
- **Triangle:** one triangle whose corners each move on their own; a dragged corner moves a degree at a time and locks on to right angles, isosceles and the usual angles (30°, 45°, 60°… and lightly every 5°), showing each angle.
- A shape stays selected right after it's drawn or adjusted, so it can be changed straight away (the pen still writes inside it).
- **Part circle:** a closed slice of a circle, drawn from the centre outwards (the curve faces the way you drag). It starts as a half; its end dots make it any percent (snapping to quarters and 5% steps) and show "% · °" at the centre. Arc ends can be dragged round either way; going past a full turn starts again from nothing (361° is 1°), and the arc shows its angle.

- Every one-of-many choice (segmented switches, tools, pen types, colours, papers, the library sidebar) has one highlight that glides and stretches to the new choice.

**Typing**
- The keyboard button in the toolbar starts a text block where the next line would go on a normal page: at the left margin, below what's already written (near the top of an empty page). A small bar above it sets the size and colour, deletes it, or finishes; tapping elsewhere or Back also finishes.
- Tap typed text to select it, then move it, resize it from the corner (the text grows with it), recolour it, or tap *Edit text* to type in it again. It exports to PDF as real, sharp text.

**Editing**
- The eraser can remove whole strokes or erase precisely. Filters choose which pen types the eraser and the lasso affect. On a fill, the stroke eraser takes the whole colour and the precise eraser rubs out just the part it passes over.
- **Fill:** tap inside a shape to fill it with colour, or inside an area you drew around to fill it behind your writing (small gaps in the outline are bridged). It has its own colour, opacity, and "None" to take a fill away.
- The lasso picks up anything it touches (circling the tip of a line takes the whole line). Pictures and writing can be brought to the front or sent to the back.
- **Screenshot lasso** (a switch in the lasso panel): draw around anything and the rectangle around it becomes a picture to save to the gallery, share or copy (Copy also lets you hold on a page to paste it there).
- The lasso (or *Free form*, which picks whatever the pen traces over) can move, resize and rotate a selection (with live angle and 15° steps). Its menu shows Copy, Cut, Duplicate and Delete; ⋯ adds more buttons in the same row: colour and thickness (each opens its own drop-down; thickness also has the line style), to front, to back, mirror left–right or upside down, lock, edit text, save as shape.
- **Paste:** after copying or cutting, hold the pen or a finger still on the page and tap *Paste*; it lands where you held, in this notebook or any other.
- A fill and the outline it was made in act as one item (move, copy, mirror, lock); only the eraser treats them apart.
- **Lock:** the selection menu's lock keeps things in place; the lasso, taps and the eraser leave them alone. Hold a locked item with a finger to unlock it, or use *Unlock all* in the ⋯ menu.
- Deleting a page asks first.
- Undo and redo work with the buttons, or with *Smart undo and redo* (⋯ menu): a two-finger tap undoes, a three-finger tap redoes.
- The full-screen button beside undo hides the toolbars, leaving the page and a small frosted button to bring them back. A puff of mist plays where the bars were; when a notebook opens, the bars just slide in.
- *Hide navigation bar* (⋯ menu) hides Android's navigation bar in every notebook; swipe up from the bottom to see it for a moment.
- Scrolling glides on after a flick.

**Pages and paper**
- New notebooks are A4 pages, one under the other, each with its number in the corner. To add one, pull up from the end of the last page: a small sign says "Drag for a new page", then "Release to add a page"; letting go early springs back. A fast scroll just stops at the end.
- *Pages* (⋯ menu, or tap the page number) opens an overview of every page. Tapping a page lifts it up large, with *Delete page* (undo brings the writing back) and *Go to page*; the last tile adds a page.
- *Endless canvas* in the ⋯ menu switches a notebook between A4 pages and one endless page.
- New notebooks pick their paper, A4 pages or endless canvas, portrait or landscape pages, dark paper and cover colour up front.
- Paper styles: blank, dots, grid, lines, engineering (2 mm / 10 mm), isometric, Cornell notes and ruled exercise-book pages, in quiet grey on white. *Line thickness* under the papers (Thin, Standard, Bold, Bolder) makes every paper's lines, grid and dots thinner or bolder (PDF export too). The paper menu shows Blank, Lines and Grid, with *More* for the rest and *Your own*.
- *Your own* paper: design a pattern (lines, grid or dots, spacing, line and paper colours, an exercise-book margin line, and how strongly the lines show), or use a picture such as a planner page, which fills each A4 page (with its own opacity). Her papers are saved in settings and offered in every notebook (hold one to remove it).
- *Dark paper* (⋯ menu, per notebook) shows a black page. Ink colours flip to light versions on screen, but the real colours are kept, so switching back restores them. It's separate from the tablet's dark mode, which only changes the toolbars to soft lilac.
- Pictures can come from the gallery or the camera.
- The library's + adds a notebook, a file (PDFs and pictures), a scan (Google's document scanner: edges found, cropped, straightened), a photo from the camera or photos from the gallery. A PDF, picture or scan becomes an item of its own, shown in the library as what it is (a badge and a folded corner instead of a notebook spine), and opens just for looking, with a Write button that brings the toolbars to write on it.
- PDF import renders lecture slides as pages to write on, with a page chip to jump between them. In a paged notebook each slide gets an A4 page of its own.
- On an endless canvas, optional A4 page guides show where pages will break.
- PDF export offers three layouts: the notebook's pages (or A4 pages cut from an endless canvas), one long page, or the imported PDF's own pages. The dialog offers *Best* (pictures at full resolution), *Balanced* (150 dpi), *Light* (75 dpi) or *One long page*, and shows the real file size of the choice: the PDF is made in the background and that same file is shared or saved. Writing always stays sharp lines.

**Library**
- Folders, folders inside folders (a tree in the sidebar, folder tiles and a path back up in the main area), each with its own colour (chosen when it's made; the selected folder's highlight takes that colour), search, cover colours, and a *Recently deleted* area that keeps notebooks for 30 days. New folders arrive, and deleted notebooks, folders and pages go, on a puff of mist that dissipates. Tapping a cloud in the sky bursts it into the same mist; it forms again a few seconds later.
- Backups: *Back up by itself* into one file she picks once (her Google Drive, OneDrive, Dropbox or the tablet), kept up to date in the background; a backup file with merge-restore; Android's own backup; and optional Google Drive backup. See [BACKUP_SETUP.md](BACKUP_SETUP.md).
- From other apps: a PDF opened with or shared to Tali's Notes opens in a new notebook (landscape for slides) just for looking, with a Save button to keep it and write on it (or, with *Add them to my notebooks* in Settings, straight into her notebooks); pictures shared to it ask which notebook they go in (or *On its own*, a Picture item); her backup file opened with the app offers to restore; pictures, PDFs and text dragged over from an app side by side land where they're dropped.

## Building

Requirements:
- Android Studio (it provides the JDK).
- SDK 37.

```
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release APK, signed with keystore/ (see BACKUP_SETUP.md)
./gradlew testDebugUnitTest  # geometry, shapes, lasso, fill, storage and Robolectric UI/animation tests
./gradlew connectedDebugAndroidTest   # touch tests on a device or emulator
```

## Code map

| Folder | What's there |
| --- | --- |
| `canvas/` | `NoteCanvasView` (input, tools, selection, fill), `TileCache` (GPU display-list tiles), `Camera`, `Document` (items and undo), `PaperRenderer`, `ItemRenderer`, `Pages` (portrait/landscape A4 layout), `AreaFill` (flood fill of hand-drawn areas) |
| `ink/` | Brushes and helpers that build strokes from geometry |
| `geometry/` | Points and affine maths, `ShapeRecognizer` (hold-to-shape), `ShapeMatcher` (her own shapes), `Lasso` (what a loop touches), `Snap` (shapes locking onto each other) |
| `model/` | Canvas items, shape geometry, the engineering shapes and `CustomShape` |
| `data/` | Plain SQLite (`NotesDb`), `NotesRepository` (single writer thread, trash, merge-restore, PDF import), `ImageStore` (lazy, memory-capped pictures), settings |
| `backup/` | `BackupArchive` (the .zip), `AutoBackup` (into a file she picked, WorkManager) and `DriveBackup` (Drive REST and WorkManager; switched off until set up) |
| `export/` | PDF export and thumbnails |
| `ui/` | Compose screens: library, notebook, panels, dialogs, and the cloud components (`Clouds.kt` with bursting sky clouds, `CloudSurface.kt`, `CloudPuff.kt` for the mist things come and go on, `SlidingSelection.kt` for the gliding highlight) |
