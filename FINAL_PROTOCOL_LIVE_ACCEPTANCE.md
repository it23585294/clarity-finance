# FR01-FR21 protocol live acceptance

## Evidence scope for repository finalization

This document records the audit, review or live phase described below. Historical statements about no Git actions apply to that phase; finalization commits and pushes the accepted files with editorial documentation sanitization only.

- Current accepted Android evidence:264 unit tests,successful app/test builds,and lint0 errors/22 warnings. These results are reused for finalization because executable files match the accepted snapshot; no test rerun is claimed.
- The147-rule-test result was executed in the pre-publication review and reused for the later UI-only repair and finalization. Earlier API26/API35 isolated and SDK checks remain separate historical evidence; there is no clean full-suite API35 claim.
- Actual bounded live acceptance is recorded in FINAL_PROTOCOL_LIVE_ACCEPTANCE.md:fixture-only income/surplus+LKR350,expenses0,and unchanged crypto,savings and collectible pending. Personal account totals and identifiers are excluded from the public record.
- API35 instability,deployed-source equality without administrative access,and unexecuted live legacy migration/server-in-flight races remain limitations. Automatic sync requires a running eligible process/session; queued work resumes after reopening and is not guaranteed to execute while force-stopped.

Date: 4 November 2026, Asia/Colombo. This report covers the bounded fixtures authorized for FINAL_PROTOCOL_PUBLICATION_REVIEW.md. Private account/document identifiers, fixture suffixes, credentials, screenshots and diagnostic writers are excluded.

**Status: bounded live acceptance passed after one focused income-list accessibility repair.** Final results below come from fresh server records and actual production UI/network/restart checks. Broader unexecuted scenarios remain explicit.

## Environment and baseline protection

- Only the separate Clarity_API26_Verification AVD, `emulator-5560`, was targeted. Android 8.0/API 26,x86_64,480x800/240dpi (320dp width),font scale1.3. Pixel_6a was never targeted, launched, installed, inspected or modified.
- Before Application or instrumentation startup, read-only file inspection found zero recovery proposals and no default Firebase Auth user. Known named demo-only Auth preferences from earlier isolated checks were preserved. The user signed into the designated live test account interactively.
- Android configuration and SDK checks both identified clarity-1c83e, the `(default)` database, `firestore.googleapis.com` with SSL. No Firebase target/configuration/dependency/index change occurred.
- Complete local rules SHA-256 matched the published candidate: `95D0DFF16C7814BD4434F76E14CCA034CB510B897BB73293053CA9241BD8ED29`. No authenticated administrative CLI account was available. Published source equality is **not independently established**; the positive/negative live behavior below is distinct evidence. No rules were deployed or edited in this task.
- Server-only reads succeeded for finance, catalogue, proof/fence/state, recurring history and savings collections. The production-shaped recurringOccurrences templateId/month range query succeeded; no connectivity, unexpected permission or missing-index failure occurred in preflight. The existing COLLECTION index (templateId ascending,month ascending) needs no change.
- Fresh full server and local-recovery baselines were captured before writes. Existing data was not replaced with a blank test account assumption. Existing financial records,projects,recurring history and a savings goal were present; full baselines remain private. No unrelated local proposal existed on this separate device.

Fresh November baselines were calculated from server records using Asia/Colombo transaction dates and exact integer minor units. Account totals remain private; the public results below show only authorized fixture deltas.

The complete original documents, links, counters, timestamps, goal ledger and recovery payloads are compared privately; no totals are copied from older acceptance reports. Linked receipts count once as income. Milestones are not added again to parent outstanding balances.

## Live fixture results

All samples share one fresh date-labelled `PROTO-20261104-…` batch. Role letters below identify their purpose without publishing IDs. Production Activity/screens/ViewModels/repositories and the Application coordinator performed submissions. Temporary opt-in UI drivers clicked those real screens; they did not write Firestore directly or inject fake repositories.

| Authorized operation | Actual observation |
| --- | --- |
| Category C/source S creation, rename,archive | Each logical operation has a server proof. IDs remained stable. New expense/income choices excluded the archives; historical E/I retained their references and were correctable. No financial delta arose from catalogue changes. |
| Expense E100 ->120 ->delete | Creation/correction acknowledged; the cancelled deletion confirmation left the record unchanged, followed by confirmed deletion. Final E contribution0. |
| Standalone income I1000 ->1100 ->delete | Creation acknowledged; correction completed after the focused accessibility repair below. Cancel/confirm deletion behaved correctly. Final I contribution0. Linked receipts were not edited/deleted. |
| Project P1000 ->1200; M1 400 ->500; M2 100 | All changes acknowledged. Parent allocation reached600 with received0, matching the two milestone agreements. |
| Archived inherited-source receipts: direct200/M1 150 | Two distinct immutable linked receipts totalling350, counted once. Both were accepted under the agreement's unchanged archived source. |
| M2 cancellation,partially-paid M1 cancellation,parent cancellation | Allocation600 ->500 ->150. M1 retained agreed500/received150; M2 retained agreed100/received0. Parent retained agreement1200/direct200/milestone150/allocation150 and historical outstanding850. Cancellation excluded that850 from collectible pending; receipt income350 remained. |
| Template T suggested20, first due today | No expense while unconfirmed. Monthly anchor is the actual current day. No old October fixture dates were hard-coded. |
| T actual25 using active Other category | Exactly one consumed occurrence and one ordinary expense. The archived template category was not fabricated as an active actual selection. |
| Actual correction30 and backdate | The current expense moved from November4 to October4, derived from today minus one month. November lost the25 contribution and October gained30. The confirmation retained its original actual25/November4 history and expense reference. |
| Actual deletion,T cancellation,reopen | Actual contribution returned to0. Template and consumed confirmation remain. Reopening offered inspection of the deleted actual, showed it missing,and did not offer reconfirmation or recreate it. Keep template left it unchanged before Confirm cancellation. |
| O offline create25,process restart,reconnect on Home | Identical UID/type/record/payload survived force-stop/reopen. Pending ->uncertain feedback retained the proposal; cached totals excluded25. Reconnection while staying on Home cleared recovery; one matching server proof/expense shared the atomic timestamp. No feature screen or instrumentation was opened between reconnect and observed queue clearance. |
| O offline edit25 ->30,process restart | The exact immutable proposal survived; cached totals retained confirmed25 while30 was uncertain. The proposal remained nondismissible through account switching. |
| Second-account isolation | Server reads and actual UI showed no old fixture records/feedback, no old source/category labels, and blank new-form fields. The second account had zero own proposals. Its server records were unchanged. A valid own read succeeded and a server read of original-account fixture O correctly returned PERMISSION_DENIED. The original edit remained unchanged and uncertain. |
| Return to original account | The original edit automatically acknowledged on Home. Its saved operation ID matched exactly one UPDATE receipt; one O record became30,with only one CREATE and one UPDATE proof. Recovery cleared without manual retry. |
| O offline deletion/restart/Home reconnect | The same DELETE proposal survived force-stop/reopen. Cached confirmed totals retained30. Reconnection on Home cleared recovery once; the same O ID is absent on the server and retired. CREATE/UPDATE/DELETE each have exactly one matching receipt. |

The expected cross-owner denial is a successful security check, separate from an unexpected permission failure on an authorized operation. No live rules were weakened, clocks manipulated or records corrupted to manufacture failures.

## Actual offline mechanism and scope

The separate AOSP device used reversible IPv4/IPv6 OUTPUT rules restricted to the acceptance application's OS UID. This blocked actual app networking, rather than calling Firestore.disableNetwork or substituting a fake transport. A server-only Firestore read succeeded before isolation and failed/timed out during isolation. Radio,proxy,DNS,host networking and Pixel settings were untouched. All three outage cycles restored device firewall rules exactly to their saved baselines. Actual Firestore reachability was rechecked successfully after the final restore. ADB root used for separate-AOSP-device diagnostics/firewall isolation was returned to the original non-root shell mode. No storage clear,uninstall or wipe occurred.

Forced process stops and production Activity reopening were exercised. Activity recreation alone is not counted as process-death evidence. Raw recovery snapshots proved stable immutable fields; status/error/cache preferences were permitted to change. SDK transactions were not treated as an offline queue: the durable proposal drove recovery after reopening.

Pending creation contributed0. Pending edit retained confirmed25; pending deletion retained confirmed30. Home was explicitly visible before and after create/delete reconnection and original-account return. Recovery was observed by file reads without starting Application or a feature, before server-audit instrumentation was run. App-wide work is process/session dependent,not guaranteed execution while the process is absent.

For account switching, the original account was signed out while still isolated; its proposal was retained before networking was restored. User sign-in was interactive; no sign-in screen was captured, and no password or token was printed, placed in public artifacts, or entered by the agent. Saved Auth presence was inspected as required. This exercises a real saved offline proposal across account changes; it does not prove every server-in-flight timing or simultaneous multi-device race.

## Demonstrated blocking defect and focused repair

At 320dp/font1.3, the income summary with a long custom source consumed the Column's available height, leaving the separate LazyColumn transaction viewport at zero. The live UI test stalled attempting to reach I. Diagnostics and an empty recovery snapshot were preserved; a fresh server read confirmed I was still1000, so its edit was not blindly repeated.

IncomeListScreen now places month selection,recovery/cache feedback,totals and income rows in one weighted LazyColumn. Content,financial calculations,links and edit/delete callbacks are preserved. No schema,recovery,security or dependency change was needed. **Rules republication is not required.**

IncomeListAccessibilityTest uses isolated production screen/ViewModel data with eight long archived sources at320dp/font1.3. It first asserts a usable viewport so a regression fails promptly rather than hanging,then scrolls to a real row and checks its inspection callback. It failed on the original layout and passed after the repair. The affected live income correction and deletions subsequently passed.

Before correction, accepted working files were hash-verified in an outside-repository backup. All earlier work and the unrelated IDE/review files are preserved. Selector,keyboard and lazy-scroll problems in temporary drivers were diagnosed separately; attempts that stopped before Save were not reported as rejected financial operations or application defects. The test timeout was investigated rather than restarting a full suite repeatedly.

## Verification actually executed

| Check | Result |
| --- | --- |
| `firebase.cmd login:list --json` |0 administrative accounts; no deployed-source equality claim. |
| Opt-in ProtocolLiveReadOnlyTest Source.SERVER snapshots | Fresh account-scoped baselines/checkpoints, catalogue/index access,full document/counter comparisons; private outputs excluded. |
| Guarded ProtocolLiveUiStepTest on production MainActivity | Live operations listed above, with duplicate-create/expected-version guards and server acknowledgement checks. Failed helper attempts did not cause blind resubmission. |
| IncomeListAccessibilityTest before/after fix |1 isolated regression failed before,passed after. |
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain` |264 unit tests,0 failures/errors/skips; both APK builds successful; lint0 errors/22 warnings. Executed after production repair. |
| Temporary read-only/account/network/UI harness builds | Existing stack and dependencies; opt-in sources under ignored build only. No production Firebase target override. |
| ProtocolAccountIsolationTest | Passed on the user-selected second account; own reads succeed, foreign fixture read denied, old proposal retained,forms/feedback isolated. |
| Firestore emulator suite | Unchanged147-test passing evidence reused from publication review. UI-only correction does not warrant repeating unchanged rules. |
| Final normal test APK | `gradlew.bat :app:assembleDebugAndroidTest` rebuilt without ignored live-driver sources; replaced only the acceptance device test APK, preserving storage. |
| Final preservation/artifact checks | HEAD `8957e560c5fd6624edeaf31459d0a5527cd7b66b` unchanged,index empty; whitespace/privacy and SHA-256 archive/source checks passed. Unrelated IDE/review files,dependencies,Firebase configuration and indexes matched their saved hashes. |

Earlier isolated API26/API35, deterministic protocol/fence/expiry tests and milestone live reports remain historical evidence,not new executions here. API35 ART/JIT/system_server instability remains unresolved. This task used the separate API26 device; it did not test Pixel or repeat a full newer-Android suite.

## Final reconciliation and retained fixtures

Final server comparison passed for all original finance/catalogue/protocol/goal records. No original record was changed or removed, no crypto/goal record was added, and no unrelated recovery entry was processed. The second account's before/after server records were also identical. The original separate-device recovery baseline was empty and final recovery is empty. Catalogue/Auth cache preferences naturally changed; the entire preference file is not claimed byte-identical.

| Verified November fixture-only result | Delta |
| --- | ---: |
| Ordinary received income | +LKR350.00 |
| Recorded expenses | LKR0.00 |
| Recorded cash surplus | +LKR350.00 |
| Realised crypto net | LKR0.00 |
| Goal recorded saved | LKR0.00 |
| Current collectible pending |0 |

All reporting months were independently recalculated. October income/expense/crypto deltas are zero after the backdated actual was deleted. Only November received income/surplus gained350. This is recorded surplus,not a bank balance or verified savings.

Retained fixed sample summary (nine business/history documents):

- One archived custom category and one archived custom source, renamed with their original IDs/createdAt preserved.
- One cancelled project: agreement1200,received350,historical outstanding850,current collectible0,allocated150.
- Two cancelled milestones: M1 agreed500/received150; M2 agreed100/received0.
- Two immutable linked income receipts: direct200 and milestone150,total350.
- One cancelled monthly template,suggested20,and one consumed confirmation preserving the original actual25/November4. Its current actual expense was deleted after correction/backdating.

Four mutable sample records were deleted exactly as authorized: ordinary expense E,standalone income I,recurring actual R,and offline expense O. Their retirement/proof history remains. There are **27 new global operation receipts,three recurring operation receipts,one consumed occurrence and11 state/retirement markers**. No fence document was added. Counts by target/kind match the planned logical operations; O has one CREATE,one UPDATE and one DELETE receipt with its captured immutable operation IDs. No duplicate expense,income,receipt or allocation was found. Parent/milestone counters,retained receipt payloads and original createdAt values were checked independently across checkpoint snapshots.

Final production dashboard values matched the server. The production recovery panel explicitly showed no saved changes and no unresolved-operation action. App networking/firewall state and non-root ADB mode were restored. The original account is left signed in on Home. Pixel storage/network/DNS were never inspected or modified.

## Remaining limits

Published-source equality remains unverified without an administrative session; behavioral verification is not a substitute for that claim. No live malformed write,definitive-rejection/conflicting-edit/fence race,server-in-flight multi-device race,expired credential,legacy migration or corrupt-store scenario was manufactured. Deterministic/emulator evidence for those safeguards is reused. The preserved unrelated Pixel recovery entry was not inspected or processed. OS background execution while the process is absent is not guaranteed. Existing API35 instability remains unresolved,and no new full API35 run is claimed.

The new fake-data regression still starts the Android Application under instrumentation. Future operators must perform the same device/queue preflight; an in-test opt-in guard cannot prevent Application initialization. Do not run this build or instrumentation on the uninspected live-account Pixel.

Updated archive: `clarity-finance-protocol-live-acceptance-review-20261104.zip`. It contains the accepted85-file milestone snapshot updated to the repaired source,plus the new regression and this report:87 complete current files. Relative paths are preserved and every archive member is SHA-256 verified against its source. Compared with the previous ZIP,one existing source file changed,two files were added,none removed. Private live drivers,account/document IDs,server snapshots,logs,credentials,configuration and build outputs are excluded. Rules remain the same reviewed complete file; no republication is needed.

Public changes in this task: `app/src/main/java/com/clarity/finance/ui/income/IncomeListScreen.kt`, `app/src/androidTest/java/com/clarity/finance/IncomeListAccessibilityTest.kt`,and this report. Private fixture IDs,baselines,network snapshots,UI drivers and diagnostics remain under ignored build/protocol-live. No commit,push,Firebase publication,app-store deployment or UI redesign occurred.
