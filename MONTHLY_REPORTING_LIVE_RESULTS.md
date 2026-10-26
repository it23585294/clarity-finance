# FR11–13 read-only live acceptance

Executed 26 October 2026 on the existing API 37 emulator and signed-in account, using live Firebase project `clarity-1c83e`, database `(default)`. This run used existing records only. No financial record or recovery operation was created, edited, deleted, dismissed or retried. No deployment, dependency change, target change, commit or push occurred.

Account identity, record/operation IDs, private notes, screenshots, database copies and raw server/UI/network diagnostics are omitted here and retained only in ignored `build/reporting-live` evidence. The existing uncommitted implementation and unrelated `.vscode/settings.json` were preserved.

## Fresh server baseline and independent calculation

The existing explicitly opt-in, read-only `LiveFirestoreAuditTest` was executed twice. It checked the actual project ID, `firestore.googleapis.com`, TLS, `Source.SERVER`, no cache result and no pending SDK writes. It read all owner receipts, expenses, crypto results, parent projects, their milestone history and scoped recovery. These fresh snapshots, rather than earlier report totals, supplied the calculation inputs.

- Two successful server checkpoints on **26 October 2026**, before and after the read-only checks. Precise timestamps remain in private evidence.
- Selected reporting month: **October 2026**, allocated by Asia/Colombo transaction-date fields.

An independent Python validator summed integer minor units and used Decimal formatting/percentage rounding; it did not call the application's reporting calculations.

| October measure | Fresh server calculation | Production UI |
| --- | ---: | --- |
| Ordinary received income | LKR 143,650.00 | Matched |
| Recorded expenses | LKR 650.75 | Matched |
| Recorded cash surplus | LKR 142,999.25 | Matched |
| Separate signed realised crypto net | +LKR 60,080.00 | Matched; excluded from surplus |
| Other category | LKR 650.75 | Matched; the only category with expenses |
| Committed spending | LKR 0.00, 0.0% | Matched |
| Discretionary spending | LKR 650.75, 100.0% | Matched |
| Current collectible pending balances by currency | No collectible amount; both parents cancelled | Matched; current-balance label shown |

The five ordinary receipts comprise salary LKR 128,000.00, AdSense credits LKR 15,300.00 and freelance receipts LKR 350.00. The two linked project receipts are LKR 200.00 and LKR 150.00, included once each by distinct receipt ID. Parent/milestone receipt counters were not added as additional income. Category sums and committed plus discretionary equal the expense total: 10,050 + 55,025 = 65,075 minor units.

The four crypto records comprise gains 60,000, 100 and 20 and a loss of 40 LKR, producing +60,080. They remain separate from ordinary cash transactions. Current pending totals exclude both cancelled parents without deleting their history; no milestone allocation is added to parent outstanding.

## Production navigation and reporting checks

| Read-only scenario | Actual observation |
| --- | --- |
| October overview | Income, expense, surplus, crypto and pending sections matched the independent server totals. Period/timezone/recorded-transaction wording was visible. Initial cached labels disappeared after server-backed listeners connected. |
| Full monthly history | Matched all **2 expenses, 5 ordinary receipts and 4 crypto cards** against fresh server date, amount, note and action fields. Crypto appeared in its separate section. |
| Other category drill-down | Exactly the two October expenses, LKR 100.50 and 550.25, appeared. Ordinary income/crypto sections were absent from this category view. |
| Empty category | Transport showed no matching October expenses. |
| Spending insights | Other LKR 650.75 and the committed/discretionary 0.0%/100.0% split matched server calculations. Numeric labels accompanied the bar. |
| Linked-receipt routing | A representative linked receipt opened its matching cancelled parent project. The standalone income editor was absent. No project action was used. Both linked receipt cards had the protected project-routing action. |
| Month navigation and confirmed empty month | October → September → October updated correctly. September had zero records in the fresh complete server baseline; online overview showed zero totals, history showed the three separate empty sections and insights showed the zero-spending message without percentages. Returning to October restored its values. |
| Add Expense navigation | Opened the actual production form, inspected its blank/default fields, then Cancel. No text was entered and Save was never pressed. Recovery bytes were unchanged afterward. |

## Actual offline process-restart check

1. Before opening the app, a stopped-app, read-only copy of its Firestore cache passed SQLite integrity checking and contained **zero SDK mutation batches/document mutations**. One pre-existing DataStore `EXPENSE/PENDING` attempt existed. Its bytes and status were recorded and left untouched. This avoided unintentionally replaying existing SDK writes when reconnecting.
2. Only the test emulator's Wi-Fi and mobile-data radios were disabled with `svc`. Initial connectivity output still reflected asynchronous radio shutdown; the settled dump explicitly reported **Active default network: none**. The app was force-stopped and reopened again after that settled offline state was confirmed. App storage, caches and the AVD were preserved.
3. October's income, expenses and surplus survived restart with their original values and explicit cached/incomplete-data warnings. Cached crypto showed +60,080.00; cached current pending balances excluded the cancelled parents. Cached spending insights retained Other LKR 650.75 and 0.0%/100.0%. All **11 history cards** were independently matched again while offline after restart.
4. Unvisited August reporting did **not** treat missing cached data as confirmed zero. Income, expenses and surplus stayed unavailable, including after the ten-second initial-data watchdog. Spending insights remained unavailable with recoverable connection guidance.
5. Original Wi-Fi/mobile-data settings were restored. October reporting automatically regained the same values and lost its cached warnings. No reporting Retry button, financial Retry action or recovery action was pressed. Reconnection was checked on October history/overview; an August unavailable → server-zero transition was not executed.
6. Emulator/DNS relay PIDs and complete launch arguments matched before/after. No emulator restart, DNS change, proxy change, host-network change, storage clear, uninstall or cache clear occurred. Device Wi-Fi/mobile data and proxy settings match their original state.

The final `Source.SERVER` audit matched the starting financial document sets, fields and timestamps exactly. Scoped recovery objects matched, and the complete DataStore recovery file was **byte-for-byte unchanged**. The existing pending expense was neither retried nor reconciled by this acceptance run.

## Commands and evidence distinction

Principal actual commands, with the selected emulator:

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.clarity.finance.LiveFirestoreAuditTest -e liveAudit true -e auditLabel reporting-baseline com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am start -n com.clarity.finance/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/reporting-readonly.xml
adb -s emulator-5554 shell svc wifi disable
adb -s emulator-5554 shell svc data disable
adb -s emulator-5554 shell dumpsys connectivity
adb -s emulator-5554 shell am force-stop com.clarity.finance
adb -s emulator-5554 shell am start -n com.clarity.finance/.MainActivity
adb -s emulator-5554 shell svc wifi enable
adb -s emulator-5554 shell svc data enable
adb -s emulator-5554 shell am instrument -w -r -e class com.clarity.finance.LiveFirestoreAuditTest -e liveAudit true -e auditLabel reporting-final com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
```

Both read-only live audit executions passed (**two successful server checkpoints**). ADB taps/swipes drove production navigation; private local helpers calculated and checked the snapshots, UI cards, recovery bytes and network configuration. Those helpers contain no financial writer/retry calls and are ignored diagnostics, not retained application features or reusable automated acceptance tests.

The earlier **137 unit tests, 87 Firestore emulator tests and five isolated Android tests** are separate implementation evidence described in `MONTHLY_REPORTING.md`. They were not rerun during this documentation/read-only acceptance task, and their fake/emulator data is not substituted for the live results above. No application/test/rule changes were necessary during that live run. UI-dump startup timing and offscreen-control attempts were harness limitations, not demonstrated application defects.

After this live run, independent review found the optional-expense-note decoder and aggregate-overflow history defects. Their corrections passed **142 unit tests**, **four isolated dashboard Android tests**, app/test builds and lint (**0 errors/20 warnings**). Those later checks created no live records and did not repeat live acceptance. The earlier 87-test emulator result and previous navigation checks remain distinct evidence. Commit preparation changes only documentation and reuses the accepted executable contents and verification.

## Limits and optional subsequent fixtures

No application defect was demonstrated in the executed read-only scenarios. Live records contained only one spending category, zero committed spending, positive surplus, and no active collectible parent. Therefore live 80%/20%, negative surplus, nonzero mixed-currency pending balances and active-parent/milestone double-counting cases remain unexecuted. Their earlier isolated behavioral coverage is not live proof. No live edit/delete/backdating, rejected-proposal, permission-denied or account-switch test was performed in this read-only run. No live rule changes were made to manufacture failures.

If write-based acceptance is desired later, a small proposed fixture set is **six clearly labelled documents**, with prefix `FR11-13-acceptance-<session>`, in an otherwise empty past month:

- One ordinary LKR 1,000 income receipt.
- Two expenses: LKR 1,200 committed/Housing and LKR 300 discretionary/Transport. Expected spending 1,500, surplus −500 and 80.0%/20.0%.
- One active LKR 1,000 parent with a LKR 400 planned milestone, plus one active USD 10 parent. Expected current pending LKR 1,000 and USD 10 separately; the milestone must not inflate LKR pending to 1,400.

Only those samples would be candidates for authorized correction, backdating, deletion/cancellation and cleanup. **No fixtures were created. Further writes require separate explicit authorization.** Hilt/Koin adoption and API 26 runtime verification remain outstanding assignment tasks outside this milestone.
