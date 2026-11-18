# MindKeep

MindKeep is an offline-first personal assistant and second brain for Android. It is designed to capture information, organize it into useful knowledge, and help manage tasks and reminders while keeping user data on-device by default.

## Stack
- Kotlin, Jetpack Compose and Material 3
- Application ID / namespace: com.vikram.mindkeep
- Feature-oriented architecture with domain, data and core layers
- Room + DataStore for local persistence
- WorkManager for deferrable background work
- Navigation Compose
- On-device AI behind a replaceable runtime interface

## Build
Open the project in Android Studio with JDK 21 and Android SDK 36 installed, sync Gradle, then run the app configuration.

## Structure
    app/src/main/java/com/vikram/mindkeep/
    ├── core/          # ai, common, database, datastore, designsystem, notifications
    ├── data/          # Repository implementations
    ├── domain/        # Models, contracts and use cases
    ├── feature/       # capture, home, reminders, search, settings, tasks
    └── navigation/    # Navigation graph

## Principles
1. Offline-first: core functionality must not depend on network access.
2. Private by default: no analytics, cloud sync or remote AI is enabled.
3. Explicit capture: microphone and sensitive sensors are only used after clear user action and permission.
4. Replaceable AI: features depend on interfaces, not a specific model vendor.
5. Use WorkManager for deferrable work; keep exact-alarm behavior opt-in.

## Current status
The app includes Activity, Assistant and Settings tabs, a subpage back stack with slide transitions, local notes/tasks/reminders, an opt-in Android notification listener, a LiteRT-LM local model manager, direct model downloads, persistent conversation recall and a WorkManager queue for resumable assistant tasks. The current model catalogue includes Qwen 3 0.6B, Qwen 3 1.7B and Gemma 4 E2B. The Assistant screen loads the selected local model on demand; no cloud inference fallback is implemented. Google Keep, Google Calendar and MCP connections remain UI foundations and are not authenticated or operational yet.


## GitHub Actions builds

Two workflows are included:

- **Debug APK** — runs on pushes to `main`, signs the debug APK with the CI keystore, and replaces the single `debug-latest` prerelease containing the APK, SHA-256 checksum, recent commit changelog, full commit SHA, branch and workflow run.
- **Release APK** — manually started from Actions → Release APK → Run workflow. Enter a version; the workflow builds and signs the release APK and publishes a normal GitHub Release.

Both variants use the same upload keystore. Add these repository Actions secrets before the first workflow run:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The signing key must be backed up securely. Never commit the keystore or its passwords. The repository ignores `.jks`, `.keystore` and `.p12` files.


## Permissions

- **POST_NOTIFICATIONS** — requested only when the user enables local reminder notifications on Android 13+.
- **Notification access** — the user must explicitly enable this in Android settings and then turn on capture in Activity. Notification text is stored locally.
- **INTERNET** — used only to check app releases and download user-selected model/APK files. Inference and stored memory remain on-device.
- No contacts, location, camera or microphone permission is requested.
- Reminder work is scheduled locally through WorkManager; exact-alarm permission is intentionally not requested.
