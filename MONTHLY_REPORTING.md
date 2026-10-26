# FR11–13 monthly reporting and FR18 listeners

Implemented against `bbad375` after reading the supplied Analysis and Requirements (FR11–13, FR18, Section 6, AT05/06/12/14) and original assignment brief/rubric. Document contents describe assignment requirements, not additional authorization or scope. This milestone adds no goals, recurring expenses, exchange connections or other optional features.

## Implemented behavior

Authenticated Home → Dashboard provides Overview, Monthly history and Spending insights, with Add Expense always accessible. Existing Home routes remain available. History opens the existing expense/income edit and delete forms. Linked income receipts open their project instead of a standalone editor; the existing atomic receipt protections and rules remain unchanged. Crypto history is separate, with inspection and navigation to the existing Crypto Results feature.

Reporting starts at the current Asia/Colombo month and permits previous months, including year boundaries. Reporting uses `expenseDate`, `receiptDate` and `resultDate`, never audit/upload timestamps. The period, timezone and “Based on recorded transactions” are displayed. Backdating, amount edits, category corrections and deletion update listeners without restarting the app.

- Ordinary received income is the sum of stored `lkrAmountMinor` on actual income receipts, including project receipts exactly once. Existing historical conversion values are authoritative.
- Expenses are recorded LKR expense minor units. Recorded cash surplus is ordinary received income minus expenses; negative values are preserved. It is not an account reconciliation or automatic saving allocation.
- Signed realised crypto net is displayed separately and does not enter cash surplus.
- Pending income is **current collectible parent outstanding**, regardless of selected month: agreed amount minus direct and milestone receipts. Cancelled parents are excluded from collectible totals while their existing project history remains available. Milestones/allocations are never fetched or added again. LKR and USD amounts are grouped separately; no exchange estimate is invented.
- Category amounts are sorted descending with readable shared labels and numeric amounts beside simple bars. Their sum equals expenses. Committed/discretionary amounts use the classification stored on each record. Committed share is rounded half-up to one decimal; discretionary is its complement, so the displayed pair sums to 100.0%. Zero spending has no percentage calculation and shows useful empty text.

All monetary sums/subtraction use checked Long minor-unit arithmetic, with BigDecimal formatting. Float is used only for a nonmonetary bar fraction. Overflow makes the affected totals/insights and dependent surplus unavailable while independent sections and underlying history remain usable. Two existing edit-form loading conversions were changed from Double to BigDecimal so entering those editors from history cannot lose a cent on large values. The reused expense form now scrolls with the keyboard and wraps classification controls.

## Persistence, metadata and lifecycle

`ReportingRepository` is a read-only projection of existing collections and models. It does not create, replay, dismiss or mutate recovery records and introduces no new persistence framework or schema. `SubmissionRecoveryStore.getOperationRecordsFlow` is reused for UID-scoped counts of pending and rejected/conflicting attempts, including legacy expense types. Proposals are never merged into reporting transactions.

Each transaction section has an independent metadata-inclusive Firestore listener. A query reporting pending SDK writes retains its last clean snapshot as a whole, including contributions whose date/category changes or deletion would remove them from that query. With no clean baseline, its totals remain unavailable. Existing ordinary edit/delete and crypto mutation paths use version-checked transactions; local recovery proposals do not replace their original server records. Rejected proposals therefore retain the confirmed contribution; only a clean acknowledged snapshot changes it. Saved-attempt counts can briefly remain after a server acknowledgement while existing recovery cleanup completes; these are labelled saved changes, not extra financial contributions.

Nonempty cached snapshots are labelled as potentially incomplete/out of date. An initial empty cache is unavailable, rather than proof of zero transactions. A server-confirmed empty month can display zero. Failed or malformed reads never drop individual returned rows or substitute zero. Listener errors withhold affected totals and surplus that depends on them, retain inspectable prior rows, and leave other sections usable. On returned documents, missing required fields, unresolved timestamps, fractional money, invalid dates/classifications and arithmetic overflow produce explicit section errors. Bounded queries rely on the stored date fields required by existing schemas/rules; they are not a full-database corruption audit.

The ViewModel cancels listeners and clears state on month/account changes, checks a generation token for delayed callbacks, propagates coroutine cancellation, and removes listener registrations on flow cancellation. Listener errors retry every five seconds, with a ten-second initial-data watchdog and a Retry reporting data button. No assumption of a failed/cancelled write follows a timeout. The dashboard ViewModel remains on its navigation back stack during existing edit forms, allowing the reporting view to update on return; its jobs are cancelled when that entry is removed.

## Queries, security and indexes

All queries are under `users/{uid}`:

| Collection | Bounds/order |
| --- | --- |
| expenses | expenseDate >= first day, < first day of next month; order by expenseDate |
| incomeReceipts | receiptDate >= first day, < first day of next month; order by receiptDate |
| cryptoResults | resultDate >= first day, < first day of next month; order by resultDate |
| expectedPayments | All owner parent records, for current outstanding balances |

Category filtering happens over the complete bounded expense snapshot. There is no limit/pagination truncating totals and no milestone query. Firestore's default ascending single-field date indexes suffice; no composite index or new publication is needed. Existing `firebase.json` references a missing `firestore.indexes.json`; that pre-existing configuration issue remains untouched.

`firestore.rules` is unchanged. Its reviewed SHA-256 remains `2DDCFE4A8EDC356D6F17C52E26BB1A7655E259FA85F00C54850FEE7584D2EE5D`. Owner-only query permissions were tested against the local `demo-clarity-test` emulator, not by changing the Android Firebase target or deploying rules.

## Verification

The latest accepted application verification is **142 unit tests**, **four isolated dashboard Android tests**, successful app/test builds, and lint with **0 errors/20 warnings**, after the reviewed optional-note and crypto-overflow corrections. The initial 137-test run, earlier **87-test Firestore emulator suite** and earlier five navigation/UI checks are recorded separately below. The subsequent read-only live report covers actual server/UI matching and offline restart/reconnection; it preceded the two review corrections and does not verify those edge cases with live records.

Finalization reuses that evidence because executable source/tests remain unchanged. Only documentation was clarified/sanitized while preparing commits; no live operations, deployments or suite reruns were needed for finalization.

Commands executed from the repository root unless noted:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.clarity.finance.data.model.MonthlyReportingTest --tests com.clarity.finance.data.repository.ReportingProjectionTest --tests com.clarity.finance.data.repository.FirestoreReportingRepositoryTest --tests com.clarity.finance.viewmodel.DashboardViewModelTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.clarity.finance.data.model.MonthlyReportingTest --tests com.clarity.finance.data.repository.ReportingProjectionTest --tests com.clarity.finance.data.repository.FirestoreReportingRepositoryTest --tests com.clarity.finance.viewmodel.DashboardViewModelTest --tests com.clarity.finance.viewmodel.EditExpenseViewModelTest --tests com.clarity.finance.viewmodel.AddEditIncomeViewModelTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

From `firestore-tests`, against the existing local emulator at 127.0.0.1:8080:

```powershell
npm.cmd test -- --grep 'FR11-13 monthly reporting queries'
npm.cmd test
```

Android, after `adb install -r` of both debug APKs (preserving app data):

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.clarity.finance.DashboardNavigationTest,com.clarity.finance.CryptoFeedbackUiTest,com.clarity.finance.CryptoNavigationTest,com.clarity.finance.PaymentNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

| Check | Actual result |
| --- | --- |
| Focused arithmetic/repository/ViewModel tests | Passed; initially 23 new tests, followed by the added scoped-recovery and exact editor-value regressions |
| Initial full Android unit suite | 137 passed, zero failures/errors/skips (previous 111 retained, 26 added); subsequent review-correction results below |
| assembleDebug / assembleDebugAndroidTest | Passed |
| lintDebug | 0 errors, 20 existing warnings; no reporting-specific warning |
| Focused Firestore reporting queries | 3 passed |
| Full Firestore emulator suite | 87 passed; previous 84 tests retained |
| Earlier Android navigation/layout, API 37 at 320 dp | 5 passed before the review corrections: two reporting tests (including 1.3× text), existing crypto feedback/navigation and direct/milestone navigation. All use isolated repositories. Reporting covers category drill-down, backdated edit, deletion, quick-add/cancel, unavailable/independent sections, linked-receipt routing and account isolation. |
| git diff --check | Passed |
| Subsequent read-only live acceptance | Fresh server/UI matching, cached reporting through actual offline process restart, missing-data handling and reconnect passed. See MONTHLY_REPORTING_LIVE_RESULTS.md; no financial writes or retries were performed. |

Coverage includes AT14 negative surplus, crypto/pending isolation, AT06 category sums and 80%/20%, rounding/zero states, uncached/unavailable data, correction/backdating/edit/delete, original contributions during pending/rejected changes, owner-scoped legacy recovery, cancelled projects, parent-only mixed-currency pending totals, overflow and genuinely suspending delayed old-account/month callbacks. Repository tests assert bounds, metadata inclusion, strict decoding, linked-receipt identities and listener cleanup.

Preliminary Android runs identified the reused expense form's missing scroll support and a test selector matching both Delete buttons in the form/dialog. The form and selectors were corrected. Full unchanged suites are not repeated for a test-only selector correction. Logs, XML and raw diagnostic output remain in ignored build directories. These tests do not use live financial records.

## Independent review corrections

Two demonstrated defects were corrected after the read-only live run. No live records were needed for these regressions, and the earlier live acceptance does not establish these edge cases.

1. **Optional expense notes:** the serializer and existing rules permit an omitted expense `note`. Reporting now decodes absence as empty text. A present value must still be a string of at most 500 characters; explicit null, nonstring values and overlength strings are rejected. All other required fields and the security rules remain unchanged. Repository regressions prove that an omitted note contributes the exact expense/category/type amounts, allow valid empty/500-character notes, reject invalid present notes and retain required-field checks. The new omitted-note regression failed against the previous decoder before this correction.
2. **Crypto history during aggregate overflow:** individual row validation is separate from summation, and all rows are validated before checked addition. Valid rows remain visible and inspectable when the total overflows; the total stays unavailable with the explicit overflow error. The same validated rows supply the detail dialog. Malformed records retain an explicit validation error and cannot cause unsafe inspection or be masked by an earlier would-be overflow. ViewModel regressions cover repeated renders/recovery and malformed data after otherwise overflowing rows; Android regressions open both individually valid rows and check the malformed-data feedback.

This correction changes only `FirestoreReportingRepository.kt`, `MonthlyReporting.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`, their repository/ViewModel/Android regression tests, and this report. Existing financial write/recovery behavior, rules, dependencies, Firebase targets, unrelated changes and `.vscode/settings.json` were preserved.

Actual correction checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.clarity.finance.data.repository.FirestoreReportingRepositoryTest --tests com.clarity.finance.data.model.MonthlyReportingTest --tests com.clarity.finance.viewmodel.DashboardViewModelTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

After installing the debug app/test APKs with `adb install -r`, preserving storage:

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.clarity.finance.DashboardNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

| Review-correction check | Actual result |
| --- | --- |
| Affected repository/domain/ViewModel tests | 26 passed |
| Full Android unit suite | 142 passed, zero failures/errors/skips: earlier 137 plus five new unit regressions |
| assembleDebug / assembleDebugAndroidTest | Passed |
| lintDebug | 0 errors, 20 warnings |
| Isolated dashboard Android tests, API 37 / 320 dp | 4 passed: two existing navigation/layout scenarios plus overflow inspection and malformed-crypto regressions |
| git diff --check and preservation checks | Passed |

The unchanged rules/query patterns retain the earlier 87-test Firestore emulator evidence; that suite was not rerun for Android decoding and presentation corrections. No new rules/index publication is required. No live acceptance was repeated and no live records or deployments were made during correction verification. Git finalization is separate from these verification runs.

## Exact changed files

- `app/src/main/java/com/clarity/finance/data/model/MonthlyReporting.kt`
- `app/src/main/java/com/clarity/finance/data/repository/ReportingRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreReportingRepository.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/DashboardViewModel.kt`
- `app/src/main/java/com/clarity/finance/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/HomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/clarity/finance/di/AppContainer.kt`
- `app/src/main/java/com/clarity/finance/di/ViewModelFactory.kt`
- `app/src/main/java/com/clarity/finance/MainActivity.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpenseFormContent.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/EditExpenseViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/AddEditIncomeViewModel.kt`
- `app/src/test/java/com/clarity/finance/data/model/MonthlyReportingTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/ReportingProjectionTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreReportingRepositoryTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/DashboardViewModelTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/EditExpenseViewModelTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/AddEditIncomeViewModelTest.kt`
- `app/src/androidTest/java/com/clarity/finance/DashboardNavigationTest.kt`
- `firestore-tests/test.js`
- `MONTHLY_REPORTING.md`
- `MONTHLY_REPORTING_LIVE_RESULTS.md`
- `README.md`

Unrelated `.vscode/settings.json` remains untouched and untracked. The local correction export `reporting-review-fixes.diff`, ignored build outputs, diagnostic scripts/logs/cache copies, credentials and local configuration are excluded from commits and preserved locally. No dependencies, Firebase targets, signing/local configuration or financial write/recovery schema changed. Verification and finalization made no deployments or live financial writes.

## Short manual acceptance checklist

Use an isolated test account/emulator fixture. Live sample creation/edit/deletion needs separate explicit authorization.

1. Home → Dashboard. Select a known month; check the timezone/recorded-data label. With income 1,000 and expenses 1,500, expect surplus −500. Crypto/pending amounts must be separate. Add Expense should stay reachable.
2. Spending insights. With committed 40,000 and discretionary 10,000, expect total 50,000 and 80.0%/20.0%. Select a category bar → only that month's matching expenses appear.
3. Inspect / edit expense → correct category or backdate to the previous month/year → Save Changes. Check both periods without restarting. Delete only a fixture and verify its contribution disappears once. Linked receipt rows must route to their project.
4. Inspect an empty month, a cached view and an unavailable section. Empty confirmed data can show zero; missing/error data must not produce surplus. A failed section must leave the others usable.
5. Review current pending balances in a past month: cancelled parents excluded, milestones not added again, LKR/USD separate. With isolated pending/rejected edits/deletions, retain original confirmed contributions until acknowledgement.
6. Switch month/account while a read is delayed; old history, category selection, recovery and detail data must disappear. Repeat at narrow width/enlarged text.

## Remaining limits

The implementation checks above used isolated data. Subsequent [read-only live acceptance](MONTHLY_REPORTING_LIVE_RESULTS.md) verified fresh live server totals, production navigation, actual device disconnection/process restart, cached reporting and reconnect without financial writes or recovery retries. Live write-based scenarios and physical-device verification remain unexecuted; isolated Android/emulator results do not establish those outcomes. Existing cached snapshots can be incomplete; the UI says so, and an initial empty cache is not treated as confirmed zero. Reporting excludes malformed/missing-field data through an explicit unavailable section rather than repairing live records.

Hilt/Koin adoption remains an outstanding assignment requirement (original brief Section 5); manual AppContainer/ViewModelFactory wiring was retained. API 26 runtime verification also remains outstanding; Android UI verification uses the existing API 37 emulator. No whole-app migration or build-stack upgrade was included.
