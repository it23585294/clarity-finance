# Phase A — shared visual foundation and refined pilot

4 November 2026. Git HEAD remains `d38965de521aa1a02eade787c0d3ae95545beeeb`. All pilot work is uncommitted. This report supersedes the original Phase 1 implementation report for the **current saved source**; historical evidence is distinguished below.

## Intended outcome and this review boundary

The intended final outcome is a coherent, professional redesign of the **whole app, including authentication**. This pass completes **Phase A only**: visual foundation, Overview → Add Expense → monthly History/expense inspection. Income, agreements, catalogues, savings, recurring, crypto and authentication retain their existing feature-screen presentation until their own review phases. All accepted destinations remain accessible through the shell, existing links or simple Plans/More hubs.

Design references `UI_UX_AUDIT_AND_PLAN.md` and `design/ui-ux/` are unchanged. Shared conventions and the complete A–D rollout/acceptance gates are in [design/phase-a/CONVENTIONS.md](design/phase-a/CONVENTIONS.md). The scope adapter `PilotTheme` delegates to **one** `ClarityDesignTheme` foundation. Later phases reuse that foundation; after rollout the old global palette/adapters should be replaced, not maintained as competing design systems. No later-phase screen redesign was implemented here.

No applicable AGENTS.md was found at the drive/repository roots or in the source/design tree. The current pilot, original design artifacts, unrelated IDE settings and the three historical review diffs were inspected and preserved. Before this pass, **248 saved files** were backed up outside the repository to `D:\clarity-finance-phase-a-backup-20261104.zip`; every archive member was verified against its source SHA-256. Backup hash: `5FEE530935380D763CBBC36003CF76659B37D28B19588F9005F09FDBA2BB4197`.

**Backend files changed: 0.** Repositories, data models, schemas, arithmetic, durable recovery, sync workers, operation identities, Firebase configuration/target, rules/indexes, authentication logic, Koin modules and dependency/build versions are byte-identical to the pre-pass snapshot. The existing two presentation ViewModel corrections from Phase 1 remain intact. No live financial account/data access, financial writer, Pixel_6a command, commit, push or deployment occurred.

## What changed and why

- **Overview:** a compact readable month selector, prominent Add Expense and a grouped cash card replace repeated month buttons and separate tall blocks. Normal 390 dp / 1.0x assertions verify received income **LKR 120,000.00**, expenses **LKR 45,000.00** and surplus **LKR 75,000.00** visible together at initial scroll. “Based on recorded transactions” and the not-bank-balance/not-automatic-savings meaning remain visible. Longer definitions use named information actions. Separate crypto, current collectible original-currency pending balances and the existing savings card/links remain below; no invented balances, goal, chart or sync timestamp.
- **History:** month → Expenses/Income/Crypto history → category → records. The named plus action opens Add Expense; **History options** exposes Spending insights and All recorded expenses. Transaction rows lead with exact grouped amounts, then readable category/date and note. The monthly total explicitly says **all categories**, including under a category filter. Income and linked receipt routing are unchanged. Category, month, mode and scroll survive inspection/Back and hub visits. Back from Insights returns to History; shell Back still returns to Overview.
- **Expense capture:** amount-first typography, reviewable classifications, a labelled calendar action beside the ISO date, and short helper text. Manual entry/backdating and existing validation remain. The Material 3 calendar uses date-only UTC conversion and the existing **Asia/Colombo today** upper boundary, disabling future selections; enlarged text uses its editable-date mode. Calendar selection does not change stored date semantics. Note, Save, Cancel and validation remain scrollable/keyboard-reachable.
- **Inspection:** readable date, grouped full amount and labelled recorded fields precede Edit. Existing Delete confirmation, Keep/Cancel, stale/version handling and pending contribution protections remain. Long labels and amounts wrap without ellipsis, shrinking or fixed-height cards.
- **Foundation:** explicit system-following teal light/dark palettes, defined typography/shape scale, consistent period/info/detail/status components and familiar labelled navigation icons with selected semantics. New components remain scoped to the pilot; unfinished screens do not inherit a global redesign accidentally.
- **Truthful feedback:** cached data remains qualified next to surplus and per section; unavailable inputs never yield a fabricated surplus. Reporting recovery is conservatively labelled unresolved/rejected-conflicting, with the existing review action. Pending edits/deletions retain recorded contributions. No timeout/access error is reclassified by UI, and no permanent success message/global operation count is invented. Empty successful reporting no longer offers a redundant Retry button; errors retain the same existing reload action.

## Ownership, restoration and preserved behavior

The navigation/ownership mapping was reviewed **before** editing and retained:

| Owner | Current responsibility |
|---|---|
| Activity | Existing AuthViewModel and CatalogueViewModel through Koin/AndroidX. Catalogue content is exposed only for the active UID. |
| Authenticated `home` back-stack entry | HomeViewModel, one DashboardViewModel and one GoalCardViewModel. Overview/History share reporting; tabs do not create workers or repositories. |
| Shell | Saveable selected tab/report page/mode and separate scroll states; SaveableStateHolder retains reporting UI across Plans/More. Bottom navigation is outside scrolling content and hidden on focused routes. |
| Add/Edit Expense back-stack entries | Existing entry-owned ViewModels/factories. Six draft fields remain transient UID-scoped state in the retained AddExpenseViewModel, separate from durable submission recovery. |
| All other features | Existing routes, ViewModel owners, optional milestone query argument and linked income→project routing. All 17 original route declarations remain, including legacy dashboard/all-expenses entry points. |

The previous demonstrated draft and radio-label repairs are preserved: Activity recreation retains the route/draft, UID changes clear it and ignore old-owner edits, label taps select the radio option, and a new submission ViewModel cannot restore an outstanding/accepted operation as a new unsent draft. A genuinely suspended admission plus rapid repeated Save reaches the fake repository once. The original retained owner survives recreation while admission is outstanding. **Unsaved draft process-death persistence is not promised; Activity recreation and saved-instance simulation are not process death.** Durable admitted operations continue using the unchanged accepted queue/protocol.

## Device safety and evidence method

Only the separate **Clarity_UI_Pilot_API26 / emulator-5564** was used: Android **8.0.0**, API **26**, **x86_64**, 1080 × 1920 physical display, 420 dpi. The existing official AOSP image/isolated AVD was reused and started without wipe/uninstall/storage clearing or snapshots. No existing live-account device was inspected. Pixel_6a, its storage/network/DNS workaround, other AVDs, credentials and Firebase target were untouched.

**Before Application/instrumentation startup**, an explicit-serial `run-as` check inspected saved preferences/files without starting Application: no default Firebase user, no queue/recovery files; only the SDK heartbeat file. The check was repeated before later instrumentation batches. Production Application therefore had no signed-in owner to synchronize. Every screen/test received an explicit standalone fake-only Koin graph with no production repository fallback. Fake account emissions never sign the Firebase SDK in. No live acceptance writer was selected.

Normal captures use **390 dp / 1.0x**. Narrow checks use **320 dp / 2.0x**; existing tests also cover 1.3x. Width/font are Compose overrides, not claims about a system-wide accessibility setting. Dark captures use actual device night mode, restored to light afterward. The separate test AVD remains storage-preserved. The debug test host still mirrors production edge-to-edge/outer Scaffold framing and has no production-graph fallback.

## Verification executed in this refinement

All Gradle commands use the existing wrapper and caches. Resource bounds are command-line-only, not project configuration changes:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 `
  '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' `
  '-Pkotlin.compiler.execution.strategy=in-process' `
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

| Executed check | Actual result |
|---|---|
| Baseline app/test builds and opt-in PhaseAVisualTest before production edits | Successful; **1/1** fake-only capture flow. Four matching “before” screenshots saved. |
| Focused `testDebugUnitTest --tests '*PresentationFormatTest' --tests '*AddExpenseViewModelTest' --tests '*EditExpenseViewModelTest'`, app/test builds | Successful. Covers exact grouping/sign/extreme Long amounts, readable dates/device-locale independence and retained draft/inspection state. |
| Full command above | **268 unit tests, 0 failures/errors/skips; app/test builds successful; lint 0 errors / 22 warnings.** XML reports inspected. This is current evidence, not the earlier 266-test pilot result. |
| Core device run: PhaseAVisualTest (1), PhaseAInteractionTest (3), PilotUiTest (5), PilotRiskRegressionTest (3), PilotRecreationTest (1), PilotKeyboardTest (1), DashboardNavigationTest (4) | **18/18 passed** on refined production UI with isolated data. Includes first-viewport cash values; initial History offset zero; calendar/backdating/future rejection; long archived name/Long.MAX_VALUE at 2.0x; mode/month/hub context; double Save; UID clearing; cached/unavailable/overflow/invalid records; actual Activity recreation and real IME bounds. |
| Dark repeat: PhaseAVisualTest, PilotUiTest and long-name/amount interaction case | **7/7 passed.** Actual system dark mode; captures retained. Repeats, not seven additional distinct cases. |
| Narrow repeat: PhaseAVisualTest with `-e phaseANarrow true` | **1/1 passed**, 320 dp/2.0x initial Overview/History plus form/inspection captures. This is a repeated case. |
| Existing feature run: PaymentNavigationTest (2), CryptoNavigationTest (1), GoalNavigationTest (4), RecurringNavigationTest (4), CatalogueNavigationTest (2), DiViewModelOwnershipTest (1), IncomeListAccessibilityTest (1), CryptoFeedbackUiTest (1), AuthAccessibilityTest (2) | **17/18 initially passed**; recurring dashboard assertions still expected ungrouped `40250.50`. Only those two expected labels changed to `40,250.50`. Test APK rebuilt; targeted original AT09 test **1/1 passed**, retaining counts, consumed identities and exact amount assertions. All **36 distinct device cases** across core and feature runs passed across the documented runs; no single clean final 36-case run is claimed. |
| Final test-only `assembleDebugAndroidTest` after those selectors | Successful. Main source remains byte-identical to full unit/build/lint run; those results apply unchanged. |
| `git diff --check`, protected/unrelated hashes, complete archive member SHA-256 comparison | Completed before delivery; see manifest and packaging result. Nothing staged or committed. |

Representative device commands (all ADB commands explicitly target **emulator-5564**):

```powershell
& $adb -s emulator-5564 shell am instrument -w -r `
  -e class 'com.clarity.finance.PhaseAVisualTest,com.clarity.finance.PhaseAInteractionTest,com.clarity.finance.PilotUiTest,com.clarity.finance.PilotRiskRegressionTest,com.clarity.finance.PilotRecreationTest,com.clarity.finance.PilotKeyboardTest,com.clarity.finance.DashboardNavigationTest' `
  -e pilotScreenshots true com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5564 shell cmd uimode night yes
# Dark subset: -e pilotScreenshotSuffix -dark; then night no.
# Narrow capture: PhaseAVisualTest -e phaseANarrow true -e pilotScreenshotSuffix -320-2x.
& $adb -s emulator-5564 shell am instrument -w -r `
  -e class 'com.clarity.finance.RecurringNavigationTest#AT09RentIgnoredUtilitiesAndEditedActualUpdateDashboardExactlyOnce' `
  com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

The first sandboxed wrapper call failed on its external Gradle cache lock before compilation; it was rerun with authorized cache access. No test hang or application crash occurred in these current checks. Earlier intermediate screenshots were superseded by final captures with explicit initial-scroll assertions. No financial assertions/tests were removed, weakened or redirected to live data. Selector changes are confined to intended shell, grouped labels and named icon/menu controls.

### Reused versus unexecuted evidence

- **Earlier Phase 1:** 266 unit tests, builds/lint 0/22, 10 focused device cases and 22 existing cases across its documented run/rerun. Its 17 captures remain unchanged in `design/pilot-ui/`; this pass's full 268-unit run and current device results supersede relevant UI evidence. The earlier pre-repair draft/label failures and fresh-owner resurrection regression remain documented in test/source history and the preserved original review ZIP/backup; their fixes remain in the saved tree.
- **Unchanged backend:** earlier accepted protocol/rule/emulator/live evidence is reused only for unchanged backend behavior. No Firestore suite or live reconnection/account test was rerun for this UI-only milestone.
- **Limits:** current runtime verification is API26 only. The previously unresolved **API35 native instrumentation instability remains unresolved**; no clean new API35/full-suite pass is claimed. No real process-death/reconnection financial acceptance, live account switching, clock manipulation, background sync guarantee or live financial record was exercised here.
- **Accessibility evidence:** automated selected/radio semantics, named calendar/period/navigation/info controls, minimum navigation targets, large-font reachability, full textual values, deletion Keep/Cancel and a real IME with Save fitting above its visible boundary. **TalkBack traversal, a complete contrast audit and human usability/timed expense-entry trials were not executed.** Do not infer these from screenshots or passing semantics tests.

## Screenshot evidence

[Current matching gallery](design/phase-a/index.html): **22 new unedited app-only screenshots**, containing four before/four after comparisons at matching **390 dp / 1.0x / light** settings, plus dark, 320 dp/2.0x, maximum amount/long name, cached/unavailable, real-keyboard and saved-proposal examples. Overview and History normal comparisons are **initial scroll**, before scrolling to amounts/records; the final test additionally asserts all three cash values displayed and History offset zero. Other examples may be scrolled to the named element, explicitly labelled in the gallery.

Fictional baseline: rent 34,000, groceries 6,500, transport 4,500, received ordinary income 120,000; expenses 45,000 and surplus 75,000. A separately shown pending 750 proposal is excluded. Crypto and pending projects are known empty; no goal is fabricated. The maximum-Long archived-label fixture is separate from that baseline and never submitted. Dates derive from current Colombo date; nothing was written to Firebase. IME pictures capture app content above the real keyboard, not keyboard pixels outside the Compose root. The earlier `design/pilot-ui/` gallery remains historical and unchanged; its original before captures were at a different width, so use the new gallery for matching comparisons.

## Current exact saved source/test/config manifest

The **31 files below** comprise the entire uncommitted pilot implementation relative to the committed baseline (including preserved prior fixes), not just this refinement. Five were added and nine existing pilot files were refined in this pass; the remaining accepted source files are preserved byte-for-byte. The package also includes this report, the updated SHA manifest, all 17 original pilot PNGs/their gallery, and the 22 new PNGs/new gallery/conventions document. **Deletions: none.** Generated output, Gradle/SDK/AVD files, private logs/scripts, credentials, local configuration, original unrelated IDE/review files and archives are excluded.

- `app/src/androidTest/java/com/clarity/finance/CatalogueNavigationTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/CryptoNavigationTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/DashboardNavigationTest.kt` — refined in this pass
- `app/src/androidTest/java/com/clarity/finance/GoalNavigationTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/IsolatedAppGraph.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PaymentNavigationTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PhaseAInteractionTest.kt` — new in this refinement
- `app/src/androidTest/java/com/clarity/finance/PhaseAVisualTest.kt` — new in this refinement
- `app/src/androidTest/java/com/clarity/finance/PilotBaselineCaptureTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PilotFixtures.kt` — refined in this pass
- `app/src/androidTest/java/com/clarity/finance/PilotKeyboardTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PilotRecreationTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PilotRiskRegressionTest.kt` — preserved Phase 1 implementation
- `app/src/androidTest/java/com/clarity/finance/PilotUiTest.kt` — refined in this pass
- `app/src/androidTest/java/com/clarity/finance/RecurringNavigationTest.kt` — refined in this pass
- `app/src/debug/AndroidManifest.xml` — preserved Phase 1 implementation
- `app/src/debug/java/com/clarity/finance/PilotTestActivity.kt` — preserved Phase 1 implementation
- `app/src/main/java/com/clarity/finance/ui/dashboard/DashboardScreen.kt` — refined in this pass
- `app/src/main/java/com/clarity/finance/ui/expenses/AddExpenseScreen.kt` — preserved Phase 1 implementation
- `app/src/main/java/com/clarity/finance/ui/expenses/EditExpenseScreen.kt` — refined in this pass
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpenseFormContent.kt` — refined in this pass
- `app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt` — preserved Phase 1 implementation
- `app/src/main/java/com/clarity/finance/ui/pilot/DesignComponents.kt` — new in this refinement
- `app/src/main/java/com/clarity/finance/ui/pilot/PilotShell.kt` — refined in this pass
- `app/src/main/java/com/clarity/finance/ui/pilot/PilotTheme.kt` — refined in this pass
- `app/src/main/java/com/clarity/finance/ui/pilot/PresentationFormat.kt` — new in this refinement
- `app/src/main/java/com/clarity/finance/viewmodel/AddExpenseViewModel.kt` — preserved Phase 1 implementation
- `app/src/main/java/com/clarity/finance/viewmodel/EditExpenseViewModel.kt` — preserved Phase 1 implementation
- `app/src/test/java/com/clarity/finance/ui/pilot/PresentationFormatTest.kt` — new in this refinement
- `app/src/test/java/com/clarity/finance/viewmodel/AddExpenseViewModelTest.kt` — preserved Phase 1 implementation
- `app/src/test/java/com/clarity/finance/viewmodel/EditExpenseViewModelTest.kt` — preserved Phase 1 implementation

## Bounded visual review checklist and later rollout

1. At 390 dp / normal font, open Overview with isolated fixtures. Confirm all three cash measures initially visible, month readable, Add Expense discoverable, essential surplus qualification and information action. Scroll to separate crypto, current original-currency pending and savings links.
2. Open History. Confirm transaction-led initial content; month arrows; Expenses/Income/Crypto history; category filter and explicitly all-category monthly total. Open History options → Insights and All recorded expenses. Inspect a row, Back, visit Plans/More and return: period/filter/mode/scroll must remain.
3. Open Add Expense. Leave amount blank/zero and try Save; inspect validation. Enter an exact amount, backdate manually and through the calendar, try a future date, choose classifications by labels and inspect their information action. Verify Save/Cancel with the real keyboard at 320 dp/2.0x.
4. Inspect/edit/backdate and Keep/Confirm deletion using isolated fixtures. Verify original recorded contribution through pending/rejected states, independent cache/errors, no fabricated zero and linked receipts routing to their project.
5. Recreate Activity before and during a genuinely suspended Save; then change fake owner. Verify retained same-owner draft/one submission and cleared old-owner forms/feedback. A new submission owner must start empty. Do not treat this as process death.
6. Review both system themes and long names/large amounts. Human TalkBack/contrast/usability review remains a separate acceptance gate. Do not enter live sessions to obtain visual evidence.

Proceed only after visual review to **B** (income, projects/milestones/receipts, catalogues), then **C** (savings, recurring, crypto), then **D** (authentication/shared surfaces/final consistency). All use the same conventions and retain their independent financial meanings/security/recovery controls. No later-phase implementation, commits, pushes, deployments or live changes were performed.

## Review package

The new `clarity-finance-phase-a-ui-review-20261104.zip` contains **75 files**: the 31 current source/test/config files, this report, 39 PNGs across the preserved/current galleries, two gallery HTML files, shared conventions, and the SHA-256 manifest. No deletions. Every archived member is verified against its saved source; unrelated local work and historical design/review artifacts remain on disk uncommitted.
