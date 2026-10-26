# FR07 realised crypto results

This milestone implements FR07, the crypto-related editing/backdating part of FR09, and AT03 from the supplied Analysis and Requirements PDF (pages 4, 6 and 9). The original assignment brief/rubric was reviewed separately. Its broader submission requirements do not expand this feature's scope.

## Architecture and assignment gap

Authenticated Home → Crypto Results → inspect/add/edit/delete. Compose observes CryptoViewModel StateFlow; validation/reporting lives in CryptoReporting, and persistence goes through CryptoRepository → FirestoreCryptoRepository → FirestoreCryptoRemote. No ordinary financial repository is called by this feature.

The original brief's Section 5, page 6, specifies “Hilt (preferred) or Koin” and prohibits manual ViewModel construction inside Composables. This project uses AppContainer and ViewModelFactory with Compose viewModel(factory). The factory pattern preserves MVVM, but absence of Hilt/Koin remains an assignment compliance gap. No whole-app DI migration, dependency addition/upgrade, or build-stack change was bundled here. Room is strongly recommended in the brief, rather than mandatory; existing Firestore caching and DataStore recovery are retained.

## Meaning and arithmetic

Record the realised LKR profit/loss supplied by the user. Gross sale proceeds, capital deposits/withdrawals and unrealised valuations are excluded. Crypto never writes incomeReceipts, expenses, expectedPayments, milestones or goal savings, and never changes cash-surplus calculations.

- Direction is gain/loss; amountMinor is a positive signed-64-bit integer magnitude. Only the domain model derives the sign. No signed duplicate field or stored total exists.
- Input uses an unsigned plain decimal with up to two decimal places. Blank, zero, negative/signed input, scientific notation, fractional minor units and Long overflow are rejected.
- There is no arbitrary product monetary ceiling: Long.MAX_VALUE minor units is the representation limit shared with Firestore integer storage. Checked addition/negation detects total overflow; the UI retains inspectable records and shows unavailable totals. Display uses BigDecimal.
- Actual result date defaults to today in Asia/Colombo, permits backdating, and rejects invalid/future dates. Notes are optional in the form and stored as an empty string when absent, with the existing 500-character convention.
- Selected-month history uses resultDate, not upload time. Current-year YTD is January 1 through today; a selected past year's cutoff is December 31. Future years/months are not offered. The cutoff and timezone are visible. Returning to the screen or adding after a date change refreshes the reporting window.
- Totals and monthly history use the confirmed/cached baseline, with an explicit cached-data warning. Unconfirmed proposals are shown separately: a failed edit or pending deletion retains the original contribution until the server changes it. These are recorded-data totals, not account balances.

## Schema, queries and rules

Dedicated collection: users/{uid}/cryptoResults/{resultId}.

| Field | Stored representation |
| --- | --- |
| direction | gain or loss |
| amountMinor | positive integer LKR minor units |
| resultDate | zero-padded YYYY-MM-DD |
| note | string, up to 500 characters |
| lastOperationId | stable UUID for the latest mutation |
| createdAt | server timestamp on creation; preserved on update |
| updatedAt | server timestamp on creation/update |

One real-time owner query bounds resultDate from YYYY-01-01 through the labelled YTD cutoff, ordered by resultDate. Monthly history is filtered from that bounded year for a consistent month/YTD snapshot. Firestore's default ascending single-field resultDate index suffices; no composite index is required. Existing firebase.json references a missing firestore.indexes.json; that pre-existing configuration gap is unchanged. Do not disable the resultDate single-field index when publishing.

Only an explicit cryptoResults match and its validation helpers were added to firestore.rules; existing policy blocks remain unchanged. Owner-only reads/queries/create/update/delete, an exact field allowlist, direction, positive integer amount, typed/bounded notes/operation IDs, timestamps and immutable createdAt are enforced by rules. The date regex checks shape only: numeric components, month lengths, Gregorian leap-year arithmetic and request.time plus 330 minutes separately enforce calendar validity and the Colombo future cutoff. [Firebase timestamp](https://firebase.google.com/docs/reference/rules/rules.Timestamp_) and [string conversion/slicing reference](https://firebase.google.com/docs/reference/rules/rules.String).

Client-only checks include decimal-input syntax, reporting sums/overflow, and user feedback. Optimistic version checks and operation reconciliation are implemented in repository transactions; rules do not constitute a crypto operation-history ledger or prevent an owner deliberately adding two distinct records.

The user reported manually publishing this reviewed rules file to clarity-1c83e before the authorized live acceptance. Live owner access and CRUD then succeeded; see [live results](FR07_LIVE_RESULTS.md). The agent performed no deployment and changed no Firebase target/configuration. Earlier payment-rule hashes describe their prior artifact.

Current local rules SHA-256: 2DDCFE4A8EDC356D6F17C52E26BB1A7655E259FA85F00C54850FEE7584D2EE5D.

## Durable operations and lifecycle

The existing SubmissionRecoveryStore/DataStore file and legacy EXPENSE/INCOME/project formats remain compatible. New CRYPTO_CREATE, CRYPTO_UPDATE and CRYPTO_DELETE records use UID, operation type and result ID. Payloads are immutable, including operation ID and expected update timestamp. A different operation on an unresolved result is blocked.

Save confirms durable local acceptance only. Pending proposals are visible separately from confirmed data. While Crypto Results is open for the owner, a scoped coroutine reconciles all crypto attempts, including other reporting years. Transient/offline outcomes retry every five seconds; waits are bounded at ten seconds. Closing the screen/process stops the synchronizer; reopening restores the queue. This is not an OS background-service guarantee.

Transactions reconcile matching operation IDs and complete values before any write. Creation cannot reset an existing timestamp; update/delete require the original server version. An already absent delete is complete, and a changed record cannot be deleted by a stale retry. Timed-out transactions may still commit; pending identity is retained. Confirmed SDK snapshots and transaction acknowledgements, not cache presence, determine completion.

Definitive rejections stay failed and can retry the saved proposal. Dismissing a definitively rejected proposal removes only that local proposal, allowing a fresh server record to be inspected/corrected; pending/unknown operations cannot be dismissed. Editing/deletion starts from a confirmed server version. Offline creation is available; already accepted changes survive restart through durable recovery.

UID changes cancel collection/synchronization jobs, clear forms/history/selection and invalidate callbacks. The remote checks the active UID before starting and before writing in transaction callbacks. CancellationException propagates; listener registrations are removed on cancellation. Listener errors retain usable feedback and retry automatically or through Retry loading.

## Operation-derived feedback

The local-save banner derives from current recovery attempts and unknown outcomes, rather than a permanent save message. Matching confirmation/reconciliation clears only that operation; another pending or rejected proposal stays visible. Confirmation remains recognized while recovery cleanup catches up. Unknown outcomes retain truthful feedback until matching evidence resolves them. Account changes clear old-user feedback and invalidate stale callbacks. Ten focused ViewModel regressions and an isolated Android UI regression cover these transitions.

## Verification

Evidence has three distinct phases: the original implementation checks (101 unit tests), the subsequent authorized live acceptance (25 successful server snapshots), and the later banner correction (111 unit tests and three isolated Android tests). The banner correction and commit preparation created no additional live records. Logs and private snapshots stay in ignored build directories. Android UI tests use isolated fake repositories; they are not live-network evidence. No Firebase deployment was performed by the agent.

Commands executed:

    .\gradlew.bat :app:testDebugUnitTest --tests com.clarity.finance.viewmodel.CryptoViewModelTest --tests com.clarity.finance.viewmodel.CryptoFeedbackViewModelTest --console=plain
    .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain

From firestore-tests, using the existing local Firestore emulator at 127.0.0.1:8080 and demo-clarity-test:

    npm.cmd test -- --grep 'FR07 realised crypto'
    npm.cmd test

Android feedback and navigation, after installing both APKs with adb install -r (preserving app data):

    adb shell am instrument -w -r -e class com.clarity.finance.CryptoFeedbackUiTest,com.clarity.finance.CryptoNavigationTest,com.clarity.finance.PaymentNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner

Coverage includes AT03, net loss, exact amounts, invalid/overflow/future inputs, Colombo boundaries, backdated edits/deletion, pending/failed states, stable retry/reconciliation, account changes, legacy recovery preservation, transaction payloads, listener cleanup and ordinary financial isolation. Coroutine fakes suspend normally; no deferred wait is hidden inside a blocking Mockito answer.

| Executed check | Result |
| --- | --- |
| Initial Android unit tests, before live acceptance | 101 passed: original 73 plus 28 crypto tests |
| Final Android unit tests, after banner correction | 111 passed: initial 101 plus 10 feedback regressions |
| assembleDebug and assembleDebugAndroidTest | Passed |
| lintDebug | 0 errors, 20 existing warnings |
| Focused crypto rules | 8 passed |
| Full Firestore emulator suite | 84 passed: all original 76 plus 8 crypto tests |
| Final Android instrumentation at 320 dp, API 37 | 3 passed: operation feedback, crypto navigation and existing payment navigation, using isolated repositories |
| Earlier authorized live acceptance | 25 successful Source.SERVER snapshots; see FR07_LIVE_RESULTS.md for actual scenarios and limits |

An initial calendar-rule test reused a created document with a new createdAt and was correctly denied. The fixture was corrected to create distinct IDs; no rule relaxation was needed. No hung tests occurred. The earlier live run exercised offline creation, force-stop/reconnect, rejected proposals and interactive account switching; it did not force an in-flight cross-account completion race or OS low-memory kill. Those live scenarios were not repeated for the banner correction. API 26 runtime verification remains outstanding. Target live project remains clarity-1c83e; emulator rule tests use their separate demo project.

## Exact changed files

All paths below are relative to the repository root. No generated output, Firebase configuration, dependency files or credentials are included.

| Area | Files |
| --- | --- |
| Domain | app/src/main/java/com/clarity/finance/data/model/CryptoResult.kt |
| Repository | app/src/main/java/com/clarity/finance/data/repository/CryptoRepository.kt; app/src/main/java/com/clarity/finance/data/repository/FirestoreCryptoRepository.kt; app/src/main/java/com/clarity/finance/data/repository/FirestoreCryptoRemote.kt |
| Shared durable operations | app/src/main/java/com/clarity/finance/data/local/SubmissionRecoveryStore.kt |
| ViewModel and UI | app/src/main/java/com/clarity/finance/viewmodel/CryptoViewModel.kt; app/src/main/java/com/clarity/finance/ui/crypto/CryptoScreen.kt |
| DI and entry point | app/src/main/java/com/clarity/finance/di/AppContainer.kt; app/src/main/java/com/clarity/finance/di/ViewModelFactory.kt; app/src/main/java/com/clarity/finance/MainActivity.kt |
| Home and navigation | app/src/main/java/com/clarity/finance/ui/HomeScreen.kt; app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt |
| Domain unit tests | app/src/test/java/com/clarity/finance/data/model/CryptoReportingTest.kt |
| Repository unit tests | app/src/test/java/com/clarity/finance/data/repository/CryptoReconciliationTest.kt; app/src/test/java/com/clarity/finance/data/repository/FirestoreCryptoRepositoryTest.kt; app/src/test/java/com/clarity/finance/data/repository/FirestoreCryptoRemoteTest.kt |
| ViewModel unit tests | app/src/test/java/com/clarity/finance/viewmodel/CryptoViewModelTest.kt; app/src/test/java/com/clarity/finance/viewmodel/CryptoFeedbackViewModelTest.kt |
| Android instrumentation | app/src/androidTest/java/com/clarity/finance/CryptoNavigationTest.kt; app/src/androidTest/java/com/clarity/finance/CryptoFeedbackUiTest.kt; app/src/androidTest/java/com/clarity/finance/PaymentNavigationTest.kt (factory wiring only); app/src/androidTest/java/com/clarity/finance/LiveFirestoreAuditTest.kt (explicitly opt-in and read-only) |
| Security and emulator tests | firestore.rules; firestore-tests/test.js |
| Documentation | CRYPTO_RESULTS.md; FR07_LIVE_ACCEPTANCE.md; FR07_LIVE_RESULTS.md; README.md |

## Manual acceptance (for a separately authorized future sample run)

The completed run is recorded separately in FR07_LIVE_RESULTS.md. This checklist grants no authorization for additional live writes.

1. Home → Crypto Results → Add Result → Gain → 60000 → choose an actual non-future date → Save Result. Inspect pending/confirmed feedback.
2. Add Result → Loss → 25000 on the same date → Save Result. Select that month: month and YTD net should be +35000.00. Income/expenses/project balances must remain unchanged.
3. Open a confirmed card → Edit Result → change amount/direction or backdate into the prior month/year → Save Result. Confirm both reporting periods and their labelled YTD cutoff.
4. Open a confirmed card → Delete Result → Keep Result; verify unchanged totals. Repeat → Confirm Delete; verify exactly one contribution removed after acknowledgement.
5. Inspect an empty month and a net-loss month. Try zero, negative, exponent, excess decimals, overflow, invalid calendar date and tomorrow; saving must be blocked.
6. With separately authorized test data, go offline → add distinct results → close/restart → reopen Crypto Results → reconnect. Verify the same IDs synchronize once, with no false server success. Switch accounts during an operation and verify no former-user content appears.

Dashboard, goals, recurring expenses, exchange connectivity, holdings/prices/wallets/trading/tax features and unrelated refactors are outside this milestone.
