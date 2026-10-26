# Savings Goals live acceptance

Status: **live acceptance A–E passed, with the limitations below**. The user explicitly designated the current signed-in account as the test account before any writes. Only uniquely labelled savings fixtures in that account were modified. The fixed goal, immutable receipts and ledger history remain afterward. Acceptance completed before the conditionally authorized Git finalization.

## Environment established

- Starting branch: main, tracking origin/main; starting HEAD dbfc3c7a6d9217f13e0cb8e157c5f56107ceab85. Existing uncommitted savings work, IDE settings and review diffs are preserved.
- Local firestore.rules SHA-256 matches the user-published expected value: `45A13EE4E81717FFBC198D54744DAFA7A6322C4FBD5379105287BE8922285A0A`.
- App configuration and actual SDK assertions identify clarity-1c83e, live firestore.googleapis.com and SSL, not the local emulator.
- No authenticated Firebase CLI account is available, so deployed source contents could not be downloaded or compared. Publication is not treated as proof of content equality.
- An explicitly opted-in, read-only production-SDK audit completed successfully with Source.SERVER and asserted no cache/pending-write metadata. The designated test account's active goal and entry/operation/expiry-fence collections were absent/empty in a fresh baseline before writes. No existing goal or ledger was overwritten.
- Before initializing Firestore for that audit, stopped-app cache copies showed zero SDK mutations/document mutations. The durable recovery baseline contains one unrelated operation and no goal operations. No recovery worker was started or unrelated proposal retried.
- Account identity and server/document evidence are held privately under ignored build/savings-live. No account/document identifiers or credentials belong in this report or commits.
- App storage, the existing DNS workaround, Firebase configuration and dependencies are preserved. Only the test device's Wi-Fi/mobile connectivity was temporarily disabled for the offline checks and restored to its original enabled settings. No device/server clock or host networking was changed.

## Live execution

The existing Android test APK was built using an ignored init script to add a temporary, explicitly opt-in authentication-only inspector; that inspector passed once without initializing Firestore. The retained LiveFirestoreAuditTest was extended with read-only goal/history/receipt/fence queries, goal recovery types and an optional cross-owner read-denial probe. Repeated explicitly opted-in audits captured fresh server checkpoints throughout A–E; no audit calls a financial writer or recovery retry API. Test APK installations used adb install -r, without clearing storage or uninstalling.

At live preflight, automated evidence was the documented 186 Android unit tests, 106 Firestore emulator tests, four isolated savings Android checks, successful app/test builds and lint 0 errors/20 warnings. Those suites were not rerun for preflight. Finalization verification is recorded separately below. Live expiry boundaries remain unexecuted; deterministic/emulator evidence is reused without changing clocks or manufacturing expired live records.

## Acceptance status

| Check | Status |
| --- | --- |
| Designated test account and fresh write baseline | Passed; explicit designation, absent goal and no goal recovery before writes |
| A: Creation, acknowledgement and contribution schedule | Passed: target 490,000; opening/saved 11,200; remaining 478,800; 12 distinct dates including deadline; required 39,900; progress 2.29% |
| B: Contributions, withdrawals, exact balances and financial separation | Passed: +10,000 → 21,200; −1,200 → 20,000; distinct accepted operations; ordinary records and surplus unchanged |
| C: Corrections, deletion, editing and dashboard behavior | Passed: withdrawal corrected to 1,300 → 19,900; Keep unchanged; confirmed deletion → 21,200; excessive/zero inputs rejected without writes; metadata/deadline edit preserved start and ledger; dashboard reflected server label without app reopening and savings stayed constant across reporting months |
| D: Actual offline save, process restart and reconnect | Passed: labelled 1,000 proposal persisted unchanged through force-stop/reopen; cached confirmed savings stayed 21,200; reconnect acknowledged exactly that entry/operation once → 22,200 and cleared pending feedback |
| E: Interactive account switching/isolation | Passed: the user signed into a second account and returned; four foreign savings server reads were denied; previous-account goal/forms/proposal feedback were absent; its queued 1 proposal stayed unchanged until the original account opened Savings Goal, then reconciled once → 22,201 |
| Live deadline expiry | Intentionally unexecuted; automated evidence only |

Fresh final server evidence contains **one goal, four ledger documents and eight immutable accepted operation receipts**, with confirmed saved savings **LKR 22,201**. Three contribution entries are active; the corrected withdrawal is a deleted tombstone. No expiry fences were created live. All IDs, exact fixture labels and account details remain private under ignored build/savings-live and are excluded from commits.

| Accepted operation | Saved LKR afterward |
| --- | ---: |
| Goal creation, opening allocation 11,200 | 11,200 |
| Contribution 10,000 | 21,200 |
| Withdrawal 1,200 | 20,000 |
| Correct withdrawal to 1,300 | 19,900 |
| Confirm deletion of that withdrawal | 21,200 |
| Edit labelled metadata/deadline | 21,200 |
| Offline/restart contribution 1,000 | 22,200 |
| Original-account queued contribution 1 | 22,201 |

Keep, excessive withdrawal, zero correction, month navigation and read-only audits produced no accepted mutations. The eight receipts represent these eight intended operations, not duplicate allocations. Independent replay of every receipt reproduces the parent and ledger exactly, including operation versions, last-operation identities and creation/update timestamps. The account-switch proposal's exact queued entry/operation IDs matched one accepted receipt and one ledger document after returning.

Creation used start 2026-10-26 and deadline 2027-10-26. The metadata-edit check extended the deadline to 2027-11-26 while retaining the start and ledger; that revised schedule has 13 remaining opportunities. The initial AT07 check was performed before this intentional edit and had exactly 12. Opening allocation remains 11,200 and target remains 490,000.

An early deletion checkpoint captured a real pending state; later server evidence matched its original operation ID and confirmed exactly one deletion. The helper was adjusted to distinguish that acknowledgement delay from success. An ADB metadata-entry helper appended the test label rather than replacing it; the retained dashboard immediately matched the actual new server label. No application defect or production fix was required.

Ordinary income receipts, expenses, crypto results and projects (including milestone records) compare exactly with the fresh baseline at every completed original-account server checkpoint. Independent monthly income/expense/surplus totals match the production dashboard. Unrelated recovery payloads/statuses are unchanged; after all goal recovery cleared, the complete recovery preference file matched its original bytes. Both device network settings were restored, and fresh server reads after reconnection establish actual Firestore reachability rather than IP-ping connectivity.

The account-isolation test used a deliberately offline-queued proposal with the app stopped before reconnecting and Home shown before the user signed out. The second account's own empty savings view loaded; reads of the first account's goal, entries, receipts and fences returned PERMISSION_DENIED. The original account's pending payload remained intact. On return, fresh server evidence still showed 22,200 and the same pending proposal; opening Savings Goal acknowledged it once. This is a live basic/queued-account-switch check. It is **not** an in-flight server-commit or delayed-callback race; those remain deterministic/unit/emulator coverage. Sign-in was performed only by the user, and sign-in screens/passwords were not captured.

The earlier 186-unit/106-emulator/four-isolated-Android evidence applies to unchanged production code and rules. During live acceptance, fresh test APK builds and explicitly opted-in server audits exercised the retained read-only audit extensions. Finalization checks and intermediate compile checks are recorded separately below. No production repair was necessary during acceptance. Deployed source equality remains unverified because authenticated administrative access was unavailable, despite successful live behavior and the matching local expected hash. Live deadline expiry, API 26 runtime verification and Hilt/Koin adoption remain outside this acceptance result.

The user authorized granular commits and a normal upstream push after acceptance succeeded. No Firebase deployment, live rule alteration, dependency upgrade or new feature milestone was performed. Local review ZIPs/diffs, private evidence, build outputs, credentials and unrelated IDE settings are excluded from commits.

## Finalization verification

After live acceptance, the accepted working state was backed up to a new excluded ZIP outside the repository and verified with SHA-256 before staging. Historical snapshots allowed the initial implementation and both reviewed recovery corrections to be committed separately without replacing the working files. Each of the eight executable commit trees passed compilation of application, unit-test and Android-test Kotlin sources. The final documentation group changes no executable behavior.

Fresh final verification used the normal working sources, without the temporary authentication inspector or snapshot init script:

```powershell
.\gradlew.bat '-Pkotlin.incremental=false' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
git diff --check
```

Result: **BUILD SUCCESSFUL; 186 unit tests executed, zero failures/errors/skips; app and test APKs built; lint 0 errors / 20 warnings.** Unit totals were checked from all 29 XML suites. No additional live records were written during finalization. Snapshot compilation initially encountered duplicate incremental-cache declarations after changing source roots; disabling incremental compilation passed without a source change. A first final command misquoted the dotted property and ran no test tasks; the quoted command above then completed successfully. Lint completed in that run; the build was not restarted for its longer analysis phase.

The **106 Firestore emulator tests** and **four isolated GoalNavigationTest Android checks** are reused from the latest deadline-correction verification, not claimed as fresh finalization executions. Their rules and production/test sources remain unchanged. The four earlier dashboard Android checks are also historical evidence. The live A–E results above are separate fresh production-app/server checks and do not convert unexecuted expiry or in-flight races into live passes.

All 22 accepted source/test/rule files match the pre-staging backup byte-for-byte. Committed blobs are checked against the accepted saved files, with only intentional documentation updates/sanitization permitted. The existing expense, income, linked-receipt, project/milestone and crypto rule text is identical to the baseline. The expected complete rules SHA-256 remains unchanged. Staged diffs undergo whitespace and private-identifier checks; only the 24 files in the implementation manifest belong to this milestone. Unrelated IDE settings and all three local review diffs retain their backup hashes and remain uncommitted. Private evidence and the fixture registry remain excluded.

The intended upstream was confirmed as main tracking origin/main on the existing repository remote, and fetched before push with no divergent remote commits. The normal push and actual remote/local commit equality are verified after the final documentation commit and reported separately; no history rewriting is authorized.
