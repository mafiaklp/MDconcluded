# BrewTap Direct xBloom BLE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build BrewTap v1.1 as a premium Android app where the user creates/selects a brew recipe, taps the phone on the xBloom as a proximity trigger, and BrewTap sends that exact recipe directly to the machine over BLE. The app does not use Auto/Easy Mode A/B/C slots and does not write xBloom recipe cards.

**Architecture:** NFC on the machine is used only as a foreground proximity trigger when detected. Recipe data is encoded in pure Kotlin and transmitted with the xBloom direct recipe BLE commands. MainActivity owns navigation/UI, SendCoordinator maps tap/BLE events into states, and XBloomBleClient handles Android BluetoothGatt lifecycle.

**Tech Stack:** Android SDK 35, Kotlin/JVM 17, platform NFC + BluetoothGatt/BluetoothLeScanner APIs, JUnit 4, GitHub Actions/Gradle 8.11.1.

## Global Constraints
- minSdk 26, targetSdk 35.
- No Auto/Easy Mode A/B/C preset storage.
- No recipe-card write in the primary flow.
- NFC tap is a trigger; recipe bytes travel by BLE.
- Recipe transfer must not send the BLE Execute Recipe command in v1.1; user starts from the xBloom itself if the firmware exposes that state.
- BLE advertiser names start with `XBLOOM`.
- Write characteristic: `0000FFE1-0000-1000-8000-00805F9B34FB`.
- Notify characteristic: `0000FFE2-0000-1000-8000-00805F9B34FB`.
- CRC16 polynomial `0x8408`, initial value 0.
- Direct recipe command: `8001` when grinder is enabled, `8004` when grinder is disabled.
- Before recipe send, transmit handshake 8100, dose/bypass 8102, and cup range 8104.
- Do not send execute command 8002.
- CI must run tests before APK build.

---

### Task 1: Pure direct-recipe BLE protocol and tests

**Files:**
- Create: `xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/XBloomBleProtocolTest.kt`
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomBleProtocol.kt`

**Interfaces:**
- Produces: `crc16`, `buildType1`, `buildType1Hex`, `encodeRecipe`, `buildHandshake`, `buildBypass`, `buildCupRange`, `buildDirectRecipePacket`.

- [ ] Write failing tests for CRC16, known handshake packet framing, EDISON recipe encoding, and command selection 8001/8004.
- [ ] Run `gradle testDebugUnitTest --stacktrace` and verify red.
- [ ] Implement little-endian packet framing, xBloom recipe blob encoding, dose/cup helper packets, and direct recipe packet.
- [ ] Re-run tests and verify green.

### Task 2: Android BLE client

**Files:**
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomBleClient.kt`
- Modify: `xbloom-brewtap/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `XBloomBleClient.sendDirectRecipe(recipe, callback)`.
- Callback states: searching, connecting, syncing, loaded, error(message).

- [ ] Add BLUETOOTH/SCAN/CONNECT runtime permissions and legacy location permission for Android <= 11.
- [ ] Scan for first device whose name starts with `XBLOOM`, connectGatt, discover services and characteristics.
- [ ] Send handshake 8100, back-to-home 8022, bypass+dose 8102, cup range 8104, then direct recipe packet 8001/8004 in sequence.
- [ ] Explicitly do not send command 8002.
- [ ] Treat successful characteristic write sequence as `LOADED`; keep connection briefly for ACK notifications, then disconnect.
- [ ] Map timeout/GATT/official-app-conflict failures into concise messages.

### Task 3: NFC proximity trigger + premium SEND experience

**Files:**
- Replace: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/MainActivity.kt`
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/SendCoordinator.kt`

**Interfaces:**
- `SendCoordinator.State`: READY_TO_TAP, TAG_DETECTED, SEARCHING, CONNECTING, SYNCING, LOADED, ERROR.

- [ ] Build premium warm-ivory shell with HOME / RECIPES / SEND / SETTINGS bottom navigation.
- [ ] HOME shows the selected recipe and large `TAP xBLOOM TO SEND` CTA.
- [ ] SEND opens a rounded sheet, enables NFC reader mode for common NFC technologies, and waits for a machine-side NFC tag.
- [ ] Any NFC tag detected while the SEND sheet is actively waiting is treated as the proximity trigger, then reader mode stops and BLE direct send begins.
- [ ] Provide fallback `SEND NOW` button so BLE can be tested if the machine NFC tag is not readable by Android.
- [ ] LOADED state says `Recipe sent to xBloom` and `Press Start on the machine.`
- [ ] RECIPES supports Use/Edit/Duplicate/Delete for local recipes.
- [ ] SETTINGS shows Bluetooth/NFC readiness and app version/update action.

### Task 4: Update manager

**Files:**
- Create: `xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/UpdateManager.kt`
- Create: `xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/UpdateManagerTest.kt`
- Modify: `xbloom-brewtap/app/src/main/AndroidManifest.xml`

- [ ] Test semantic version comparison.
- [ ] Add INTERNET permission and latest GitHub Release lookup for `mafiaklp/MDconcluded`.
- [ ] Wire Settings `Check for updates` and open release page when newer.
- [ ] Update/network failures remain non-blocking.

### Task 5: Version, CI, APK

**Files:**
- Modify: `xbloom-brewtap/app/build.gradle.kts`
- Modify: `.github/workflows/xbloom-brewtap-android.yml`

- [ ] Set `versionCode = 11`, `versionName = "1.1.0"`.
- [ ] Ensure `feature/brewtap-premium-v11` triggers CI.
- [ ] Run full unit tests and debug APK build.
- [ ] Download artifact for Samsung manual test.

## Acceptance
- Four premium tabs are interactive.
- Recipe editor/library can represent EDISON 18g / 270ml / 93°C / grind 55 @ 80 RPM.
- Opening SEND waits for a physical phone-to-machine NFC tap.
- NFC tap triggers BLE discovery and direct recipe transfer; no A/B/C slot command is sent.
- Fallback SEND NOW runs the same direct BLE path.
- No BLE Execute Recipe command 8002 is sent.
- On-device test confirms whether xBloom firmware presents a physical Start action after direct recipe load; if not, that firmware behavior becomes the next measured protocol task rather than silently auto-starting.
- Unit tests and APK build pass in CI.
