# BrewTap AI Smart Recipe Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a modern premium Android BrewTap app that creates Coffee Profiles from photo or manual input, generates competition-style Hot and Iced xBloom recipes with expected taste and tuning, persists revisions/feedback locally, and deterministically encodes recipes into the validated xBloom binary format.

**Architecture:** Keep recipe intelligence pure and independent from transport. `CoffeeProfile + RecipeIntent -> SmartRecipeEngine -> GeneratedRecipe(BrewRecipe, ExpectedCup, rationale)`; `RecipeTuner` mutates from the current recipe with bounded deltas; `XBloomRecipeEncoder` remains deterministic; Android UI/repository code only orchestrates domain objects. Physical NFC card tooling remains under Developer Tools and direct phone-to-machine transport stays experimental.

**Tech Stack:** Kotlin, Android SDK 35/minSdk 26, JUnit 4, platform Android views for current MVP, local JSON persistence via SharedPreferences/files, existing NFC-V implementation, existing GitHub Actions Gradle 8.11.1/JDK 17 pipeline.

## Global Constraints

- Modern premium utility visual direction: warm ivory/off-white, near-black text, restrained espresso/copper accent, spacious cards, no skeuomorphic coffee-shop styling.
- Coffee creation supports both photo-assisted extraction and manual entry; both converge to one editable `CoffeeProfile`.
- Every Coffee Profile generates independent **HOT Recommended** and **ICED Recommended** recipes.
- Iced invariant: `brewWaterMl + iceGrams == targetBeverageWaterMl`.
- Expected taste is explicitly predictive, shown as 0–10 sensory scores plus a short natural-language summary.
- Tune Taste goals: Brighter, Sweeter, More Floral, Juicier, More Body, Cleaner, Less Bitter.
- Expert mode exposes every xBloom-capable per-pour field.
- xBloom format: preserve first 32 bytes on physical cards; recipe begins at block 8; 7-byte XID; cup type; pour count; 8 bytes/pour; grind offset 40; ratio; CRC-8/MAXIM-DOW.
- Never auto-start the brewer.
- Direct phone-to-xBloom transport remains experimental until validated on hardware.
- TDD: each domain behavior starts with a failing test; CI must pass before APK handoff.

---

## File Structure

- `app/src/main/java/com/brewtap/xbloom/domain/CoffeeProfile.kt` — canonical coffee metadata and input provenance.
- `app/src/main/java/com/brewtap/xbloom/domain/RecipeIntent.kt` — Hot/Iced mode, taste target, dose preference, feedback context.
- `app/src/main/java/com/brewtap/xbloom/domain/ExpectedCup.kt` — sensory prediction model.
- `app/src/main/java/com/brewtap/xbloom/domain/GeneratedRecipe.kt` — recipe + expected cup + rationale + iced split.
- `app/src/main/java/com/brewtap/xbloom/domain/SmartRecipeEngine.kt` — deterministic competition-style baseline generator.
- `app/src/main/java/com/brewtap/xbloom/domain/RecipeTuner.kt` — bounded taste and feedback adjustments from current recipe.
- `app/src/main/java/com/brewtap/xbloom/data/CoffeeRepository.kt` — local profiles, recipe revisions, feedback.
- `app/src/main/java/com/brewtap/xbloom/photo/CoffeeBagAnalyzer.kt` — interface + MVP heuristic/manual review result; future vision provider plugs in here.
- `app/src/main/java/com/brewtap/xbloom/ui/AppState.kt` — navigation and selected coffee/recipe state.
- `app/src/main/java/com/brewtap/xbloom/ui/MainActivity.kt` — shell and bottom navigation.
- `app/src/main/java/com/brewtap/xbloom/ui/HomeScreen.kt` — saved coffees/current brew/add coffee.
- `app/src/main/java/com/brewtap/xbloom/ui/AddCoffeeScreen.kt` — photo/manual entry and review.
- `app/src/main/java/com/brewtap/xbloom/ui/CoffeeDetailScreen.kt` — Hot/Iced recommended cards, expected cup, Tune Taste.
- `app/src/main/java/com/brewtap/xbloom/ui/ExpertRecipeScreen.kt` — per-pour editing and validation.
- `app/src/main/java/com/brewtap/xbloom/ui/BrewScreen.kt` — selected recipe summary and xBloom action.
- `app/src/main/java/com/brewtap/xbloom/ui/SettingsScreen.kt` — preferences and Developer Tools/Card Lab.
- Existing `XBloomRecipeEncoder.kt`, `NfcVCardInspector.kt`, `NfcVCardWriter.kt` — retain and harden with fixtures.

---

### Task 1: Canonical coffee and recipe intent domain models

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/domain/CoffeeProfile.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/domain/RecipeIntent.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/domain/ExpectedCup.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/domain/GeneratedRecipe.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/domain/CoffeeProfileTest.kt`

**Interfaces:**
- Produces: `CoffeeProfile`, `CoffeeSource`, `BrewMode`, `TasteGoal`, `RecipeIntent`, `ExpectedCup`, `GeneratedRecipe`, `IcedSplit`.

- [ ] **Step 1: Write failing model validation tests**

```kotlin
@Test fun icedSplitRequiresMassBalance() {
    assertFailsWith<IllegalArgumentException> { IcedSplit(180, 80, 270) }
}

@Test fun coffeeProfileMinimumInfoIsEnforced() {
    assertFalse(CoffeeProfile(name = "Test").isGeneratable())
    assertTrue(CoffeeProfile(name = "Test", roastLevel = RoastLevel.LIGHT).isGeneratable())
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `gradle testDebugUnitTest --tests '*CoffeeProfileTest*'`
Expected: FAIL because types do not exist.

- [ ] **Step 3: Implement minimal immutable models**

```kotlin
enum class CoffeeSource { PHOTO, MANUAL }
enum class RoastLevel { LIGHT, MEDIUM_LIGHT, MEDIUM, MEDIUM_DARK, DARK, UNKNOWN }
enum class BrewMode { HOT, ICED }
enum class TasteGoal { RECOMMENDED, BRIGHTER, SWEETER, MORE_FLORAL, JUICIER, MORE_BODY, CLEANER, LESS_BITTER }

data class CoffeeProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val roaster: String = "",
    val country: String = "",
    val region: String = "",
    val farm: String = "",
    val variety: String = "",
    val process: String = "",
    val roastLevel: RoastLevel = RoastLevel.UNKNOWN,
    val altitudeM: Int? = null,
    val tastingNotes: List<String> = emptyList(),
    val bagBrewNotes: String = "",
    val source: CoffeeSource = CoffeeSource.MANUAL,
) {
    fun isGeneratable() = name.isNotBlank() && (
        roastLevel != RoastLevel.UNKNOWN || process.isNotBlank() || country.isNotBlank() || tastingNotes.isNotEmpty()
    )
}

data class IcedSplit(val brewWaterMl: Int, val iceGrams: Int, val targetBeverageWaterMl: Int) {
    init { require(brewWaterMl + iceGrams == targetBeverageWaterMl) }
}
```

- [ ] **Step 4: Run model tests**

Run: `gradle testDebugUnitTest --tests '*CoffeeProfileTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/domain xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/domain
git commit -m "feat: add coffee and recipe intent domain models"
```

---

### Task 2: Competition-style SmartRecipeEngine

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/domain/SmartRecipeEngine.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/domain/SmartRecipeEngineTest.kt`
- Modify: `app/src/main/java/com/brewtap/xbloom/RecipeModels.kt`

**Interfaces:**
- Consumes: `CoffeeProfile`, `RecipeIntent`.
- Produces: `fun generate(profile: CoffeeProfile, intent: RecipeIntent): GeneratedRecipe`.

- [ ] **Step 1: Write failing baseline generation tests**

```kotlin
@Test fun washedLightCoffeeGetsClarityBiasedHotRecipe() {
    val p = CoffeeProfile(name="Ethiopia", process="washed", roastLevel=RoastLevel.LIGHT,
        altitudeM=2100, tastingNotes=listOf("jasmine", "bergamot", "peach"))
    val g = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.HOT, doseGrams=18))
    assertEquals(18, g.recipe.doseGrams)
    assertTrue(g.recipe.temperatureC >= 92)
    assertTrue(g.expectedCup.clarity >= 7)
    assertTrue(g.expectedCup.floral >= 7)
}

@Test fun icedRecipeBalancesHotWaterAndIce() {
    val p = CoffeeProfile(name="Natural Ethiopia", process="natural", roastLevel=RoastLevel.LIGHT,
        tastingNotes=listOf("berry", "floral"))
    val g = SmartRecipeEngine.generate(p, RecipeIntent(BrewMode.ICED, doseGrams=18))
    val split = requireNotNull(g.icedSplit)
    assertEquals(split.targetBeverageWaterMl, split.brewWaterMl + split.iceGrams)
    assertTrue(split.brewWaterMl < split.targetBeverageWaterMl)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `gradle testDebugUnitTest --tests '*SmartRecipeEngineTest*'`
Expected: FAIL because engine does not exist.

- [ ] **Step 3: Implement deterministic heuristic scoring**

Use explicit semantic buckets:

```kotlin
private fun noteScore(notes: List<String>, vararg tokens: String): Int =
    notes.sumOf { note -> tokens.count { token -> note.lowercase().contains(token) } }
```

Rules to encode in one pure generator:
- Light/high-altitude/washed -> 92–95°C, moderate-fine grind, lower agitation, 3–4 pours, clarity/floral bias.
- Natural/anaerobic -> 90–93°C, controlled agitation, slightly coarser than washed peer, fruit/sweetness bias.
- Medium/dark -> 86–91°C, coarser grind, fewer pours, lower agitation, body/sweetness bias.
- Iced -> target beverage ratio 1:14–1:16 depending roast/process; 28–38% of beverage water as ice; hotter/finer or more concentrated hot phase within safe bounds.
- Keep xBloom-safe bounds: dose 10–25 g, grind 41–80 for MVP, RPM 60–120, temperature 85–96°C, flow 2.0–4.5 ml/s, 2–5 pours.

- [ ] **Step 4: Run generation tests**

Run: `gradle testDebugUnitTest --tests '*SmartRecipeEngineTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/domain/SmartRecipeEngine.kt xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/RecipeModels.kt xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/domain/SmartRecipeEngineTest.kt
git commit -m "feat: add competition style smart recipe engine"
```

---

### Task 3: Expected Cup prediction and rationale

**Files:**
- Modify: `app/src/main/java/com/brewtap/xbloom/domain/SmartRecipeEngine.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/domain/ExpectedCupTest.kt`

**Interfaces:**
- Produces `ExpectedCup(acidity, sweetness, body, clarity, floral, fruit, bitternessRisk, summary)` with each score `0..10`.

- [ ] **Step 1: Write failing sensory tests**

```kotlin
@Test fun floralWashedCoffeePredictsFloralClarity() {
    val g = SmartRecipeEngine.generate(
        CoffeeProfile(name="Kenya", process="washed", roastLevel=RoastLevel.LIGHT,
            tastingNotes=listOf("jasmine", "blackcurrant", "citrus")),
        RecipeIntent(BrewMode.HOT, doseGrams=18)
    )
    assertTrue(g.expectedCup.floral >= 6)
    assertTrue(g.expectedCup.clarity >= 6)
    assertTrue(g.expectedCup.summary.isNotBlank())
}
```

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*ExpectedCupTest*'`

- [ ] **Step 3: Implement score clamping and summary construction**

```kotlin
private fun clamp(v: Int) = v.coerceIn(0, 10)
```

Build summary from top 2 sensory dimensions plus finish/body descriptor; never claim certainty, prefix UI label later with `Expected taste`.

- [ ] **Step 4: Run tests**

Run: `gradle testDebugUnitTest --tests '*ExpectedCupTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/domain/SmartRecipeEngine.kt xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/domain/ExpectedCupTest.kt
git commit -m "feat: add expected cup prediction"
```

---

### Task 4: RecipeTuner and brew feedback deltas

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/domain/RecipeTuner.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/domain/BrewFeedback.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/domain/RecipeTunerTest.kt`

**Interfaces:**
- Produces: `fun tune(current: GeneratedRecipe, goal: TasteGoal): GeneratedRecipe`.
- Produces: `fun applyFeedback(current: GeneratedRecipe, feedback: BrewFeedback): GeneratedRecipe`.

- [ ] **Step 1: Write failing directional tests**

```kotlin
@Test fun lessBitterReducesExtractionPressure() {
    val base = fixtureGeneratedRecipe()
    val tuned = RecipeTuner.tune(base, TasteGoal.LESS_BITTER)
    val lowerTemp = tuned.recipe.temperatureC <= base.recipe.temperatureC
    val coarser = tuned.recipe.grindSize >= base.recipe.grindSize
    val lowerAgitation = tuned.recipe.pours.sumOf { it.agitation } <= base.recipe.pours.sumOf { it.agitation }
    assertTrue(lowerTemp || coarser || lowerAgitation)
}

@Test fun brighterDoesNotBlindlyIncreaseEveryExtractionLever() {
    val tuned = RecipeTuner.tune(fixtureGeneratedRecipe(), TasteGoal.BRIGHTER)
    assertTrue(tuned.rationale.contains("bright", ignoreCase=true))
}
```

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*RecipeTunerTest*'`

- [ ] **Step 3: Implement bounded deltas**

Examples:
- Brighter: slightly coarser or faster later pours, preserve first-pour extraction, possibly +1°C only for light roast profile; lower body prediction.
- Sweeter: modestly finer OR +1°C, slower middle pour, avoid simultaneous max changes.
- More Floral/Cleaner: lower agitation, center/spiral choice favoring evenness, slightly coarser finish.
- More Body/Juicier: slightly finer, stronger middle pour, modest agitation increase.
- Less Bitter/Too dry: coarser, lower temp, lower agitation, shorten late contact.
- Too sour/weak: one or two extraction-support deltas, not all controls at once.

Each returned `GeneratedRecipe` appends a concise `rationale` diff.

- [ ] **Step 4: Run tuner tests**

Run: `gradle testDebugUnitTest --tests '*RecipeTunerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/domain xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/domain/RecipeTunerTest.kt
git commit -m "feat: add taste tuning and brew feedback"
```

---

### Task 5: Real-card encoder fixtures and validation

**Files:**
- Modify: `app/src/main/java/com/brewtap/xbloom/XBloomRecipeEncoder.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/XBloomRecipeValidator.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/XBloomRealCardFixtureTest.kt`

**Interfaces:**
- Produces: `XBloomRecipeValidator.validate(recipe: BrewRecipe, icedSplit: IcedSplit?): List<String>`.

- [ ] **Step 1: Add exact fixture tests from captured cards**

Fixture #1 recipe bytes after 32-byte signature begin with XID `TH0035`; fixture #2 with `X43001`. Assert decoded known fields and CRC are reproduced exactly for unchanged recipes.

- [ ] **Step 2: Run fixtures and verify any mismatch is visible**

Run: `gradle testDebugUnitTest --tests '*XBloomRealCardFixtureTest*'`

- [ ] **Step 3: Fix encoder semantics only where fixture proves it**

Keep grind stored as `grindSize - 40`; ratio must represent actual recipe ratio for generated recipes; validate total pour volume equals intended brew water, not iced total beverage water.

- [ ] **Step 4: Run encoder/CRC/fixture tests**

Run: `gradle testDebugUnitTest --tests '*XBloom*Test*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomRecipeEncoder.kt xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/XBloomRecipeValidator.kt xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/XBloomRealCardFixtureTest.kt
git commit -m "test: lock xBloom encoder to real card fixtures"
```

---

### Task 6: Local CoffeeRepository with revision history

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/data/CoffeeRepository.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/data/StoredCoffee.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/data/CoffeeRepositoryCodecTest.kt`

**Interfaces:**
- `saveCoffee(profile, hot, iced)`
- `listCoffees(): List<StoredCoffee>`
- `saveRevision(coffeeId, mode, generatedRecipe)`
- `saveFeedback(coffeeId, mode, feedback)`
- `getLatestRecipe(coffeeId, mode)`

- [ ] **Step 1: Write JSON codec round-trip tests**

Persist profile + Hot/Iced + revisions to a deterministic JSON string and parse back without loss.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*CoffeeRepositoryCodecTest*'`

- [ ] **Step 3: Implement local JSON repository**

Use Android `SharedPreferences` for the MVP index and one JSON payload per coffee ID; keep serialization code isolated from Activity/UI.

- [ ] **Step 4: Run repository tests**

Run: `gradle testDebugUnitTest --tests '*CoffeeRepositoryCodecTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/data xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/data
git commit -m "feat: persist coffees recipes and revisions"
```

---

### Task 7: Photo/manual Coffee Profile intake boundary

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/photo/CoffeeBagAnalyzer.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/photo/AnalyzedCoffeeDraft.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/AddCoffeeScreen.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/photo/CoffeeBagAnalyzerTest.kt`

**Interfaces:**
- `CoffeeBagAnalyzer.analyze(textHints: String): AnalyzedCoffeeDraft` for MVP.
- UI supports photo capture/import entry point but always shows editable review before generation.

- [ ] **Step 1: Write analyzer parsing tests**

Given OCR-like/manual text `"Ethiopia Guji / Natural / 2100m / jasmine berry / light roast"`, expect origin/process/altitude/notes/roast draft fields.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*CoffeeBagAnalyzerTest*'`

- [ ] **Step 3: Implement conservative analyzer**

Do not invent missing fields. Unknown values stay blank/UNKNOWN. The Android photo action may collect an image now, but MVP parsing can use user-confirmed text hints until a vision provider is connected; the boundary must remain replaceable without changing domain models.

- [ ] **Step 4: Run analyzer tests**

Run: `gradle testDebugUnitTest --tests '*CoffeeBagAnalyzerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/photo xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui/AddCoffeeScreen.kt xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/photo
git commit -m "feat: add photo and manual coffee intake"
```

---

### Task 8: Modern premium app shell and navigation

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/ui/AppState.kt`
- Replace: `app/src/main/java/com/brewtap/xbloom/MainActivity.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/UiKit.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/HomeScreen.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/CoffeesScreen.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/SettingsScreen.kt`

**Interfaces:**
- Bottom tabs: Home, Coffees, Brew, Settings.
- Central state tracks selected coffee and selected Hot/Iced recipe.

- [ ] **Step 1: Define UI state transition tests as pure functions**

Create `AppStateReducerTest` validating `AddCoffee -> CoffeeDetail`, `SelectIced -> Brew`, `Settings -> Developer Tools` navigation states.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*AppStateReducerTest*'`

- [ ] **Step 3: Implement state reducer and reusable premium UI primitives**

`UiKit` exposes `title`, `body`, `card`, `primaryButton`, `segmentedControl` with ivory/ink/espresso palette and consistent spacing.

- [ ] **Step 4: Build shell screens and run tests**

Run: `gradle testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/MainActivity.kt xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/ui
git commit -m "feat: add premium BrewTap app shell"
```

---

### Task 9: Coffee Detail with Hot/Iced, Expected Cup, Tune Taste

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/ui/CoffeeDetailScreen.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/ExpectedCupView.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/TuneTasteSheet.kt`

**Interfaces:**
- Hot/Iced segmented switch.
- Displays dose, brew water, ice if applicable, grind, temperature, pours summary, expected taste.
- Tune Taste operates on current recipe revision.

- [ ] **Step 1: Add pure presenter tests**

Assert Iced presenter shows both `brewWaterMl` and `iceGrams`, while Hot hides ice. Assert Expected Cup label includes `Expected taste`.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*CoffeeDetailPresenterTest*'`

- [ ] **Step 3: Implement Coffee Detail UI and tuning actions**

Use 0–10 bars/chips for sensory scores, not a radar chart; keep scanability high. Tune Taste sheet shows the seven goals and rationale after regeneration.

- [ ] **Step 4: Run all tests and assemble debug**

Run: `gradle testDebugUnitTest assembleDebug`
Expected: PASS and APK produced.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui
git commit -m "feat: add Hot Iced and taste tuning experience"
```

---

### Task 10: Expert Recipe editor and preflight validation

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/ui/ExpertRecipeScreen.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/RecipePreflightView.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/RecipePreflightTest.kt`

**Interfaces:**
- Editable: dose, grind, RPM, each pour's volume/temp/flow/pattern/agitation/pause.
- Preflight blocks encoding if bounds or mass balance fail.

- [ ] **Step 1: Write failing validation tests**

Examples: Iced hot pour total must equal `icedSplit.brewWaterMl`; Hot pour total must equal recipe target water; invalid grind or flow returns blocking errors.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*RecipePreflightTest*'`

- [ ] **Step 3: Implement editor and validation summary**

Show exact human-readable recipe before any write/send. Do not allow physical brew start commands.

- [ ] **Step 4: Run tests and build**

Run: `gradle testDebugUnitTest assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/RecipePreflightTest.kt
git commit -m "feat: add expert recipe editing and preflight"
```

---

### Task 11: Brew screen, xBloom encoder handoff, Developer Tools

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/ui/BrewScreen.kt`
- Modify: `app/src/main/java/com/brewtap/xbloom/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/brewtap/xbloom/ui/DeveloperToolsScreen.kt`
- Reuse: `NfcVCardInspector.kt`, `NfcVCardWriter.kt`

**Interfaces:**
- Main Brew flow encodes selected recipe and shows exact recipe/payload status.
- Physical Card Lab is only under Developer Tools.
- Direct machine transport toggle is labeled Experimental and off by default.

- [ ] **Step 1: Write presenter tests for transport labeling**

Assert card write is available only through Developer Tools; direct-machine action renders `Experimental` and cannot auto-start.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*BrewPresenterTest*'`

- [ ] **Step 3: Implement Brew/Developer Tools split**

Main flow uses `XBloomRecipeEncoder` after validator passes. Developer Tools retains raw dump, backup, restore, and card write diagnostic functions.

- [ ] **Step 4: Run tests and build APK**

Run: `gradle testDebugUnitTest assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui
git commit -m "feat: integrate xBloom encoding into Brew flow"
```

---

### Task 12: Brew feedback and recipe revision UX

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/ui/BrewFeedbackSheet.kt`
- Modify: `app/src/main/java/com/brewtap/xbloom/data/CoffeeRepository.kt`
- Modify: `app/src/main/java/com/brewtap/xbloom/ui/CoffeeDetailScreen.kt`
- Test: `app/src/test/java/com/brewtap/xbloom/domain/BrewFeedbackIntegrationTest.kt`

**Interfaces:**
- Feedback options: Too sour, Too bitter, Too weak, Too strong, Too dry/astringent, Muted/flat plus optional sensory sliders.
- Saving feedback creates a new revision; previous recipe remains revertible.

- [ ] **Step 1: Write feedback revision test**

Generate base -> apply `TOO_SOUR` -> save revision -> assert revision count increments and original remains unchanged.

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*BrewFeedbackIntegrationTest*'`

- [ ] **Step 3: Implement feedback sheet and revision history**

Expose `Compare` and `Revert` actions using stored revisions; rationale shows major deltas only.

- [ ] **Step 4: Run all tests**

Run: `gradle testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui/BrewFeedbackSheet.kt xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/data/CoffeeRepository.kt xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/domain/BrewFeedbackIntegrationTest.kt
git commit -m "feat: add brew feedback learning loop"
```

---

### Task 13: Update system and release metadata

**Files:**
- Create: `app/src/main/java/com/brewtap/xbloom/update/UpdateChecker.kt`
- Modify: `app/src/main/java/com/brewtap/xbloom/ui/SettingsScreen.kt`
- Modify: `.github/workflows/xbloom-brewtap-android.yml`

**Interfaces:**
- Settings shows current version and manual `Check for update`.
- GitHub release metadata is used for update availability; APK installation always requires Android user confirmation.

- [ ] **Step 1: Write semantic version comparison tests**

```kotlin
assertTrue(UpdateChecker.isNewer("1.3.0", "1.2.0"))
assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.0"))
```

- [ ] **Step 2: Run and verify failure**

Run: `gradle testDebugUnitTest --tests '*UpdateCheckerTest*'`

- [ ] **Step 3: Implement update checker and release workflow metadata**

Keep network lookup isolated; failure to check updates must never block brewing or recipe generation.

- [ ] **Step 4: Run tests and CI build**

Run: `gradle testDebugUnitTest assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/update xbloom-brewtap/app/src/main/java/com/brewtap/xbloom/ui/SettingsScreen.kt .github/workflows/xbloom-brewtap-android.yml
git commit -m "feat: add BrewTap update checking"
```

---

### Task 14: End-to-end acceptance fixtures and hardware checklist

**Files:**
- Create: `app/src/test/java/com/brewtap/xbloom/SmartRecipeAcceptanceTest.kt`
- Create: `docs/brewtap-hardware-validation.md`
- Modify: `README.md` or `xbloom-brewtap/README.md`

**Interfaces:**
- Acceptance scenarios cover manual coffee, photo-draft coffee, Hot, Iced, Tune Taste, encoder, feedback revision.

- [ ] **Step 1: Add acceptance test for a floral washed coffee**

Manual profile -> generate -> Hot and Iced are meaningfully different -> Iced mass balance valid -> tune `SWEETER` -> encode without validation errors.

- [ ] **Step 2: Add acceptance test for medium natural coffee**

Assert lower temperature/controlled agitation relative to floral washed baseline and valid xBloom payload.

- [ ] **Step 3: Run full verification**

Run: `gradle clean testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass, APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Complete hardware checklist**

Document exact manual checks:
1. Install APK.
2. Create one manual coffee.
3. Verify Hot/Iced recipe values and expected taste.
4. Tune one recipe and verify rationale.
5. Encode selected recipe.
6. In Developer Tools, write only to a backed-up test xBloom card.
7. Verify xBloom reads changed grind/temp/pour values.
8. Confirm brewer does not auto-start.
9. Record firmware/model and any mismatch.

- [ ] **Step 5: Commit**

```bash
git add xbloom-brewtap/app/src/test/java/com/brewtap/xbloom/SmartRecipeAcceptanceTest.kt docs/brewtap-hardware-validation.md xbloom-brewtap/README.md
git commit -m "test: add BrewTap smart recipe acceptance coverage"
```

---

## Self-Review Result

- Spec coverage: Coffee Profile photo/manual, Hot/Iced, competition-style heuristics, Expected Cup, Tune Taste, feedback revisions, Expert mode, local persistence, xBloom encoder, Developer Tools, update system, and hardware validation all map to explicit tasks.
- Placeholder scan: no TBD/TODO implementation placeholders remain; photo analysis is intentionally bounded behind a replaceable analyzer interface and user review rather than falsely claiming on-device vision support.
- Type consistency: `CoffeeProfile`, `RecipeIntent`, `GeneratedRecipe`, `IcedSplit`, `ExpectedCup`, `TasteGoal`, `SmartRecipeEngine.generate`, and `RecipeTuner.tune/applyFeedback` are the canonical interfaces used throughout.
- Scope: direct phone-to-machine transport remains explicitly outside production MVP and therefore does not block this plan.
