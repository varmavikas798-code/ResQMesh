# ResQNet – Android (Jetpack Compose)

Open this `android/` folder in **Android Studio** (Koala or newer, JDK 17), let Gradle sync, then press **Run**.

## Flow
1. **Splash** – logo, "ResQNet" wordmark, tagline, helping-hand illustration, "Initializing…" loader (2.8 s)
2. **Register** – full name, Gmail, mobile (+91), password, medical qualification → *Send OTP & Register*
3. **OTP** – demo mode shows the generated code on screen (no SMS backend)
4. **Login** – email + password (for returning users)
5. **Dashboard** – bottom nav: Home (SOS, connection status) · Responders · History · Profile (logout)

## Branding
- The logo lives in `res/drawable/ic_logo.xml` (colour) and `ic_logo_mark.xml` (white mark for red tiles).
- It is used on the splash, the auth screens, the dashboard header and the launcher icon.
- Theme colours (white + red) are in `ui/theme/Theme.kt`.
- Splash artwork: `res/drawable-nodpi/splash_illustration.jpg` – replace with your own image of the same name if you want the exact artwork.

## Notes
- Min SDK 26, target SDK 34, Kotlin 1.9.24, Compose BOM 2024.09.00.
- User data is stored locally with SharedPreferences (`data/SessionStore.kt`). Swap it for a real backend/OTP provider when ready.
