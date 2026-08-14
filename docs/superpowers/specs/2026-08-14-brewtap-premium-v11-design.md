# BrewTap Premium v1.1 Design

## Goal
Transform the existing BrewTap xBloom NFC writer MVP into a modern, premium, useful Android app centered on fast recipe selection, NFC writing, verification, and easy self-updating through GitHub Releases.

## Product principles
- Premium but restrained: warm ivory surfaces, near-black typography, subtle depth, minimal accent color.
- Utility first: the fastest path from opening the app to writing a selected xBloom recipe card should be two taps plus the NFC tap.
- No decorative complexity that interferes with brewing.
- Preserve the existing xBloom NFC-V writer behavior and read-back verification.
- Keep app structure modular so AI recipe generation and taste feedback can be added later without rewriting NFC code.

## Navigation
Use a persistent four-tab bottom navigation:
- HOME
- RECIPES
- SCAN (centered primary circular action)
- SETTINGS

SCAN is the most prominent control. Pressing it opens the NFC scan/write bottom sheet immediately using the currently selected recipe.

## Visual system
### Colors
- App background: warm ivory `#F6F2E9`
- Primary text: near-black `#171512`
- Secondary text: muted brown-gray `#746F67`
- Card background: `#FFFDF8`
- Accent: deep espresso `#392B24`
- Success: muted evergreen `#2F6B55`
- Error: muted brick `#A74F44`

### Typography
- Large editorial-style display headings for greeting and section titles.
- Clean sans-serif body text and UI labels.
- Recipe numbers are visually dominant for quick scanning.
- Avoid excessive all-caps except small utility labels and primary scan state labels.

### Components
- Large rounded cards with subtle elevation.
- 20–28 dp corner radius for primary surfaces.
- Thin separators, minimal outlines.
- Bottom sheets use large rounded top corners and occupy roughly 55–75% of screen height depending on state.
- Motion: short fades/slides, no decorative looping animation.

## Home screen
Content order:
1. BrewTap wordmark.
2. Time-based greeting such as “Good evening.”
3. Current Coffee card.
4. Primary action: `BREW THIS RECIPE`.
5. Recent recipes section.

The Current Coffee card shows:
- Coffee name
- Origin / roast
- Taste notes
- Dose
- Total water
- Temperature
- Grind size
- Short recipe summary

For the current EDISON recipe, the card displays:
- EDISON Ethiopia
- Medium / Light
- Berry · Citrus · Floral
- 18 g
- 270 ml
- 93°C
- Grind 55

Tapping `BREW THIS RECIPE` sets the recipe as selected and opens the NFC scan sheet.

## Recipes screen
Display recipes as premium list cards.

Each recipe card includes:
- Name
- Origin / roast when available
- Taste profile
- Dose / water / temperature / grind
- Selected indicator when active

Recipe actions:
- Use
- Edit
- Duplicate
- Delete

For v1.1, EDISON Ethiopia is preloaded and remains the default selected recipe. Storage is local on-device. The data model must support multiple recipes even if only one preset ships initially.

## NFC Scan experience
Pressing the central SCAN tab or `BREW THIS RECIPE` opens a bottom sheet rather than navigating away.

States:
1. `READY TO SCAN`
   - “Hold your xBloom recipe card to the NFC area of your phone.”
   - Starts NFC-V reader mode immediately.
2. `CARD DETECTED`
3. `WRITING RECIPE`
4. `VERIFYING`
5. `READY TO BREW ✓`

Failure states:
- NFC unavailable
- NFC disabled
- Not an NFC-V card
- Card removed too early
- Read/write failed
- Verification mismatch

Each failure offers `TRY AGAIN` and a close action.

NFC behavior:
- Use existing ISO15693 / NFC-V implementation.
- Preserve the first 32 bytes/signature/hash area.
- Preserve XID behavior as currently implemented.
- Write only the recipe payload area.
- Read back after writing and verify bytes before showing success.
- Do not show success unless read-back verification passes.

## Settings screen
Sections:
### Brewing defaults
- Default dose
- Preferred ratio
- Default xBloom model label

### NFC diagnostics
- NFC available
- NFC enabled
- Last card UID
- Last write verification result

### App
- Current version
- Check for updates
- Latest version status
- Open release notes

## Update system
Use GitHub Releases for v1.1.

### Release flow
A version tag such as `v1.1.0` triggers GitHub Actions to:
1. Run unit tests.
2. Build the Android APK.
3. Create a GitHub Release.
4. Attach the APK to the release.

### In-app update check
The app checks the latest GitHub Release:
- once at app start when network is available, but no more than once every 24 hours automatically;
- whenever the user presses `Check for updates`.

If a newer semantic version exists, show a premium update sheet:
- `BrewTap 1.2.0 is ready`
- short release notes
- `UPDATE NOW`
- `LATER`

`UPDATE NOW` opens the release APK download in the browser/download manager. Android still performs the final install confirmation; the app must not attempt silent installation.

Update-check failures must not block brewing or NFC writing.

## Architecture
Split responsibilities instead of expanding the current MainActivity.

Suggested modules/classes:
- `MainActivity` — application shell and navigation host only.
- `HomeScreen` — greeting and selected recipe summary.
- `RecipesScreen` — recipe browsing and selection.
- `ScanSheet` — NFC workflow UI/state presentation.
- `SettingsScreen` — preferences, NFC diagnostics, update controls.
- `RecipeRepository` — local recipe persistence and selected recipe state.
- `RecipeGenerator` — existing preset generation, later AI integration point.
- `NfcVCardWriter` — existing NFC-V writing logic; keep protocol logic isolated.
- `NfcWriteCoordinator` — maps NFC operations to scan sheet states.
- `UpdateManager` — GitHub Release lookup, semantic-version comparison, update metadata.
- `AppPreferences` — last update check, defaults, diagnostics.

## Data flow
### Brewing
Selected recipe → Scan action → NfcWriteCoordinator → NfcVCardWriter → read-back verification → success/failure state.

### Recipe selection
RecipesScreen → RecipeRepository.setSelectedRecipe() → Home reflects selected recipe immediately.

### Update check
App start/settings → UpdateManager → GitHub latest release metadata → semantic version compare → update sheet if newer.

## Error handling
- NFC errors never crash the app.
- Update/network errors are non-blocking and appear only as concise status messages.
- Recipe validation occurs before NFC writing begins.
- A recipe with invalid total water/pour totals cannot be written.
- Verification mismatch is treated as failure even if the write calls returned successfully.

## Testing
Unit tests must cover:
- EDISON preset values.
- recipe validation and pour totals.
- NFC payload encoding.
- CRC behavior.
- semantic version comparison.
- update decision logic.
- scan state transitions independent of Android NFC hardware where possible.

GitHub Actions must run tests before building the APK.

Manual acceptance test on Samsung Android device:
1. Install APK.
2. Navigate all four tabs.
3. Select EDISON recipe.
4. Open SCAN.
5. Write a genuine xBloom NFC-V card.
6. Confirm read-back verification succeeds.
7. Tap card on xBloom and confirm recipe settings load.
8. Confirm update checker does not interrupt use when offline.

## v1.1 scope exclusions
- Direct phone-to-xBloom ISO15693 card emulation.
- Cloud accounts / login.
- Google Play distribution.
- Silent APK installation.
- Camera-based coffee recognition.
- AI taste tuner implementation.

These can follow after the premium shell, recipe library, NFC workflow, and update channel are proven stable.
