# Repository Guidelines

## Project Structure & Module Organization

Bird Time is a single-module Android app built with Kotlin, Jetpack Compose Material 3, CameraX, and bundled ML Kit Latin text recognition. It reads a game question on demand and matches it against local question data.

- `app/src/main/java/com/andhiewong/birdtime/MainActivity.kt`: camera permission, preview, screen guides, tap-to-scan OCR, lifecycle handling, and adaptive result UI.
- `app/src/main/java/com/andhiewong/birdtime/QuestionMatcher.kt`: serializable `Question`, `Match`, text normalization, and edit-distance matching.
- `app/src/main/assets/questions.json`: 496 records with `remark`, `answer`, and optional `character` fields.
- `app/src/test/java/com/andhiewong/birdtime/`: JUnit 4 matching and packaged-data tests.
- `app/src/main/AndroidManifest.xml`: camera requirement and explicit removal of dependency-provided network permissions.
- `gradle/libs.versions.toml`: dependency and plugin versions; `app/build.gradle.kts`: Android configuration.

The Android implementation must use `app/src/main/java/com/andhiewong/birdtime/` for Kotlin, `app/src/main/assets/questions.json` for packaged data, and `app/src/test/java/com/andhiewong/birdtime/` for unit tests.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper.

Windows (PowerShell):

```powershell
.\gradlew.bat test          # run JVM unit tests
.\gradlew.bat assembleDebug # build the debug APK
```

macOS/Linux (shell):

```sh
./gradlew test          # run JVM unit tests
./gradlew assembleDebug # build the debug APK
```

The checked-in configuration uses Gradle 9.6.0, AGP 9.5.1, Kotlin plugins 2.4.21, compile/target SDK 37, and minimum SDK 24. The Gradle daemon criteria request JDK 25; Java source/target compatibility is 17. Keep SDK locations in untracked `local.properties`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Edit `app/src/main/assets/questions.json` directly and run the unit tests after changing it. Both `MainActivity.kt` and `QuestionDataTest.kt` enforce exactly 496 unique remarks with nonblank remarks and answers; update both checks if an intentional data change alters the count. Preserve UTF-8 data and serialization compatibility.

## OCR & Matching Behavior

Scanning starts only on a button tap, consumes the first OCR result, and clears the analyzer. A 15-second timeout, request IDs, and lifecycle cleanup prevent stale results. Preserve these safeguards and the fixed analysis resolution setup before camera binding so scans do not reset zoom/focus.

OCR uses view-referenced coordinates and selects text blocks whose centers fall in `QUESTION_ZONE`, ordered top-to-bottom then left-to-right. Keep the drawn guide and selection region aligned; this is block filtering, not image cropping.

Matching applies Unicode NFKC normalization, locale-independent lowercase, and punctuation/whitespace normalization before normalized edit-distance scoring. Inputs shorter than 20 normalized characters and matches below 0.40 similarity are rejected. A strong match requires a score of at least 0.62 and a margin of at least 0.08 over the runner-up. Weak matches above the minimum remain visible as possible answers with a comparison warning; similarity is not a probability of correctness.

## Coding Style & Naming Conventions

Use standard Kotlin style with four-space indentation. Name classes and composables in `PascalCase`, functions and properties in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Keep camera UI in `MainActivity.kt` and pure matching logic in `QuestionMatcher.kt`. No formatter is configured; use Android Studio defaults and avoid unrelated reformatting.

## Testing Guidelines

Use JUnit tests named after behavior, such as `marksAnExactQuestionAsStrong`. Cover normalization, OCR errors, weak matches, confidence margins, short-input rejection, ties, and empty datasets. `QuestionDataTest` validates the actual packaged JSON. Run unit tests and a debug build before handoff. For camera/UI changes, also check permission denial, retry/timeout, backgrounding, rotation, zoom/focus preservation, and portrait/landscape layouts on a device. Before declaring OCR complete, test target-phone captures from the front, left, and right, in dim light, and with glare. Use these samples to calibrate the screen guide, question zone, and matching thresholds; passing JVM tests alone does not establish OCR calibration.

## Commit & Pull Request Guidelines

Use short imperative commits, for example `Add tap-to-scan OCR flow`. Keep commits focused. Pull requests must describe behavior changes, list validation commands, and include screenshots for camera or result-state changes. Link an issue when one exists.

## Security & Scope

Keep OCR and images on-device. Do not add networking, analytics, cloud OCR, OpenCV, or continuous scanning unless explicitly requested. Preserve the manifest's network-permission removals and bundled OCR model. Never commit local SDK paths, signing keys, or captured images containing personal information. If calibration photos are collected, create a local `camera-sample/` folder (not currently present); `.gitignore` already excludes it. Do not force-add captures.
