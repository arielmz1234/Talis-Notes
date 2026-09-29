# Backups in Tali's Notes

Tali's notes are protected in three layers:

1. **On the tablet.** Every stroke is written to the app's database as soon as she draws it, and nothing needs internet.
2. **Backup file.** In the library, tap **Saved on this tablet** at the bottom of the sidebar, then **Save backup file**. This makes one `.zip` with every notebook, picture, pen, colour and saved shape.
   - To restore, use **Restore from file**. It merges the backup in and never deletes anything:
     - A notebook that is only in the backup is added.
     - A notebook that is in both places keeps its most recently edited version.
3. **Android's own backup.** This is free and already on. If Google backup is on in the tablet's settings (Settings > Accounts and backup > Back up data), Android saves the app's notebooks, pictures and settings to her Google account about once a day. It restores them automatically after a reinstall or on a new tablet. The limit is 25 MB, so pictures beyond that won't be included.

There is also a fourth, optional layer:

4. **Google Drive (automatic, optional).** The code is written and ready, but hidden until Google sign-in is set up for the app (below).
   - When it's on, the app uploads one file, `Tali's Notes backup.zip`, to a `Tali's Notes` folder in her Drive. It does this a couple of minutes after she stops writing, and at least once a day. It only uploads when something changed.
   - Drive keeps older versions of that file automatically. To see them, right-click the file and choose "Manage versions".
   - The app uses the `drive.file` permission, so it can only see the files it created, never anything else in her Drive.

## Turning on Google Drive backup (one time, about 10 minutes, free)

Google only lets an app use Drive once the app is registered. There's nothing to pay, and because `drive.file` is a "non-sensitive" permission, Google doesn't need to review anything.

1. Open <https://console.cloud.google.com/> with the Google account that should own the project (yours is fine) and create a project called **Tali's Notes**.
2. Go to **APIs & Services > Library**, search for **Google Drive API** and click **Enable**.
3. Go to **Google Auth Platform** (the OAuth consent screen):
   - **Branding:** app name *Tali's Notes*, plus your email as the support and developer contact.
   - **Audience:** choose *External*, then click **Publish app** to move it to *In production*.
     - If you leave it in *Testing*, Google signs her out every 7 days.
     - With only the `drive.file` scope there's no verification step. Google may show her a one-time "unverified app" notice; she taps *Advanced > Continue*.
   - **Data access:** add the scope `.../auth/drive.file`.
4. Go to **Clients > Create client**, choose **Android**, and fill in:
   - **Package name:** `com.tali.notes`
   - **SHA-1 certificate fingerprint:** `B9:89:A0:5A:05:74:6D:3D:AD:20:3C:F2:A0:74:51:BB:08:13:CE:08`. This is the release key in `keystore/`.
   - For debug builds from Android Studio, create a second Android client with the debug key's SHA-1: `CF:26:56:3F:68:65:8A:80:5B:0A:DB:7A:0B:1B:C4:64:DB:0F:1D:BC`.
5. In `app/build.gradle.kts`, change `buildConfigField("boolean", "DRIVE_BACKUP", "false")` to `"true"`, then build the release APK (see below).
6. On the tablet:
   1. Open **Backups**.
   2. Turn on **Back up automatically**.
   3. Pick her Google account and allow access.
   4. The first backup runs right away.

No client ID or secret goes into the code: Google recognises the app by its package name and signing key.

## The signing key (important)

The release APK is signed with `keystore/talis-release.jks`. Its passwords are in `keystore/keystore.properties`, and the folder is not in git.

**Back up the `keystore/` folder** somewhere safe, such as a USB stick or your own cloud storage. Android only installs an update over the existing app if it's signed with the same key. Without it, a new version would need an uninstall first, which erases the notes on the tablet.

## Building the release APK

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Copy it to the tablet and open it to install or update. Her notes stay in place when updating.
