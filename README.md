# CART3R AVIATOR INTELLIGENCE — Live OCR companion (experimental)

This companion is a native Android prototype that uses Android's user-approved MediaProjection screen-capture flow and on-device ML Kit text recognition to look for multiplier-like text on the visible screen.

## Important truth about the feature

- The app cannot predict the next crash multiplier or identify a safe bet.
- OCR is best-effort. It can misread text, duplicate values, or mistake unrelated numbers for multipliers.
- The app does not connect to Spribe servers, call private endpoints, inspect browser storage, automate betting, click bet controls, or bypass platform protections.
- Android displays a screen-sharing consent prompt. The user must approve capture and can stop it from the app or Android notification controls.
- This prototype uses simple text-pattern matching. It is not a validated Aviator-specific result detector. Always verify captured values against the game display.
- Screen contents may include sensitive information. Do not capture messages, passwords, banking, or payment screens.

## Build

The easiest build route is the included GitHub Actions workflow. Upload this project to a GitHub repository, then open **Actions** and run **Build CART3R Android OCR APK**. Download the `CART3R-Live-OCR-debug` artifact.

Local build requirements: Android SDK (API 35), JDK 17+, and Gradle 8.9+.

## Run

1. Install the generated APK on your Android phone.
2. Open CART3R Live OCR.
3. Tap **Start screen monitoring** and approve Android's screen-capture prompt.
4. Switch to Chrome and keep the game results visible.
5. Return to CART3R to review captured candidates.
6. Stop monitoring when finished.

The Android OS may stop capture when the device locks, the app is force-stopped, or permission is revoked. The app must be started again and permission granted again.

## Project

- `android-companion/` — native Android app.
- `.github/workflows/build-android.yml` — GitHub Actions APK build.
- `web-dashboard/` — optional place to copy the earlier browser dashboard files.

## Privacy

Recognized text is processed on-device using bundled ML Kit. The app stores multiplier candidates locally in SharedPreferences. It does not upload screenshots or recognized text.
