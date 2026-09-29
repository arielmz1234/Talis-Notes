# Tali's Notes

A handwriting notes app for Android tablets with an S Pen (Galaxy Tab S6 Lite and newer), made for a civil-engineering student. It has an endless canvas per notebook, a lilac-and-sky look with cartoon clouds, and it runs entirely on the tablet.

## What it does

**Writing**
- Six pen types: fineliner, fountain pen, pencil, calligraphy, dashed and highlighter. She can keep up to 8 pens, and her saved colours are shared by all of them.
- Low-latency inking (androidx.ink), with pressure.
- The S Pen side button erases while held.
- **Hold to perfect:** pause at the end of a stroke and it snaps into a clean shape: a line (with 0°, 30°, 45°, 60° and 90° snapping), arrow, circle, ellipse, triangle, rectangle, polygon or star.
- **My shapes:** lasso any drawing and tap *Save as shape*. Holding the pen at the end of a similar one-stroke drawing then snaps to it. Matching uses a point-cloud recognizer, `geometry/ShapeMatcher.kt`.
- Engineering shapes: pin, roller and fixed supports, point and distributed loads, moment and dimension lines.
- Tap a shape to get its dots. Dragging a line's end stretches it without changing its thickness, and shows the angle and printed length.

**Editing**
- The eraser can remove whole strokes or erase precisely. Filters choose which pen types the eraser and the lasso affect.
- The lasso can move, resize and rotate a selection (with live angle and 15° steps), recolour, duplicate, copy to another notebook, delete, or save as a shape.
- Undo and redo work with the buttons, a two-finger tap (undo) or a three-finger tap (redo).

**Pages and paper**
- Paper styles: blank, dots, grid, lines, engineering (2 mm / 10 mm) and isometric.
- Pictures can come from the gallery or the camera.
- PDF import renders lecture slides as pages to write on, with a page chip to jump between them.
- Optional A4 page guides show where pages will break.
- PDF export offers three layouts: A4 pages, one long page, or the imported PDF's own pages.

**Library**
- Folders, search, cover colours, and a *Recently deleted* area that keeps notebooks for 30 days.
- Backups: a backup file with merge-restore, Android's own backup, and optional automatic Google Drive backup. See [BACKUP_SETUP.md](BACKUP_SETUP.md).

## Building

Requirements:
- Android Studio (it provides the JDK).
- SDK 37.

```
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release APK, signed with keystore/ (see BACKUP_SETUP.md)
./gradlew testDebugUnitTest  # geometry, recognizer and shape-matcher tests
./gradlew connectedDebugAndroidTest   # touch tests on a device or emulator
```

## Code map

| Folder | What's there |
| --- | --- |
| `canvas/` | `NoteCanvasView` (input, tools, selection), `TileCache` (GPU display-list tiles), `Camera`, `Document` (items and undo), `PaperRenderer`, `ItemRenderer` |
| `ink/` | Brushes and helpers that build strokes from geometry |
| `geometry/` | Points and affine maths, `ShapeRecognizer` (hold-to-shape), `ShapeMatcher` (her own shapes) |
| `model/` | Canvas items, shape geometry, the engineering shapes and `CustomShape` |
| `data/` | Plain SQLite (`NotesDb`), `NotesRepository` (single writer thread, trash, merge-restore, PDF import), `ImageStore` (lazy, memory-capped pictures), settings |
| `backup/` | `BackupArchive` (the .zip) and `DriveBackup` (Drive REST and WorkManager; switched off until set up) |
| `export/` | PDF export and thumbnails |
| `ui/` | Compose screens: library, notebook, panels, dialogs, and the cloud components (`Clouds.kt`, `CloudSurface.kt`) |
