<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Bird Time app icon" width="128" />
</p>

<h1 align="center">Bird Time</h1>

An Android companion app to help you get **Perfect Bird Time** when you feed the Pale Raven in **Fire Emblem: Fortune's Weave** and raise support with the protagonist, **Eshmel**.

Point your phone at the game’s question, tap to scan, and Bird Time looks up the corresponding answer in its bundled collection of 496 questions.

## How to use

1. Open the app and allow camera access.
2. Fit the game screen inside the white guide and the full question inside the green box.
3. Tap **Scan question** while holding the phone steady.
4. Read the suggested answer and compare the question reference with the game screen.
5. Tap **Scan again** for the next question or to retry an unclear scan.

Weaker matches are labeled **Possible answer** with a comparison warning. The displayed text similarity measures how closely the scanned text matches the stored question; it is not a probability that the answer is correct. If recognition struggles, adjust the angle or lighting and scan again.

## Features and privacy

- On-demand scanning with CameraX and bundled ML Kit Latin text recognition.
- Local question matching that tolerates some OCR errors.
- Portrait and landscape layouts.
- On-device processing: no networking, analytics, or cloud OCR.
- A 15-second scan timeout and lifecycle cleanup to prevent stale results.

Requires Android 7.0 (API 24) or newer and a camera. Recognition quality depends on framing, text clarity, lighting, and glare; target-phone calibration is still needed before treating OCR accuracy as established.

## Build and test

Open the repository in Android Studio and configure your local Android SDK. The project targets SDK 37, and its Gradle daemon criteria request JDK 25. Keep local SDK paths in untracked `local.properties`.

From the repository root on Windows (PowerShell):

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```sh
./gradlew test
./gradlew assembleDebug
```

If needed, run `chmod +x gradlew` first. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Repository layout

- `app/src/main/java/com/andhiewong/birdtime/MainActivity.kt` — camera permissions, preview, scanning, and result UI.
- `app/src/main/java/com/andhiewong/birdtime/QuestionMatcher.kt` — question models, text normalization, and edit-distance matching.
- `app/src/main/assets/questions.json` — bundled questions and answers.
- `app/src/test/java/com/andhiewong/birdtime/` — matching and packaged-data tests.

The app uses Kotlin, Jetpack Compose Material 3, CameraX, ML Kit, and Kotlin serialization. Edit `questions.json` directly to correct data, preserve UTF-8, and run the unit tests afterward. An intentional change to the record count also requires updating the count checks in `MainActivity.kt` and `QuestionDataTest.kt`.
