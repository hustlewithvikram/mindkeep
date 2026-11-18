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
Open the project in Android Studio with JDK 17 and Android SDK 36 installed, sync Gradle, then run the app configuration.

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
The app now includes a single-screen introduction, animated in-app splash, Material 3 Compose shell, Home, Ask (AI readiness), Search, Tasks, Reminders and Settings pages. Notes, tasks and reminders are persisted locally using app-private storage. Reminders are scheduled with WorkManager and delivered through a notification channel when notification permission is granted. No network permission or account is required. The on-device assistant contract is defined, but a model runtime and inference implementation are not connected yet. Voice capture, richer note editing/search indexing, export/import and database migration to Room remain future implementation areas.


## GitHub Actions builds

Two workflows are included:

- **Debug APK** — runs on every branch push, signs the debug APK with the CI keystore, and publishes a GitHub prerelease containing the APK, SHA-256 checksum, recent commit changelog, full commit SHA, branch and workflow run.
- **Release APK** — manually started from Actions → Release APK → Run workflow. Enter a version; the workflow builds and signs the release APK and publishes a normal GitHub Release.

Both variants use the same upload keystore. Add these repository Actions secrets before the first workflow run:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The signing key must be backed up securely. Never commit the keystore or its passwords. The repository ignores `.jks`, `.keystore` and `.p12` files.


## Permissions

- **POST_NOTIFICATIONS** — requested only when the user enables notifications or creates a reminder on Android 13+.
- No internet, contacts, location, camera or microphone permission is requested by the current foundation.
- Reminder work is scheduled locally through WorkManager; exact-alarm permission is intentionally not requested.
