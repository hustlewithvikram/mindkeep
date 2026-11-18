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
Open the project in Android Studio with JDK 17 and Android SDK 37 installed, sync Gradle, then run the app configuration.

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
This scaffold establishes the Android app, Gradle configuration, Material 3 theme, navigation entry point and feature boundaries. Database schemas, AI inference, capture services and reminder scheduling are not implemented yet.
