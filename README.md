# Tali's Notes

A handwriting notes app for Android tablets with an S Pen (Galaxy Tab S6 Lite and newer), made for a civil-engineering student. It has an endless canvas per notebook, a lilac-and-sky look with cartoon clouds, and it runs entirely on the tablet.

## What it does

**Writing**
- Six pen types: fineliner, fountain pen, pencil, calligraphy, dashed and highlighter. She can keep up to 8 pens, and her saved colours are shared by all of them.
- Low-latency inking (androidx.ink), with pressure.
- The S Pen side button erases while held.
- **Hold to perfect:** pause at the end of a stroke and it snaps into a clean shape: a line (with 0°, 30°, 45°, 60° and 90° snapping), arrow, circle, ellipse, triangle, rectangle, polygon or star. Keep moving without lifting to resize it (a line follows the pen tip).
- **My shapes:** lasso any drawing and tap *Save as shape*. Holding the pen at the end of a similar one-stroke drawing then snaps to it. Matching uses a point-cloud recognizer, `geometry/ShapeMatcher.kt`.
- Engineering shapes: pin, roller and fixed supports, point and distributed loads, moment and dimension lines.
- Tap a shape (with a finger or the pen) to get its dots; drag them with either, and the shape changes live. Dragging a line's end stretches it without changing its thickness, and shows the angle and printed length. Dragging a shape's corner dot resizes it from the opposite side and keeps its shape (rectangles stay rectangles, circles stay circles).
- **Part circle:** a closed slice of a circle, drawn from the centre outwards (the curve faces the way you drag). It starts as a half; its end dots make it any percent (snapping to quarters and 5% steps) and show "% · °".

- Every one-of-many choice (segmented switches, tools, pen types, colours, papers, the library sidebar) has one highlight that glides and stretches to the new choice.

**Typing**
- The keyboard button in the toolbar starts a text block where the next line would go on a normal page: at the left margin, below what's already written (near the top of an empty page). A small bar above it sets the size and colour, deletes it, or finishes; tapping elsewhere or Back also finishes.
- Tap typed text to select it, then move it, resize it from the corner (the text grows with it), recolour it, or tap *Edit text* to type in it again. It exports to PDF as real, sharp text.

**Editing**
- The eraser can remove whole strokes or erase precisely. Filters choose which pen types the eraser and the lasso affect.
- **Fill:** tap inside a shape to fill it with colour, or inside an area you drew around to fill it behind your writing (small gaps in the outline are bridged). It has its own colour, opacity, and "None" to take a fill away.
- The lasso picks up anything it touches (circling the tip of a line takes the whole line). Pictures and writing can be brought to the front or sent to the back.
- The lasso can move, resize and rotate a selection (with live angle and 15° steps), recolour, duplicate, copy to another notebook, delete, or save as a shape.
- Undo and redo work with the buttons, or with *Smart undo and redo* (⋯ menu): a two-finger tap undoes, a three-finger tap redoes.
- The full-screen button beside undo hides the toolbars, leaving the page and a small frosted button to bring them back. A puff of mist plays where the bars were; when a notebook opens, the bars just slide in.
- Scrolling glides on after a flick.

**Pages and paper**
- New notebooks are A4 pages, one under the other, each with its number in the corner. To add one, pull up from the end of the last page: a small sign says "Drag for a new page", then "Release to add a page"; letting go early springs back. A fast scroll just stops at the end.
- *Pages* (⋯ menu, or tap the page number) opens an overview of every page. Tapping a page lifts it up large, with *Delete page* (undo brings the writing back) and *Go to page*; the last tile adds a page.
- *Endless canvas* in the ⋯ menu switches a notebook between A4 pages and one endless page.
- New notebooks pick their paper, A4 pages or endless canvas, portrait or landscape pages, dark paper and cover colour up front.
- Paper styles: blank, dots, grid, lines, engineering (2 mm / 10 mm), isometric, Cornell notes and ruled exercise-book pages, in quiet grey on white. The paper menu shows Blank, Lines and Grid, with *More* for the rest and *Your own*.
- *Your own* paper: design a pattern (lines, grid or dots, spacing, line and paper colours, an exercise-book margin line, and how strongly the lines show), or use a picture such as a planner page, which fills each A4 page (with its own opacity). Her papers are saved in settings and offered in every notebook (hold one to remove it).
- *Dark paper* (⋯ menu, per notebook) shows a black page. Ink colours flip to light versions on screen, but the real colours are kept, so switching back restores them. It's separate from the tablet's dark mode, which only changes the toolbars to soft lilac.
- Pictures can come from the gallery or the camera.
- PDF import renders lecture slides as pages to write on, with a page chip to jump between them. In a paged notebook each slide gets an A4 page of its own.
- On an endless canvas, optional A4 page guides show where pages will break.
- PDF export offers three layouts: the notebook's pages (or A4 pages cut from an endless canvas), one long page, or the imported PDF's own pages. The dialog offers *Best* (pictures at full resolution), *Balanced* (150 dpi), *Light* (75 dpi) or *One long page*, and shows the real file size of the choice: the PDF is made in the background and that same file is shared or saved. Writing always stays sharp lines.

**Library**
- Folders, folders inside folders (a tree in the sidebar, folder tiles and a path back up in the main area), each with its own colour (chosen when it's made; the selected folder's highlight takes that colour), search, cover colours, and a *Recently deleted* area that keeps notebooks for 30 days. New folders arrive, and deleted notebooks, folders and pages go, on a puff of mist that dissipates. Tapping a cloud in the sky bursts it into the same mist; it forms again a few seconds later.
- Backups: a backup file with merge-restore, Android's own backup, and optional automatic Google Drive backup. See [BACKUP_SETUP.md](BACKUP_SETUP.md).

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
| `geometry/` | Points and affine maths, `ShapeRecognizer` (hold-to-shape), `ShapeMatcher` (her own shapes), `Lasso` (what a loop touches) |
| `model/` | Canvas items, shape geometry, the engineering shapes and `CustomShape` |
| `data/` | Plain SQLite (`NotesDb`), `NotesRepository` (single writer thread, trash, merge-restore, PDF import), `ImageStore` (lazy, memory-capped pictures), settings |
| `backup/` | `BackupArchive` (the .zip) and `DriveBackup` (Drive REST and WorkManager; switched off until set up) |
| `export/` | PDF export and thumbnails |
| `ui/` | Compose screens: library, notebook, panels, dialogs, and the cloud components (`Clouds.kt` with bursting sky clouds, `CloudSurface.kt`, `CloudPuff.kt` for the mist things come and go on, `SlidingSelection.kt` for the gliding highlight) |
