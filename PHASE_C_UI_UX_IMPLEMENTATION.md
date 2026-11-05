# Phase C: Savings, recurring expenses and crypto

Phase C applies the approved shared design to Savings Goal, its Overview card, Recurring Expenses and Crypto Results. Phase A/B and the lighter colour review remain preserved. Authentication and remaining cross-app surfaces are Phase D, not implemented here.

[Open the screenshot review](design/phase-c/index.html). Captures use fictional isolated repositories; none show a live account. The gallery distinguishes matching before/after light-mode captures from after-only dark, enlarged-text and keyboard evidence.

## Baseline and preservation

HEAD remains `d38965de521aa1a02eade787c0d3ae95545beeeb`. Nothing is staged, committed, pushed or deployed. There are no dependency, Firebase-target, rules or index changes, and no live financial writes.

Before editing, 369 current saved files were backed up outside the repository to `D:\clarity-finance-phase-c-backup-20261105.zip`. Every archived member matched its source SHA-256. Archive SHA-256: `489119D9B006786D2A6E69D742CFEF4EC8907D957AA3114134D4A23EC3F4FB3A`. Phase C scope is measured against that working snapshot, not HEAD, which would also include earlier uncommitted phases.

The final preservation check verifies that only seven existing files differ from that snapshot, with four new Android test files. The other 362 baseline files remain byte-identical, including earlier reports, screenshots, colour tokens, navigation, all ViewModels, data/recovery code, and the unrelated `.vscode/settings.json` and three historical review diffs. No files were deleted.

## Ownership and interaction boundaries

The existing `AppNavigation` destinations still obtain their goal, recurring and crypto ViewModels from Koin at the same navigation-entry owners. `GoalCard` retains its existing separate owner. Route arguments, optional milestone navigation, DI modules, repository lifetimes, workers and account subscriptions are untouched. Presentation receives the same callbacks and eligibility flags as before.

| Surface | Changes | Behavior retained |
|---|---|---|
| Savings summary and Overview card | Grouped saved, target, remaining and progress; readable start/deadline/next date; contribution opportunity amount; concise purpose and labelled information action | Existing `SavingsMath`/ViewModel values, explicit allocations, reached/overdue/overfunded branches, no surplus transfer |
| Savings forms/history/schedule | Clear contribution/withdrawal radio labels; scrollable forms with separate actions; readable ledger entries; themed correction/deletion dialogs and inspectable schedule | Opening allocation, immutable proposal locks, entry correction/deletion callbacks, original confirmed savings, safe rejected-deadline revision eligibility |
| Recurring template list/details | Suggested amounts explicitly separate from spending; readable category/channel references; clear View template/Review payment actions; compact selected-window caption | Existing monthly paging, original schedule anchors, template edit/cancel rules, archived-reference exceptions and missing-catalogue labels |
| Recurring actual-payment form/history | Actual amount/date distinct from suggestion/due date; confirmation footer; consumed confirmation shown with route to actual expense | Exactly the same immutable confirmation callback and eligibility; correction/backdating/deletion does not offer a second confirmation |
| Crypto period/history/forms | Grouped signed month and YTD values, readable cutoff, Gain/Loss choices, inspect/edit/delete dialogs, concise purpose and labelled information | Separate crypto accounting, existing period/year bounds, genuine unavailable totals, valid individual rows remain inspectable on aggregate overflow |
| Status surfaces | Shared semantic containers and concise local purpose text; existing error/recovery evidence retained | Pending/unknown/rejected/confirmed distinctions, no invented success, dismissal only under original guards, confirmed contributions retained |

An empty but known cached goal now receives the cache qualification too; cache labeling is not restricted to a nonempty goal. No new data or zero balance is inferred. Crypto initial loading is labeled Loading rather than showing an initial numeric result; its existing Unavailable sentinel stays unavailable, never formatted as zero.

The only new shared components are `ReviewFormDialog` and `InformationDialog`. The former bounds and scrolls form content while keeping actions separate. A real API 26 keyboard check demonstrated that a default floating dialog could leave its footer covered despite Compose reporting it displayed. The dialog now uses `DialogProperties(decorFitsSystemWindows = false)` with safe-drawing/IME padding so the action area follows window insets. The native-window regression checks visible accessibility-node bounds against the real IME. See the [official DialogProperties reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties) for the window-inset property; device execution is the evidence for this app's layout.

## Shared design

No palette file changed. These screens use `ClarityDesignTheme`, existing typography/shapes/spacing, `PageHeader`, `DetailValue`, `StatusPanel`, `ChoiceOption`, `SelectionField`, `InformationAction` and `PresentationFormat`. Light primary remains `#237D68`, pale mint `#EEF9F3`; reviewed dark roles remain `#96DCC4` and `#234237`. Prior nominal colour contrast evidence in `COLOUR_REVIEW.md` remains applicable: white/light-primary 4.9943:1; primary/mint 4.6328:1; dark button-label/primary 8.2410:1. This is reused token-pair evidence, not a fresh all-control accessibility certification.

Formatting is at the UI boundary only: full grouped amounts, preserved signs and readable display dates. Inputs retain exact ungrouped parsing and ISO dates. Long text and amounts wrap; no essential amount uses ellipsis or font shrinking. Controls retain Material touch targets, labelled information actions and radio semantics. Longer explanations are accessible on demand; allocations, realised crypto and suggested recurring amounts keep their essential meanings visible.

## Exact source/test changes

Existing files modified in this phase:

1. `app/src/main/java/com/clarity/finance/ui/pilot/DesignComponents.kt`
2. `app/src/main/java/com/clarity/finance/ui/goals/GoalScreen.kt`
3. `app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt`
4. `app/src/main/java/com/clarity/finance/ui/crypto/CryptoScreen.kt`
5. `app/src/androidTest/java/com/clarity/finance/GoalNavigationTest.kt`
6. `app/src/androidTest/java/com/clarity/finance/RecurringNavigationTest.kt`
7. `app/src/androidTest/java/com/clarity/finance/CryptoNavigationTest.kt`

New tests:

8. `app/src/androidTest/java/com/clarity/finance/PhaseCFixtures.kt`
9. `app/src/androidTest/java/com/clarity/finance/PhaseCInteractionTest.kt`
10. `app/src/androidTest/java/com/clarity/finance/PhaseCVisualTest.kt`
11. `app/src/androidTest/java/com/clarity/finance/PhaseCKeyboardTest.kt`

New review artifacts: this report, `PHASE_C_UI_UX_REVIEW_MANIFEST.json`, and the gallery/images under `design/phase-c/`, individually enumerated with hashes in that manifest. Deletions: none. **Backend files changed: zero**, including models, repositories, ViewModels, rules, indexes, sync/recovery and financial calculations.

The package requires the preserved Phase A/B shared foundation already in this working tree. It is a Phase C review delta, not a replacement repository. The existing theme and formatters are reused rather than duplicated in the package as newly changed files.

## Verification environment and fixtures

Only `Clarity_UI_Pilot_API26`, serial `emulator-5564`, was targeted. Android 8/API 26/x86_64; physical display 1080×1920, density 420. Before Application/instrumentation startup and installations, a read-only run-as preflight inspected default authentication and saved files without launching Application. It repeatedly found no authenticated user and no unexpected recovery files; only the SDK heartbeat file was present. No storage clearing, uninstall, networking or DNS changes. Pixel_6a was not targeted.

Tests construct the existing isolated Koin graph with explicit fake repositories; no live repository fallback. Genuine `CompletableDeferred.await()` suspends local goal acceptance for repeated-Save coverage. Fixture IDs and labels are fictional. Goal saved is LKR 20,000 (opening 11,200 + contribution 10,000 − withdrawal 1,200), target 490,000; remaining 470,000 and 12 future opportunities. Crypto gain 60,000 minus loss 25,000 yields +35,000. Rent is suggested 34,000 with an earlier consumed confirmation; current-month Rent and Utilities remain unconfirmed until an isolated test explicitly confirms one. No test values are production records.

Commands below ran from the repository. Gradle commands use `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process --console=plain`. ADB commands always specify `-s emulator-5564`.

| Check | Actual result |
|---|---|
| `:app:testDebugUnitTest --tests 'com.clarity.finance.*Goal*' --tests 'com.clarity.finance.*Savings*' --tests 'com.clarity.finance.*Recurring*' --tests 'com.clarity.finance.*Crypto*' --tests 'com.clarity.finance.*PresentationFormat*' :app:assembleDebug :app:assembleDebugAndroidTest` | 125 focused unit tests passed; both builds passed. An earlier command using a bare Windows wildcard failed task discovery because it expanded to a local review diff; no test execution was claimed for it. |
| `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | Initial complete run: 271 unit tests passed, app/test builds passed, lint 0 errors / 22 warnings. Final inset-correction run recorded below. |
| Instrumentation classes `PhaseCInteractionTest,GoalNavigationTest,RecurringNavigationTest,CryptoNavigationTest,CryptoFeedbackUiTest,DiViewModelOwnershipTest,PilotUiTest` | 23 of 24 cases passed in the earlier combined run. One new context test assumed a Back icon in existing expense inspection. Its selector was corrected to the existing Back to history button, which correctly returns to the selected recurring window. The corrected case and an added crypto year-backdate case passed in a separate 2-case run. The final post-inset combined run then passed all 25 cases. |
| Baseline `PhaseCVisualTest` with `-e phaseCBefore true` | 1 case passed, 16 before captures at 390 dp / 1.0x. |
| After visual/keyboard checks | Final results recorded below. |
| Firestore emulator suite | Reused unchanged 147-rule-test evidence; not rerun for UI-only changes. No rules/index publication required. |

Earlier focused Android runs exposed three ambiguous selectors (the same date or error in background and modal, and Deadline label versus field). Selectors now scope dialogs/fields without removing the assertions. The initial native keyboard assertion and subsequent timing-only retry both failed; inspecting the screenshot/native tree established a real covered-footer defect, repaired as described above. Diagnostic dumps and failing screenshots are excluded from the public package.

### Final verification and screenshots

After the production dialog-inset repair, `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` completed successfully in 2m 48s: **271 tests, zero failures/errors/skips; both APK builds; lint 0 errors / 22 warnings**. XML reports were counted, rather than inferring the test count from a successful Gradle task.

The final combined instrumentation run on the repaired production code passed **25/25 in 89.675s**, including all nine new interaction cases, four existing goal cases, four recurring cases, crypto navigation/feedback, DI ViewModel ownership and five pilot regressions. Savings coverage includes creation, contributions/withdrawals, schedule, correction/deletion, dashboard update, invalid withdrawal, reached/overfunded states, rejected deadline revision and account clearing. Recurring coverage includes AT09, exact single actual expense, correction/backdating/deletion with consumed confirmation retained, cancellation, stale payload rejection, paging and account recovery states. Crypto includes exact gain/loss, overflow inspection, year backdating, edit/delete, pending/rejected contribution retention and operation-derived feedback. These are UI/VM paths using isolated repositories, not new server-protocol evidence.

The following command variants use `adb -s emulator-5564 shell am instrument -w -r -e class com.clarity.finance.<Class> ... com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner`:

| Final device check | Arguments / outcome |
|---|---|
| `PhaseCKeyboardTest`, light then dark, each in a separate invocation | Real device 320 dp / system font 2.0x; `-e phaseCDark false/true`. Both passed (10.024s / 10.435s). Native accessibility visibility and bounds checked Save proposal, Confirm payment proposal and Save result above the actual IME. Three captures per theme. |
| `PhaseCVisualTest`, light then dark at actual 320 dp / 2.0x | `-e phaseCDevice320 true -e phaseCNarrow true -e phaseCDark false/true`. Final refreshed runs both passed (20.666s / 21.059s), 19 scenes per theme including deletion/cancellation dialogs. The test asserts actual resource width/font scale before rendering. |
| Normal-width refreshed `PhaseCVisualTest`, light then dark | `-e phaseCDark false/true`; 390 dp / 1.0x. Final refreshed runs both passed (21.099s / 20.964s); 19 scenes per theme, captured again after the production dialog correction and scene cleanup. |
| Final diagnostics-free `PhaseCInteractionTest` | All 9 cases passed in 31.109s after the LazyColumn-aware helper correction. The already passing 25-case combined run remains separate evidence; unchanged other classes were not repeatedly rerun. |
| Enlarged long-label/full-amount context check | `PhaseCInteractionTest#longNamesLargeAmountsAndMonthContextRemainInspectable -e phaseCNarrow true`, with actual device 320 dp / system font 2.0x: passed in 8.209s. Saved and suggested values near Long.MAX_VALUE remain complete and inspectable; recurring inspection/Back and crypto period context assertions remain. |

The narrow checks used `adb -s emulator-5564 shell wm size 840x1920` at the unchanged physical density of 420 and `settings put system font_scale 2.0`. `finally` restored the original physical 1080×1920 configuration with no size override and font scale 1.0. No storage/network/DNS changes.

A long-name/near-Long.MAX_VALUE variant initially failed because the helper treated a partially visible button as safely clickable. The helper now calls scroll-to for controls inside scrollable containers before clicking, preserving every business assertion. This correction is test-only; it does not loosen eligibility, replace a production callback or change financial behavior.

The enlarged variant also required using LazyColumn's own node-scroll action for an offscreen confirmation following a tall template summary, rather than a generic nested scroll action. The test then navigates to the actual expense and returns to the original recurring window; it also retains the crypto period across form open/close. No production layout/calculation was changed for this test-helper issue. Temporary diagnostic tree dumps/screenshots were removed from the retained test and excluded from the package.

The screenshot scene also explicitly closes the retained crypto inspection after choosing Keep result, before injecting a pending edit. This makes the pending before/after pair compare the same list scene rather than leaving an inspection dialog over one version. It changes test setup only; normal and enlarged visual runs were refreshed afterward. The gallery contains 100 fictional-data captures: 16 before, 38 normal after, 38 enlarged after, six real-keyboard views and two long-label/full-amount views. It is a review gallery rather than evidence of a human usability session.

The final test APK rebuild passed after test-only cleanup (32s). Full unit/build/lint evidence above remains applicable to the byte-identical final production source. `git diff --check`, exact pre-phase hash preservation and review-payload whitespace/privacy checks passed. The final device preflight again found no default authentication or unrelated recovery; physical size 1080×1920, density 420 and font scale 1.0 were restored.

The sanitized review ZIP contains the 11 source/test changes, this report, the HTML gallery, 100 images and a relative-path/SHA-256 manifest: **114 files**, no deletions. Packaging validates the exact member inventory, CRC integrity and SHA-256 of every member against the current saved file, including the manifest itself. Credentials, local configuration, historical review diffs/archives, build outputs and private diagnostic scripts/logs are excluded. Earlier review artifacts remain in place.

## Limits and remaining rollout

- Account changes are driven through isolated auth flows; no live login/account switching or live in-flight race was exercised. Existing repository/rule evidence is reused for server atomicity, not inferred from UI fakes.
- No new process-death, offline-restart or live reconciliation claim. This phase changes presentation only; previously accepted protocol evidence remains separate.
- Device tests use production screens and actual navigation owners with fake repositories. Themes are selected using a test configuration override; production follows system theme as before.
- Automated semantics, selected-state assertions, screenshots, native window/IME bounds and enlarged-text checks do not establish TalkBack usability or a human accessibility audit. Those remain unexecuted.
- API 35 native instrumentation instability remains unresolved and was not retested here. This phase's new device evidence is API 26 only.
- The existing shared actual-expense inspection button says Back to history even when opened from a recurring confirmation; its callback correctly restores the recurring destination. General shared-surface wording remains for Phase D, rather than changing Phase A behavior in this phase.
- Authentication and remaining shared surfaces are Phase D. No Phase D implementation, global theme replacement, backend repair or additional feature is included.

## Button-by-button review checklist

Use isolated fixtures for this visual review; this document does not authorize live records.

1. Overview → Open Savings Goal: compare saved, target, remaining and required contribution; open About recorded savings; inspect contribution schedule and close it.
2. Record allocation → Contribution/Withdrawal: verify selected radio state, exact amount/date labels, scroll to note, open keyboard and reach Save/Close. Inspect/correct an entry; open Delete and choose Keep entry before separately testing confirmed deletion in a disposable fake fixture.
3. Plans → Recurring Expenses → View template: distinguish suggested amount from spending; page the existing 12-month window; Review payment; check actual amount/date and category/channel choices. Cancel → Keep template. Confirmed items must show the original confirmation and actual-expense route, never Review again after actual deletion.
4. Crypto Results: compare +35,000 month/YTD with reporting cutoff; inspect gain/loss; Edit result and backdate across year, verify period totals; Delete → Keep result. Inspect a valid row when aggregate totals are unavailable.
5. Review retained pending/uncertain/rejected fixtures: original confirmed contributions remain; dismissal is absent for uncertain operations. Change the fake account and confirm old forms, history and feedback clear.
6. Repeat at normal width and actual 320 dp/system-font 2.0x, both themes, with real keyboard. Check all dialog actions and full amounts; do not infer human TalkBack acceptance from automated results.
