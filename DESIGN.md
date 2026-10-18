---
name: kegent
description: A native Android running workspace with the retained KmAo blue identity.
colors:
  primary_brand: "#DAF0FB"
  on_primary_brand: "#132D3D"
  ui_background: "#F3F7FA"
  ui_surface: "#FBFDFF"
  ui_tonal: "#E7F1F7"
  ui_text: "#182B38"
  ui_muted: "#526774"
  ui_accent: "#235E80"
  ui_outline: "#C9D8E2"
  ui_control_outline: "#50768C"
  ui_on_accent: "#FFFFFF"
  ui_disabled_fill: "#E3EBF0"
  ui_disabled_text: "#526774"
  ui_error: "#A93236"
  ui_background_night: "#111C24"
  ui_surface_night: "#192731"
  ui_tonal_night: "#243D4C"
  ui_text_night: "#E4EFF6"
  ui_muted_night: "#B3C8D5"
  ui_accent_night: "#A0D4F0"
  ui_outline_night: "#405B6B"
  ui_control_outline_night: "#8BB8D0"
  ui_on_accent_night: "#132D3D"
  ui_disabled_fill_night: "#263640"
  ui_disabled_text_night: "#A3B6C2"
  ui_error_night: "#FFB1B4"
typography:
  app-title:
    fontFamily: "sans-serif-medium"
    fontSize: "23sp"
    fontWeight: 500
  section-title:
    fontFamily: "sans-serif-medium"
    fontSize: "20sp"
    fontWeight: 500
  route-title:
    fontFamily: "sans-serif-medium"
    fontSize: "19sp"
    fontWeight: 500
  route-title-compact:
    fontFamily: "sans-serif-medium"
    fontSize: "17sp"
    fontWeight: 500
  status:
    fontFamily: "sans-serif-medium"
    fontSize: "16sp"
    fontWeight: 500
  status-compact:
    fontFamily: "sans-serif-medium"
    fontSize: "13sp"
    fontWeight: 500
  field:
    fontFamily: "sans"
    fontSize: "16sp"
  body:
    fontFamily: "sans"
    fontSize: "14sp"
  button:
    fontSize: "14sp"
  note:
    fontFamily: "sans"
    fontSize: "13sp"
  metric-compact:
    fontFamily: "sans"
    fontSize: "12sp"
rounded:
  card: "20dp"
  button: "14dp"
  field: "12dp"
  route: "14dp"
  progress: "3dp"
spacing:
  tight: "4dp"
  caption: "6dp"
  small: "8dp"
  pair: "12dp"
  compact: "14dp"
  medium: "16dp"
  large: "20dp"
components:
  button-primary:
    backgroundColor: "{colors.primary_brand}"
    textColor: "{colors.on_primary_brand}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
  button-primary-disabled:
    backgroundColor: "{colors.ui_disabled_fill}"
    textColor: "{colors.ui_disabled_text}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
  button-secondary:
    backgroundColor: "{colors.ui_tonal}"
    textColor: "{colors.ui_accent}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
  button-secondary-disabled:
    backgroundColor: "{colors.ui_disabled_fill}"
    textColor: "{colors.ui_disabled_text}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
  field-outline:
    textColor: "{colors.ui_text}"
    typography: "{typography.field}"
    rounded: "{rounded.field}"
  workspace-card:
    backgroundColor: "{colors.ui_surface}"
    rounded: "{rounded.card}"
    padding: "20dp"
  mode-navigation:
    backgroundColor: "{colors.ui_background}"
    textColor: "{colors.ui_muted}"
    height: "48dp"
  mode-navigation-selected:
    textColor: "{colors.ui_accent}"
  route-preview:
    backgroundColor: "{colors.ui_tonal}"
    rounded: "{rounded.route}"
  history-review:
    backgroundColor: "{colors.ui_tonal}"
    textColor: "{colors.ui_text}"
    typography: "{typography.body}"
    padding: "14dp"
  pace-switch:
    textColor: "{colors.ui_text}"
  time-picker:
    padding: "8dp 16dp"
---

# Design System: kegent

## Overview

**Creative North Star: "Route-first running workspace"**

kegent is a compact native Android tool for configuring route playback and preparing one historical run. Its visual identity retains the installed KmAo light blue and original artwork, with ink-blue text, cool surfaces, and native Material controls. The app title is kegent; the package is `com.haven.kegent`.

The interface gives route geometry, editable parameters, and current run state clear roles. Clear control boundaries and quiet tonal panels organize a practical workspace. Motion explains a state change or progress update. Light and dark themes share the same blue family, and text uses Android's scalable native typography.

**Key Characteristics:**

- Retained light-blue identity with cool neutral surfaces.
- Native Material 3 controls and Android dp/sp units.
- Actual route geometry paired with visible run status.
- Compact forms, persistent actions, and restrained state motion.
- One historical record with explicit preview and handoff states.

This document describes the implemented source, not a web translation. The frontmatter owns the native values. Color keys with `_night` document the matching `values-night` override; they are not additional Android resource names. Component color references show the default configuration and resolve through the same resource name in night mode. The sidecar records native extensions and source specimens, without inventing browser hover states or palette ramps.

## Colors

The palette combines a pale blue primary action with darker blue interaction marks and a restrained cool-neutral ground.

### Primary

- **Retained sky blue** (`primary_brand`): the custom filled primary actions in `Ui.button`. It remains exactly the frontmatter value in both themes.
- **Ink on sky** (`on_primary_brand`): text on the primary fill, also unchanged in night mode.
- **Route blue** (`ui_accent`): route strokes, selected tabs, outlined action text, focused fields, and native dialog/control accents. Material maps this resource to `colorPrimary`, `colorSecondary`, and `android:colorAccent`; its night override is a lighter blue.
- **On accent** (`ui_on_accent`): the native theme's `colorOnPrimary` and `colorOnSecondary`, with a matching night override. The custom pale-blue primary button continues to use the separate brand pair.

### Neutral

- **Cool ground** (`ui_background`): screen, tabs, system bars, and window background.
- **Paper surface** (`ui_surface`): workspace cards, footer, and the route marker's outer disk.
- **Tonal blue surface** (`ui_tonal`): enabled secondary button fill, route canvas, history review inset, and progress track.
- **Primary ink** (`ui_text`): titles, status, field values, and body copy.
- **Muted slate** (`ui_muted`): supporting copy, route details, inactive tabs, and default field hints.
- **Soft outline** (`ui_outline`): card borders, disabled button borders, the uncompleted route, and the theme's outline role.
- **Control outline** (`ui_control_outline`): the stronger border on enabled secondary buttons. Enabled primary buttons use the accent border.
- **Disabled fill / disabled text** (`ui_disabled_fill`, `ui_disabled_text`): dedicated background and foreground roles shared by disabled primary and secondary buttons.

### Feedback

- **Error red** (`ui_error`): validation and footer error copy. Use text to explain the error; color alone does not carry the message.

**The Blue Continuity Rule.** Use the existing blue and neutral resource roles in both themes. Do not introduce a green UI palette. The original artwork's grass pixels belong to the retained image, not to the control palette.

**The Resource Pair Rule.** Resolve colors through Android resources. Keep the shared brand pair stable and use the corresponding night overrides for surfaces, text, control states, accents, and errors.

## Typography

The theme uses Android `sans`; medium emphasis is explicitly created with `sans-serif-medium`. There is no custom display face or bundled web font. Material widgets retain their inherited typeface and state behavior unless `Ui` overrides them.

The hierarchy is functional: app title, form section title, route title, current state, editable value, then supporting copy. The frontmatter records the observed sizes rather than a fabricated modular ratio. Wide and compact route/status roles are separate because the source changes their sizes at the layout breakpoint.

- **App title / section title:** identify the application and each form.
- **Route title / compact route title:** name the selected route alongside its geometry.
- **Status / compact status:** state what the active run is doing.
- **Field:** input values; labels and error appearances otherwise inherit Material behavior.
- **Body / button:** review copy and clearly worded actions.
- **Note / compact metric:** secondary explanations and the phone footer's distance/time.

`Ui.text` removes extra font padding and adds (3 dp) line spacing with a multiplier of (1). It does not set a fixed line height or custom letter spacing. `TextInputEditText` and `MaterialButton` do not use this text helper. Buttons explicitly preserve normal case.

**The Native Scale Rule.** Keep text in sp and geometry in dp. Preserve wrapping and the scrollable form when Android font scaling increases; do not compress the layout by fixing text heights.

## Layout

The root is a vertical Android layout: top bar, mode tabs, a weighted `ScrollView`, and a footer outside the scroll area. The original logo occupies (36 × 36 dp) beside the app title. The top bar uses (20 dp) horizontal padding; the scroll shell uses the medium spacing token and each workspace card uses the large token.

At `screenWidthDp >= 840`, route and configuration cards sit side by side with layout weights (0.9 : 1.1) and a medium gap. The route canvas is (240 dp) high, and status, metrics, and progress sit in the route card. This is a width check, not an orientation check.

Below that breakpoint, the cards stack with the same gap. The route becomes a compact summary with a (108 × 100 dp) canvas beside the route name/details. Status, metrics, and progress move into the footer. While a live run is active, these remain visible in either mode, alongside pause/resume and end controls. Idle history mode hides the compact live-status strip.

Paired fields and secondary actions use equal layout weights with (6 dp) inner margins on each side of the pair, producing the pair spacing token. Vertical gaps reuse the frontmatter spacing set; the route's wide preview adds a local (18 dp) top gap. No universal 24 dp spacing token is implemented.

The footer uses (20 dp) horizontal, (8 dp) top, and (12 dp) bottom padding. Its primary action expands to remaining width; run controls follow with small gaps. Footer buttons have (56 dp) layout height. Content scrolls above this persistent control area.

### Current visual refinements

- The top bar keeps `实时跑步 / 历史补跑` beside the title on wide screens and in the immediately following row on phones. `使用说明` sits directly before the 76 × 48 dp day/night control.
- The original 36 dp artwork is clipped to a 10 dp rounded square without changing its pixels. `DayNightToggleView` is a native Canvas component that keeps the 76 × 42 dp capsule, three halos, two clouds, six four-point stars, three craters, 650 ms motion, and accessible switch label from the supplied day-night-toggle specification.
- `ui_background` resolves to `#121212` in `values-night`; the theme transition handles `uiMode` in place so a theme change does not expose a black recreation frame.
- `RoutePreviewView` projects KML WGS84 points with Web Mercator, loads OpenStreetMap tiles asynchronously with a seven-day cache and attribution, and keeps the block/park/water canvas as a deterministic offline fallback. The preview never claims that this fallback is geographic imagery, and Keep's own Street layer remains authoritative for saved records.

**The Visible Run Rule.** Switching tabs must preserve the active run's status, distance/time, progress, and pause/resume/end controls. A blocked history action explains its state with “结束后补跑”.

## Elevation & Depth

Workspace cards use tonal separation, a (1 dp) outline, and zero card elevation. The footer is a plain surface, and the history review is a tonal inset. There are no custom shadow recipes, gradients, blur, or fabricated physical materials. Native Material buttons and dialogs retain their platform/library state treatment; zero card elevation is not a claim that every Android widget is shadowless.

**The Quiet Surface Rule.** Reuse surface tone and outlines to group workspace content. Keep cards flat at rest instead of adding decorative elevation.

## Shapes

Cards have the largest rounded corners, with smaller corners for buttons, fields, and the route canvas. The progress track has its own small corner radius. Use the frontmatter roles rather than one radius for all components.

The history review is a rectangular tonal inset created with a plain background color; the source does not round it. Route strokes have round caps and joins. The position marker is a surface-colored outer circle (7 dp radius) with an accent center (4 dp radius). These are route-specific geometry, not general icon tokens.

## Components

### Buttons

Clear text actions use `MaterialButton`. `Ui.button` chooses native filled or outlined styles, applies the button radius, sets a minimum height of (48 dp), and uses (3 dp) top/bottom insets. It removes the minimum width so context-sensitive secondary controls fit the footer. Horizontal padding and press/focus effects remain Material-owned; no browser hover treatment is defined.

The filled primary uses the brand pair and an accent stroke (2 dp). Secondary actions use tonal fill, accent text, and the stronger control outline (1.5 dp). Both variants use the dedicated disabled fill/text pair and soft outline when disabled, through a `ColorStateList` keyed to Android's enabled state. The main action changes between start, apply speed, history preview, Keep handoff, busy, and blocked states according to actual state. Native enabled/disabled state must remain functional, not only visual.

### Cards / Containers

`Ui.card` combines the surface fill, card radius, zero elevation, and outline. The route and configuration containers supply their own large internal padding. A generic call to `Ui.card` does not itself add padding.

### Inputs / Fields

`Ui.field` uses outlined `TextInputLayout` with `TextInputEditText`, the field radius, muted default hints, accent focused hints/stroke, and primary text. Values are single-line, select all on focus, and have a minimum height of (56 dp). Field errors use the native error slot; editing clears the field's current error. Preserve labels with units and existing input types.

Start/end fields first open `DatePickerDialog`, then a `MaterialAlertDialog` with three equal-width native `NumberPicker` columns labeled 时 / 分 / 秒. Each picker is (148 dp) high, displays two digits, and wraps within its valid range; labels use medium (14 sp) text. The row has (16 dp) horizontal and (8 dp) vertical padding. Dialog actions inherit the accent theme roles. Confirming writes `uuuu-MM-dd HH:mm:ss`; `RunDateTime` also accepts saved minute-only values and displays their seconds as `00`. Picker widgets retain native typography and interaction behavior.

### Natural Pace

Both forms expose a native `MaterialSwitch` labeled “自然配速”, with (14 sp) text and a minimum height of (48 dp). Its checked state is restored independently for each mode and defaults to enabled; switching it off selects uniform pace. The live switch is locked while a run is active or work is busy, and the live form displays current pace and speed during the run. Changing the history switch invalidates the prepared review.

The supporting copy explains a slightly faster opening for runs longer than 1 km, settling through the first kilometer into small, smooth changes. `PaceProfile` combines interpolated low-frequency noise at 145 m and 330 m with a sine component; its amplitude coefficient is 0.020–0.030, the opening factor makes the first kilometer faster, and the integrated curve is normalized by its mean. Historical duration, distance, and average pace therefore remain consistent with the requested inputs. These behavioral coefficients are implementation evidence, not visual design tokens. Switch track/thumb styling remains native Material behavior.

### Navigation

The native `TabLayout` contains “实时跑步” and “历史补跑”. The navigation height is in the frontmatter. Muted tab text becomes accent text when selected, and an accent underline marks the selected tab. The source does not define a custom tab font, custom icon set, or browser interaction states.

### Route Preview and Progress

`RoutePreviewView` draws the selected KML route geometry on a tonal canvas; it does not show map tiles or invented roads. It centers the geometry with a maximum inset of (28 dp), limited to (16%) of the smaller canvas dimension. The full route uses the outline color, and traveled geometry uses the accent, with a (3 dp) stroke. At zero distance the full route is accent-colored. Its accessible description names the route, point count, and approximate length.

The canvas remains a route overview. Help directs users to Keep record details → Fullscreen → Layers → tap the Street thumbnail → confirm; tapping the text alone may not trigger the layer. Hide map shows a flat background. New history handoffs adapt standard GPS/WGS84 KML coordinates to Keep's GCJ-02 chain, while GPX retains GPS coordinates. Existing cloud records are not rewritten, and alignment of a newly converted cloud record has not been independently verified.

Visible live progress interpolates from the current displayed distance over (220 ms) with native `DecelerateInterpolator(2)`. A new update cancels the previous interpolation, and route replacement or detachment cancels it too. History previews set distance immediately. `LinearProgressIndicator` uses accent fill, tonal track, and native progress animation when system animators are enabled; its animation duration is not overridden by the app.

### State and History Review

`Ui.reveal` cancels an existing view animation, then reveals a changed form, status, or review over (180 ms), from alpha (0.6) and vertical offset (6 dp) to the resting state. It uses `DecelerateInterpolator(2)`. Both reveal and route interpolation consult `ValueAnimator.areAnimatorsEnabled()` and apply the final state immediately when animations are disabled. Footer errors become visible immediately without a custom reveal animation.

History is one record at a time. The tonal review shows generated distance and trajectory details before an explicit Keep handoff dialog. Edits invalidate the prepared review. Keep the copy precise about preview, local preparation, and handoff; the UI does not establish server acceptance. `KeepRecordBuilder` writes the `vendor` identity (`source=KEEP`, `genre=KEEP_APP`) plus detail `source=keep_app` and `source_page=keep_app`; the SQLite summary uses Keep's normalized `sourceType=offline` value. Local parser and unit-test evidence do not establish Keep cloud/list source-badge acceptance. Status, error, and connection messages use polite accessibility live regions where implemented.

### Identity Assets

Use the recovered `ka_squ.jpg` artwork in the app bar. Its decoded pixels remain unchanged; JPEG provenance is embedded in a comment. Preserved launcher WebP assets have adjacent `.webp.json` provenance files. `docs/ASSET_PROVENANCE.json` is the authority for origins, source hashes, and current hashes. Do not reinterpret the photo's colors as UI palette tokens.

The Android resource preparation task copies source resources into a generated directory while excluding `.webp.json` metadata. This is a source packaging convention, not a claim that a particular APK has passed packaging or installation validation.

## Do's and Don'ts

### Do:

- Do retain the existing blue brand pair, original artwork, and kegent identity.
- Do resolve all theme colors through Android resources and their night overrides.
- Do use native Material controls, dp geometry, sp text, and system font scaling.
- Do keep active-run status and controls available when switching modes.
- Do draw actual route geometry and cancel superseded motion.
- Do honor system animation settings for reveals and progress.
- Do label generated history, local preparation, and Keep handoff according to the actual state.
- Do keep the Keep map help path explicit about tapping the Street thumbnail and confirming the layer.
- Do preserve the Keep vendor identity (`KEEP_APP`), detail source fields (`keep_app`), and offline summary classification (`offline`).
- Do retain raster provenance and use the provenance manifest when checking asset changes.

### Don't:

- Don't introduce a green UI palette or recolor the retained artwork to remove its original grass pixels.
- Don't invent browser tokens, hover states, font families, or tonal scales for this native app.
- Don't add decorative shadows to the flat workspace cards.
- Don't hide an active run's controls behind a disabled history action.
- Don't label a prepared or locally handed-off record as verified cloud success.
- Don't infer cloud/list source-badge acceptance from local JSON or SQLite fields alone.
- Don't treat provenance JSON sidecars as Android drawable or mipmap resources.

Source authority: `FakeGPSActivity.java`, `ui/Ui.java`, `ui/RoutePreviewView.java`, `core/PaceProfile.java`, `core/RunDateTime.java`, the Keep coordinate adapter, `keep/KeepRecordBuilder.java`, both color/theme resource configurations, `PRODUCT.md`, the running-workspace surface contract, README, and the asset provenance manifest. The refinement finish review at `.impeccable/review/refinements-v2-finish-review.md` records “ship”, no material fixes, and validates two new 600×1100 captures alongside the prior 14 refinement captures. This documentation pass read the source, product/surface contracts, and review artifact only; it performed no screenshot or device operations. The review does not establish Keep source-badge cloud/list acceptance or new cloud-record alignment; inherited Material defaults remain unexpanded.
