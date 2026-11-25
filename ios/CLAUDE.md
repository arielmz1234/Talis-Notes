# Tali's Notes for iPad and iPhone: notes for Claude

This folder is the Apple version of Tali's Notes (Swift, SwiftUI, PencilKit). The Android app it
follows is the rest of this repository (the repo root, `../`). Read [`../CLAUDE.md`](../CLAUDE.md)
first: its top section says how the two apps share the repo, and its owner's preferences apply here
too.

## Ground rules

- **Work only in `ios/`** unless the owner asks you to change the Android app as well. Read the
  Android code as much as you like (it's the reference for behaviour and numbers): it's right here,
  at `../app/src/main/java/com/tali/notes/`.
- **Never change what a basic tap, hold or touch does without the owner asking.**
- Keep [`PROGRESS.md`](PROGRESS.md) up to date: a dated entry at the top for each piece of work
  (what changed, how it was checked, what wasn't). [`PLAN.md`](PLAN.md) has the milestones,
  [`BRIEF.md`](BRIEF.md) the original brief, [`OWNER_TODO.md`](OWNER_TODO.md) what only the owner can do.

## Building and testing

- **On a Mac:** `brew install xcodegen && xcodegen generate` (makes `TalisNotes.xcodeproj`, never
  hand-edited, not in git), `swift test --package-path Packages/TaliCore` for the core tests, then
  run on a simulator or a USB iPad from Xcode. See the top of `PROGRESS.md`.
- **On Windows** there is no Swift: every compile and test happens in CI. Push, then read the run.
  Without the GitHub CLI, the run's status can be read from the public API (unauthenticated, 60
  requests an hour: poll every few minutes, not every few seconds), e.g.
  `https://api.github.com/repos/arielmz1234/Talis-Notes/actions/runs?branch=<branch>&per_page=3`.
  Downloads (the `.ipa`, screenshots) need a sign-in: give the owner the artifact's link.
- **CI** is `../.github/workflows/ios.yml`, workflow *iOS*. It runs only when `ios/` changes.
  - **Quick** (every push to `master` and every pull request, about 15 min): core tests, a build of
    the app and of every UI test, the iPad screenshot set (`ScreenshotTests`), and the unsigned
    `.ipa` for sideloading.
  - **Full** (only when asked; about 45 min across six Mac jobs): every UI test class on iPad,
    iPhone, 11-inch iPad, dark and Hebrew. *Actions → iOS → Run workflow* with *full* ticked, or
    a `full-ci` label on the pull request. Ask for it before a risky merge; the owner tries the
    rest on a real iPad.
  - A failing UI test doesn't fail its job (the screenshots are what those jobs are for): search
    the job's log for `failed`. Core tests and compile errors do fail the run.
- `tools/import_strings.py`, `tools/import_palettes.py` and `tools/make_icon.py` read the Android
  app's strings, palettes and icon straight from `../` by default.
