# Required savings goal: FR14–16 / Section 6.5 / AT07–08

## Chosen invariants (recorded before schema implementation)

One fixed `users/{uid}/savingsGoals/active` document is permitted by rules. Other goal IDs and parent deletion are denied: there is one editable goal, not multiple-goal management. Its immutable start date anchors the contribution schedule. Creation requires a future deadline; an unchanged overdue deadline is retained on other edits, while a revised deadline must be future-dated.

The parent stores a checked integer `savedMinor` balance, opening allocation, configuration, version and last operation/entry IDs. Saved equals opening allocation plus contributions minus withdrawals. Ledger entries are separate documents in `entries`, never an array on the parent. Logical deletion preserves an entry tombstone and its identity while removing its contribution exactly once. Immutable `operations/{operationId}` receipts attest the accepted payload and parent version, so matching historical operations reconcile even after later edits. Receipt update/deletion and tombstone resurrection are denied.

Every mutation creates one new receipt. Each entry mutation also changes the parent version/balance atomically. Both sides require matching operation ID, entry ID, version and exact signed before/after delta. A parent/child create batch cannot bypass the opening balance: entry creation requires an already-existing parent. Only the parent's one named entry may change in an operation, preventing multiple entries sharing a delta. Counter-only changes and receipt replay are rejected. Goal configuration edits preserve the ledger, adjusting saved only by the opening-allocation difference. All resulting balances must be nonnegative. Overfunding is valid.

Rule arithmetic checks integer representation bounds before computing opposite-sign deltas; it must not rely on overflowing addition. Client calculations use checked integer/BigInteger intermediate arithmetic and BigDecimal display. Rules enforce owner, exact allowed fields, types, real calendar/leap dates, Colombo date boundaries, server timestamps and immutable creation/start dates. Client-only checks include typed decimal syntax, schedule/progress presentation and UI feedback.

Recovery reuses SubmissionRecoveryStore with UID, goal operation type and record ID. Payloads and operation IDs are immutable. One unresolved mutation per owner/goal prevents independently queued stale versions from being silently rebased. A timeout retains an uncertain outcome; an acknowledged atomic transaction or exact matching receipt establishes success. Only a successful server transaction verifying a permanent invariant contradiction or acknowledging an immutable expiry fence establishes dismissible rejection; exception classes, local-clock validation and authentication/access errors do not. Pending/rejected proposals never replace confirmed savings or ledger history. Dashboard observation is read-only; synchronisation runs only in the savings feature. Account/target changes invalidate old jobs and callbacks. No ordinary income/expense/crypto/project writes are involved.

Dates use Asia/Colombo. Each monthly date is derived from the original start-day anchor, with month-end clamping. The deadline is included once; no later date is offered. The inspectable schedule is computed, not stored as a growing document. Remaining/overfunding/progress and required contribution are derived; only the visual bar is capped. A reached goal requires zero further contribution; an overdue goal never divides by zero.

The user subsequently reported manual publication of the reviewed rules and authorized labelled savings fixtures in a specifically designated test account. Actual live acceptance is recorded in SAVINGS_GOALS_LIVE_RESULTS.md; the earlier local-only results below are historical. No deployment was performed by the agent. Hilt/Koin adoption and API 26 runtime verification remain separate outstanding assignment tasks.

## Implemented behavior

FR14: authenticated Home and the financial dashboard open the Savings Goal screen. Creation starts with an empty name/target/deadline and a visible zero opening allocation for explicit confirmation, never the scenario figures. The start date is the Colombo creation date. Editing retains the original start and every ledger entry. An open form or deletion confirmation is bound to its original parent version; remote changes require reopening current details rather than silently rebasing a draft. Existing overdue goals remain inspectable and editable.

FR15: contributions, withdrawals, corrections and confirmed deletions use the same versioned transaction path. History shows confirmed entries; logical deletion removes a contribution once without reusing its identity. The opening allocation remains separately visible. Withdrawals, opening changes and entry corrections/deletions are checked against the resulting balance. Overfunding remains recorded. Pending/rejected payload details and correction guidance are shown separately. No income, expense, crypto or project repository is involved in allocation writes.

FR16: exact remaining, actual percentage, overfunding, opportunity count, next date and cent-ceiling contribution are calculated outside Composables. The visual bar alone is capped. The full schedule can be inspected in a lazy list, including past dates. Deadline inclusion, original month-end anchoring, today inclusion, reached and overdue behavior are covered by domain tests. An active timer checks the Colombo midnight boundary (at most 60-second sleeps to accommodate clock changes); resume also refreshes calculations. There are no ahead/behind judgements or completion forecasts.

Real-time/offline/error behavior: parent and full-ledger metadata listeners retain the last coherent confirmed values across pending writes or out-of-order snapshots. Empty cache is not proof that no goal or ledger exists. Cached values are labelled; missing/invalid data produces an error rather than confirmed zero. Listener and recovery-read errors retry, and registrations are removed on cancellation. Account changes cancel old jobs, clear forms/messages/data and reject delayed old callbacks. CancellationException propagates.

Local persistence precedes remote submission. The existing DataStore keeps immutable `GOAL_CREATE`, `GOAL_EDIT`, `GOAL_ENTRY_CREATE`, `GOAL_ENTRY_UPDATE` and `GOAL_ENTRY_DELETE` records. One unresolved goal mutation per owner is intentional: resolve/reconcile it before making another mutation, rather than queueing independently based on a stale shared balance. Offline transactions are retried from this durable queue; Firestore transactions themselves are not assumed to queue offline. Retries inspect the immutable operation receipt before version/temporal validation or further writes. Await timeouts retain the operation. A locally persisted proposal also blocks repeated taps until recovery observation or acknowledgement catches up. A bounded, UID-filtered in-process acknowledgement signal clears feedback even if a fast receipt was reconciled before its pending recovery emission was seen; it stores no financial total. Definitively rejected proposals can be retried unchanged or dismissed before reopening current details. Unknown outcomes cannot be dismissed as failed.

## Schema and query details

- `users/{uid}/savingsGoals/active`: `name`, `targetMinor`, `openingMinor`, `startDate`, `deadline`, `savedMinor`, `version`, `lastOperationId`, `lastEntryId`, `createdAt`, `updatedAt`.
- Its `entries/{entryId}`: `kind` (`contribution`/`withdrawal`), positive `amountMinor`, `entryDate`, `note`, `deleted`, `version`, `lastOperationId`, `createdAt`, `updatedAt`.
- Its `operations/{operationId}`: immutable `kind`, `entryId`, resulting parent `version`, exact user-field `payload`, `createdAt`.
- Its `rejections/{operationId}`: immutable `kind` (CREATE/EDIT), resulting `version`, exact original goal `payload`, server `createdAt`. This expiry fence has no savings effect and is mutually exclusive with an accepted receipt for that operation.

All money is LKR integer minor units, bounded only by the existing signed 64-bit representation. BigInteger intermediates avoid order-dependent overflow while checking the resulting ledger balance; explicit bounds conversion is compatible with API 26. Decimal input and BigDecimal display reuse the existing exact-money conventions. No redundant direction/signed total, goal-progress total or schedule array is stored. Timestamps use server values; updates preserve createdAt. Notes follow the existing 500-character convention and goal names allow up to 100 characters. Client date parsing and rule calendar arithmetic both validate real dates, including Gregorian century/leap rules; regex alone is not treated as calendar validation. Rules compare transaction dates with request.time shifted to Colombo and require future deadlines for creation/revision. Client-only behavior includes decimal input syntax, form conflict feedback and schedule calculations.

Reads are the fixed active document plus the complete owner-scoped entries collection, with metadata included and no query limit. The ledger query deliberately has no orderBy: malformed documents missing entryDate must reach validation rather than disappear from the query. Display ordering occurs after decoding. The coherent ledger sum is checked against the protected parent counter. Operation receipts and expiry fences are read individually inside transactions. No composite index or firestore.indexes.json change is required. Ledger, receipt and fence collections may grow, but individual documents stay bounded; complete-history reads can become costly for very large histories and are never silently truncated.

The existing expense, income, linked receipt, project/milestone and crypto rule blocks are unchanged (text comparison with baseline after removing the savings block). Savings rules additionally enforce expiry fences. The user reported manually publishing this reviewed version; no deployment was performed by the agent. The expected local SHA-256 is verified; deployed source could not be retrieved without an administrative CLI login, so live behavior is reported separately rather than claimed as source equality. SHA-256 of the current complete local firestore.rules: `45A13EE4E81717FFBC198D54744DAFA7A6322C4FBD5379105287BE8922285A0A`.

## Initial milestone verification — 26 October 2026 (before focused recovery correction)

Baseline: `dbfc3c7a6d9217f13e0cb8e157c5f56107ceab85`. No applicable AGENTS.md was found. During this initial implementation pass, no commits, pushes, dependency edits, Firebase target changes, rules publication or live financial writes were performed. The unrelated `.vscode/settings.json` and `reporting-review-fixes.diff` retained their initial hashes; the Git index was unchanged.

Focused runs used:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*SavingsMathTest' --tests '*FirestoreGoal*Test' --tests '*GoalViewModelTest' --console=plain
npm.cmd test -- --grep 'Savings allocation integrity' # from firestore-tests
```

Final required Android checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: BUILD SUCCESSFUL; **169 unit tests, zero failures/errors/skips** (142 existing plus 27 savings tests); app and test APKs built; lint **0 errors / 20 warnings**. Initial lint caught BigInteger.longValueExact requiring API 31; it was replaced with an explicitly checked API 26-compatible conversion. One initial test run stalled in the new rollover fixture because its periodic ViewModel timer was not cancelled until after runTest tried to drain the scheduler. The exact worker/test was identified with jstack, only that worker was stopped, and fixture cleanup now clears ViewModels before runTest completes. No blocking deferred waits were added inside Mockito answers. The mutable test clock was corrected to honor withZone before the final rollover result.

Full rules command: `npm.cmd test` from `firestore-tests`, against **demo-clarity-test on the local Firestore emulator**. Result: **99 passing**, retaining all 87 existing tests plus 12 savings tests. Coverage includes owner CRUD, AT07/08 allocation balances and financial separation, strict schema/dates/timestamps, overdue edits, negative withdrawals/corrections, forbidden parent-plus-child initialization, counter-only/entry-only/receipt-only changes, multiple entries sharing a delta, immutable receipts/tombstones, fully valid foreign atomic writes, two concurrent withdrawals, and an exact Long.MAX_VALUE REST-integer wraparound attack. The existing emulator was already running; an attempted additional emulator startup correctly refused the occupied port. No production endpoint was used by this suite.

Isolated Android command after installing both APKs with `adb install -r` (no storage clearing):

```powershell
adb -s emulator-5554 shell am instrument -w -e class com.clarity.finance.GoalNavigationTest,com.clarity.finance.DashboardNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Result on the API 37 Android emulator: **OK (7 tests)**, comprising three savings checks and the four existing dashboard checks. The isolated tests use the production navigation, ViewModels and screens with fake repositories at **320 dp and 1.3 font scale**. They cover explicit creation, AT07/08 figures, the schedule dialog, allocation correction, deletion/keep confirmation, dashboard navigation/value, pending/rejected deletion retaining confirmed savings, account changes and overdue-deadline revision. The existing dashboard tests exercise reporting/navigation and malformed/overflow history protections. Initial UI fixture failures in scroll helpers, operation-count expectation and today inclusion were corrected before the final run. These are isolated UI checks, not live Firebase acceptance.

Local logs/reports are ignored build artifacts: `build/savings-full-checks.log`, `build/savings-full-rules.log`, `build/savings-android-ui.log`, Android unit XML under `app/build/test-results/testDebugUnitTest` and lint reports under `app/build/reports`. `git diff --check` and a separate new-file trailing-whitespace scan are clean. No pre-existing source changed outside the integration/rules/test files listed below. Emulator app storage and the existing DNS workaround were preserved.

## Focused recovery correction — 26 October 2026

The initial implementation incorrectly mapped broad argument/state exceptions and authentication/access codes to FAILED. A later failed reconciliation attempt did not establish that an earlier timed-out Firebase transaction could never commit, yet FAILED exposed dismissal. This correction changes only that recovery policy, its UI gating, tests and this report. The initial 21-file milestone and unrelated local work remain intact.

Task lifecycle was inspected in the existing cached dependency sources: Firebase BoM 34.2.0 selects Firestore 26.0.0, and coroutines-play-services is 1.11.0. Task.await() delegates to awaitImpl(null); cancelling its coroutine does not cancel the receiving Task. Firestore TransactionRunner completes its result Task after commit acknowledgement and may retry callbacks. Transaction.commit() adds VerifyMutation preconditions for documents read but not written. No dependency versions were changed.

Current outcome policy:

- Persist UNCERTAIN before starting/awaiting a Firebase transaction. Timeouts and CancellationException retain the exact operation ID and immutable payload; cancellation propagates. A later account/auth/permission error cannot disprove an earlier commit. Legacy FAILED records are also non-dismissable and reconciled again, rather than trusted as rejection proofs.
- Retain an outstanding SDK Task keyed by UID and operation identity, with an exact payload check. New waiters reuse it instead of starting overlapping transactions. Account checks still run before reawaiting. A repository sweep mutex also prevents concurrent recovery workers from submitting together. A process restart can lose a Task handle, so durable uncertainty and server reconciliation remain necessary.
- Remote results are explicit Confirmed, Rejected or Uncertain outcomes. Matching immutable receipts reconcile before other checks. A successful atomic write also acknowledges the receipt it created. Mismatched receipts remain an error and are never treated as success.
- Rejected is returned only from a no-write server transaction after receipt absence and a permanent contradiction are checked: an already-existing fixed goal for CREATE, a parent version beyond the immutable expected version, or a deterministic ledger identity/balance violation at that expected version. The existing rules forbid parent deletion/version rollback and ledger changes without a matching version increment. Consequently an older outstanding attempt cannot later repair these contradictions while retaining its expected version. Pure balance/identity validation is the only exception context classified this way; decode, SDK, auth and access failures are not proofs.
- The callback returns the rejection outcome rather than throwing it. The SDK must successfully verify its read set before that outcome is exposed. If an earlier transaction commits between receipt read and verification, the SDK retries and reconciles its receipt. If verification fails without a successful outcome, the proposal stays uncertain. Rules are unchanged.
- Only persisted REJECTED records expose Dismiss rejected proposal, with the same guard in the ViewModel and repository. Retrying preserves the original identity/payload/version. Dismissal permits a separately corrected proposal with a new identity; uncertain operations cannot be silently replaced or discarded. If access remains unavailable or no authoritative proof can be established, uncertainty remains retained rather than being forced to failure.
- Local decode, recovery-store/status and cleanup errors are handled outside the remote outcome boundary and shown as UID-scoped recovery bookkeeping errors. A confirmed remote outcome remains acknowledged if local cleanup fails; the record remains and cleanup retries without another remote submission in that process. The UI distinguishes confirmed-with-local-cleanup-pending from an uncertain remote outcome. After restart, exact receipt reconciliation remains safe.

Fresh verification for this correction:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*FirestoreGoal*Test' --tests '*GoalViewModelTest' --console=plain
```

Result: **27 focused tests passed**. The required suspending regression starts A's underlying work independently of its cancelled await, times out A before any mutation, restarts repository recovery, makes B throw an access error before receipt reconciliation, asserts retained UNCERTAIN status/identical payload/non-dismissability, then permits A to commit and verifies that C reconciles its receipt with exactly one mutation and clears recovery. The previous commit-before-timeout test is retained. Additional coverage exercises an actual pending TaskCompletionSource reused after await timeout/account change, a changed-payload refusal, proven rejection/correction, legacy failures, raw errors, local cleanup failure and UID-isolated feedback. No deferred wait occurs inside a Mockito answer. The first fixture run hit Android's unmocked SparseArray while constructing Firebase's error-code enum; the exception object is now mocked while all asynchronous operation behavior remains genuinely suspending.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL; 175 unit tests, zero failures/errors; both APK builds successful; lint 0 errors / 20 warnings.** After that full run, two assertions were strengthened in the existing remote tests (negative-balance rejection and running-payload immutability), and an explanatory production comment was added; no production behavior changed. A fresh `:app:testDebugUnitTest --tests '*FirestoreGoalRemoteTest'` run passed **all seven** remote tests with those assertions.

`npm.cmd test -- --grep 'read-only rejection|verified stale version'` from firestore-tests passed both new emulator races. Fresh full `npm.cmd test` then passed **101 tests** (all 99 previous tests retained). One case verifies that an earlier commit invalidates a stale receipt-absence read and causes reconciliation. The other verifies a stale-version rejection while an older write is still outstanding, then proves the late write cannot commit. Depending on SDK validation order, the late stale write can receive PERMISSION_DENIED before its callback retries; the earlier verified contradiction, not that error, is the rejection proof. This was observed and the fixture asserts the exact permitted denial without changing rules.

After `adb install -r` for both APKs, fresh isolated Android execution of `com.clarity.finance.GoalNavigationTest` on API 37 passed **3 tests** at 320 dp/1.3 font scale. The expanded pending/rejection check verifies that a legacy FAILED proposal shows uncertain feedback, retains the original confirmed contribution and hides Dismiss; a proven REJECTED fixture then exposes correction/dismissal. The four dashboard Android checks are reused from the initial seven-test milestone run and were not rerun for this correction. No live repositories or records were used. Emulator storage and DNS configuration were preserved.

Fresh logs: `build/savings-recovery-focused.log`, `build/savings-recovery-final-checks.log`, `build/savings-recovery-task-final.log`, `build/savings-recovery-rules-focused.log`, `build/savings-recovery-rules-full.log`, `build/savings-recovery-android-ui.log`. Earlier 169-unit/99-emulator/seven-Android evidence above remains historical and is not substituted for the fresh results. Live delayed-commit/network/auth scenarios and API 26 runtime checks remain unexecuted. Hilt/Koin remains outside scope.

A local pre-correction snapshot was taken before editing, including existing uncommitted milestone files. The exported correction-only diff compares against that snapshot, not HEAD, and is verified by applying it to a separate snapshot copy and comparing resulting file SHA-256 values. Correction files are FirestoreGoalRepository.kt, FirestoreGoalRemote.kt, GoalRepository.kt, GoalViewModel.kt, GoalScreen.kt, their three unit-test files, GoalNavigationTest.kt, firestore-tests/savings-tests.js and this report. Other milestone/source files, firestore.rules, dependencies, Firebase targets, unrelated IDE settings and reporting-review-fixes.diff remain unchanged. No publication, live write, commit or push occurred.

## Deadline-expiry correction — 26 October 2026

The earlier recovery correction remains intact. A separate dead end existed when an offline CREATE or deadline-changing EDIT expired before synchronisation: local date validation could only retain UNCERTAIN, leaving no way to revise its immutable proposal. The fix establishes a server-authoritative, permanent barrier before offering deadline revision.

FirestoreGoalRemote still checks the exact accepted receipt first, even if the deadline has passed. If it matches, the operation is confirmed and no replacement is allowed. Otherwise an expired client date merely proposes creation of `savingsGoals/active/rejections/{operationId}`. Its exact fields are kind, next version, original five-field goal payload and server createdAt. The server rules must independently verify a real calendar deadline on or before request.time's Asia/Colombo day, strict schema/owner/types, no accepted receipt after the commit, and an unchanged parent. CREATE requires no parent before or after; EDIT requires the original expected parent version and a proposed deadline different from its confirmed deadline. An unchanged overdue deadline remains editable normally.

Both parent/new-receipt guards and accepted-receipt creation forbid a matching expiry fence after the transaction. Accepted receipt and fence therefore cannot coexist through permitted writes; the fence cannot be edited or deleted. This also blocks an older client transaction that never read the new fence. Only acknowledgement of the fence transaction, or reconciliation of its exact immutable marker, yields EXPIRED. A callback choosing expiry, a local-clock exception, server refusal, timeout, access error or cancelled await alone never does. A client clock ahead of the server cannot create an authoritative fence. No counters, ledger entries or ordinary financial records change when a fence is created.

Ordinary retries continue sharing the outstanding Firebase Task. A finance-free expiry-check transaction may run alongside an outstanding old Task to settle it safely; these expiry-check Tasks are also reused, including after the old financial Task completes. If the old operation wins, receipt reconciliation preserves its savings. If the fence wins, the old operation cannot subsequently commit. Only then can the user replace it. This is a narrowly scoped new rejection mechanism, not a weakening of the existing uncertainty policy.

The UI offers **Revise deadline** only for server-established EXPIRED CREATE/EDIT proposals. It preserves name, target, opening allocation, start date and expected version, and makes only the deadline editable. Closing the form retains the old durable proposal. Save validates a genuinely future deadline and uses a fresh operation ID; a conditional DataStore edit atomically replaces the old EXPIRED recovery row with the new PENDING row. There is never a delete-then-save gap. The old operation and its payload remain immutable in the server fence. Idempotent local retries retain the same replacement identity, including a timeout after persistence. A local write failure retains the original row; account changes still clear old-user forms and messages. If the confirmed goal changed version meanwhile, the UI asks the user to inspect/edit the latest goal instead of silently rebasing the proposal. The atomic replacement helper is restricted to goal CREATE/EDIT recovery types; legacy recovery behavior is unchanged.

Fresh verification commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*FirestoreGoal*Test' --tests '*GoalViewModelTest' --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests '*FirestoreGoalRemoteTest' --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
# From firestore-tests:
npm.cmd test -- --grep 'expiry fence|expiry fences|accepted receipt wins'
npm.cmd test
```

The focused recovery run passed all **38 tests**; the strengthened Task-reuse run passed all **12 remote tests**. Final full verification passed **186 unit tests, zero failures/errors/skips**, both APK builds and lint **0 errors / 20 warnings**. All prior premature-dismissal regressions are retained. Deterministic clock tests save a valid proposal on 2026-01-31 with deadline 2026-02-28, advance to 2026-03-01 before any commit, establish expiry, persist a revised deadline, reopen repository recovery and acknowledge exactly one creation with the same opening allocation and start date. EDIT coverage preserves the confirmed contribution ledger and applies its opening-allocation delta once. Earlier committed CREATE and EDIT operations reconcile their receipts after expiry and cannot be replaced. Other tests cover a still-outstanding old Task, an outstanding expiry-check Task, server refusal of a client-proposed fence, mismatched markers, local persistence failure, cancelled post-persistence awaits and stable replacement identity after closing the form.

The five focused expiry emulator tests passed, then the full suite passed **106 tests** with all previous 101 retained. They use the actual local rule file and demo project, including server request.time/Colombo date validation. Coverage includes CREATE and EDIT revision, unchanged overdue editing, strict schema and owner isolation, immutable fences, accepted-receipt exclusion, receipt-plus-fence batch denial, and an outstanding legacy transaction denied after fencing even when its altered payload uses a future deadline. Revised CREATE saved exactly 11,200; revised EDIT retained the 10,000 contribution and changed the opening allocation from 11,200 to 12,000, yielding exactly 22,000. No financial counter changes during fencing.

After both APKs were installed with `adb install -r`, fresh isolated API 37 execution of `com.clarity.finance.GoalNavigationTest` passed **4 tests** at 320 dp/1.3 font scale. The new UI case opens Revise deadline, checks that the other fields are disabled, closes/reopens without losing the stored proposal, saves the revised deadline and observes exactly one goal with the original 11,200 opening and start date. These tests use isolated repositories, not live data. The earlier four dashboard Android checks were reused and not rerun in this focused pass. App storage and the DNS workaround were preserved.

Logs: `build/savings-deadline-focused.log`, `build/savings-deadline-task-final.log`, `build/savings-deadline-final-checks.log`, `build/savings-deadline-rules-focused.log`, `build/savings-deadline-rules-full.log`, `build/savings-deadline-android-ui.log`. The previous 175-unit/101-emulator recovery results above remain historical. This correction pass included fresh full runs rather than substituting that evidence. No live expiry, network interruption or actual Firebase account-switch acceptance was executed during that pass; the subsequent authorized live run is recorded separately. Hilt/Koin and API 26 runtime verification remain outstanding.

A separate pre-deadline-correction snapshot was created before edits. The new correction-only diff is against that snapshot, not HEAD and not the original milestone baseline. It is verified by applying it to a separate snapshot copy and comparing every resulting changed file by SHA-256. The earlier review ZIP and recovery diff are preserved unchanged. This correction changed 13 saved files: the recovery store, goal interface/remote/repository/ViewModel/screen, their three unit-test files, Android goal test, savings emulator tests, local firestore.rules and this report. That pass made no dependency, Firebase target, live-data, commit, push or deployment changes. Only savings rules changed; other financial policy blocks match the pre-correction snapshot exactly. Publication was still required at that point; the user subsequently reported publishing the reviewed file. Refused/unavailable marker writes remain uncertain. No new indexes are required.

Manual check after that later publication and explicit live-write authorization: queue a valid offline goal proposal; let its proposed deadline expire; reconnect; wait for server-established expiry; tap Revise deadline; enter a future date; Save proposal. Verify the preserved name/target/opening/start, one accepted replacement and no duplicate savings. Repeat for a deadline-changing EDIT with existing contributions. If an earlier attempt committed, expect receipt reconciliation and the original goal/ledger, with no replacement prompt. Do not alter live rules to manufacture outcomes.

## Accepted live run and finalization

The subsequent explicitly authorized production-app acceptance passed creation/schedule, allocations, correction/deletion, goal editing, actual offline process restart/reconnect and interactive queued-operation account isolation. Fresh server receipt replay verified exactly eight accepted operations and final saved savings of **LKR 22,201**, with no duplicate savings mutations or unrelated financial/recovery changes. The retained goal and samples are documented without private identifiers in [SAVINGS_GOALS_LIVE_RESULTS.md](SAVINGS_GOALS_LIVE_RESULTS.md). No live deadline expiry or in-flight server/account race is claimed.

After acceptance, the authorized finalization backed up the saved working state outside the repository before staging. Verified historical snapshots kept both recovery corrections separate. Each intermediate executable commit tree compiled, and the final normal-source command below freshly passed **186 unit tests**, app/test APK builds and lint **0 errors / 20 warnings**:

```powershell
.\gradlew.bat '-Pkotlin.incremental=false' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
git diff --check
```

The latest **106-test Firestore emulator** and **four-test isolated savings Android** results above are reused because their rules and executable sources are unchanged; they were not rerun during finalization. The opt-in read-only live audit extension was compiled and exercised during acceptance. Temporary writers, authentication inspectors, private snapshots, diagnostic scripts, logs, caches, credentials and build outputs are excluded. Source/test/rule files retain their accepted backup hashes; unrelated IDE settings and review diffs are preserved uncommitted. Only documentation was clarified/sanitized during grouping. Full command details, initial harness invocation issues and the distinction between live and automated checks appear in the live-results report.

No agent deployment, additional live-data change, dependency upgrade or unrelated feature was introduced during finalization. Hilt/Koin adoption and API 26 runtime verification remain outstanding. No new indexes are required.

## Exact milestone files

The current full milestone contains **24 files**: the original 21, the existing SubmissionRecoveryStore.kt needed for atomic deadline-only replacement, the extended opt-in read-only live audit and the sanitized live-results report. The earlier 21-file ZIP and correction-only diffs are historical and have not been overwritten.

New:

```text
SAVINGS_GOALS.md
SAVINGS_GOALS_LIVE_RESULTS.md
app/src/main/java/com/clarity/finance/data/model/SavingsGoal.kt
app/src/main/java/com/clarity/finance/data/repository/GoalRepository.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreGoalRemote.kt
app/src/main/java/com/clarity/finance/data/repository/FirestoreGoalRepository.kt
app/src/main/java/com/clarity/finance/viewmodel/GoalViewModel.kt
app/src/main/java/com/clarity/finance/ui/goals/GoalScreen.kt
app/src/test/java/com/clarity/finance/data/model/SavingsMathTest.kt
app/src/test/java/com/clarity/finance/data/repository/FirestoreGoalRemoteTest.kt
app/src/test/java/com/clarity/finance/data/repository/FirestoreGoalRepositoryTest.kt
app/src/test/java/com/clarity/finance/viewmodel/GoalViewModelTest.kt
app/src/androidTest/java/com/clarity/finance/GoalNavigationTest.kt
firestore-tests/savings-tests.js
```

Modified:

```text
app/src/main/java/com/clarity/finance/data/local/SubmissionRecoveryStore.kt
app/src/androidTest/java/com/clarity/finance/LiveFirestoreAuditTest.kt
app/src/main/java/com/clarity/finance/MainActivity.kt
app/src/main/java/com/clarity/finance/di/AppContainer.kt
app/src/main/java/com/clarity/finance/di/ViewModelFactory.kt
app/src/main/java/com/clarity/finance/ui/HomeScreen.kt
app/src/main/java/com/clarity/finance/ui/dashboard/DashboardScreen.kt
app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt
firestore.rules
firestore-tests/test.js
```

## Manual acceptance after rules review/publication and explicit live-write authorization

1. Home → Savings Goal → Create goal. Enter your own name, target 490000, opening 11200 and deadline exactly one year from the displayed start. Save proposal. Wait for acknowledgement: saved 11200, remaining 478800, 12 opportunities, required 39900, progress 2.29%. Inspect contribution schedule; verify final date, original day anchor and no duplicates.
2. Record allocation → contribution → 10000, choose actual date and optional note → Save proposal. After acknowledgement, saved is 21200, even if the monthly cash surplus is also 10000. Record allocation → change type to withdrawal → 1200 → Save proposal. Confirm saved 20000. Check ordinary income, expenses, crypto and pending projects are unchanged.
3. Inspect / correct entry → change amount/date/note → Save proposal. Inspect the updated history and exact saved balance. Delete entry → Keep entry leaves it unchanged; Delete entry → Confirm deletion changes the balance once after acknowledgement. A correction, contribution deletion or withdrawal exceeding available savings must retain the confirmed ledger and show an error.
4. Edit goal → change opening allocation/target/deadline → Save proposal. Confirm ledger history and start date survive; a negative resulting balance is rejected. Inspect reached/overfunded and naturally overdue fixtures; only the bar caps at 100%, and an overdue goal invites deadline revision.
5. With a previously server-confirmed goal/history loaded, disconnect only the test device → Record allocation → Save proposal. Confirm old saved balance plus a separate pending proposal. Force-stop/reopen without clearing storage → open Savings Goal → reconnect. Confirm the exact entry/operation IDs reconcile once, pending feedback clears and no duplicate appears. Only one unresolved goal mutation is allowed at a time. Do not retry unrelated operations.
6. Use a second device/isolated fixture to change the goal while a form is open. Saving that stale draft must request reopening current details. Pending/rejected edits/deletions retain the original confirmed contribution. Do not change live rules to manufacture failure.
7. Switch accounts interactively in the app, then return. Confirm old goal, forms and messages disappear; only the original owner can resume its saved operation. From Dashboard, verify the goal card updates from confirmed records and remains independent of selected reporting month and ordinary reporting.

Current verification limits: live expiry boundaries, in-flight server/account races and real multi-device conflicts remain **unexecuted**. The subsequent authorized live run did verify owner CRUD, exact balances, separation, actual Android offline restart/reconnect and a queued-operation account switch; see SAVINGS_GOALS_LIVE_RESULTS.md for fresh server evidence and retained test fixtures. Durable DataStore close/reopen, suspending timeout/reconciliation fakes, stale callbacks and conflicts remain automated coverage, not additional live claims. Hilt/Koin migration and API 26 device/runtime verification remain outstanding assignment tasks outside this milestone. Scope remains the required single savings goal and its integration; no recurring expenses, multiple goals, forecasting, automatic transfers or optional features were added.
