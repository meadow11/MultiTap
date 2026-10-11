# MultiTap: Floating Rapid Touch Multiplier (Android)

MultiTap is a lightweight, high-performance Android touch multiplier that operates via Android's native `AccessibilityService`. It displays a movable overlay directly over other applications and generates randomized high-frequency automated taps within the button's boundary while held down.

---

## Key Technical Decisions & Architecture

1. **Overlay Mechanism (`TYPE_ACCESSIBILITY_OVERLAY`)**:
   - MultiTap creates its floating window using `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
   - **No special "Display over other apps" (SYSTEM_ALERT_WINDOW) permission is required.** The accessibility service is granted overlay rights natively by Android.

2. **Pass-Through Touch Solution (`FLAG_NOT_TOUCHABLE`)**:
   - Problem: Injected taps would normally collide with the overlay view itself instead of reaching the underlying game or app.
   - Solution: Before dispatching each 15ms `dispatchGesture` stroke, `FLAG_NOT_TOUCHABLE` is temporarily applied to the overlay window layout (`updateViewLayout`). Upon completion or cancellation callback, the flag is cleared.

3. **Stop-Detection Guard (`deviceId` Tracking)**:
   - Problem: Injected taps could trigger unexpected `ACTION_UP` or `ACTION_CANCEL` events that cause the hold loop to prematurely terminate or lock up.
   - Solution: The `MotionEvent.deviceId` from the initial physical finger `ACTION_DOWN` is recorded. The loop strictly ignores any termination event that does not match this physical `deviceId`.

4. **Randomized Boundary Clamping**:
   - Each tap calculates the exact on-screen coordinates of the `HOLD` view using `getLocationOnScreen()`.
   - Coordinates are jittered randomly on every tick with an inset padding (10%) to guarantee every stroke lands cleanly inside the button's visual boundary.

5. **Pure Framework Architecture**:
   - Built purely with the core Android SDK (`android.*`). Zero external Jetpack, AndroidX, or Compose dependencies, guaranteeing minimal binary size and instant startup.

---

## Project Structure & File Index

| File Path | Description |
|---|---|
| `settings.gradle.kts` | Root Gradle settings defining repositories and `:app` module |
| `build.gradle.kts` | Root build script with Android Gradle Plugin 8.5.2 & Kotlin 1.9.24 |
| `gradle.properties` | Build optimization properties and Android framework flags |
| `app/build.gradle.kts` | Module build config targeting compileSdk 34 and minSdk 26 |
| `app/src/main/AndroidManifest.xml` | Declares `MainActivity` and `TapAccessibilityService` with BIND permission |
| `app/src/main/res/xml/accessibility_service_config.xml` | Configures `canPerformGestures="true"` with content retrieval disabled |
| `app/src/main/res/values/strings.xml` | Application title and accessibility service description |
| `app/src/main/java/com/example/multitap/TapAccessibilityService.kt` | Accessibility service managing floating overlay and gesture loop |
| `app/src/main/java/com/example/multitap/MainActivity.kt` | Programmatic UI for service status, delay SeekBar (20–500ms), and tips |
| `.github/workflows/build-apk.yml` | Automated GitHub Actions CI workflow to compile and upload `app-debug.apk` |

---

## Phone-Only Build & Installation Guide (No PC Required)

Since you only have an Android phone, you can build the signed APK in the cloud completely for free using GitHub Actions in under 2 minutes:

### Step 1: Create a GitHub Repository on Mobile
1. Open [github.com](https://github.com) on your mobile browser (Chrome/Brave/Firefox).
2. Tap the `+` menu at the top right and select **New repository**.
3. Name it `MultiTap` and make it **Public** (free GitHub Actions runners).
4. Tap **Create repository**.

### Step 2: Upload or Add Files
- Option A: In the GitHub web UI, tap **Add file -> Create new file**, enter the relative path (e.g., `app/build.gradle.kts`), copy-paste the file contents from this project, and commit.
- Option B: Use the companion web app's **"Download Project ZIP"** button, then upload the repository or push using a mobile Git client like **MGit** or **Termux**.

### Step 3: Run the Cloud Build
1. In your GitHub repo, tap the **Actions** tab.
2. You will see the **Build MultiTap Debug APK** workflow running automatically (or tap **Run workflow**).
3. The runner will set up Java 17, Android SDK 34, Gradle 8.7, and execute `gradle assembleDebug`.
4. In ~60 seconds, tap the finished run, scroll down to **Artifacts**, and tap **app-debug** to download `app-debug.zip`.
5. Extract the ZIP to get `app-debug.apk` and tap it to install!

---

## Android 13, 14 & 15 "Restricted Settings" Bypass

When sideloading an APK outside Google Play that requests accessibility access on modern Android versions, Android protects users with a security guard:

1. When you open **Accessibility** and tap **MultiTap**, the toggle may be grayed out with a message: *"Restricted setting: For your security, this setting is currently unavailable."*
2. To allow it:
   - Go to your phone's **Settings -> Apps -> MultiTap**.
   - Tap the **three dots (⋮)** in the top right corner.
   - Tap **Allow restricted settings**.
   - Confirm with your device PIN, pattern, or fingerprint.
   - Return to **Settings -> Accessibility -> MultiTap** and switch it **ON**.

---

## Limitations & Honest Testing Disclosure

1. **Protected Apps & Security Flags**:
   - Certain banking apps, secure enterprise applications, and games utilizing anti-cheat engines (such as Hoyoverse, Tencent ACE, EasyAntiCheat mobile, or Knox-enforced apps) monitor or block synthetic accessibility gestures.
   - Apps utilizing `FLAG_SECURE` or direct driver-level touch polling may ignore `dispatchGesture()`.

2. **Display Cutouts & Notches**:
   - `getLocationOnScreen()` accurately tracks window space, but when dragging over status bar cutout areas on certain OEM skins (MIUI/HyperOS, ColorOS), coordinate offsets can shift slightly depending on full-screen immersion mode.

3. **Maximum Gesture Dispatch Rate**:
   - Android's framework limits `dispatchGesture` execution frequency. While the slider allows 20ms intervals, standard Android OS throttling typically tops out at ~25 to 40 synthetic gestures per second to prevent event queue starvation.
