# BrewTap Direct xBloom BLE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build BrewTap v1.1 as a premium Android app that sends the selected recipe directly to xBloom Studio/J15 over BLE, loading Auto/Easy Mode slot A so the user can press Start on the machine.

**Architecture:** Keep recipe encoding pure Kotlin and isolate Android BLE lifecycle in a client class. MainActivity owns only navigation/UI, while SendCoordinator maps BLE callbacks into user-facing states. Legacy NFC-card writing stays in source but is removed from the primary flow.

**Tech Stack:** Android SDK 35, Kotlin/JVM 17, platform BluetoothGatt/BluetoothLeScanner APIs, JUnit 4, GitHub Actions/Gradle 8.11.1.

## Global Constraints
- minSdk 26, targetSdk 35.
- Recipe transfer must not auto-start brewing.
- BLE primary transport: xBloom advertiser names start with `XBLOOM`.
- Write characteristic: `0000FFE1-0000-1000-8000-00805F9B34FB`.
- Notify characteristic: `0000FFE2-0000-1000-8000-00805F9B34FB`.
- CRC16 polynomial `0x8408`, initial value 0.
- v1.1 loads Easy Mode slot A (index 0) and switches to Easy Mode.
- CI must run tests before APK build.

---

### Task 1: Pure BLE protocol and regression tests

**Files:**
- Create: `xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/XBloomBleProtocolTest.kt`
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomBleProtocol.kt`

**Interfaces:**
- Produces: `XBloomBleProtocol.crc16`, `buildType1`, `buildType2`, `encodeRecipe`, `buildSlotA`, `handshakePacket`, `easyModePacket`.

- [ ] **Step 1: Write failing tests** for CRC16, known Easy Mode packet `580102F72C100000000191327856FF58`, and EDISON slot-A packet structure.
- [ ] **Step 2: Run `gradle testDebugUnitTest --stacktrace`** and confirm failure because `XBloomBleProtocol` does not exist.
- [ ] **Step 3: Implement minimal pure-Kotlin protocol** matching `brAzzi64/xbloom-ble`: type1/type2 framing, little-endian fields, recipe payload encoding, slot flags and slot-A packet.
- [ ] **Step 4: Re-run tests** and confirm green.

### Task 2: Android BLE client

**Files:**
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomBleClient.kt`
- Modify: `xbloom-brewtap/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `XBloomBleClient.sendRecipe(recipe: BrewRecipe, callback: Callback)`.
- Callback states: searching, connecting, syncing, loaded, error(message).

- [ ] **Step 1: Add Android BLE permissions**: BLUETOOTH, BLUETOOTH_ADMIN for legacy; BLUETOOTH_SCAN and BLUETOOTH_CONNECT with runtime permission on Android 12+; ACCESS_FINE_LOCATION maxSdkVersion=30.
- [ ] **Step 2: Implement scanner filter by device name prefix `XBLOOM`**, stop after first match, connectGatt, discover services.
- [ ] **Step 3: Locate FFE1/FFE2, enable notification descriptor when present, send handshake packet, then slot-A recipe packet, then Easy Mode packet using characteristic writes in sequence.
- [ ] **Step 4: Do not send Execute Recipe command.** Disconnect after successful sync or short confirmation delay.
- [ ] **Step 5: Map common BLE failures to concise messages including conflict with the official xBloom app.

### Task 3: Premium navigation and SEND sheet

**Files:**
- Replace: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/MainActivity.kt`
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/SendCoordinator.kt`

**Interfaces:**
- `SendCoordinator.State`: READY, SEARCHING, CONNECTING, SYNCING, LOADED, ERROR.

- [ ] **Step 1: Build premium warm-ivory shell** with HOME / RECIPES / SEND / SETTINGS bottom navigation.
- [ ] **Step 2: HOME** shows EDISON card and `SEND TO xBLOOM` CTA.
- [ ] **Step 3: RECIPES** shows selected EDISON preset and supports Use action.
- [ ] **Step 4: SEND** opens rounded modal/bottom-sheet-style overlay and immediately starts BLE after permissions.
- [ ] **Step 5: LOADED state** clearly says `Recipe loaded to Auto A` and `Press Start on your xBloom to brew.`
- [ ] **Step 6: SETTINGS** shows version, Bluetooth readiness and a Check for updates action placeholder wired to UpdateManager in Task 4.

### Task 4: Update manager

**Files:**
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/UpdateManager.kt`
- Create: `xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/UpdateManagerTest.kt`
- Modify: `xbloom-brewtap/app/src/main/AndroidManifest.xml`

**Interfaces:**
- `UpdateManager.isNewer(current: String, latest: String): Boolean`
- `UpdateManager.check(callback)` uses GitHub latest-release endpoint and exposes release URL/name.

- [ ] **Step 1: Write failing semantic-version tests** (`1.1.0 < 1.2.0`, equal false, prerelease normalized conservatively).
- [ ] **Step 2: Add INTERNET permission and implement simple HttpURLConnection latest-release lookup for `mafiaklp/MDconcluded`.
- [ ] **Step 3: Wire Settings `Check for updates`; on newer version open release page in browser.
- [ ] **Step 4: Keep all update failures non-blocking.

### Task 5: Versioning, CI, APK

**Files:**
- Modify: `xbloom-brewtap/app/build.gradle.kts`
- Modify: `.github/workflows/xbloom-brewtap-android.yml`

- [ ] **Step 1: Set `versionCode = 11`, `versionName = "1.1.0"`.
- [ ] **Step 2: Include `feature/brewtap-premium-v11` in CI push branches.
- [ ] **Step 3: Run full unit tests and debug APK build in GitHub Actions.
- [ ] **Step 4: Download artifact and provide APK for Samsung manual test.

## Acceptance
- App installs on Samsung Android.
- Four tabs are interactive.
- EDISON 18g / 270ml / 93°C / grind 55 @ 80 RPM is selected by default.
- SEND discovers an xBloom advertiser, connects, writes slot A recipe and switches Easy Mode without executing brew.
- Success screen instructs physical Start on xBloom.
- Tests and debug APK build pass in GitHub Actions.
