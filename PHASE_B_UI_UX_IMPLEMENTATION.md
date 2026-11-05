# Phase B UI/UX implementation

Phase B extends the accepted Phase A teal light/dark foundation to received income, pending agreements, projects, milestones, payment receipts, catalogue management and financial saved-change review. Stop here for visual review; savings, recurring, crypto and authentication redesign remain phases C/D.

## Baseline and preservation

HEAD was verified as `d38965de521aa1a02eade787c0d3ae95545beeeb`. No applicable AGENTS.md was found. The current uncommitted Phase A implementation, reports, screenshots, conventions and unrelated files were inspected and preserved.

Before production editing, 277 saved files were backed up outside the repository as `D:\clarity-finance-phase-b-backup-20261104.zip`. Every member matched its source SHA-256. Backup SHA-256: `36899F34E47D76A80DB31715A7AA68BDAAB816F7B4FAC5E354D02064E326DF02`.

The final comparison against that pre-B manifest finds 11 intentionally changed existing files, six new source/test files, and no missing files. The four unrelated files (.vscode/settings.json and the reporting/savings review diffs) remain byte-identical. Earlier Phase A reports, manifests and screenshots are historical evidence and were not rewritten. Backend file changes: **0**. No repositories, models, ViewModels, DI, routes, authentication, workers, schema, rules, indexes, build configuration or dependencies changed in Phase B. The existing Phase A expense ViewModel changes remain intact. Nothing was staged, committed, pushed or deployed; no live financial records were accessed or changed.

## Screens, ownership and interaction mapping

| Destination / surface | Existing owner and callbacks retained | Presentation changes |
|---|---|---|
| Received Income | IncomeListViewModel, existing income-list back-stack entry; changeMonth, retryIncome, add/edit/project/recovery navigation | Readable month; one Add Income action; grouped confirmed LKR total; distinct pending/cache qualifiers; collapsible By source; vertically grouped original/converted amounts, dates and source labels. Summary remains inside the LazyColumn so it cannot consume the transaction viewport. |
| Add / inspect / edit Income; Delete confirmation | AddEditIncomeViewModel, existing add/edit destination; load/reset/save/delete and stale Reload unchanged | Ordinary income opens read-only before Edit. Original currency and stored conversion basis/rate remain explicit. Archived existing references remain selectable under the original catalogue exception. Label-activated conversion choices; scrollable editor and retained deletion confirmation. |
| Pending agreements / Add agreement | PaymentListViewModel and AddPaymentViewModel on their existing entries; load/retry/create unchanged | Clear expected-versus-received purpose; grouped original-currency amount/outstanding; source names; compact source/currency selection; familiar primary action and one empty-state entry action. |
| Agreement details / Edit agreement | PaymentDetailViewModel, existing paymentId entry; edit/save/retry/cancel/recovery unchanged | Outstanding primary figure; grouped Agreement/Received/Unallocated; short unallocated meaning; cancelled unpaid history explicitly not collectible; primary Record payment / Add milestone separated from edit/cancel. |
| Add/Edit milestone; cancel milestone/agreement | Same PaymentDetailViewModel; original create/edit/cancel/Keep/Confirm callbacks and eligibility | Inherited source/currency, readable dates and grouped amounts; scrollable dialogs; consequences explain unpaid release and preserved agreement/received history. |
| Direct / milestone Record payment | PaymentConfirmationViewModel, existing receipt entry with paymentId and nullable milestoneId | Fixed Back/title outside scrolling form; clear inherited archived source and original currency; labelled conversion choices; unresolved fields visually locked when the existing ViewModel refuses edits; same retry payload/action. |
| Categories & Sources / Saved changes | Existing catalogue ViewModel owned by the authenticated navigation host; add/edit/archive/resolve/dismiss unchanged | Categories, Sources and Saved changes sections; readable names/archive badges; management separated from recovery; typed proposal details instead of raw payload dumps; explicit limited queue scope. |

No destination, route argument, Koin factory or ViewModel owner was added or replaced. Direct receipts still pass a genuinely absent milestone ID; linked income still routes to its protected project. Back uses the existing callbacks/back stack. Source rename/archive operates on stable IDs. Unknown catalogue labels remain unknown; active choices are not fabricated.

## Foundation, wording and financial meanings

Reuse `ClarityDesignTheme`, `PresentationFormat`, `PeriodSelector`, `DetailValue` and `StatusPanel` from Phase A. Add only shared responsive PageHeader, SelectionField, label-activated ChoiceOption and archived-reference display helpers. Scope theme adoption at Phase B screen roots; the remaining feature screens keep their current presentation until their own review.

Use existing teal semantic roles, 4/8/12/16/24 dp spacing, wrap/reflow at enlarged fonts, minimum Material touch targets and named Back controls. Phase B cards/dialogs use the existing surface role rather than inheriting an unrelated lavender default. Essential monetary values never use ellipsis or text shrinking. Amount grouping uses Long/BigDecimal presentation only; input remains ungrouped, stored rates/actual dates and all arithmetic are unchanged.

Received income, pending agreements, original USD, recorded LKR equivalents and goal/crypto/cash measures remain separate. Catalogue history shows actual archived metadata; missing data has an explicit unavailable label. A saved change is not success. PENDING, UNCERTAIN and REJECTED have different plain-language labels. Eligibility remains the existing `FinancialAttempt.rejected`, busy/locked/canModify flags and callbacks. Unknown operations cannot gain a Dismiss action. This panel explicitly covers transaction/project/catalogue proposals; crypto, savings and recurring retain their own panels.

Repository/model error details remain unchanged and are contextualized by the status headings; there is no new error-to-success mapping.

No new calendar, persistence store or draft lifecycle was introduced. This pass does not claim new income-draft recreation support; the pre-existing income load/reset lifecycle remains unchanged. Phase A's account-scoped expense draft/accepted-operation protection and context restoration remain intact and are included in regression checks.

## Verification environment and evidence boundary

Only the separately inspected `Clarity_UI_Pilot_API26` device (`emulator-5564`) was targeted: Android 8.0.0 / API 26 / x86_64, physical 1080 x 1920 at 420 dpi. Every adb call names that serial. Before Application/instrumentation startup, run-as inspection of saved prefs/files found no default Firebase auth and no recovery data; only the SDK heartbeat file existed. The check was repeated before batches. Standalone Koin graphs supply explicit fictional repositories; unexpected repository calls fail instead of falling back to live repositories. Production Application can start, but default auth is absent. Pixel_6a was never targeted, inspected, installed, launched or modified. No emulator storage clearing/uninstall, networking/DNS changes or live sessions.

Screens use a 390 dp normal-font content viewport or a 320 dp / 2.0x viewport on that physical device. Dark capture changes the test CompositionLocal configuration, not device/account state. The keyboard check uses the existing debug Activity with a real IME and visible-window bounds. Fake admission waits use genuinely suspending CompletableDeferred.await(), never blocking Mockito answers.

### Executed now

Common Gradle prefix (PowerShell):

```text
.\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

| Command suffix / check | Actual result |
|---|---|
| `:app:testDebugUnitTest --tests '*FinancialFeedbackTest' --tests '*Income*' --tests '*Payment*' --tests '*Catalogue*' :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` | Focused behavior/build passed. |
| `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain` | 271 tests, 0 failures/errors/skips; builds succeeded; lint 0 errors / 22 warnings. |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain` after final surface/wording refinements | Succeeded; lint 0 errors / 22 warnings. Unit result reused for these presentation-only refinements. |
| `:app:assembleDebugAndroidTest --console=plain` after final test additions | Succeeded; final explicit device selections below passed. |
| `git diff --check`, UTF-8 scan of Kotlin sources, pre-B content/hash comparison | Passed: no whitespace errors, all Kotlin source decodes as UTF-8, 277 pre-B files retained, and only the 11 documented existing files changed. |

Exact instrumentation command shape:

```text
adb -s emulator-5564 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5564 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5564 shell am instrument -w -r -e class <explicit classes or class#method> -e pilotScreenshots true com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

The earlier 35-case normal batch explicitly selects PhaseBVisualTest, PhaseBInteractionTest, PhaseBKeyboardTest, PaymentNavigationTest, CatalogueNavigationTest, IncomeListAccessibilityTest, PhaseAInteractionTest, PilotUiTest, PilotRiskRegressionTest, PilotRecreationTest and DiViewModelOwnershipTest. The final 22-case batch selects PhaseBVisualTest, PhaseBInteractionTest, PhaseBKeyboardTest, PaymentNavigationTest, CatalogueNavigationTest and IncomeListAccessibilityTest. Separate captures select PhaseBVisualTest#matchingInitialScreensFormsAndArchivedReferences, with `-e phaseBDark true -e pilotScreenshotSuffix -dark`, or `-e phaseBNarrow true -e pilotScreenshotSuffix -320-2x`.

An earlier isolated regression batch passed **35/35** (74.99 seconds), covering 35 distinct cases. After the final inherited-source label/dropdown surface changes, the clean **22 affected Phase B/navigation cases passed again** (53.804 seconds, screenshot capture disabled; all behavioral assertions enabled). Separate **1/1 normal**, **1/1 dark** and **1/1 enlarged** visual runs passed with capture enabled. The other 13 Phase A/ownership cases from the 35-case run are reused because their executable paths did not change. These runs overlap and are not additional distinct behaviors. The final complete unit/build/lint invocation (`final-reviewed.log`) also succeeded: **271 unit tests actually executed**, app/test builds passed, lint **0 errors / 22 warnings**.

For native dialog windows and the IME, a separate actual device-width/system-font check temporarily set only emulator-5564 to 840 x 1920 at 420 dpi (320 dp width) and Android font scale 2.0. The test asserts target Android configuration screenWidthDp=320 and fontScale=2.0 before composition. Its visual flow passed **1/1** (9.417 seconds); enlarged milestone/receipt interaction and real-keyboard checks passed **2/2** (9.012 seconds). Display/font restoration ran in finally: physical 1080 x 1920 / 420 dpi with no override, effective font scale 1.0. The originally unset font preference was removed after testing; Android normalized it to its default numeric 1.0. App storage, authentication, networking and DNS were preserved. Commands were `adb -s emulator-5564 shell wm size 840x1920`, `shell settings put system font_scale 2.0`, then `shell settings delete system font_scale` and `shell wm size reset`; every call specified emulator-5564. Density was read, never changed. `-device320-2x` images are these actual-window/system-font captures; `-320-2x` images constrain Compose content/font on the normal-width device and do not independently establish native-window font sizing.

Following the test-only modal-capture correction and archived-dropdown capture addition, three normal/dark/enlarged visual reruns and the enlarged milestone/receipt interaction also passed before the last display refinement. Capture/selector rebuilds succeeded. The already passing broader batch was not unnecessarily repeated.

During enlarged capture, two offscreen lazy-list lookups initially failed: the transaction was not composed, then Back correctly restored scroll away from Add Income. The capture now explicitly scrolls to the transaction and back to index 0 for the next task. Substantive display assertions remain. These were capture navigation issues, not missing records or an application-state reset.

The new unit assertions cover exact displayed original/converted details, truthful unavailable/archive labels and unknown versus rejected/pending status. Device assertions cover both USD income conversion bases, linked-receipt protection, nullable direct/milestone routes, actual receipt date/stored USD rate, project/milestone creation and edits with inherited source/counters, Keep/Confirm cancellation/deletion, source add/rename/archive stable identity and archived exclusion for new agreements, unknown catalogue disabling, pending/cached confirmed totals, locked unresolved receipt payload and repeat tap, account changes during suspended submission, long labels/large amounts, Back and existing Phase A ownership/restoration.

Fakes test production navigation, screens, ViewModels and callback payloads. Their mutation recorders do not prove Firestore atomicity or real cancellation allocation release. No live acceptance, offline restart/reconnect or new server proof was performed in this UI pass. Unchanged financial/rule behavior relies on prior accepted evidence, including the 147-rule-test result; that suite was not rerun. Earlier API 35 native instrumentation instability remains unresolved and no new API 35 pass is claimed.

### Interrupted runner and capture separation

One later 22-case invocation lost normal host output after case 14. Device TestRunner logged all 22 completed with zero failed assertions, but runner teardown threw `IllegalStateException: UiAutomation not connected`; the host ADB daemon had also restarted. This invocation is **not counted as a clean pass**, and no application failure cause was assumed. Functional assertions were rerun without image capture and produced the clean 22/22 result above. Screenshot flows were then executed in standalone invocations, each with a normal successful runner result. No tests/assertions were removed, and no production/recovery change was made to address this test-infrastructure failure. Private Logcat remains excluded. This API 26 instrumentation/UiAutomation incident is distinct from the earlier unresolved API 35 native instability.

### Failures investigated during this pass

- Baseline screenshots initially failed because onRoot() matched both the screen and modal root. The helper initially selected the last root; image review showed that this could still be the underlying page. It now explicitly selects dialog/popup semantics. Compose dialog capture is unsupported below API 28, so API 26 dialogs use UiAutomation.takeScreenshot() on the inspected fake-only device, with a bounded 400 ms wait for platform animations outside the Compose test clock. Those captures include the full physical screen. The visual flow closes the keyboard for the initial milestone-form image; separate tests verify real-keyboard reachability. The three variants and enlarged interaction were rerun after this correction. A Cancel selector was scoped to its dialog. Baseline capture then passed.
- Initial production compile caught unlabelled early returns inside the scoped theme lambda and a helper parameter shadowing the catalogue payload; corrected before successful builds.
- First Phase B run: 12/13 passed; header Back was not visible after enlarged receipt scrolling. Keep the receipt header outside the scrolling body.
- Expanded run: 11/14 passed. One keyboard-panned Back check needed to close the IME before checking header navigation; separate real-keyboard action visibility passed. The other failures were ambiguous dropdown/Overview selectors, now scoped to popup/clickable destination. Three targeted reruns passed. No financial eligibility assertions were removed or relaxed.
- Final source review normalized two Phase B files to UTF-8 after an editing helper introduced a non-UTF-8 middle dot; source/rules/backend content was not altered beyond the scoped UI work. Final whitespace review also removed five trailing spaces introduced beside dialog arguments; this was non-executable cleanup, so the applicable passing build/unit/lint evidence was reused.

Automated Compose visibility/semantics and screenshot inspection are not actual TalkBack, human usability or a comprehensive contrast audit. Those remain review tasks. Unsaved income-draft recreation and process-death draft retention were not newly verified or promised. Financial queue persistence/retry protocols were not modified.

The corrected image also exposed a remaining raw source ID in Add milestone. It now uses the same catalogue-name/archive display helper as the other Phase B surfaces. The regression scopes that assertion to the dialog, rather than accidentally matching its underlying project. Existing source identity, inheritance and choice eligibility are unchanged. Dropdown surfaces were aligned with the existing surface role. Final full Android checks and affected device cases were rerun for these last presentation changes.

## Screenshot review

Open [the gallery](design/phase-b/index.html). All data is fictional. Normal before/after use matching 390 dp / 1.0x light viewports and the same fixture values: ordinary salary LKR 100,000; USD 100 at stored 310 LKR/USD = LKR 31,000; linked receipt LKR 150; total LKR 131,150. Agreement LKR 1,000, received LKR 150, outstanding LKR 850, allocation LKR 400 and unallocated LKR 550. Milestone LKR 400 agreed / LKR 100 received. These are isolated in-memory examples, not account balances.

Income, agreement list, project and catalogue screenshots capture initial positions without scrolling to amounts. Form captures focus on relevant fields; recovery is scrolled to its action; dialog images use the API 26 full-device fallback so the actual modal is visible. The earlier before-milestone image captured only its underlying page; it is retained as an explicitly incomplete artifact, not a valid modal comparison. The before archived income opens directly in editing; after opens in intentional read-only inspection. Most captures are Compose content; API 26 dialog captures include the physical screen; the keyboard case proves real IME bounds programmatically but its PNG may not include the IME outside Compose. Narrow screenshots intentionally show a partial viewport: scrollability and controls are separately asserted, not a claim that all content fits at once. Dark/enlarged samples have no matching pre-B variant; only normal light pairs are direct comparisons.

The gallery contains **62 PNGs**: nine normal-light before/after pairs plus dark, 320 dp/2.0x, archived-dropdown, pending/cached/uncertain, keyboard and corrected modal examples. The incomplete pre-B modal capture is preserved but explicitly labelled and not paired. No private screenshots are included.

## Exact Phase B source/test files

- `app/src/androidTest/java/com/clarity/finance/IncomeListAccessibilityTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PaymentNavigationTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseBFixtures.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseBInteractionTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseBKeyboardTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseBVisualTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PilotFixtures.kt`
- `app/src/main/java/com/clarity/finance/ui/CatalogueScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/AddEditIncomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/IncomeListScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/AddPaymentScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ExpectedPaymentsScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/PaymentConfirmationScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ProjectDetailScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/pilot/DesignComponents.kt`
- `app/src/main/java/com/clarity/finance/ui/pilot/FinancialFeedback.kt`
- `app/src/test/java/com/clarity/finance/ui/pilot/FinancialFeedbackTest.kt`

No deletions. Report, gallery and all `design/phase-b/*.png` files are new review artifacts. The ZIP manifest lists every archived file and its SHA-256. The complete saved `PilotTheme.kt` and `PresentationFormat.kt` are included as byte-identical Phase A supporting files, not Phase B changes. The package supplements the existing Phase A implementation; it is not a replacement repository. Credentials, Firebase/local configuration, build outputs, private diagnostics, unrelated review diffs and earlier archives are excluded.

## Bounded manual visual acceptance

1. History > Received Income: inspect initial month/recorded total, By source, scroll actual rows, and Back. Open ordinary income, inspect original/converted values and archived source, Edit/backdate, then Keep or Confirm Delete on isolated data. Open a linked receipt: it must route to its agreement.
2. Plans > Pending Payments: Add agreement with source/currency/title/date, inspect expected-versus-received copy. Open agreement: compare all four monetary meanings. Edit, add/edit milestone, Record payment directly and via milestone; verify inherited source and distinct currency/LKR conversion. Keep/Confirm cancellations and inspect retained partially paid history.
3. More > Categories & Sources: add/rename/archive category and source; Keep must not archive. Inspect archived historical names; new forms exclude them under existing rules. Open Saved changes; confirm queue scope, original amount/context, and different unknown/rejected eligibility. Do not dismiss unknown work.
4. Repeat with long names/large amounts, 320 dp/2.0x, real keyboard and both system themes. Back must preserve the existing entry/period and not create duplicate owners. Repeated Save must not duplicate local submissions. Switch fake account during suspended work and check old forms/messages disappear.
5. Human review: read aloud with TalkBack, verify focus order and practical touch/contrast usability. This has not been executed automatically.

Stop after Phase B. Phase C covers savings/recurring/crypto; Phase D covers authentication and remaining shared surfaces. Both must reuse this foundation and keep the accepted financial/recovery semantics. No later-phase implementation was undertaken.
