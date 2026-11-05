# Shared teal colour refinement

[Open the current/proposed gallery](design/colour-review/index.html). Sixteen actual Android captures compare Overview, an unsaved income form with an enabled primary button, Categories and Saved Changes in light and dark palettes. All scenes use fictional repositories, the same 390 dp / 1.0x content viewport and matching data/scroll positions. The income amount is LKR 1,250.50; nothing is submitted. Overview's recorded income LKR 120,000, expenses LKR 45,000 and surplus LKR 75,000 are unchanged.

## Decision

Keep the requested light primary **#237D68**. White button labels measure **4.9943:1**, versus **6.9525:1** with #166553. The candidate on the old #E3F1EA selected container measures only **4.2895:1**; the shared selected/highlight containers are therefore a paler mint **#EEF9F3**, giving primary-coloured navigation labels **4.6328:1**. Body text remains dark #182B29. Essential control outlines are slightly stronger so their contrast on the page background improves from **2.8817:1** to **3.3928:1**.

Dark mode was considered independently: a modestly fresher mint accent **#96DCC4**, deep mint containers **#234237**, and clearer outlines **#7B9587**. Button labels remain dark #0D382B, measuring **8.2410:1**; white labels are not substituted. Body text stays #ECF4F0. Error colours, error-container roles and all status meanings are unchanged. Unknown/rejected text remains explicit and is visible in the Saved Changes comparisons.

## Exact shared token changes

Only production file changed during this pass: `app/src/main/java/com/clarity/finance/ui/pilot/PilotTheme.kt`. Fourteen semantic assignments share six changed colour values. No new screen colour overrides or second theme. Typography, shapes, layouts, navigation, calculations, business state, repositories, schema, rules, synchronization, authentication and dependencies are unchanged.

| Mode | Role | Current | Proposed/applied |
|---|---|---|---|
| light | `primary` | `#166553` | `#237D68` |
| light | `primaryContainer` | `#E3F1EA` | `#EEF9F3` |
| light | `secondary` | `#166553` | `#237D68` |
| light | `secondaryContainer` | `#E3F1EA` | `#EEF9F3` |
| light | `surfaceTint` | `#166553` | `#237D68` |
| light | `outlineVariant` | `#82978D` | `#748B7F` |
| light | `outline` | `#82978D` | `#748B7F` |
| dark | `primary` | `#8BD5BC` | `#96DCC4` |
| dark | `primaryContainer` | `#203D33` | `#234237` |
| dark | `secondary` | `#8BD5BC` | `#96DCC4` |
| dark | `secondaryContainer` | `#203D33` | `#234237` |
| dark | `surfaceTint` | `#8BD5BC` | `#96DCC4` |
| dark | `outlineVariant` | `#718B7E` | `#7B9587` |
| dark | `outline` | `#718B7E` | `#7B9587` |

Unchanged foreground/surface/error roles include onPrimary, onPrimaryContainer, onSecondaryContainer, onSurface, onBackground, onSurfaceVariant, background, surface and error. Light error remains #A83732; dark error remains #FFB4AB. Success/pending eligibility is not inferred from colour.

## Measured contrast

Use WCAG relative luminance from linearized sRGB and `(Llighter + 0.05) / (Ldarker + 0.05)`. Decisions use unrounded values in [contrast.json](design/colour-review/contrast.json); this table rounds only for reading. The reference targets are 4.5:1 for ordinary text ([W3C Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)) and 3:1 for essential visible control cues ([W3C Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)). These are nominal specified role-pair checks, not antialiased-edge pixel ratios or a certification of every Material control/state. Inactive controls and human accessibility review are separate considerations.

| Mode | Pair | Proposed foreground/background | Current ratio | Proposed ratio | Target |
|---|---|---|---|---|---|
| light | Button label | `#FFFFFF` / `#237D68` | 6.95:1 | 4.99:1 | 4.5:1 |
| light | Primary on surface | `#237D68` / `#FFFFFF` | 6.95:1 | 4.99:1 | 4.5:1 |
| light | Primary on background | `#237D68` / `#F4F7F6` | 6.45:1 | 4.63:1 | 4.5:1 |
| light | Selected navigation label | `#237D68` / `#EEF9F3` | 5.97:1 | 4.63:1 | 4.5:1 |
| light | Body on surface | `#182B29` / `#FFFFFF` | 14.83:1 | 14.83:1 | 4.5:1 |
| light | Body on background | `#182B29` / `#F4F7F6` | 13.75:1 | 13.75:1 | 4.5:1 |
| light | Supporting text on background | `#506560` / `#F4F7F6` | 5.78:1 | 5.78:1 | 4.5:1 |
| light | Selected container label | `#182B29` / `#EEF9F3` | 12.73:1 | 13.75:1 | 4.5:1 |
| light | Status text | `#182B29` / `#EEF9F3` | 12.73:1 | 13.75:1 | 4.5:1 |
| light | Control outline / surface | `#748B7F` / `#FFFFFF` | 3.11:1 | 3.66:1 | 3:1 |
| light | Control outline / background | `#748B7F` / `#F4F7F6` | 2.88:1 | 3.39:1 | 3:1 |
| light | Control outline / container | `#748B7F` / `#EEF9F3` | 2.67:1 | 3.39:1 | 3:1 |
| light | Error foreground / surface | `#A83732` / `#FFFFFF` | 6.45:1 | 6.45:1 | 4.5:1 |
| dark | Button label | `#0D382B` / `#96DCC4` | 7.63:1 | 8.24:1 | 4.5:1 |
| dark | Primary on surface | `#96DCC4` / `#192724` | 9.09:1 | 9.82:1 | 4.5:1 |
| dark | Primary on background | `#96DCC4` / `#101B19` | 10.35:1 | 11.18:1 | 4.5:1 |
| dark | Selected navigation label | `#96DCC4` / `#234237` | 6.95:1 | 6.99:1 | 4.5:1 |
| dark | Body on surface | `#ECF4F0` / `#192724` | 13.83:1 | 13.83:1 | 4.5:1 |
| dark | Body on background | `#ECF4F0` / `#101B19` | 15.73:1 | 15.73:1 | 4.5:1 |
| dark | Supporting text on background | `#B2C5BD` / `#101B19` | 9.74:1 | 9.74:1 | 4.5:1 |
| dark | Selected container label | `#ECF4F0` / `#234237` | 10.56:1 | 9.85:1 | 4.5:1 |
| dark | Status text | `#ECF4F0` / `#234237` | 10.56:1 | 9.85:1 | 4.5:1 |
| dark | Control outline / surface | `#7B9587` / `#192724` | 4.20:1 | 4.78:1 | 3:1 |
| dark | Control outline / background | `#7B9587` / `#101B19` | 4.78:1 | 5.44:1 | 3:1 |
| dark | Control outline / container | `#7B9587` / `#234237` | 3.21:1 | 3.41:1 | 3:1 |
| dark | Error foreground / surface | `#FFB4AB` / `#192724` | 9.11:1 | 9.11:1 | 4.5:1 |

All **26 measured proposed pairs** meet their stated targets. Pixel analysis additionally confirms the actual old/new primary fills in both Overview and income-button captures (over 10,000 exact-colour pixels each); matching image dimensions were verified. The preserved selected semantics and status wording accompany the colour cues. No full TalkBack or human usability/contrast study was conducted.

## Verification actually executed

- Before editing, a verified backup of **348 saved files** was created outside the repository: `D:\clarity-finance-colour-review-backup-20261104.zip`. All archived hashes matched their saved sources. Final hash comparison confirms that the theme is the **only changed pre-existing file**; all 347 others, including Phase A/B reports, screenshots, manifests and unrelated IDE/review files, remain byte-identical. Previous review ZIPs were not overwritten. Their recorded palettes are historical evidence.
- Current palette build: `:app:assembleDebug :app:assembleDebugAndroidTest` succeeded. Proposed palette build: `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` succeeded; lint **0 errors / 22 warnings**. A final test-only rebuild succeeded after pinning the fictional form date to 2026-11-04 when the session crossed midnight. Production date behavior was untouched.
- Gradle prefix: `.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'`; all invocations used `--console=plain`.
- Matching current light/dark and final proposed light/dark instrumentation invocations each passed **1/1** (four executions of the same visual-flow test, not four distinct behavioral cases). The initial proposal also passed before its midnight date difference was noticed; the delivered proposal images are the matching-date rerun.
- Command: `adb -s emulator-5564 shell am instrument -w -r -e class com.clarity.finance.ColourReviewTest -e colourVariant current|proposed -e colourDark false|true -e pilotScreenshots true com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner`. Install used `-r` for the existing app/test APKs; storage was not cleared.
- Only the previously isolated API 26 device **emulator-5564** was used. Saved auth/recovery was checked with run-as before Application/instrumentation: no default authenticated user or recovery files, only the SDK heartbeat file. Explicit fake Koin repositories were used; mutation/recovery callbacks fail if invoked, and the test asserts no income/receipt/expense submissions. No live financial session, Pixel_6a access, networking/DNS alteration or device font/display change in this colour pass. Dark appearance is exercised through the test configuration, not an OS theme-setting change.
- `git diff --check`, token-only reverse comparison and saved-file hash preservation checks passed. HEAD remains `d38965de521aa1a02eade787c0d3ae95545beeeb`; nothing is staged, committed or pushed.

The accepted **271-unit-test** and Phase B financial/navigation evidence are reused; those unchanged suites and Firestore tests were not rerun for RGB-only changes. Prior API 35 instability remains outside this review. No new business behavior or dependencies require server publication.

## Added review files

- `app/src/androidTest/java/com/clarity/finance/ColourReviewTest.kt`: isolated read-only screenshot harness and existing-flow assertions. No application feature.
- This report, `design/colour-review/index.html`, `design/colour-review/contrast.json`, sixteen current/proposed PNGs and `design/colour-review/manifest.json`.

Backend files changed: **0**. No Phase C/D work, commits, pushes, deployments or live writes. The lighter proposal remains in the working theme for visual review.
