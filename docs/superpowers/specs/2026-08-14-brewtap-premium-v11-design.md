# BrewTap Premium v1.1 Design

## Goal
Transform BrewTap into a modern premium Android companion for xBloom Studio/J15 where the primary flow is: select a recipe → tap Send to xBloom → connect directly over BLE → load the recipe into the machine's Auto/Easy Mode slot A → user presses Start on the xBloom. Physical NFC recipe cards remain an optional legacy tool, not the main experience.

## Product principles
- Premium but restrained: warm ivory surfaces, near-black typography, subtle depth, minimal accent color.
- Utility first: opening BrewTap to sending the selected recipe should require no more than two screen taps.
- Direct machine integration is the core value; recipe-card writing is secondary.
- Never start brewing automatically from the phone in v1.1. BrewTap loads the preset and the user physically starts the brew on xBloom.
- BLE/network errors must never crash the app or corrupt local recipes.

## Navigation
Persistent bottom navigation:
- HOME
- RECIPES
- SEND (large centered primary action)
- SETTINGS

SEND opens a premium bottom sheet and immediately begins xBloom BLE discovery after Android Bluetooth permission is available.

## Visual system
- Background: warm ivory `#F6F2E9`
- Primary text: `#171512`
- Secondary text: `#746F67`
- Card: `#FFFDF8`
- Accent: deep espresso `#392B24`
- Success: `#2F6B55`
- Error: `#A74F44`
- Large rounded cards, subtle elevation, 20–28dp corner radius, concise motion.
- Recipe values such as 18 g / 270 ml / 93°C / Grind 55 are visually dominant.

## Home
Show BrewTap wordmark, time-based greeting, Current Coffee card, large `SEND TO xBLOOM` CTA, and recent recipes.

Default EDISON card:
- EDISON Ethiopia
- Medium / Light
- Berry · Citrus · Floral
- 18 g
- 270 ml
- 93°C
- Grind 55 @ 80 RPM

## Recipes
Local recipe library with selected-state indicator. v1.1 ships with EDISON Ethiopia and supports a data model for multiple recipes. Actions: Use, Edit, Duplicate, Delete.

## Direct xBloom send flow
Primary state machine:
1. `READY TO SEND`
2. `SEARCHING FOR xBLOOM`
3. `CONNECTING`
4. `SYNCING RECIPE`
5. `RECIPE LOADED ✓`
6. Copy: `Press Start on your xBloom to brew.`

BLE transport is based on the independently reverse-engineered xBloom J15 protocol from `brAzzi64/xbloom-ble`, specifically:
- advertiser name beginning with `XBLOOM`
- command characteristic UUID `0000FFE1-0000-1000-8000-00805F9B34FB`
- notification characteristic UUID `0000FFE2-0000-1000-8000-00805F9B34FB`
- packet header `0x58`, CRC16 polynomial `0x8408`, initial value `0`
- handshake command 8100 with `[185, 1]`
- Auto/Easy Mode command 11511 with payload `91327856`
- Easy Mode recipe slot write command 11510

For v1.1, BrewTap writes the selected recipe to Auto/Easy Mode slot A (index 0), then switches the machine to Auto/Easy Mode. It does not send the Execute Recipe command. The physical Start action remains on the xBloom.

Recipe encoder must support:
- 1–80 grinder size
- 60–120 RPM
- 40–98°C
- center / circular / spiral pour pattern
- 3.0–3.5 ml/s flow
- per-pour volume and post-pour wait
- vibration/agitation bits
- ratio tail encoded as ratio × 10

Failure states:
- Bluetooth unsupported
- Bluetooth disabled
- permission denied
- xBloom not found
- official xBloom app or another central already connected
- GATT connection/service discovery failure
- handshake timeout
- recipe write failure
- disconnected during sync

Every failure offers `TRY AGAIN`; brewing/recipe browsing remains usable.

## Optional NFC proximity trigger
If Android detects an NFC/NDEF tag on the xBloom machine while BrewTap is foreground and the tag matches a known xBloom URI/tag pattern, BrewTap may use that tap only as a trigger to open the SEND sheet. Recipe data still travels over BLE. Until the exact machine NFC payload is validated on the user's unit, NFC proximity is optional and BLE SEND remains fully usable from the center button.

## Settings
### Machine
- Last xBloom device name/address
- Connection status
- Firmware/status when available
- Forget saved machine

### Brewing defaults
- Default dose
- Preferred ratio
- Default cup/dripper type

### App
- Current version
- Check for updates
- Latest release status
- Release notes

## Update system
GitHub Releases is the v1.1 update channel. A version tag runs tests, builds APK, creates a Release and attaches the APK. The app checks latest Release no more than once per 24h automatically and on-demand in Settings. `UPDATE NOW` opens the APK download; Android retains final install confirmation. Update failures never block brewing.

## Architecture
- `MainActivity` — premium shell + bottom navigation host.
- `RecipeModels` / `RecipeGenerator` — recipe model and presets.
- `RecipeRepository` — local recipe persistence and selected recipe.
- `XBloomBleProtocol` — pure Kotlin packet builders, CRC16, recipe encoder, slot packet.
- `XBloomBleClient` — Android BLE scan/connect/GATT/handshake/write lifecycle.
- `SendCoordinator` — UI-independent send state machine.
- `UpdateManager` — semantic version + GitHub Release lookup.
- Legacy `NfcVCardWriter` remains available but is not in the primary navigation flow.

## Data flow
Selected recipe → SEND → BLE scan → connect → discover GATT → subscribe/handshake → encode slot A → send slot packet → send Auto/Easy Mode packet → success UI → user presses Start on xBloom.

## Testing
Unit tests must cover:
- EDISON preset
- recipe validation and pour totals
- CRC16 known vectors
- type1/type2 packet framing
- recipe BLE encoding
- Easy Mode slot A packet
- semantic version comparison
- send-state transitions

CI must run unit tests before building APK.

## Manual acceptance on Samsung
1. Install APK and grant Nearby devices permission.
2. Disconnect the official xBloom app from the machine.
3. Navigate all four tabs.
4. Select EDISON Ethiopia.
5. Tap SEND TO xBLOOM.
6. Confirm BrewTap discovers and connects to the machine.
7. Confirm recipe sync reports success.
8. Confirm xBloom is in Auto/Easy Mode with the recipe loaded to slot A.
9. Press Start on xBloom and verify the expected grind/brew preset.
10. Confirm offline/update-check failure never blocks SEND.

## v1.1 exclusions
- Silent auto-start brew from BrewTap.
- Guaranteed NFC-tap trigger before the machine's NFC payload is captured and validated.
- Cloud accounts/login.
- Google Play distribution.
- Silent APK installation.
- Camera coffee recognition.
- AI taste tuner.
