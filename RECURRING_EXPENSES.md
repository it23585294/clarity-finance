# Required recurring expenses — FR17 / Section 6.6 / AT09

## Invariants chosen before schema implementation

Monthly is the required cadence in the supplied requirements and assignment scenario. A template is an obligation, never spending. Only a user-confirmed actual occurrence creates an ordinary expense. No notifications, scheduled service, automatic payment, forecast or bank integration is included.

Owner paths are users/{uid}/recurringTemplates/{templateId}, recurringOccurrences/{templateId_YYYY-MM}, recurringOperations/{operationId}, and the existing expenses/{expenseId}. Occurrence identity is the immutable template ID plus calendar month, independent of mutable due-day/amount/category settings. The expense ID is rec_ plus that occurrence ID. The reserved expense prefix and immutable recurringOccurrenceId prevent link stripping and recreation after deletion.

Confirmation atomically creates exactly one occurrence, one matching ordinary expense and one immutable operation receipt. Rules require the existing uncancelled template at the proposal's exact version, a valid scheduled due date no later than the server's Colombo day, matching owner paths/IDs and exact actual payload. All three writes verify each other. Occurrence-only, expense-only, receipt-only, parent-plus-confirmation, multiple expenses per occurrence and multiple occurrences per operation are denied. Occurrences and receipts cannot be updated or deleted. Concurrent clients contend on the same occurrence and deterministic expense IDs.

Receipt/occurrence reconciliation precedes mutable template checks. A matching accepted occurrence remains acknowledged even if its expense was corrected/deleted or its template changed/cancelled. A different accepted payload is a visible conflict, never an overwrite. Expense correction/backdating/deletion uses the existing expense flow; the immutable confirmation is original audit history, not a second expense or current expense total. Hard deletion of the expense leaves confirmation consumed forever. Existing standalone expenses, including omitted optional notes, retain compatibility.

The first due date/month never changes. Subsequent monthly due dates derive independently from the selected original day anchor, clamped to month end. Editing the anchor/defaults applies to every still-unconfirmed month, including missed months, at the newly acknowledged template version; the first date stays fixed. Confirmed records retain their original scheduled date and actual payload. Pending confirmations retain their saved version/payload: if an edit/cancellation wins first, a successful server verification establishes the permanent version/cancellation contradiction and allows correction/dismissal. If confirmation wins first, its expense/history remain. Cancellation is permanent and stops all unconfirmed occurrences, including already-due/overdue ones, while preserving all confirmations and expenses.

All proposals are persisted with stable operation IDs and immutable payloads in SubmissionRecoveryStore, scoped by UID/type/record ID, before any remote Task starts. Timeouts, cancelled awaits, auth/access failures and local bookkeeping errors are not definitive rejection. Outstanding Tasks are reused; matching receipts establish success. Only a successfully verified server transaction proving an immutable accepted conflict or monotonic template contradiction permits rejected-proposal dismissal. Template edits/cancellation remain possible while confirmations are pending; saved payloads never silently change. Multiple distinct offline occurrences may be queued. Account/target changes clear forms/data/messages, cancel listeners/workers and reject stale callbacks; CancellationException propagates.

Template browsing is explicitly paged. A selected template's occurrence query and generated schedule cover at most 12 calendar months, beginning with its oldest scheduled month, with explicit previous/next pages through missed months. There is no silent latest-N truncation of outstanding obligations. Monthly reporting continues to query ordinary expenses by actual expenseDate; templates and pending proposals contribute nothing to totals.

## Baseline and requirement mapping

Baseline verified: `8e94576230cae6dbdaba03b8f59dea6c627a375f`. No applicable AGENTS.md was found. Both supplied PDFs were read using the existing local PDF reader. Their instructions are assignment evidence, not authorization to publish, submit or change live data. The existing manual DI architecture is retained; the original brief requires Hilt (preferred) or Koin, so adoption remains an outstanding assignment task outside this milestone.

| Requirement | Implementation |
| --- | --- |
| FR17 / Section 6.6 | Authenticated Home → Recurring Expenses; explicit template creation/inspection/editing/cancellation; due/overdue review; separate suggested and actual amounts; one actual expense per confirmed month |
| AT09 | Rent 34,000 once, retry/reopen/concurrent-client reconciliation; ignored utilities create nothing; edited utility actual 6,250.50 is recorded once in automated fixtures |
| FR09 | Actual amount/category/type/channel/note correction, real nonfuture actual dates, backdating and deletion through the existing expense screen; immutable origin/history survives |
| FR18 | Metadata-aware template/history listeners; normal expense/reporting listeners receive confirmed actual expenses without reopening; Colombo date rollover updates due candidates |
| FR19 | Existing strict DataStore operation APIs; multiple distinct offline proposals; storage reopen/recovery; stable IDs; cached labels and unavailable history; uncertain Tasks are not treated as rejected |
| FR20 | Validation, loading/empty/unavailable, authentication changes, recoverable listener/store/network errors, explicit pending/uncertain/rejected states; cancellation conflicts appear inside the open dialog |

Template forms begin with empty name/amount. Date/day defaults are visible for user confirmation. Every actual confirmation lets the user change amount, actual payment date, category, spending type, channel and note. Notes use the existing 500-character convention. Exact decimal parsing rejects zero, negative/scientific/excess-precision/overflow input; money remains signed-64-bit positive minor units with BigDecimal display. There is no new monetary ceiling. Monthly financial overflow handling remains with the existing checked reporting calculations.

## Schema, validation and reconciliation details

- `recurringTemplates/{templateId}`: name, categoryId, spendingType, paymentChannelId, suggestedMinor, firstDueDate, anchorDay, cadence, isCancelled, version, lastOperationId, createdAt, updatedAt. Template IDs contain letters/digits/hyphens; generated IDs are UUIDs. Parent deletion and cancellation reversal are denied.
- `recurringOccurrences/{templateId_YYYY-MM}`: templateId, month, scheduled dueDate, templateVersion/name at confirmation, deterministic expenseId, original actual payload, winning operationId and server createdAt. This immutable record consumes the logical month permanently.
- `recurringOperations/{operationId}`: kind, templateId, recordId, version, immutable payload and server createdAt. CREATE/EDIT/CANCEL receipts accompany the parent mutation; CONFIRM accompanies its occurrence and expense. One receipt cannot authorize multiple targets.
- Existing `expenses/{rec_templateId_YYYY-MM}`: ordinary expense fields/timestamps plus immutable recurringOccurrenceId. No template/occurrence row is separately added to reporting totals. All paths above are under the same users/{uid} owner.

New template and confirmation rules enforce exact required/allowed fields, integer types, classifications, monthly cadence/day, real Gregorian dates including century/leap rules, immutable creation/origin fields, server timestamps and the Asia/Colombo server-day boundary. Actual recurring expense dates, including corrections, cannot be future dates. Regex alone is not calendar validation. Typed decimal syntax, UI schedule generation, display formatting, proposal status and form conflict feedback are client responsibilities. Standalone legacy expense rules retain their prior date semantics and omitted-note compatibility; their existing edit form now uses the shared exact amount/calendar helper and rejects future corrections. A blank edited note explicitly clears the stored note. Other financial policy blocks are unchanged.

Expense creation reserves the `rec_` prefix for linked recurring actuals. This prevents recreating a deleted deterministic expense as an unlinked ordinary record. Existing standalone expense updates/deletions retain their previous owner access; their origin cannot be forged on update. Linked expenses remain editable/deletable, unlike protected linked project income receipts. The immutable confirmation shows **original** actual values and routes to the ordinary expense for its current corrected/deleted state; it is never presented as an additional expense or current total.

Template mutation proposals use RECURRING_CREATE/EDIT/CANCEL and a template record ID. Confirmations use RECURRING_CONFIRM and the deterministic occurrence ID. One unresolved template mutation blocks another mutation of that template; distinct occurrence proposals can coexist. Existing confirmations may still finish while a template change/cancellation is pending. Until acknowledgement the confirmed template state remains authoritative; whichever valid server operation commits first determines whether a pending confirmation is accepted or subsequently rejected. No saved payload is rebased.

Remote confirmation first reads the immutable occurrence. Matching original details establish success without reading/recreating its expense or consulting the current template. Different details establish a conflict only after the read-only transaction successfully verifies its read set. Other definitive rejections require a verified permanent contradiction: existing CREATE identity, monotonic newer template version, permanent cancellation or immutable schedule/configuration conflict at the expected version. Permission/authentication errors, local validation/clock errors, failed read verification and SDK/store exceptions alone do not prove rejection. If an earlier Task wins a race, the verified read retries and reconciles its receipt.

The existing coroutine Task await does not cancel a Firestore Task on timeout/coroutine cancellation. Outstanding Tasks are keyed by UID/operation and exact payload and reused to avoid overlap; the repository serializes recovery sweeps. After restart the Task handle can be lost, so server reconciliation remains required. Local bookkeeping is outside remote classification; acknowledged cleanup retries without another mutation, and one record's bookkeeping error does not stop other independent proposals. Transient in-process acknowledgement feedback is UID/type/record scoped and bounded; an evicted acknowledgement still reconciles through immutable server history. SubmissionRecoveryStore and legacy recovery serialization are unchanged. Recovery workers run only while the recurring feature ViewModel is alive, not as a background scheduling service.

## Queries and publication status

Templates use document-ID ordering, an explicit page size initially 25, and one extra sentinel to show Load more templates. All selected history/schedule windows are at most 12 months and start at the oldest scheduled month; Previous/Next 12 months exposes every intervening missed month. Confirmed history is filtered by templateId and inclusive month start/exclusive next window, then ordered by month, without a hidden latest-N cutoff. Unconfirmed slots are computed lazily per window and never pre-created in Firestore. Calendar year 9999 query upper bounds are handled without constructing invalid stored months.

The selected-history query needs the COLLECTION composite index on recurringOccurrences: templateId ASC, month ASC, recorded in `firestore.indexes.json`. At the original local-verification stage neither rules nor index was deployed by the agent. On 3 November 2026 the user confirmed manual publication to clarity-1c83e, (default), and index Enabled/Ready status. Subsequent live acceptance executed the production-shaped query successfully with Source.SERVER, including confirmed history. Deployed rules text was not independently downloaded or hashed. Existing deployed indexes must be preserved: this local JSON records the required addition, not an inventory of remote indexes. No Firebase configuration/target or dependency changed, and no further publication is part of finalization.

SHA-256 of the complete reviewed local firestore.rules: `51A56DB95589AE167763889EC7E163191EAC3D4DBCE1BD4BD91F560191996339`.

Cached nonempty views are labelled potentially incomplete/out of date. Empty uncached history remains unavailable, not confirmed zero. A cached template may offer explicitly labelled schedule candidates when confirmation history is unavailable; these are not proof of unpaid occurrences, and every submitted proposal must reconcile server history before writing. No financial totals are calculated from those candidates. Listener failures retain previous clean rows, reattach and remove old registrations. Account/selected-template/window changes cancel old listeners and reject stale callbacks; old-user forms, feedback and records clear.

## Local verification — 27 October 2026

All commands use the existing dependencies/build stack. No live financial records or recovery operations were created, edited, deleted or retried. Both APK installations used adb install -r; app storage, credentials, device networking and the DNS workaround were preserved.

Focused Android command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*Recurring*Test' --tests '*FirestoreReportingRepositoryTest' --tests '*EditExpenseViewModelTest' --console=plain
```

Result: **55 focused tests passed**. Coverage includes AT09 amounts/separation, leap/month-end anchors, missed windows, stable IDs, variable actual classifications/date, correction/backdating/deletion, duplicate taps, matching/conflicting receipts, real outstanding Task reuse, genuinely suspending timeout-A/access-error-B/late-commit reconciliation, durable DataStore close/reopen with three proposals, legacy/UID/type isolation, proven rejection/dismissal, local cleanup failure, cached/pending snapshots, listener errors/cleanup/reattachment, rollover and delayed old account/target results. No deferred wait is blocked inside a Mockito runBlocking answer.

Full required Android command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

The initial full run passed 225 tests and both builds with lint 0 errors/20 warnings. After two additional listener regressions and inline modal recovery/conflict feedback were added, the **final full run passed 227 unit tests, zero failures/errors/skips, both APK builds, and lint 0 errors / 20 warnings** (BUILD SUCCESSFUL). This retains all 186 baseline unit tests and adds 41 behavioral regressions. Initial compile cleanup and isolated UI selector/scroll-helper failures were diagnosed and corrected; no existing tests were removed or weakened. No hanging test was restarted blindly.

Rules commands, from firestore-tests:

```powershell
npm.cmd test -- --grep 'Recurring expense integrity'
npm.cmd test
```

Result: **18 focused recurring tests passed; full suite 124 passed**, retaining all 106 previous tests. Actual demo-clarity-test server rules covered owner CRUD, concurrent matching/conflicting clients, ignored utilities, edited actual amounts, three-way atomic bypass attacks, parent-plus-confirmation denial, shared-operation attacks, strict schema/calendars/Colombo dates, immutable origin/history, correction/backdating/note omission/deletion, cancellation/version races, read verification, full foreign/unauthenticated atomic writes and bounded history queries. An attempted additional emulator startup refused an occupied port; the already-running local emulator was used. The Android app target was never switched to it. A concurrent stale create can receive PERMISSION_DENIED before SDK retry; the test requires retained uncertainty followed by matching reconciliation, rather than weakening rules or assuming immediate acknowledgement.

Android instrumentation uses only isolated repositories with production navigation, screens and ViewModels at **320 dp / 1.3 font scale** on the existing **API 37** emulator:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.clarity.finance.RecurringNavigationTest,com.clarity.finance.DashboardNavigationTest,com.clarity.finance.GoalNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

The first four recurring checks passed after disambiguating dialog/menu selectors and scrolling feedback into view. **Final execution passed all 12 tests**: four recurring, four existing dashboard and four existing savings checks, including the extended cancellation-version conflict check. The recurring checks exercise actual-expense correction/backdating/deletion routing, AT09 dashboard totals, ignored utilities, template edits/cancellation, uncertainty/rejection feedback and account isolation. The APKs contain no temporary live writers or authentication helpers. Private build/test diagnostics remain excluded under build/recurring; they are not part of the review archive.

The 55-focused/227-full Android results and 12-test final UI result are actual executions. The 124-test rules run is reused for the final unchanged rules; no second full emulator run was needed for UI/test-only follow-ups. Source inspection and hash checks verify that the savings, income/linked-receipt, project/milestone and crypto policies/tests remain intact. Whitespace/new-file checks, unchanged baseline HEAD/index, exact manifest membership and preserved unrelated-file hashes are checked before packaging. The review ZIP contains only the saved manifest files and is verified file-for-file by SHA-256; its final absolute path/count/hash are reported separately.

## Remaining limits

The 27 October results above are earlier automated evidence: 227 full unit tests, 124 Firestore emulator tests and 12 isolated Android tests, with successful app/test builds and lint 0 errors/20 warnings. They were not rerun during documentation-only finalization. The separate bounded live acceptance on 3 November exercised production AT09 amounts, correction/backdating/deletion, cancelled-template history, actual offline process restart/reconnect and interactive account isolation with queued proposals. See [RECURRING_EXPENSES_LIVE_RESULTS.md](RECURRING_EXPENSES_LIVE_RESULTS.md) for actual checkpoints and retained fixtures.

Simultaneous live devices, deliberately timed duplicate taps, direct replay, forced rejection, in-flight account-switch races, cancellation of an additional overdue unconfirmed slot and manipulated calendar boundaries remain unexecuted live. Their automated evidence must not be described as live verification. Hilt/Koin adoption and API 26 runtime verification remain outstanding assignment tasks. Complete monthly pages/template expansion remain user-driven; no notifications or automatic bulk confirmation exist.

## Manual acceptance checklist

The checklist below describes possible checks, not a claim that every scenario ran live. The bounded executed subset and limitations are recorded in the separate live-results report. Any new financial fixtures or cleanup require fresh authorization.

1. In a designated test account, record fresh server/reporting/recovery baselines. Add the reviewed rules/index separately first. Home → Recurring Expenses → Create recurring template. Enter a labelled rent name, Housing, Committed, Cash, suggested 34000, first due today and the displayed monthly day. Save template proposal; wait for acknowledgement. Expenses/surplus must be unchanged.
2. Inspect the rent template → Review this month's payment. Check/change all actual fields; enter 34000 and a nonfuture actual date. Confirm payment proposal; wait for acknowledgement. Exactly one linked ordinary expense of 34000 appears. Reopen: the month offers Inspect actual expense, not another confirmation. Two devices opening the same unconfirmed month before either confirms must still yield one expense; a differing proposal must show a conflict/existing result.
3. Create labelled utilities with suggested 5000. Leave it unconfirmed: no expense. Review its payment → change actual amount to 6250.50 → Confirm payment proposal. Relative to baseline, actual spending is now 40250.50 if both actual dates are in this month; surplus decreases by that amount. Ordinary income, crypto, goal allocations and pending projects remain unchanged.
4. Inspect actual expense for rent → correct to 33000, change category/type/channel/note and backdate to the prior month → Save Changes. Verify one expense remains; current-month delta becomes 6250.50 and prior-month delta 33000. History/categories/spending split/surplus update by actual dates without reopening.
5. Inspect rent's actual expense → Delete → Cancel keeps it. Delete → confirm removes it once. Return to recurring history: original confirmation remains consumed; opening its expense reports deleted/missing. Retry/reopen must never recreate it. The utilities expense is unaffected.
6. Edit the template's name/suggestion/monthly day. The first due date is disabled/fixed. Inspect older unconfirmed pages and month-end clamping; confirmed audit dates/expenses remain unchanged. A stale open confirmation or cancellation must request reviewing the newer version, with a visible error.
7. Cancel template → Keep template makes no change. Cancel template → Confirm cancellation → wait for acknowledgement. All unconfirmed due/overdue/future items stop; previous actual expenses and confirmations remain. Template cancellation alone changes no financial total.
8. With confirmed templates cached, disconnect only the test device. Queue three clearly labelled distinct occurrence proposals, retaining their IDs privately. Pending/uncertain proposals add no confirmed spending. Force-stop/reopen without clearing storage; unavailable cache must be labelled honestly. Reconnect; exact entry/operation IDs reconcile once each, feedback clears and unrelated recovery records remain untouched. Restore the original device networking/DNS setup.
9. Sign into a second account interactively, never through chat credentials. Confirm old templates, actual-payment drafts, proposals and feedback disappear. Return to the test account and verify its own queued/history state. Distinguish this basic switch from any deliberately exercised in-flight race. Do not weaken live rules to manufacture rejection; use the documented isolated/emulator evidence where live scenarios are unavailable.

## Exact milestone files

These 28 implementation files were included in the original verified review archive. The later sanitized RECURRING_EXPENSES_LIVE_RESULTS.md is the additional finalization deliverable, bringing the committed milestone to 29 files. Documentation now incorporates subsequent acceptance/publication evidence; the original local archive remains preserved as its earlier snapshot. Existing IDE settings and all three historical review diffs are deliberately excluded and preserved. Credentials, local configuration, build outputs, dependencies and private diagnostics are excluded.

```text
RECURRING_EXPENSES.md
app/src/androidTest/java/com/clarity/finance/RecurringNavigationTest.kt
app/src/main/java/com/clarity/finance/MainActivity.kt
app/src/main/java/com/clarity/finance/data/model/Expense.kt
app/src/main/java/com/clarity/finance/data/model/RecurringExpense.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreExpenseRepository.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreRecurringRemote.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreRecurringRepository.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreReportingRepository.kt
app/src/main/java/com/clarity/finance/data/repository/RecurringRepository.kt
app/src/main/java/com/clarity/finance/di/AppContainer.kt
app/src/main/java/com/clarity/finance/di/ViewModelFactory.kt
app/src/main/java/com/clarity/finance/ui/HomeScreen.kt
app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt
app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt
app/src/main/java/com/clarity/finance/viewmodel/EditExpenseViewModel.kt
app/src/main/java/com/clarity/finance/viewmodel/RecurringViewModel.kt
app/src/test/java/com/clarity/finance/data/model/RecurringScheduleTest.kt
app/src/test/java/com/clarity/finance/data/repository/FirestoreRecurringRemoteTest.kt
app/src/test/java/com/clarity/finance/data/repository/FirestoreRecurringRepositoryTest.kt
app/src/test/java/com/clarity/finance/data/repository/FirestoreReportingRepositoryTest.kt
app/src/test/java/com/clarity/finance/data/repository/RecurringExpenseCompatibilityTest.kt
app/src/test/java/com/clarity/finance/viewmodel/EditExpenseViewModelTest.kt
app/src/test/java/com/clarity/finance/viewmodel/RecurringViewModelTest.kt
firestore-tests/recurring-tests.js
firestore-tests/test.js
firestore.indexes.json
firestore.rules
```
