# Final protocol publication review

## Evidence scope for repository finalization

This document records the audit, review or live phase described below. Historical statements about no Git actions apply to that phase; finalization commits and pushes the accepted files with editorial documentation sanitization only.

- Current accepted Android evidence:264 unit tests,successful app/test builds,and lint0 errors/22 warnings. These results are reused for finalization because executable files match the accepted snapshot; no test rerun is claimed.
- The147-rule-test result was executed in the pre-publication review and reused for the later UI-only repair and finalization. Earlier API26/API35 isolated and SDK checks remain separate historical evidence; there is no clean full-suite API35 claim.
- Actual bounded live acceptance is recorded in FINAL_PROTOCOL_LIVE_ACCEPTANCE.md:fixture-only income/surplus+LKR350,expenses0,and unchanged crypto,savings and collectible pending. Personal account totals and identifiers are excluded from the public record.
- API35 instability,deployed-source equality without administrative access,and unexecuted live legacy migration/server-in-flight races remain limitations. Automatic sync requires a running eligible process/session; queued work resumes after reopening and is not guaranteed to execute while force-stopped.

Review date: 4 November 2026, Asia/Colombo. Scope: the accepted FR01-FR21 candidate; no feature milestone or visual redesign.

## Decision and preservation

The demonstrated blocking scenarios below have scoped corrections and passing local regressions. This is a **local publication handoff**, not deployed-rule equivalence or live acceptance. Do not publish the previous ZIP/rules hash. Use the complete current rules and safe startup order below.

Actual HEAD remained `8957e560c5fd6624edeaf31459d0a5527cd7b66b`; the index remained empty. No applicable AGENTS.md was found in the repository or ancestors. The accepted 80 files initially matched `clarity-finance-final-requirements-review-20261103.zip` byte for byte (ZIP SHA-256 `29174403F441E76E3110496DBCBE306C310ECEFDE86FB2EE4F91731B5C61E962`). Before editing, 202 files and Git inspection evidence were copied and hash-verified in an outside-repository backup. Credentials were excluded; protected configuration was checked by hash.

Unrelated `.vscode/settings.json`, `reporting-review-fixes.diff`, `savings-goals-deadline-fixes.diff` and `savings-goals-recovery-fixes.diff` remain byte-identical and uncommitted. No publication, Git staging/commit/push, live financial writes, target/dependency changes, fixture cleanup or Pixel_6a commands occurred. Its unrelated recovery entry was not opened, migrated, retried, fenced or discarded.

FINAL_REQUIREMENTS_AUDIT.md and OFFLINE_SYNC_MATRIX.md were checked against production code and relevant assertions, then updated. FR21's authoritative wording is **“Allow source/category additions, renaming and archiving”**, with criterion **“Archive prevents future selection while preserving historical records and totals”** (requirements PDF page4, section4). These requirements do not authorize processing existing live proposals.

## Demonstrated findings and corrections

| Finding and concrete failing scenario | Correction and regression |
| --- | --- |
| **F1: existing legacy target remained writable after fencing.** An older milestone-cancellation Task ignores syncFences. The candidate fenced its proposal without adopting the existing milestone into syncStates; a late old cancellation could still commit after dismissal. | First fencing now adopts an existing record with `deleted=false`, leaving every business field/timestamp unchanged, or retires an absent record with `deleted=true`. Existing state/retirement is never overwritten. Pre-fix unit expected a barrier and got none. Emulator controls first prove valid legacy cancellation, then require the barrier, deny that old path and accept a new audited cancellation. Before correction the required barrier was denied. |
| **F2: proof marker was not bound to the business operation.** Crypto could commit receipt/state nonce A with business lastOperationId B. Milestone primary markers also need binding. | fReceipt now binds crypto lastOperationId and milestone lastMilestoneOpId to the receipt ID. A valid crypto counterpart succeeds; changing only the marker fails. Before correction the forged counterpart succeeded. Existing audited milestone allocation/payment/cancellation still passes. |
| **F3: delayed account A bookkeeping erased B feedback.** One `(UID,error)` pair meant a late A error removed B's unresolved error. | Errors are a UID-keyed map; updates/removal affect only their owner. A production FinancialQueue observer now retains B's error after A's retry error. Before correction B's error became null. Existing auth-generation clearing remains. |
| **F4: cold/partial cache fabricated catalogue authority.** Empty cached snapshots were merged with active built-ins before server rename/archive overrides were known. Missing names could appear as Other, Salary or static category labels. | Persist a complete server-confirmed UID catalogue in a separate preference of the **existing** DataStore. Both category/source listeners must be server-backed before adopting a new complete snapshot. Cold/partial cache stays unknown; trusted earlier names/archives remain cached and labelled stale. Unknown names display an explicit unavailable label and stable ID. Forms gate new selections; immutable retries/inherited receipts keep their semantics. The cold test failed before correction. Current tests cover one-server/two-server transitions, reopened store, archived names, UID isolation and unchanged corrupt recovery JSON. An isolated production expense form stays disabled for unknown/archived new choices and enables a valid active counterpart. |
| **F5: archived recurring selection could remain uncertain forever.** A saved proposal can outlive its selected category's archive; a generic permission response cannot establish rejection. | After matching immutable occurrence/receipt reconciliation, the production transaction reads archive state. A consistent read proving **irreversible archive** permits rejection. Missing custom-category/access errors stay uncertain. Unchanged existing template references remain valid. Tests cover archived rejection without writes, matching confirmation winning before catalogue checks, and an active custom category preserved in the actual expense payload. |
| **F6: incomplete/conflicting proof was treated as definitive rejection.** Rules allowed a fence without a record barrier; the remote accepted any existing fence, and a receipt for different immutable details could reject an outstanding legacy operation on another target. | Fence creation now requires existing adopted state or the matching atomic first barrier. Reading a fence requires matching immutable details **and** a barrier. Conflicting receipts/fences and incomplete fences stay uncertain, retained and nondismissible. Two pre-fix unit regressions failed; a separate emulator regression accepted the forbidden fence-only request. Corrected tests reject that bypass, accept its atomic counterpart and block old direct recreation. Matching success receipts still win before mutable reads. |

Recovery details also contained actual misencoded separators; they now use plain ASCII separators. No financial computation changed.

## End-to-end production trace

1. **Durable acceptance:** FinancialQueue.submit persists immutable UID/type/record/operation scope before remote work and reuses identical interrupted acceptance. Unresolved same-target/project-family proposals gate competing/dependent changes; children need confirmed parents. The existing SubmissionRecoveryStore remains the sole Preferences DataStore. Crypto, savings and recurring keep their typed records/protocols.
2. **Workers/lifetimes:** ClarityApplication starts one Koin-singleton SessionSyncCoordinator. Idempotent start observes distinct UID generations and supervises financial, crypto, savings and recurring workers. Old jobs are cancelled; generation checks reject stale visible errors. Financial sweeps are mutex-serialized. RetainedTasks keeps actual Firebase Tasks across timeout/cancelled awaits. Coordinator/Task tests exercise these production paths; no new screen worker or DataStore is added. A fence intentionally may race its mutation; server rules serialize the winner.
3. **Reconciliation first:** FirestoreFinancialRemote checks immutable receipt before mutable target/version/catalogue/capacity. A matching receipt wins even if its business record was subsequently edited/deleted. Recurring reconciles consumed occurrences first. Savings retains its reviewed receipt/version/expiry-fence policy. Nonmatching proof never becomes success or definitive rejection merely through a generic exception.
4. **Two-way server proof:** syncOperations is immutable and requires matching state and business mutation. CREATE needs absent primary/no prior state; UPDATE needs expected timestamp; DELETE retires identity. State cannot be deleted. CANCEL remains subject to original business/counter policy. Crypto/milestone markers agree. Receipt and fence for one ID cannot both acknowledge. First fence adoption cannot hide a business change. Retired/adopted IDs cannot bypass proof through direct update/delete/recreate.
5. **Atomic related balances:** Existing parent/milestone secondary-counter exceptions still require exact original allocation/receipt relationships. Linked income stays immutable. Retained tests cover zero initial counters, parent-plus-child bypass, orphan/multiple/cross-user receipts, counter-only/receipt-only writes, cancelled-parent restrictions and cancellation double-release. The audited parent -> allocation -> receipt -> partial cancellation production-shaped case passes. No financial schema/arithmetic change was needed in this review.
6. **Feedback/outcomes:** Local acceptance is not server success. Pending/uncertain proposals do not contribute to confirmed totals; pending/rejected edits/deletions retain confirmed contributions. Generic Task/access/network/decode/store errors cannot establish rejection. Only acknowledged monotonic outcomes/fences expose dismissal. Completing one operation does not hide another. Account changes clear old visible data/feedback.
7. **Cleanup/restart:** Acknowledgement is recorded before local removal. A new test forces cleanup failure after success, recreates the queue over the same DataStore and reconciles one receipt: exactly one mutation and eventual recovery removal. The cleanup exception stays local, not a remote rejection. Genuinely suspending fake lifetimes are used; Mockito answers do not block on deferred values.
8. **Legacy/corruption:** Atomic promotion retains original UID/type/record identity and raw payload; missing legacy nonces are derived stably and original milestone/receipt IDs retained. Matching legacy outcomes reconcile first. An absent deletable pre-protocol CREATE cannot distinguish never-committed from committed-then-deleted: it stays uncertain pending explicit online safe withdrawal/history review. Corrupt raw recovery is retained with an error, never substituted with an empty queue. Private malformed entries require separate diagnosis; automatic destructive repair is not claimed.

Rules still allow an otherwise valid legacy primary write until that record is adopted. Afterwards fGuard requires a new atomic proof. UI buttons are not the authorization boundary.

## FR21 integration

| Flow | Reviewed behavior/evidence |
| --- | --- |
| Catalogue lifecycle | Stable owner-scoped custom/built-in IDs; rename/default changes never rewrite finance history; archive irreversible; durable add/rename/archive. Model, production VM/queue, form UI and owner rules tested. |
| Ordinary expense/income | Active choices for new records, unchanged archived references permitted for corrections, different archived selection denied. Custom IDs survive backdating/deletion; omitted expense-note compatibility preserved. No Other/Salary substitution for unknown IDs. |
| Reporting/history | Names resolve by stable ID including archived entries. Cached names may be stale and are labelled. Unknown catalogue shows unavailable label plus ID independently of valid financial amounts; it does not fabricate totals or turn missing financial data into zero. |
| New project/agreement | Active source/default-currency choices; agreed source/currency immutable. Immutable saved retry can reconcile after archive. |
| Direct/milestone receipt | Source inherits its existing agreement and is not a new choice. Archived inherited source remains valid under production transaction and linked-receipt rules; a valid archived-source receipt control passes. |
| Recurring | Dynamic categories/default type in template and actual forms. Existing unchanged template references retained; new actual confirmation chooses an active category. Historical consumed confirmation survives expense edit/delete. Archived saved proposals follow F5. |
| Account/cold cache | Complete confirmed UID snapshots share the existing DataStore without touching submission_records. Unknown choices stay unavailable. Auth-generation checks reject delayed old-owner catalogue/queue results. |

New catalogue write-isolation tests send otherwise **valid atomic catalogue/proof/state operations** as another owner and unauthenticated, then accept the same operation as its real owner. This avoids claiming owner isolation from unrelated malformed-schema rejection. Existing financial atomic tests also have valid counterpart operations. Isolated Android graphs cannot fall back to live repositories.

## Actual verification

Commands ran from D:\clarity-finance with unchanged Java/Gradle/dependencies. Rules tests use 127.0.0.1:8080 and demo projects only. Private logs/snapshots stay in ignored build/protocol-review and are excluded from the archive.

| Executed now | Result |
| --- | --- |
| `gradlew.bat :app:testDebugUnitTest --tests "*ProtocolPublicationReviewTest" --tests "*CatalogueCacheReviewTest" --tests "*FinancialQueueTest" --tests "*FirestoreRecurringRemoteTest" --console=plain` | Final focused25 tests,0 failures/errors/skips. |
| Pre-correction reproductions | Initial4 units:3 expected failures/1 reconciliation pass. Later4 protocol units:2 expected failures. Initial focused rules21:19 pass/2 expected failures. Fence-only regression separately failed because the forbidden request succeeded. |
| `firebase.cmd emulators:exec --only firestore --project demo-clarity-protocol-review "npm --prefix firestore-tests test -- --grep publication"`; later same npm selector against its retained local process | Corrected focused21 then22 cases passed as coverage grew; overlapping counts, not distinct additive tests. |
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain` | **264 tests,0 failures/errors/skips; app and test APKs built; lint0 errors/22 warnings.** Earlier review262-unit result preceded final proof correction and is superseded. |
| `firebase.cmd emulators:exec --only firestore --project demo-clarity-protocol-review "npm --prefix firestore-tests test"` | **147 passing**, all retained existing cases included. Earlier review145/146 results preceded added proof/isolation regressions. |
| `adb -s emulator-5560 install -r <debug APK>` and `install -r <test APK>`; `adb -s emulator-5560 shell am instrument -w -r -e class com.clarity.finance.CatalogueNavigationTest,com.clarity.finance.DashboardNavigationTest,com.clarity.finance.RecurringNavigationTest com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner` | **10 isolated cases passed:** new unknown/archived expense selection, catalogue lifecycle/account change, dashboard/history/overflow and recurring forms/finance integration. Production Compose/navigation/ViewModels with explicit fake repositories; not live verification. |
| Preservation, whitespace, privacy/archive checks | HEAD/index unchanged; tracked whitespace plus separate new-file checks; current archive/source SHA equality; unrelated/configuration files unchanged. |

Environment/helper failures were diagnosed: initial Windows regex quoting failed before tests; cleanup-failure Mockito stubbing first used an unsupported checked exception and was corrected to UncheckedIOException; the CLI left an orphan demo-emulator child holding8080, identified by PID/full command/project and stopped before retry. No unrelated process/network was altered.

The separate Clarity_API26_Verification AVD is Android8.0/API26,x86_64,480x800/240dpi(320dp),font1.3. Before install/start, read-only run-as inspection found **zero recovery entries and no default Auth preference**; earlier named demo Auth preferences were preserved. No wipe, uninstall or storage clear. The AVD was stopped after testing. **No command targeted Pixel_6a.** The final proof-only remote correction followed the UI run; final units/emulator tests exercise that correction, and unchanged fake-graph UI evidence remains applicable.

Reused, not rerun: earlier audit254 unit/142 rules/20 isolated screen cases on each API, SDK transport/cache/restart/replay/Home-sync phases, and historical live milestone acceptances. The current10-case API26 run is not a new full API35 run. Unexplained API35 ART/JIT instrumentation instability and the separately recorded system_server failure remain unresolved. No new device process-death edit/delete test, expired production credentials, live multi-device race or live access check is claimed.

## Complete rules and indexes

Complete publication file: **firestore.rules**, not a fragment.

**SHA-256: `95D0DFF16C7814BD4434F76E14CCA034CB510B897BB73293053CA9241BD8ED29`**

No publication or deployed-rule retrieval occurred. This hash covers the saved local/archive file, not Firebase Console contents. It supersedes the previous accepted snapshot's49655B98... hash and intermediate review hashes.

firestore.indexes.json is unchanged. No new index is required for document-based queue reconciliation, owner catalogues or existing single-date reporting queries. Preserve deployed indexes and single-field date indexes. Existing required composite:

- Collection ID/group: recurringOccurrences.
- Query scope: COLLECTION.
- templateId ASCENDING, month ASCENDING.

Verify Enabled/Ready in clarity-1c83e -> Firestore -> (default) -> Indexes before recurring-history acceptance. Do not replace/delete unrelated indexes. Rule publication does not migrate documents.

## Compatibility and safe rollout order

**New app/old rules:** ordinary reads may work, but old rules deny new proof/state/fence/catalogue access. Proof-backed transactions fail atomically; no partial finance mutation should commit. Proposals remain uncertain, not successful/safely rejected. A cold catalogue is unavailable. Do not start this candidate on a live signed-in installation before the rules are published.

**Existing documents/new rules:** owner reads and omitted-note compatibility remain. Rules do not rewrite stored documents. New audited mutations adopt records into state. Original-UID workers promote legacy proposals preserving identity/raw payload, reconcile matching results first, and retain ambiguous absence. A complete catalogue is first established by both server collections. Publication itself does not synchronize a local queue.

**Older installed apps:** read compatibility is broader than write compatibility. Otherwise valid unadopted legacy writes remain possible, but adopted/fenced/retired targets require new proof and old direct primary changes are denied. Old fixed selectors cannot represent custom catalogue names/IDs or bypass archived entries. Strict calendar validation can deny previously permissive bad dates. Existing goal/recurring protections remain. A denied old SDK proposal is not necessarily cancelled. Full old-client write compatibility is **not** claimed; downgrading is not a recovery strategy and rules are not weakened for it.

Safe order, subject to separate publication/live-fixture authorization:

1. Leave the live-account Pixel and unrelated entry untouched. Do not install/launch this APK **or instrumentation** there: Application starts sync before screen filtering. Staying on Home or selecting another account afterwards is not a safe exclusion mechanism.
2. Before any future migration there, make a stable private read-only copy of recovery DataStore, SDK pending state and default-auth metadata **without Application startup** (targeted run-as file read/offline decoding, not instrumentation/Firebase helpers). Keep UID/type/record/raw payload/hashes privately; inspect all owners. If consistency cannot be established without processing work, stop for a separate plan. This review did not inspect Pixel data or authorize that unrelated operation's processing.
3. Prefer a fresh separate no-backup-restored acceptance device/profile. For a reused test device, inspect files first and stop if default auth/unrelated queue entries exist. A server-empty account does not make an old local queue safe. Do not change the production Firebase target to a benchmark/demo client.
4. Coordinate cessation of financial writes by participating old clients. Retain previous deployed rules privately, then manually publish the **complete** current file to clarity-1c83e/(default). Inspect Console published source independently; local hash alone is not deployed proof. Preserve/check required indexes Ready. Nothing was deployed here.
5. Install/start only on the inspected acceptance device. User signs into the designated account interactively. Verify server/query/catalogue reads and fresh baselines before fixtures. Any unrelated proposal must be discovered **before startup**, not before entering a screen. Do not retry/fence it to solve access errors.
6. Execute only separately authorized fixtures below. Record private stable IDs/server acknowledgements and fixture-specific deltas; reconnect staying on Home. Restore only test-device connectivity, preserve DNS, and compare unrelated server/recovery state. Separate DNS/UNAVAILABLE from PERMISSION_DENIED; stop to diagnose failures before broad retries/rollout.
7. Startup/migration of existing live devices is a later explicit decision after acceptance and unrelated-queue review. No ad hoc skip-one-operation filter is invented here.

App-wide means while the process/session can run; OS suspension/killing prevents guaranteed immediate execution. Reopening restarts synchronization. Authentication and uncached data are not fabricated offline.

## Bounded proposed live fixtures - not executed or authorized here

Use fresh `PROTO-<actual Colombo date>-<nonce>` labels/private IDs on an inspected designated account/device. Use the actual current month. First server-confirm an active builtin category B; if unavailable, stop and agree a fixture choice rather than modifying an unrelated category. No crypto/savings writes are needed.

Baseline protection: fresh server income/expenses by transaction month, crypto, goal/version/ledger, project/milestone/receipt balances, recurring/catalogue records and all local queue scopes. Keep counts/document hashes/IDs privately. Compare cached display independently. If unrelated proposals exist, choose another inspected device; do not migrate/retry/discard/fence them. Never publish credentials/private snapshots.

| Fixture and bounded operations | Expected effect and retained history |
| --- | --- |
| One custom category C (committed) and source S (LKR). Create, rename, archive **after** dependent fixtures exist. |0 finance delta; stable IDs, history preserved, new selectors exclude archives. Two catalogue documents/immutable operation history remain. Never archive an unrelated builtin. |
| Ordinary expense E:C create100, after archive correct120, delete. Standalone income I:S create1000, after archive correct1100, delete. | Expense +100 -> +120 ->0; income +1000 -> +1100 ->0 LKR. One stable record ID per fixture; new operation ID per logical edit/delete. Unchanged archived references remain correctable. Deleted IDs retired. |
| Expense O:B create25 offline -> force-stop/reopen preserving storage -> reconnect on Home. Repeat offline/restart/Home reconnect for edit30 and delete. | Confirmed delta +25 -> +30 ->0 once. Pending create contributes0; pending edit retains25; pending deletion retains30 until acknowledgement. Same immutable saved operation IDs through restarts; feedback clears per operation. No unrelated Retry. |
| Project P:S create1000, edit1200. M1 create400/edit500; M2 create100. Archive S before inherited direct200 and M1 receipt150. Cancel M2, partially-paid M1, then P. | Two linked receipts total **350 LKR once**. Before cancellations allocation600, received350, outstanding850. M2 releases100 ->allocation500; M1 releases350 ->allocation150, agreed500/received150 preserved. Parent cancellation retains agreement1200/direct200/milestone150/allocation150/history850; current collectible pending0. M2 agreed100/received0 retained. |
| Monthly template T:C before archive, suggested20, first due today. Initially ignore. After C archive select active B for actual25; confirm once/reopen/retry to inspect identity. Correct actual30/backdate to previous month; delete actual; cancel T. | Template/unconfirmed contributes0. Exactly one actual expense25 ->30 ->0, moved to its actual-date month on edit. Deleted actual remains consumed; no recreation/reconfirmation. Cancelled template/one immutable confirmation remain. Suggested amount never counts. |
| Account isolation, coordinated user sign-in only on inspected fixture device. |0 extra delta. Old rows/forms/messages disappear; other account has no fixture data. Return/reconcile only own O on Home. Switching with pending O requires explicit inclusion in fixture authorization; basic switching alone proves no universal in-flight race. |

Bounded business creation inventory: **1 category,1 source;2 ordinary expense IDs plus1 recurring actual expense ID;1 standalone income;1 project,2 milestones,2 linked receipts;1 template,1 confirmation.** Deleted finance IDs retain proof/retirement history. Additional immutable proof/state/fence documents remain; report their actual counts after execution rather than inventing them beforehand.

Intended final fixture-only deltas after the above deletions/cancellations: **ordinary received income +350 LKR, expenses0, cash surplus +350 in the receipt month; crypto0, goal saved0, collectible pending0**. Other reporting months return to baseline after backdated-expense deletion. Retained fixed samples: cancelled project,2 cancelled milestones,2 receipts totalling350, cancelled template/1 consumed confirmation and2 archived catalogue entries. Do not promise cleanup of immutable history. If execution stops partway, report actual partial deltas instead.

Uncertain/rejected/conflict safety currently has deterministic suspension/fence/archive/concurrent emulator evidence. Do not weaken live rules, manipulate clocks or corrupt live state to manufacture failures. Live conflicting edits would require separately inspected clients and authorization. Future steps need server proof and unrelated-data comparisons; cached display alone is insufficient.

## Limits and artifacts

No live access/deployed-rule comparison, live fixture or Pixel queue inspection occurred. Broader process/network/account races, genuine expired credentials, assistive-technology/task timings, API35 runtime instability and external submission artefacts remain outside verified scope. Koin adoption was completed earlier, and API26 has actual isolated evidence; neither implies all device permutations. No100%-completion claim is made.

The updated review ZIP has exactly85 complete current milestone files: the original80 plus this report,2 new focused unit-test files and2 previously unchanged recurring remote/test files now corrected in this pass. Paths are repository-relative; complete rules and relevant tests are included. Every member is SHA-256 checked against current source. No diagnostics, snapshots, scripts, credentials, configuration, IDE files, review diffs or build outputs. Complete archive manifest is in FINAL_REQUIREMENTS_AUDIT.md; correction-only files follow.

## Exact correction-pass files

- `FINAL_PROTOCOL_PUBLICATION_REVIEW.md`
- `FINAL_REQUIREMENTS_AUDIT.md`
- `OFFLINE_SYNC_MATRIX.md`
- `app/src/androidTest/java/com/clarity/finance/CatalogueNavigationTest.kt`
- `app/src/main/java/com/clarity/finance/data/local/SubmissionRecoveryStore.kt`
- `app/src/main/java/com/clarity/finance/data/model/Catalogue.kt`
- `app/src/main/java/com/clarity/finance/data/repository/CatalogueRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FinancialOperation.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FinancialQueue.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreFinancialRemote.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreRecurringRemote.kt`
- `app/src/main/java/com/clarity/finance/di/AppModules.kt`
- `app/src/main/java/com/clarity/finance/ui/CatalogueScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpenseFormContent.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpensesScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/AddEditIncomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/IncomeListScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/AddPaymentScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt`
- `app/src/test/java/com/clarity/finance/data/repository/CatalogueCacheReviewTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FinancialQueueTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreRecurringRemoteTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/ProtocolPublicationReviewTest.kt`
- `firestore-tests/final-requirements.test.js`
- `firestore.rules`

Compared with the previous ZIP: 21 existing archive entries changed, 5 added, 0 removed. The additions are this report, CatalogueCacheReviewTest.kt, ProtocolPublicationReviewTest.kt, and the now-corrected FirestoreRecurringRemote.kt/FirestoreRecurringRemoteTest.kt (existing repository files omitted from the original ZIP because they were previously unchanged). All other accepted entries remain byte-identical.
