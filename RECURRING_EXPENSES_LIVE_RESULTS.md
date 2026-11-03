# FR17 live acceptance — 3 November 2026

Executed against `clarity-1c83e`, database `(default)`, using the existing Pixel_6a API 37 emulator and production app. The user authorized three labelled test templates and their bounded confirmations, correction, deletion and cancellations. Account sign-ins were performed interactively by the user. No credentials were requested or entered by the agent.

## Preflight and evidence boundaries

- Repository HEAD remained `8e94576230cae6dbdaba03b8f59dea6c627a375f`. No applicable AGENTS.md was found. Existing application/rules/index/test files, IDE settings and review diffs were preserved; no application correction was needed during acceptance.
- Local complete rules SHA-256 remained `51A56DB95589AE167763889EC7E163191EAC3D4DBCE1BD4BD91F560191996339`.
- The user reported publishing the rules and enabling the COLLECTION index `recurringOccurrences: templateId ASC, month ASC`. The actual production-shaped equality/range/order query succeeded with `Source.SERVER`, initially empty and subsequently with confirmed history. Reads verified non-cache metadata and no pending writes. Deployed rules text was not independently fetched; this is behavioral evidence, not an independently computed hash of the published rules.
- The existing signed-in account matched the previously designated test account. Before opening the recovery-enabled recurring screen, private recovery inspection found **zero recurring proposals**, one unrelated recovery entry, and zero SDK queued writes. Fresh server reads found zero recurring templates, occurrences or operation receipts.
- Full relevant financial server snapshots, timestamps, recovery bytes and fixture identities were retained privately under ignored `build/recurring-live/`. Public evidence contains no account or document identifiers. Server checks use the current baseline, not totals copied from earlier milestones.
- The stopped emulator was reopened with its preserved storage and existing loopback-only IPv4 DNS workaround. The relay was initially unavailable; it was restarted using the current host resolver. Host DNS settings, proxy, Firebase configuration and relay source were unchanged. Later backend read timeouts were handled with bounded read-only retries. No permission or missing-index error was observed.

## Authorized fixtures and actual results

The three names shared a fresh `FR17-ACCEPT-<date>-<run>` prefix. The exact label and document/account identifiers remain private. All actual payment dates were **2026-11-03**, except the temporary Rent correction to **2026-10-31**; prior acceptance dates were not reused as current fixture dates.

| Template suffix | Initial suggestion | First due / monthly anchor | Final actual expenses |
| --- | ---: | --- | ---: |
| Rent | LKR 34,000.00 | 2026-11-03 / day 3 | None; the labelled expense was corrected and deleted |
| Utilities | LKR 5,000.00 | 2026-11-03 / day 3 | One November actual: LKR 6,250.50 |
| Offline | LKR 1.00 | 2026-09-01 / day 1 | September/October/November slots: LKR 1.00, 2.00, 3.00, all actually dated November 3 |

1. **Templates alone:** three server-acknowledged creations produced no expense and changed no financial record.
2. **AT09:** Rent confirmation created one LKR 34,000 expense. The server checkpoint still had only that expense while Utilities remained unconfirmed. Utilities was then reviewed with an actual amount of LKR 6,250.50 and category Utilities, creating exactly that amount once. November's fixture expense delta was LKR 40,250.50.
3. **Correction/backdating:** Rent became LKR 33,000, Shopping, Discretionary, Primary bank/card, dated October 31. Server evidence showed November delta LKR 6,250.50 and October delta LKR 33,000. Its original immutable confirmation remained LKR 34,000 on November 3.
4. **Deletion:** Cancel in the deletion dialog retained the corrected expense. Confirming deletion removed it once. Returning to its recurring history still offered the consumed confirmation's Inspect action; opening the expense reported “Expense not found or already deleted.” No new confirmation action or recreated expense appeared. October's fixture delta returned to zero.
5. **Template edit/cancellation:** Rent's suggestion changed to LKR 35,000 and monthly anchor to day 31. The first due date stayed November 3, and the confirmed identity/history stayed unchanged. Keep template retained it; confirmed cancellation persisted. Utilities was also cancelled while retaining its actual expense. Offline was cancelled after its three confirmations reconciled. All three retained templates are cancelled; cancellation changed no expense total.

The first correction submission was interrupted before completion by the diagnostic audit restart. Fresh server reconciliation established that the original values remained before the correction was resubmitted; production completion and a subsequent server read then verified the correction. Popup options omitted from UI Automator dumps and occasional null-root dumps required diagnostic-only interaction/retry adjustments. No rules or application behavior were changed to obtain acceptance.

## Actual offline restart, reconnect and account isolation

- With confirmed data cached, only device Wi-Fi/mobile data were disabled. `dumpsys connectivity` confirmed no active default network. Host networking and the DNS relay were retained.
- Three distinct Offline slot proposals were saved locally: September LKR 1, October LKR 2 and November LKR 3. They used three distinct operation IDs and deterministic occurrence/expense IDs. Uncertain feedback explicitly retained confirmed spending and disallowed dismissal.
- The app was force-stopped and reopened without clearing storage. All three immutable payloads and identities survived. The recurring screen restored their recovery feedback.
- After restart, the dashboard showed cached expenses **LKR 6,250.50**. Uncached income and surplus showed **Unavailable**, rather than confirmed zero. Pending proposals contributed nothing.
- The app was stopped again and reopened on Home before networking was restored. With the recurring worker inactive, a fresh online server read still showed two confirmations, eight receipts and November delta LKR 6,250.50. All three proposals remained queued.
- The user switched to a second account. Read-only preflight found no recurring proposals for that UID. Its own server collections were empty; its settled recurring screen showed no original-user records, proposals or messages. Its creation form was blank and closed without saving. Before/after audited financial server records for that account matched.
- The user returned to the original test account. Home preflight verified the same owner and all three original immutable payloads, still uncommitted. Opening Recurring Expenses reconciled them. Fresh server evidence showed the **same three operation IDs and actual payloads**, exactly one expense and immutable receipt per occurrence, and server timestamps after reconnection.
- Recurring recovery feedback cleared. A later reopen/history inspection and final server audit found no duplicates. The final recovery protobuf was **byte-for-byte identical to the original baseline**, preserving the unrelated entry. The dashboard's pre-existing unrelated recovery indicator remains; it was neither retried nor dismissed.

This exercised durable queued work across restart and interactive account switching. It did **not** exercise an account switch while a live transaction remained in flight.

## Final fresh server/UI comparison and retained fixtures

| Evidence | Final result |
| --- | --- |
| Retained templates | **3, all cancelled** |
| Immutable occurrence confirmations | **5**; includes the deleted Rent expense's consumed confirmation |
| Immutable recurring operation receipts | **12**: 3 creates, 5 confirmations, 1 template edit, 3 cancellations |
| Retained fixture actual expenses | **4**, totalling **LKR 6,256.50** |
| November fixture expense delta | **+LKR 6,256.50** |
| October / September fixture expense deltas | **Zero**; scheduled month does not replace actual transaction date |
| November ordinary income / crypto net | **LKR 0.00 / LKR 0.00**, unchanged |
| November recorded cash surplus | **−LKR 6,256.50** |
| Categories | Utilities **LKR 6,250.50** + Housing **LKR 6.00** = **LKR 6,256.50** |
| Spending split | Committed **LKR 6,256.50 (100.0%)**; Discretionary **LKR 0.00 (0.0%)** |
| Goal savings | Unchanged from the fresh server baseline |
| Current collectible pending income | None; both pre-existing cancelled parents remain unchanged and excluded |
| Recurring local proposals / SDK queued writes | **0 / 0** |

Production dashboard, monthly history, insights and both category drill-downs matched those values. November history showed Utilities once and the three Offline actuals once each. Utilities drill-down contained only its one expense; Housing contained only the three Offline expenses. The deleted Rent expense was absent.

The baseline's two unrelated expenses remained unchanged. Final all-time expenses equalled the original baseline plus the four fixtures' LKR 6,256.50. All audited original income receipts, crypto records, project/milestone records and savings goal/ledger/receipt records retained identical values and timestamps. Subsequent read-only UI navigation produced no further server or recovery changes. Private fixture IDs and the full retained-record registry are excluded from this report.

Original Wi-Fi/mobile and proxy settings were verified restored. The DNS relay remained available, emulator/app storage was preserved, and the app was left on Home in the designated test account.

## Checks executed during live acceptance versus earlier verification

Executed for this acceptance:

- Read-only stopped-app recovery/cache preflight; initial and final SDK mutation queues checked empty.
- Opt-in read-only instrumentation against the existing live session, including the exact recurring history query and existing financial audit. Diagnostic sources/output stayed ignored under `build/`; there was no temporary SDK financial writer.
- `:app:assembleDebugAndroidTest -I build/recurring-live.init.gradle --console=plain` for the read-only diagnostic APK; storage-preserving `adb install -r` for production/test APKs.
- Production UI interactions over ADB for the authorized mutations, restart, navigation, history, cancellation dialogs and dashboard/category comparisons. The account sign-ins were performed by the user.
- Independent private checks of receipt/occurrence/expense identities, payload equality, accepted timestamps, exact integer deltas, final recovery bytes and unchanged source-file hashes.
- `git diff --check`; no staging, commit, push, deployment or dependency change.

Unchanged full suites were **not rerun**. Applicable recorded evidence from `RECURRING_EXPENSES.md` remains: 55 focused tests, 227 full Android unit tests, successful app/test builds, lint 0 errors/20 warnings, 18 focused/124 full Firestore emulator tests, and 12 isolated Android tests. Those are earlier automated results, distinct from this live acceptance.

## Unexecuted live scenarios and remaining limits

- Simultaneous confirmation from two live devices, a deliberately timed duplicate tap/race, direct receipt replay, conflicting live payloads, definitive rejection and in-flight account-switch races were not manufactured. Their existing suspending-fake/emulator coverage is separate evidence.
- Cancellation with an additional still-unconfirmed overdue slot, leap/month-end rollover, future-clock boundaries and template-version races were not manufactured with extra live fixtures or clock changes. Existing automated coverage remains applicable.
- No live security rules were changed to force errors. Publication contents were not independently downloaded or hashed.
- Hilt/Koin adoption and API 26 runtime verification remain separate outstanding assignment tasks.

The bounded live checks above passed. The retained four expenses intentionally affect recorded totals; immutable confirmation/operation history and the three cancelled templates remain in the test account. No fixture cleanup, publication or feature changes were performed.

## Finalization verification — 3 November 2026

Finalization preserves the accepted executable source, tests, rules and index bytes. Only these two reports were updated to distinguish historical automated results, subsequent live checks and unexecuted scenarios, and to remove the exact fixture label and unrelated account balances. An external verified backup preceded staging. Each commit was staged with explicit paths and checked against the accepted content manifest; staged whitespace checks and final tree/content comparisons were executed. Earlier full-suite evidence was reused because executable content did not change. Final commit hashes and normal-push remote verification are reported separately after completion. Finalization performs no deployment, device operation or live-data mutation.
