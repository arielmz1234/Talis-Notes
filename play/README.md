# Google Play: everything to paste in

Files to upload are in `store/`:

| What | File | Where in the Play Console |
| --- | --- | --- |
| App bundle | `store/Talis-Notes-1.0.8.aab` (version 1.0.8, code 18; each upload needs a code higher than any uploaded before) | Test and release › Closed testing › Alpha › Create new release › Upload |
| App icon (512 × 512) | `store/icon-512.png` | Grow › Store listing › App icon |
| Feature graphic (1024 × 500) | `store/feature-graphic.png` | Store listing › Feature graphic |
| Phone screenshots | `store/phone/1-library.png`, `2-notes.png` | Store listing › Phone screenshots |
| Tablet screenshots (7" and 10") | `store/tablet/1-notes.png` … `6-dark.png` | Store listing › 7-inch and 10-inch tablet screenshots (same files for both) |

The bundle isn't in git (it's rebuilt with `./gradlew bundleRelease`); it's signed with the release key (SHA-1 `B9:89:A0:5A:…:CE:08`).

---

## App signing (when uploading the first bundle)

Play asks how to sign the app. Choose **"Use a different key"** › **"Export and upload a key from Java keystore"** (we already have one, and Tali's sideloaded app plus Drive sign-in depend on it). Play shows a command using its `pepk` tool; run it on this PC against `keystore/talis-release.jks` (alias and passwords are in `keystore/keystore.properties`) and upload the `.zip` it makes. Ask Claude to run it for you.

---

## Store listing (English)

**App name** (30 max)
> Tali's Notes

**Short description** (80 max)
> Handwritten notes for students: pens, PDFs, shapes, voice memos and backups.

**Full description** (4000 max)
> Tali's Notes is a handwriting notes app made for tablets and the pen. It started as an app for a civil engineering student, so it's built around how students really take notes: lecture slides, problem sheets, sketches and formulas.
>
> WRITE NATURALLY
> • Smooth, low-latency ink for the S Pen and other styluses
> • Fountain pen, ballpoint, pencil, marker and highlighter, with your own colours and three thicknesses per pen
> • Dotted and dashed lines, a laser pointer, and an eraser that rubs out just what you touch
> • A4 pages one under another, or an endless canvas
>
> PERFECT SHAPES
> • Draw a shape, hold the pen still, and it snaps clean: lines, circles, rectangles, triangles, arrows, curves
> • Shapes for your subject: supports, loads and trusses for engineering, circuits, graphs, diagrams and more
> • Shapes line up with each other as you move them
>
> PDFS AND PICTURES
> • Open lecture slides and PDFs and write right on them; text stays sharp at any zoom
> • Add photos from the camera or your gallery
> • Export any notebook as a PDF
>
> ORGANISED
> • Folders inside folders, coloured covers, papers for every subject (lined, grid, dots, engineering, Cornell…)
> • Lasso to move, resize, copy and recolour; lock things in place
> • Voice memos beside the page, typed text, page overview with drag to reorder
>
> SAFE
> • Everything stays on your tablet. Back up automatically to a file you choose, or to your own Google Drive, and restore on a new device
> • No ads, no account, no tracking
>
> In English, Hebrew, Arabic, Spanish, French and Portuguese.

**App category:** Productivity  ·  **Tags:** Note taking, Education
**Contact email:** your choice (shown publicly)  ·  **Website:** https://talisnotes.github.io/
**Privacy policy:** https://talisnotes.github.io/privacy.html

---

## App content (Policy › App content)

**Privacy policy:** `https://talisnotes.github.io/privacy.html`

**App access:** All functionality is available without special access (no login).

**Ads:** No, my app does not contain ads.

**Content rating questionnaire** (IARC)
- Category: **Utility, Productivity, Communication or Other**
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Does the app let users interact or exchange content with other users? **No**
- Does the app share the user's location? **No**
- Does the app allow purchases of digital goods? **No**
- → Expected rating: Everyone / PEGI 3

**Target audience:** 13 and over (choose 13–15, 16–17 and 18+). Not designed for children; the app doesn't appeal to under-13s specifically.

**News app:** No.  **Government app:** No.  **Financial features:** None.  **Health:** None.

**Data safety**
- Does your app collect or share any of the required user data types? **No.**
  Why this is accurate: notes, pictures, recordings and settings stay on the device. Drive backup sends the user's own notebooks to **the user's own Google Drive**, at their request, under their account, and the developer never receives them; Play counts user-initiated transfers to the user's own storage as not collected. Crash reports are sent only if the user taps *Send report*, as an email they see and send themselves.
- Is all data encrypted in transit? **Yes** (Google Drive uses HTTPS).
- Can users request data deletion? Data is only on the device and in their own Drive; uninstalling or deleting removes it.

**Permissions to explain if asked:** Camera (taking a photo for a notebook, only while the camera screen is open), Microphone (voice memos), Internet (Drive backup only).

---

## Closed test

1. Testing › Closed testing › Create track (or use "Alpha").
2. Testers: create an email list and add the testers' Gmail addresses (at least 12).
3. Create release › upload `Talis-Notes-1.0.aab` › release name "1.0" › notes: "First test version."
4. Countries: all (or Israel only).
5. Save › Review release › Start rollout to Closed testing.
6. Copy the **opt-in link** and send it to testers. They open it, tap "Become a tester", then install from Play.
7. Keep 12+ testers opted in for **14 days in a row**, then apply for production (Dashboard › Apply for production).

**Devices:** during the test leave phones allowed. For the public launch, if you want tablets only: Release › Device catalog › exclude phones (e.g. screen smallest width under 600 dp).
