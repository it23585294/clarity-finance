# Phase D: authentication and whole-app consistency

Phase D completes the approved design rollout. The application now has one system-following teal theme, including authentication, startup, all recorded expenses and shared dialogs. Existing financial behavior, authentication logic, ViewModel ownership and recovery protocols are retained.

[Open the review gallery](design/phase-d/index.html). Screenshots use explicitly isolated repositories and fictional data. Normal light comparisons use the same 390 dp / 1.0x content viewport before and after; dark/enlarged/keyboard examples are labelled separately.

## Preservation and scope

HEAD remains `d38965de521aa1a02eade787c0d3ae95545beeeb`. No staging, commits, pushes, deployment, live writes, dependency or Firebase-target changes. Pixel_6a was not targeted. Earlier Phase A/B/C and colour-review reports, galleries and manifests remain preserved.

Before edits, 476 saved working files were backed up to `D:\clarity-finance-phase-d-backup-20261105.zip`; all archived members matched their source SHA-256. Backup SHA-256: `E09604E32C7AB4C35C9BB094EACC0AC515BC14A0563D061B40644062579B45FF`. This phase's delta is measured against that working snapshot, rather than HEAD, which would include the earlier uncommitted phases.

Backend files changed: **zero**. No financial/domain calculations, models, repositories, schemas, rules, indexes, auth ViewModel/repository, DI modules, queues, operation IDs, workers or recovery stores changed. The Phase C native keyboard-bounds regression remains byte-identical. Unrelated `.vscode/settings.json`, `reporting-review-fixes.diff`, `savings-goals-deadline-fixes.diff` and `savings-goals-recovery-fixes.diff` are preserved.

## Changes and boundaries

- **Authentication:** shared presentation for Login/Register, concise headings, labelled email/password/confirmation fields, show/hide semantics, readable error panel, and Next/Done keyboard actions. Submission still calls the same AuthViewModel methods; disabled-button state still comes from `isSubmitting`, and the existing ViewModel guard handles repeated/IME submission. Navigation callbacks and existing password clearing on disposal remain. Password/visibility state uses ordinary `remember`, never `rememberSaveable`, a store or SavedStateHandle. No reset/social login or new auth behavior.
- **All recorded expenses:** a clear heading, reachable Add Expense, a single scrollable list, grouped full amounts, readable dates and catalogue references including archive labels. Cached/pending/unresolved/rejected evidence stays distinct. The original inspect/retry eligibility conditions and callbacks are preserved. Empty-list wording does not claim a confirmed zero; the existing list state does not carry cache provenance for an empty result, so the UI qualifies it rather than inventing server confirmation.
- **Inspection:** the context-dependent Back to history label becomes neutral Back with the same callback. Missing-expense/income return controls also use Back. Recurring confirmation → actual expense → Back still restores the recurring destination. Linked-record protections and deletion confirmations remain.
- **Shared surfaces:** labelled loading panels reuse existing state branches; no fabricated progress, counts or sync success. Plans/More retain every destination, with clearer account grouping and accurate recovery scope. Saved Changes still leads to Categories & Sources; choose its Saved changes tab. This is not described as an all-feature recovery centre.
- **Theme:** the accepted Phase A/B/C foundation is consolidated into `ui/theme/Theme.kt` as the single `ClarityTheme`, already hosted by MainActivity. Temporary `PilotTheme` / `ClarityDesignTheme` adapters and feature-local theme wrappers are removed. Feature entry owners, Koin resolution calls, route arguments, navigation-owner clearing and optional milestone query arguments remain. No presentation helper or feature folder was unnecessarily moved.
- **Semantic roles:** primary `#237D68`, mint `#EEF9F3`, dark `#96DCC4` / `#234237` and the approved text/error colours are retained. Previously unspecified Material surface/container/tertiary/inverse roles now derive from those approved colours instead of inheriting lavender defaults. Dynamic wallpaper colours no longer replace the approved app palette. Typography/shapes remain the shared foundation. Nominal text-role contrast and outline visibility have focused tests; these do not certify every disabled/control state.
- **Android window bridge:** the light-only native window style and unused template colour resources are replaced by day/night window background/accent bindings. Only those two roles are mirrored from the canonical palette for pre-Compose/platform surfaces; this is not another Compose design system. A device regression compares both configuration-qualified resources with the canonical theme. MainActivity, authentication, Firebase and application startup/synchronization code are unchanged.

The earlier design-convention/report files remain historical evidence. This report records the final canonical theme and removal of their temporary scope adapters.

## Complete screen/theme coverage checklist

Theme coverage here means every current destination/dialog inherits the one application root; it does not mean every possible error/race was manually exercised in this phase. Device evidence is listed separately below.

| Reachable surface | Theme and consistency coverage | Behavior retained / evidence paths |
|---|---|---|
| Initial session loading | Shared root, labelled Opening Clarity | Auth session initialization; no invented user or zero balance |
| Native launch/window surfaces | Day/night background/accent bridge, no legacy purple resources | Same Activity/manifest and startup flow; configuration-resource parity regression |
| Login | Shared auth presentation, error/visibility/keyboard controls | Existing validation, generic failure mapping, submission guard and navigation |
| Register | Same field/button/error conventions; six-character guidance matches existing validator | Existing email/password/mismatch checks, failure mapping and confirmation visibility |
| Overview | Shared root; existing period, amounts and Add Expense | Ordinary income/expenses/surplus, separate crypto/pending/savings; Phase A regressions |
| History and spending insights | Shared root, history menu/category mode, full monetary values | Month/filter/scroll, exact totals and linked routing retained |
| Plans / More | Shared navigation shell, selected tabs, grouped hubs/account | All feature links and Sign Out retained; account scoping unchanged |
| All recorded expenses | Restyled header/list, metadata states, full amount/date/reference labels | Original inspect/retry conditions, error and empty-list qualification |
| Expense create / inspect / edit | Root theme, existing form/info/calendar and delete dialog; neutral Back | Amount-first draft, backdating, confirmation, pending original contribution and account clearing |
| Received income / create / inspect / edit | Feature wrappers removed; shared root covers fields, protected linked view and deletion dialog | Currency/conversion basis/rates, receipt date and linked-project routing |
| Pending agreements / create | Shared root, existing Phase B list/form | Source choices, immutable proposals, original-currency balances and server eligibility |
| Project details / agreement edit | Shared root covers summary, edit and project-cancel dialog | Existing totals, stale checks, cancellation accounting and direct receipt callback |
| Milestone create / edit / cancel | Shared root covers milestone dialogs | Original allocation limits, partial-payment history and cancellation guards |
| Direct / milestone receipt confirmation | Shared root, labelled loading; existing fields and status | Genuinely optional milestone ID, exact conversion, atomic callback and retry payload |
| Categories / Sources / Saved changes | Shared root on tabs, catalogue form/archive dialog and recovery panel | Stable/archived references, missing-catalogue labels and existing resolve/dismiss eligibility |
| Savings Goal / Overview card / ledger | Shared root, existing Phase C components | Explicit allocation accounting, entry correction/delete, reached/overdue/overfunded states |
| Savings forms / deadline revision / schedule / delete | Same root for forms/dialogs; reviewed native keyboard behavior retained | Original safe-revision eligibility, immutable proposals, original start anchor and exact schedule |
| Recurring list / details / due and consumed history | Shared root, suggested-versus-actual labels | Monthly paging, actual-expense routing and consumed history after correction/deletion |
| Recurring template / actual payment / cancel dialogs | Shared root; Phase C scrolling/footer preserved | Existing category exceptions, immutable confirmation identity and stop-unconfirmed policy |
| Crypto period / history / details | Shared root, signed full values and readable cutoffs | Separate accounting; individual valid rows remain inspectable on aggregate overflow |
| Crypto add/edit/delete and saved proposals | Shared root for dialogs/status/actions | Confirmed contributions retained during pending/rejected edits/deletions, unchanged resolution guards |
| Menus, selection controls, date picker and information dialogs | Complete Material semantic surface roles at root | No default lavender fallback; existing callbacks and action eligibility |

`HomeScreen.kt` is a legacy component with no current navigation caller. It is not a second reachable home or theme; if composed by a future caller it inherits the same root. Its unrelated code is left intact. There are no production previews using a removed adapter. Test hosts intentionally reapply the same canonical theme when testing configuration overrides; they do not define another palette or load production repositories.

## Verification environment

Only the separately inspected `Clarity_UI_Pilot_API26`, `emulator-5564`, is used. Android 8/API 26/x86_64, physical 1080×1920, density 420. Read-only run-as inspection checks default auth and saved files **before Application/instrumentation startup**. It finds no default authentication or unrelated recovery; only the SDK heartbeat file. No storage clearing, uninstall, network or DNS modification.

Graphs explicitly install fake repositories and production ViewModel factories, with no global/live fallback. Auth admission uses a genuine suspending `CompletableDeferred.await()`; no blocking Mockito answers. Account changes are fake auth-flow transitions. Fictional credentials are not captured in clear text or sent to Firebase.

Gradle commands use `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process --console=plain`. Every ADB command targets `-s emulator-5564`.

| Command/check | Actual result |
|---|---|
| Baseline `:app:assembleDebug :app:assembleDebugAndroidTest`, then `PhaseDVisualTest -e phaseDBefore true -e pilotScreenshots true` | Builds passed; one capture case passed in 5.875s, eight fictional light-mode before images at 390 dp / 1.0x. |
| `:app:testDebugUnitTest --tests 'com.clarity.finance.ui.ClarityThemeTest' --tests 'com.clarity.finance.viewmodel.AuthViewModelTest' :app:assembleDebug :app:assembleDebugAndroidTest` | 10 tests passed; both builds passed (5m 29s). The longer Kotlin compile was inspected with a read-only thread snapshot: active compilation, not a hanging test. No repeated build restart. |
| `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | 274 tests, zero failures/errors/skips; both builds passed; lint 0 errors / 22 warnings; successful run 8m 40s. Subsequent window-resource/test changes have their own build/lint result below. Unchanged domain/auth unit evidence is reused. |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` after native-window resources and test corrections | Both builds passed; lint **0 errors / 15 warnings**, successful run 8m 31s. Unused template colour resources were removed; no lint suppression or dependency upgrade. Remaining warnings concern dependency/build recommendations, old target API and a redundant manifest label. |
| `PhaseDAuthTest,PhaseDNavigationTest` focused device run | Final **7/7 passed in 57.503s**: validation/visibility, suspended login/register, restoration, initial loading, logout/account/back-stack clearing, every feature entry and native window-resource parity in both configurations. Initial run was 4/6: two new tests wrongly equated visible text with removal of the password privacy semantics key. Corrected tests inspect the rendered text layout for masking/showing and retain the privacy-role check. No authentication logic was changed to satisfy them. |
| Existing isolated cross-feature Android run | **55/55 passed in 340.815s** across Phase A/B/C interaction tests, pilot UI/restoration/recreation, goals, recurring, crypto/feedback, payments, catalogues, income scrolling, dashboard and DI ownership. No source assertions were removed; existing selector updates are limited to intentional neutral Back/auth-link labels. |
| Actual 320 dp / system font 2.0x, both themes, real keyboard | Passed; final per-configuration executions and restoration details below. |
| Firestore rules | Reuse unchanged 147-test rule evidence. No rules/index changes or publication required; suite not rerun for UI-only changes. |

### Final execution results

The retained `AuthAccessibilityTest` passed **2/2 in 25.523s** in its own invocation, preserving its validation, real-IME and above-keyboard assertions. The cross-feature class list was `PhaseAInteractionTest,PhaseBInteractionTest,PhaseCInteractionTest,PilotUiTest,PilotRecreationTest,PilotRiskRegressionTest,GoalNavigationTest,RecurringNavigationTest,CryptoNavigationTest,CryptoFeedbackUiTest,PaymentNavigationTest,CatalogueNavigationTest,IncomeListAccessibilityTest,DashboardNavigationTest,DiViewModelOwnershipTest` (55 cases). New auth/navigation coverage was run separately (7 cases); these counts are not a claim of one combined 62-case invocation.

Device commands use `adb -s emulator-5564 shell am instrument -w -r -e class com.clarity.finance.<Class> ... com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner`. Final captures/checks:

| Class / configuration | Actual result |
|---|---|
| `PhaseDVisualTest`, 390 dp / 1.0x, light and dark | Each passed: 17.883s / 17.229s, eight scenes per mode. `-e phaseDDark false/true -e pilotScreenshots true`. Light comparison uses the same physical device, density, font and content viewport as before. |
| `PhaseDVisualTest`, actual 320 dp / system font 2.0x, light and dark | Each passed: 15.868s / 16.624s. `-e phaseDNarrow true -e phaseDDark false/true`; actual resource width/font scale are asserted. Four enlarged captures per mode. |
| `PhaseDKeyboardTest`, actual 320 dp / 2.0x, light and dark | Each passed: 16.697s / 16.294s. Real IME, Email → Password → confirmation focus, and native visible action bounds fully above the IME; no auth request submitted. Two native full-window captures per mode. |
| Unmodified `PhaseCKeyboardTest`, actual 320 dp / 2.0x, light and dark | Each passed: 24.877s / 25.405s. Existing allocation/payment/crypto action bounds and zero fake-mutation assertions retained. No duplicate Phase C gallery captures needed. |
| Existing `PhaseCInteractionTest#longNamesLargeAmountsAndMonthContextRemainInspectable`, actual 320 dp / 2.0x | Passed in 18.722s under the final production theme; two after-only stress captures. Full near-Long.MAX_VALUE values, recurring inspection/Back and crypto period context. This is an extra configuration of an existing case, not another unique case in the 55-case count. |

The final visual/native runs use actual device night mode (`cmd uimode night no` / `yes`) as well as the matching isolated configuration override, so native window appearance is checked too. Only the inspected test device is changed. The narrow configuration is `wm size 840x1920` at physical density 420 with `settings put system font_scale 2.0`. Finally blocks restore **1080×1920 with no override, density 420, font scale 1.0 and the original night mode `no`**. Final preflight again reports no default auth or unexpected recovery files. No networking, DNS or storage reset.

Screenshot review caught a clipped edge of a validation panel at enlarged text. The capture test now explicitly scrolls the validation message into view and asserts it displayed. No production layout, state or eligibility changed for the picture. The test APK was rebuilt successfully (1m 54s); affected normal/enlarged visual runs were repeated. Native checks were also refreshed under real system night mode. Unchanged full unit/cross-feature/build/lint evidence remains applicable to the final byte-identical production code; it was not repeatedly rerun for this capture-only change.

The gallery contains **38** unedited fictional-data images: eight matching light before images, 16 normal after views, eight enlarged after views, four real-keyboard views and two long-name/full-amount views. No earlier report/gallery was regenerated. Automated semantics/native bounds, configuration checks and screenshot inspection are distinct from unexecuted TalkBack/human usability testing.

Static call-site inspection finds one production `ClarityTheme` call in MainActivity and one MaterialTheme definition in the canonical theme file. No temporary theme adapter, purple/pink template colour, dynamic-colour branch or hardcoded grey expense feedback remains in production UI. All checked production callers compile. `git diff --check` and phase-specific hash preservation passed; the Phase C native keyboard test remains byte-identical.

The sanitized review ZIP contains **75 current files**, including 34 source/test/window-configuration files, this report, the HTML gallery, 38 images and a SHA-256 manifest. Three obsolete theme-file deletions are explicitly recorded with their previous hashes. Packaging checks the exact member inventory, CRC and every archived member's SHA-256 against its saved source, including the manifest. Credentials, local Firebase configuration, private diagnostics/logs, historical review diffs/archives and build outputs are excluded.

## Release and assignment acceptance tasks

- **Unresolved historical limitation:** API 35 native instrumentation instability. This phase does not claim it fixed or a clean newer-device suite. New device evidence here is API 26; earlier compatibility evidence remains separately documented.
- **Untested human checks:** TalkBack traversal, human contrast/visual review, usability timing and non-automated keyboard/switch-access assessment. Semantics/native bounds and screenshots are not substitutes.
- **Untested live checks:** real Firebase login/register and production-account visual acceptance. No live auth or financial writes are performed in this phase. Accepted protocol/live evidence is reused only where source is unchanged.
- **Unexecuted protocol scenarios:** no new process-death/background/offline/in-flight server-race claim. Activity/restoration tests are identified as such, not process death. Existing accepted sync limitations remain, including no guaranteed work while force-stopped.
- **Remaining build recommendations:** 15 lint warnings concern dependency/build recommendations, target API and a redundant label. No errors; no suppressions or unauthorized upgrades were used. Their disposition belongs to a separate authorized release/stack review.

Koin adoption and earlier API 26 compatibility work are already recorded milestones; this pass preserves them. It does not reclassify optional features as required work or claim all assignment acceptance is newly verified by a visual rollout.

No unresolved demonstrated Phase D application blocker remains in the executed cases. Presentation defects and test/capture corrections are described above; no backend or authentication-logic repair was required or silently included.

## Manual review checklist

Use isolated fixtures for this review; no live-record authorization is implied.

1. Login → invalid/empty fields → show/hide password → keyboard Next/Done. Register → invalid email, short/mismatched password → Back to sign in. Submit against a suspended fake and verify duplicate button/IME submissions remain one call.
2. Overview → Add Expense → Cancel; History → inspection → Back. Confirm period/filter/scroll/draft restoration and unchanged confirmed values.
3. History → All recorded expenses: inspect full amounts, dates, archived labels, cached/pending/rejected rows, empty and unavailable states; return via neutral Back.
4. Visit every Plans/More link, including category/source tabs and Saved changes. Review the panel's stated scope; preserve uncertain proposals and original action eligibility.
5. Review direct/milestone payments, income conversion/linked routing, savings ledger/schedule, recurring consumed history and crypto overflow through isolated regressions.
6. Logout and change the fake account: old forms/history/feedback must disappear. Back from signed-out root must not reopen an old account.
7. Compare both themes at normal width/1.0x and actual 320 dp/system font 2.0x with real keyboards. Scroll forms and inspect dialog actions/full amounts. Complete separate human accessibility review before claiming that acceptance.

## Exact changed files and deletions

The final manifest records every packaged current source/test/configuration/report/image path and SHA-256, with an explicit deletion list. The phase-specific source list is appended after final verification. Earlier reports/review artifacts are excluded from this delta and preserved in place.


<!-- PHASE_D_EXACT_FILES -->

Current source/test/configuration files in this phase (34):

- `app/src/androidTest/java/com/clarity/finance/AuthAccessibilityTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseAVisualTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseCInteractionTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseDAuthTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseDFixtures.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseDKeyboardTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseDNavigationTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PhaseDVisualTest.kt`
- `app/src/androidTest/java/com/clarity/finance/PilotUiTest.kt`
- `app/src/androidTest/java/com/clarity/finance/RecurringNavigationTest.kt`
- `app/src/main/java/com/clarity/finance/ui/CatalogueScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/auth/AuthFormContent.kt`
- `app/src/main/java/com/clarity/finance/ui/auth/LoginScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/auth/RegisterScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/crypto/CryptoScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/EditExpenseScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpensesScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/goals/GoalScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/AddEditIncomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/IncomeListScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/AddPaymentScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ExpectedPaymentsScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/PaymentConfirmationScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ProjectDetailScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/pilot/DesignComponents.kt`
- `app/src/main/java/com/clarity/finance/ui/pilot/PilotShell.kt`
- `app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/theme/Theme.kt`
- `app/src/main/res/values-night/colors.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/test/java/com/clarity/finance/ui/ClarityThemeTest.kt`

Deleted obsolete theme files (explicitly listed with their previous hashes in the review manifest):

- `app/src/main/java/com/clarity/finance/ui/pilot/PilotTheme.kt`
- `app/src/main/java/com/clarity/finance/ui/theme/Color.kt`
- `app/src/main/java/com/clarity/finance/ui/theme/Type.kt`

No other baseline file was deleted. The other 448 pre-phase saved files remain byte-identical, including all earlier reports/galleries, the Phase C native keyboard regression and the unrelated IDE/review files. New review artifacts are this report, `PHASE_D_UI_UX_REVIEW_MANIFEST.json`, and the HTML gallery/38 images under `design/phase-d/`, individually enumerated in the manifest.
