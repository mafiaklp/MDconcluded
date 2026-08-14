# BrewTap AI Smart Recipe Engine — Design

## Goal
Turn BrewTap from a card-analysis prototype into a premium smart-recipe product for xBloom. A user can add a coffee by photo or manual entry, receive competition-style recommended Hot and Iced recipes, tune the expected taste, and encode/send the resulting recipe using the reverse-engineered xBloom recipe format.

## Product principles
- Coffee-first, not parameter-first: start from bean character and desired cup profile.
- Hybrid intelligence: generate a strong recommendation automatically, then allow explicit taste tuning.
- Hot and Iced are independently optimized recipes.
- Iced recipes treat ice as part of the beverage water budget, e.g. 18 g coffee at 1:15 may use 180 g brew water + 90 g ice for 270 g total beverage water.
- Premium but useful UI: minimal, fast, readable, with advanced controls progressively disclosed.
- Reverse-engineered NFC/card details belong in Developer Tools, not the main user flow.

## Coffee Profile
A Coffee Profile is the canonical input to the recipe engine. It can be created in either of two ways and both converge to the same editable model.

### Photo input
The user photographs the coffee bag. AI extracts, where available:
- roaster / coffee name
- country / region / farm
- variety
- process
- roast level
- altitude
- tasting notes
- recommended brew information printed on the bag

The app shows extracted fields before saving so the user can correct them.

### Manual input
The user can manually enter or override all Coffee Profile fields. Minimum viable profile requires coffee name plus at least one of roast level, process, origin, or tasting notes.

## Smart Recipe Engine
For every Coffee Profile the engine creates two defaults immediately:

1. **HOT — Recommended**
2. **ICED — Recommended**

Each recipe is derived from bean characteristics rather than copied from a fixed template.

### Inputs considered
- origin / region
- variety
- process: washed, natural, honey, anaerobic, thermal shock, etc.
- roast level
- altitude when available
- tasting-note semantics
- user dose preference
- Hot vs Iced mode
- optional prior brew feedback for that coffee

### Parameters the engine may change
- coffee dose
- total beverage ratio
- grind size
- grinder RPM
- temperature per pour
- pour count
- volume per pour
- flow rate per pour
- pour pattern: centered / circular / spiral
- pause time per pour
- agitation flags
- for Iced: brew-water mass and ice mass separately

### Competition-style heuristics
The engine should reason about extraction and presentation rather than simply map roast level to a fixed recipe. Examples:
- delicate washed light roasts: preserve aromatics and clarity; avoid excessive agitation and overlong contact time
- dense high-altitude coffees: allow more extraction support via temperature, grind, flow, or staged pouring
- naturals / anaerobics: control agitation and temperature to retain sweetness and fruit definition without muddy fermentation character
- darker roasts: reduce harsh extraction pressure and protect sweetness
- iced coffee: deliberately brew a stronger hot concentrate, account for ice dilution, and preserve perceived acidity/aroma after cooling

The engine may use safe parameter bounds derived from xBloom format and observed cards, but must expose the exact resulting recipe before use.

## Expected Cup
Every generated recipe includes an expected sensory profile before brewing:
- acidity
- sweetness
- body
- clarity
- floral/aromatic intensity
- fruit intensity
- bitterness risk

Display as concise 0–10 scores plus a natural-language summary, for example:
> Jasmine-forward, juicy berry acidity, high sweetness, clean finish, medium-light body.

This is a prediction, not a guarantee, and the UI should label it as expected taste.

## Tune Taste
A primary action on both Hot and Iced recipes allows users to tune toward:
- Brighter
- Sweeter
- More Floral
- Juicier
- More Body
- Cleaner
- Less Bitter

Tuning regenerates the recipe from the current recipe rather than resetting to a generic template. The app should show a concise explanation of the major changes, e.g. "slightly finer grind, lower agitation, longer first pause".

## Brew Feedback Loop
After brewing, users can optionally record:
- Too sour
- Too bitter
- Too weak
- Too strong
- Too dry / astringent
- Muted / flat
- or sliders for acidity, sweetness, body, clarity

The next recipe revision for that Coffee Profile uses the previous recipe plus feedback. The system should keep recipe revisions so users can compare and revert.

## Recipe modes
### Recommended mode
Shows only the information needed to choose and brew:
- Hot / Iced
- dose
- beverage water
- ice when applicable
- grind
- temperature summary
- expected taste
- primary action: Use Recipe / Tap to xBloom

### Expert mode
Allows editing every xBloom-capable field per pour and shows validation before encoding.

## xBloom integration layer
The reverse-engineered card format is treated as a transport/encoding layer, isolated from recipe intelligence.

### Known format
- ISO15693 / NFC-V cards
- first 32 bytes are preserved card-specific signature/hash material
- recipe begins at block 8
- XID: 7 bytes
- cup type
- pour count
- 8 bytes per pour: volume, temperature, pattern, agitation, pause seconds, first-pour dose/minute bits, first-pour RPM, flow rate
- grind stored with offset 40
- ratio byte
- CRC-8/MAXIM-DOW

The encoder must be deterministic and covered by fixtures from the real cards already captured.

### Initial integration strategy
For safe validation, recipe generation and encoding are implemented first. Card read/write tooling remains accessible only through Developer Tools. Direct phone-to-machine transfer stays behind an Experimental feature flag until a reliable transport is proven on real hardware.

## Main user flow
1. Home
2. Add Coffee
3. Choose Photo or Manual
4. Review Coffee Profile
5. Generate
6. Coffee detail opens with **HOT Recommended** and **ICED Recommended** cards
7. User can inspect Expected Taste, Tune Taste, or open Expert mode
8. Select recipe
9. Use with xBloom
10. Optional Brew Feedback

## Information architecture
Bottom navigation:
- **Home** — current coffees, recent brews, add coffee
- **Coffees** — saved Coffee Profiles
- **Brew** — selected recipe / xBloom action
- **Settings** — preferences, updates, Developer Tools

Card Lab and raw HEX views move to `Settings > Developer Tools > xBloom Card Lab`.

## Visual direction
Modern premium utility:
- warm ivory/off-white canvas
- near-black typography
- restrained espresso/copper accent
- large recipe numbers and clear hierarchy
- spacious cards with subtle elevation
- no skeuomorphic coffee-shop styling
- animations only for meaningful states such as generating, tuning, encoding, and successful transfer

Key UI patterns:
- prominent Hot / Iced segmented switch
- sensory profile visualization that remains readable at a glance
- bottom sheets for Tune Taste and xBloom actions
- expert controls hidden by default

## Architecture
### `CoffeeProfile`
Canonical bean metadata and source provenance (photo/manual).

### `RecipeIntent`
Mode (Hot/Iced), taste target, dose preference, user feedback context.

### `SmartRecipeEngine`
Pure domain layer that converts CoffeeProfile + RecipeIntent into BrewRecipe + ExpectedCup + rationale.

### `RecipeTuner`
Produces controlled recipe deltas for taste goals and brew feedback.

### `XBloomRecipeEncoder`
Pure deterministic encoder for xBloom binary recipe format and CRC.

### `XBloomTransport`
Interface for physical delivery. Implementations may include physical NFC-card write and future direct-machine transport without coupling them to the recipe engine.

### `CoffeeRepository`
Persists profiles, recipes, versions, and feedback locally for MVP.

## Safety and validation
Before any xBloom write/send:
- validate recipe bounds
- confirm total pour water matches intended brew water
- validate Iced beverage-water equation: brew water + ice = target beverage water
- verify generated CRC
- show a human-readable summary of the exact recipe
- do not auto-start the brewer

When writing physical cards, preserve the original 32-byte signature area and keep automatic raw-card backup + restore capability.

## Testing
- fixture tests from both captured real xBloom cards
- encoder round-trip tests for known parameters
- CRC check-vector tests
- Hot recipe generation invariant tests
- Iced water/ice mass-balance tests
- taste-tuning direction tests (e.g. Less Bitter must not increase extraction pressure across every controllable dimension)
- UI state tests for photo/manual → generation → Hot/Iced → Tune Taste
- hardware validation checklist separated from unit tests

## MVP scope
Included:
- photo and manual Coffee Profile creation
- Recommended Hot + Iced generation
- Expected Cup
- Tune Taste
- Expert recipe detail/edit
- saved coffees and recipe revisions
- xBloom binary encoder based on captured cards
- Developer Tools Card Lab

Deferred:
- cloud accounts/sync
- social recipe sharing
- automatic machine start
- public marketplace
- direct phone-to-xBloom transport as a production promise until validated on hardware

## Success criteria
A new user can photograph or manually enter a coffee, receive two credible and meaningfully different Hot/Iced recipes within one flow, understand the expected cup, tune the recipe without manipulating every parameter manually, and obtain a valid xBloom-formatted recipe with deterministic encoding.
