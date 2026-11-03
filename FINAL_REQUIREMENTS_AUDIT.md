# Final technical requirements audit - FR01 through FR21

## Evidence scope for repository finalization

This document records the audit, review or live phase described below. Historical statements about no Git actions apply to that phase; finalization commits and pushes the accepted files with editorial documentation sanitization only.

- Current accepted Android evidence:264 unit tests,successful app/test builds,and lint0 errors/22 warnings. These results are reused for finalization because executable files match the accepted snapshot; no test rerun is claimed.
- The147-rule-test result was executed in the pre-publication review and reused for the later UI-only repair and finalization. Earlier API26/API35 isolated and SDK checks remain separate historical evidence; there is no clean full-suite API35 claim.
- Actual bounded live acceptance is recorded in FINAL_PROTOCOL_LIVE_ACCEPTANCE.md:fixture-only income/surplus+LKR350,expenses0,and unchanged crypto,savings and collectible pending. Personal account totals and identifiers are excluded from the public record.
- API35 instability,deployed-source equality without administrative access,and unexecuted live legacy migration/server-in-flight races remain limitations. Automatic sync requires a running eligible process/session; queued work resumes after reopening and is not guaranteed to execute while force-stopped.

## Pre-publication correction review - 4 November 2026

Read FINAL_PROTOCOL_PUBLICATION_REVIEW.md before publication or starting this build on an existing account. The accepted 3 November snapshot had demonstrated fence/proof, account-feedback and cold-catalogue defects; this review corrects them and adds regressions. Current local evidence is **264 unit tests, 147 Firestore emulator tests, successful app/test builds, lint 0 errors / 22 warnings**, and **10 isolated API26 UI cases**. The earlier 254-unit / 142-rule / 20-screen-per-API and SDK restart/performance evidence below is historical, not a newly repeated device or live run. The existing API35 instability and broader acceptance limits remain unresolved.

The full current rules SHA-256 is **95D0DFF16C7814BD4434F76E14CCA034CB510B897BB73293053CA9241BD8ED29**; the previous snapshot hash is superseded. A fence now requires a record barrier, including unchanged existing legacy targets. Conflicting/incomplete proofs remain uncertain. A complete confirmed UID catalogue is persisted separately in the same DataStore; cold/partial caches cannot fabricate active built-ins or confirmed names. Recurring archived-category rejection requires a consistent server read after immutable confirmation reconciliation. No preserved live recovery entry was opened, migrated, retried, fenced or discarded. Pixel_6a was not targeted.

## Result, authority and preservation

Audit date: 3 November 2026, Asia/Colombo. The audit baseline HEAD was `8957e560c5fd6624edeaf31459d0a5527cd7b66b`, matching the requested baseline. No applicable AGENTS.md was found in the repository or its ancestors. Before editing, the saved working state was backed up outside the repository to an outside-repository backup and all 177 copied files were hash-verified. The initial index/tracked tree was clean; the four unrelated IDE/review files were preserved byte-for-byte. No commit, push, deployment, live financial mutation, fixture cleanup, dependency change, host-network change or Pixel_6a ADB command was performed.

The authoritative sources are the supplied PDFs, read page by page (page numbers below are PDF pages):

- **R:** `SE3092_Analysis_and_Requirements(1).pdf`, 12 pages, SHA-256 `E1F40377D5B5235F171562228DAE59B19759B72BF498DBB56F15AB5BE54BB024`.
- **B:** `SE3092-Assignment01-PlatformBasedDevelopment-SpecificationWithMarkingScheme-2026-S2 (1).pdf`, 10 pages, SHA-256 `9232C022EEF049F4EC27CAED4BE5400F0D3BD0BB34F5B35028353B94355B6D21`.

These documents define requirements; they are not operational instructions authorizing deployments, submissions, credential use or live writes. The user's requested FR01-FR21 scope is controlling. R labels FR21 **Should**, but it is explicitly included in this task. FR22 password reset, FR23 merchant memory, FR24 optional prompts/filters and FR25 optional export/reminders/Google sign-in are outside this milestone. No visual redesign was started.

**FR21 exact wording, R PDF page 4, section 4:**

> Allow source/category additions, renaming and archiving

**Its exact acceptance criterion:**

> Archive prevents future selection while preserving historical records and totals

FR21 exists in the supplied authoritative document; it was not inferred from an earlier report. It is now implemented and locally tested, including a real production-screen catalogue lifecycle on API26/API35, strict owner rules, stable references and historical totals.

Statuses below mean **verified within the stated local/recorded evidence**, **partially verified** where a material scenario or acceptance dimension remains, **missing** where implementation is absent, and **blocked** where external publication/submission is required. A verified row is not a claim of new live acceptance or exhaustive race testing. This audit does not claim 100% assignment completion.

## Functional traceability

Production paths below are relative to `app/src/main/java/com/clarity/finance/`; tests are under corresponding `app/src/test/...` or `app/src/androidTest/...`. Existing source and relevant numeric, state, version, ownership and link assertions were inspected; milestone summaries/test counts alone were not treated as proof.

| Requirement | Exact source | Implementing screens/code | Inspected assertions and actual evidence | Final status / remaining work |
| --- | --- | --- | --- | --- |
| FR01 Firebase email/password register/sign-in/sign-out | R p3 section 4; B pp5-6 sections 4-5 | ui/auth, AuthViewModel, FirebaseAuthRepository | AuthViewModelTest checks blank/invalid/mismatched/incorrect credentials and duplicate taps; AuthCancellationTest verifies cancelled Tasks stay outstanding; local Auth SDK registration, wrong password, login and logout passed on API26/35; actual signed-out production Activity startup verified | **verified locally**; no live account registration/sign-in was performed |
| FR02 owner isolation | R p3 section 4; R p9 AT11; B pp5-6 | UID paths in repositories, auth-scoped VMs, SessionSyncCoordinator, firestore.rules | Rules A/B/unauthenticated denials including new catalogue/proofs/states; delayed old-account unit callbacks ignored; Android catalogue/crypto/goal/recurring account-state isolation; no old rows during delayed new income/expense listeners | **verified locally**; no new live in-flight account-switch race |
| FR03 ordinary income CRUD/source/date/amount | R p3 section 4; pp5-6 sections 6.1-6.2 | AddEditIncome/IncomeList, FirestoreIncomeRepository, FinancialQueue/Remote | IncomeList AT01 total 14030000 minor; month filtering; actual-credit clearing; real-store offline create/edit/delete admission; original contribution retained while pending/rejected; linked decoder protections; owner CRUD/calendar rules | **verified locally**; new protocol needs later rules publication |
| FR04 fixed original currency/conversion | R p3 section 4; p5 section 6.1; p9 AT01/15 | Income, AddEditIncomeViewModel/form-state preview, FinancialValidation | USD41 at300 -> LKR12300; half-up once; actual-credit authoritative; original amount and fixed rate persisted; invalid rates rejected; later catalogue defaults do not recompute history | **verified**; no live FX connection or invented exchange rate |
| FR05 pending separate from received | R p3 section 4; p6 section 6.2 | expectedPayments versus incomeReceipts, MonthlyReporting.pending | Pending/cancelled/mixed-currency tests; no milestone double count; real SDK baseline pending project/crypto do not change ordinary totals; pending proposals excluded | **verified**; pending card is current collectible, not historical month-end balance |
| FR06 project/milestone/partial receipts | R p3 section 4; p6 section 6.2; p9 AT02 | PaymentDetail/Confirmation, new agreement/milestone editors, FinancialRemote | Existing confirmation/capacity tests; matching retry writes nothing; no counter reset; partial cancel releases only unpaid allocation; audited rules parent+child+receipt/cancel; Android direct/milestone routes and editor Save/counter assertions | **verified locally**; existing live evidence is historical, not validation of this new server protocol |
| FR07 signed realised crypto/month/YTD | R p4 section 4; p6 section 6.3; p9 AT03 | CryptoReporting, CryptoViewModel/Screen, CryptoRepository/Remote | +60000-25000=+35000; net loss; Colombo/year/backdate/edit/delete; separate cash totals; pending/rejected original contribution; precise unknown outcomes; old JSON retry/fence/dismiss; Android CRUD/feedback/overflow inspection | **verified locally**; no new live crypto record |
| FR08 quick expense capture | R p4 section 4; p7 UC02; p9 AT05 | Dashboard Add Expense, AddExpenseViewModel/ExpenseFormContent | Positive exact amount, real nonfuture Colombo date, channel/type/category and blank note; stable duplicate Save; API26 controlled capture times below; no Double display conversion | **verified under controlled tests**; timings are automation, not a human usability study |
| FR09 edit/delete/backdate | R p4 section 4; p9 AT05/13 | existing expense/income/crypto editors, new project metadata editors, goal/recurring correction flows | Backdating moves periods; original updatedAt checked; createdAt/origin links immutable; delete exactly once; pending/rejected changes retain confirmed contribution; calendar/overflow/invalid amount tests | **verified locally**; immutable linked project receipts are inspected through their project, not silently exposed as ordinary editable income |
| FR10 categories/per-entry type correction | R p4 section 4; p6 section 6.4 | ExpenseFormContent, stored categoryId/spendingType, catalogue defaults | Transport correction, exact 80/20 split, archived IDs retained in corrections; per-entry values remain authoritative after default edits | **verified**; merchant inference/memory is FR23, outside scope |
| FR11 monthly history/drill-down | R p4 section 4 | ReportingMonth, DashboardViewModel/Screen | Month/year bounds, category-only rows, edit/delete/move updates, link routing, overflow inspection; Android history/edit routes; custom IDs and renamed/archived labels | **verified**; no upload-timestamp grouping |
| FR12 dashboard and separation | R p4 section 4; pp5-6 section 6; p9 AT12/14 | MonthlyReporting, independent ReportingRepository listeners, dashboard/goal card | Income1000-expense1500=-500; independent errors never fabricate surplus; separate crypto/pending/goal; cached labels; real 1000-expense SDK dashboard benchmark | **verified locally**; no bank-balance/safe-to-spend claim |
| FR13 category/split insights | R p4 section 4; p6 section 6.4; p9 AT06 | MonthlyReporting.expenses and labelled bars | Category sums equal total; 40000/10000 ->50000,80/20; 33.3/66.7 complementary rounding; zero and overflow; readable labels separate from colours | **verified** |
| FR14 one active goal/create/edit | R p4 section 4; p6 section 6.5 | fixed savingsGoals/active, GoalViewModel/Screen/Remote | One-goal/server version guards; target/opening/deadline validation; editing preserves ledger/start; future deadline and server expiry-fence replacement tests retained | **verified locally**; no live expired-deadline record or clock manipulation |
| FR15 explicit allocation ledger | R p4 section 4; p6 section 6.5; p9 AT08 | goal entries/counter/immutable receipts | 11200+10000=21200 then-1200=20000; income/surplus not double-counted; negative withdrawals/corrections/deletions, concurrent deltas, tombstones | **verified**; app records an asserted allocation, not a transfer |
| FR16 schedule/progress | R p4 section 4; p6 section 6.5; p9 AT07 | SavingsMath/GoalSchedule, date rollover, GoalScreen | 490000-11200=478800,12 opportunities,39900,2.29%; original anchor/leap clamp/deadline/today inclusion; ceil cent; overdue/reached/overfunded | **verified**; no forecasts or ahead/behind judgements |
| FR17 recurring actual confirmations | R p4 section 4; pp6-7 section 6.6; p9 AT09 | Recurrence, RecurringRepository/Remote/Screen, ordinary expense compatibility | AT09 rent34000 once, variable utilities, ignored zero; stable month IDs; edit/cancel/version conflicts; consumed occurrence after correction/delete; full retained atomic security suite; Android template/confirmation/dialog cases | **verified locally**; suggestions are not expenses; cancellation stops already-due unconfirmed items as documented |
| FR18 Firestore/realtime | R p4 section 4; B pp5-6 | callbackFlow/awaitClose, month/source bounds, VM lifecycle and Koin | Listener removal assertions; independent dashboard errors; stale UID/month results rejected; real SDK cache/server transactions; production Activity startup | **verified locally**; new rules are not published by this task |
| FR19 offline/cache/automatic sync | R p4 section 4; pp6-7 section 6.6; p9 AT10 | SubmissionRecoveryStore, retained Tasks/proofs, Application SessionSyncCoordinator | Real-store mutation admission/restart/isolation; ten SDK-offline entries survive actual force-stop/restart and replay exactly once on API26/35; no feature screen opened for sync; original confirmed values retained | **verified within process/reopen contract**; no promise of execution while force-stopped; see full action matrix and its explicit device-coverage limits |
| FR20 useful recoverable states | R p4 section 4; p9 AT12/16 | feature UI states, catalogue/recovery screen, queue outcomes, listener retries | Corruption retained instead of empty queue; uncached empty does not stop initial listener; access/network errors uncertain with cause; definitive fence reason retained; late await cannot undo fence; rejected proposal correction/dismissal; real failed login | **verified locally**; real expired production tokens and all live network/account races not manufactured |
| FR21 editable catalogues | R p4 section 4; p7 section 7 | Catalogue model/repository/VM/Screen, Home/navigation, dynamic forms/history/reporting, explicit owner rules | Add/rename/archive stable IDs; future selections denied, unchanged historical corrections valid; archived agreement source still accepts its immutable linked receipt; strict createdAt/fields/owner tests; API26/35 production catalogue UI lifecycle/account isolation | **verified locally and implemented**; later manual publication of complete reviewed rules required |

## NFRs and assignment constraints

| Requirement / exact source | Evidence and final status | Concrete remaining work / limit |
| --- | --- | --- |
| NFR01 >=4/5 representative captures <=10s after familiarisation; R p5 section 5 | **partially verified for human usability**, controlled API26 production-form automation passed 5/5:934,915,866,834,866 ms, blank note, one warmup | Validate representative human task performance in the eventual usability/design acceptance; do not call automated timings a user study |
| NFR02 1000 records, cached dashboard <=2s and save feedback <=1s in >=9/10; R p5 | **verified on documented emulators**: actual SDK cache with 1000 expenses; API26 cache10/10, API35 cache9/10; actual UI feedback10/10 on API26,84-150ms | Debug APK/emulator measurements, not a physical-device fleet or release benchmark |
| NFR03 offline/restart/idempotency; R p5 | **verified at tested layers**: real DataStore/Task races, server fences/stale edit/concurrency, actual force-stop phases on both APIs | Device restart tests cover ordinary creates/replays; other action-specific restart and race permutations are U/R or earlier accepted evidence, not newly run live cases |
| NFR04 integer/decimal money; R p5 and section6.1 | **verified**: checked Long arithmetic, BigDecimal display/conversion; removed expense Double formatting and unchecked income sums; overflow/error assertions retained | Visual bar fractions alone are Float; no money calculated with Double |
| NFR05 privacy/owner rules/no app password persistence/logging; R p5; B pp5-6 | **verified within inspected/tested scope**: owner A/B/unauth rules, UID workers/forms, SDK auth; hashes preserve Firebase/local files; no real credential or private snapshot in ZIP | No penetration-test claim; public dummy credentials only in explicitly demo-guarded tests |
| NFR06 accessible controls/labels/text; R p5 | **verified in isolated screen checks** at320dp/130% on API26/35, keyboard tests and scrollable new dialogs | No claim of complete assistive-technology/manual screen-reader testing; visual redesign deferred |
| NFR07 honest incomplete coverage/backdating; R p5 | **verified**: recorded-transaction wording; unavailable never zero; negative surplus/cached labels; changed date moves month | Empty uncached histories remain unavailable until loaded, not confirmed zero |
| NFR08 strict MVVM/read-only StateFlow; R p5; B pp5-6 | **verified by source and tests**: no Firebase/data-source calls or financial arithmetic in Composables; effective-rate/preview arithmetic moved to form-state/domain; UI formats values only | No new whole-app architecture/dependency migration |
| NFR09 bounded queries/indexes/listener cleanup; R p5 | **verified for reporting/selected-month/source paths**: reporting actual-date ranges; income overview now queries selected month server-side; crypto year bounds; recurring template-month index; cleanup assertions | Complete-owner expense list/current-parent outstanding/catalogue reads intentionally have no silent limit; larger all-history browsing can later use explicit pagination without truncating totals |
| NFR10 invalid/empty/denied/expired/network/overdue recovery; R p5 | **partially verified**: full U/R cases, SDK offline/restart and wrong credentials; private corrupt store retained; no uncaught Retry bookkeeping exception | A genuine expired production authentication token was not exercised; authentication/access failure is simulated in suspending fakes/rules, never labelled cloud success |
| Kotlin100%, KotlinDSL, Compose/M3, Navigation single Activity; R pp10-11 section12; B pp5-6 section5 | **verified** in source/builds and actual production startup | No build-stack or dependency upgrades |
| DI: B p6 section5 says 'Hilt (preferred) or Koin' and 'No manual ViewModel instantiation inside Composable functions.' | **verified**: existing framework-managed Koin modules, navigation-owned ViewModel factories, singleton Firebase/DataStore/recovery/remotes/coordinator; ownership/SavedStateHandle tests pass | Previous reports listing Hilt/Koin adoption as outstanding are historical; Koin now satisfies the requirement |
| API26/target34+; B p6 section5; R section12 | **verified for current tested paths**: min26,target36 unchanged; API26 actual production startup/recreation and20 isolated cases; API35 actual login Activity plus20 isolated cases and SDK phases | Earlier native ART/JIT instrumentation crash remains unexplained. A separate API35 system_server package-update NoSuchMethodError interrupted one later test launch; recovered service and targeted retry passed. Neither is claimed repaired by app code |
| Firestore primary, owner security, realtime/offline; B pp5-6 section5 | **implemented and locally verified**; default project clarity-1c83e/live endpoint unchanged; named demo-only clients for tests | Complete new rules require review/manual publication before new protocol is used live; no deployment performed |
| README/repo, technical design PDF, annotated wireframes/diagrams, demo/slides/authorship and LMS submission; B pp6-7,9-10; R p11 section13 | **partially verified / externally blocked**: source, README, genuine prior history, build artefacts and reports exist; this report is not the required professionally formatted >=15-page design PDF | No final design PDF/submission/access/deadline/demo evidence established from supplied files/repository; obtain/complete those artefacts separately. Do not infer submission from app tests |

## Schema, integrity and recovery decisions

The chosen contract was documented before legacy mutation implementation. It reuses the existing Preferences DataStore, Firebase clients and reviewed Task/outcome principles; no dependency or background service was added. See OFFLINE_SYNC_MATRIX.md for every supported action, prerequisites, process/foreground/background distinctions and evidence limits.

- `users/{uid}/expenseCategories/{id}` and `incomeSources/{id}` store only name,archived,defaultValue,createdAt,updatedAt. Built-ins are virtual defaults until renamed/archived; custom IDs are stable. Archive prevents new selection, not historical inspection/correction of an unchanged ID. Category default type/source default currency are suggestions copied into each new transaction and remain explicitly editable. Renaming/default changes never recalculate history.
- Existing primary finance schemas remain unchanged. `syncOperations/{operationId}` is an immutable receipt carrying group,kind,targetId,parentId,expectedUpdatedAt,payload,createdAt. `syncFences/{operationId}` has the same proof shape and mutually excludes a receipt. Per-record `syncStates/{group}/records/{recordId}` (milestones additionally scoped by parent) retains the last operation pointer and deleted flag. No growing array/document is introduced.
- Rules enforce owner-only access, strict fields/timestamps, exact receipt/state/business relationships, immutable createdAt, expected-version changes and retired identities. Parent/milestone/linked receipt rules remain conjunctive integrity checks: no zero-counter bypass, duplicate receipt/counter increment, or allocation/payment to a cancelled agreement. Secondary counter changes still require the original exact atomic financial relationship.
- Actual expense/income dates now have server calendar/leap-year/nonfuture Colombo validation, in addition to client validation. Crypto already had it. Regex alone is not described as calendar validation. Project/milestone due dates may be past or future, but must be real dates. The existing project/milestone monetary ceiling is preserved, not newly invented.
- A linked receipt inherits its existing agreement's immutable source even if that source was archived; this is not a new source choice. New standalone income/new agreements cannot select it. Recurring confirmation can review/select a new active category while historical expenses/confirmations remain unchanged.
- Persist before submission; reconcile receipt before mutable state; retain underlying Tasks across timeout/cancelled awaits. Same-record/project-family unresolved changes gate dependent operations. Identical interrupted acceptance reuses the original operation ID. An online server fence safely permits withdrawal/correction; auth/access/network or local-bookkeeping exceptions cannot prove rejection. Late uncertain callbacks cannot downgrade an acknowledged fence. Operation-derived state clears messages on acknowledgement/account changes.
- Old legacy JSON is retained during atomic queue promotion. Deletable pre-protocol creates whose document is absent stay uncertain because absence cannot distinguish never-sent from committed-then-deleted. Use history review and acknowledged online withdrawal before intentionally recording a new identity. Old crypto JSON without protocolVersion remains retryable/resolvable without reserialization. Corrupt private recovery data is retained with an error, never silently replaced by an empty queue.

## Rules and indexes for later publication

No rules/index were deployed. Complete current reviewed local rules: `firestore.rules`, SHA-256 **95D0DFF16C7814BD4434F76E14CCA034CB510B897BB73293053CA9241BD8ED29**. See the pre-publication review for safe install/startup order and old-client compatibility. Manual review/publication to `clarity-1c83e` / `(default)` is required before the new queue/catalogue protocol can run against live Firestore. Old deployed rules can deny reads of the new operation collections; that remains an uncertain operation, not successful sync or a dismissible rejection.

`firestore.indexes.json` is byte-identical to baseline. No new composite index is needed: monthly income/expense/crypto queries use one actual-date field/order; owner catalogues/state/receipt reconciliation use owner collections or document IDs. Preserve the existing COLLECTION recurringOccurrences index,templateId ascending + month ascending, already part of the accepted FR17 handoff. Current collectible parent totals intentionally read all owned parents once, excluding cancelled ones and without adding milestones again. All-history expense browsing intentionally reads complete owner history; no silent query limit can understate reporting.

## Earlier 3 November verification ledger (retained evidence)

All commands ran from `D:\clarity-finance`, with existing Java `D:\JAVA 21\jdk-21.0.6` and unchanged Gradle/build/dependency versions. Raw logs/private device diagnostics stay in ignored `build/final-requirements` and are excluded from the review archive.

- Focused runs: SubmissionRecoveryStoreTest (3); FinancialQueue/SessionSyncCoordinator/RetainedTasks (7 initial); integrated repository/form/DI selection (42); Catalogue/OrdinaryLifecycle (4); queue/admission/agreement (11); auth cancellation (2); delayed-account lists (5); legacy crypto (4); final queue/crypto/ordinary outcomes (15). Later full checks supersede earlier subset counts; they are not additive distinct-test counts.
- Earlier full command: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain`. Result: **254 tests, zero failures/errors**, successful app/test APK builds and lint **0 errors /22 warnings**. The last test-only strengthening was followed by `gradlew.bat :app:testDebugUnitTest :app:lintDebug --console=plain`, also successful; unchanged APK build results are reused.
- Rules: `firebase.cmd emulators:exec --only firestore --project demo-clarity-test "npm --prefix firestore-tests test"` first retained124 pass. With local Auth/Firestore running using an ignored test-only config, `npm --prefix firestore-tests test` finally passed **142**: all124 existing plus18 new. One new linked-receipt scope assertion found a real rules defect, fixed and rerun. A separate attempted replay reused a consumed SDK WriteBatch and was corrected to construct a new request; no policy was weakened.
- Android fake screens: explicit nine-class selection with `adb -s emulator-5560/5562 shell am instrument -w -r -e class <classes> com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner`: DiViewModelOwnershipTest,PaymentNavigationTest,CryptoNavigationTest,CryptoFeedbackUiTest,DashboardNavigationTest,GoalNavigationTest,RecurringNavigationTest,AuthAccessibilityTest,CatalogueNavigationTest. **20/20 API26**,71.566s; **20/20 API35**,97.652s. These use isolated fake bindings, not live repository acceptance.
- Actual SDK/local tests: each `FinalRequirementsDeviceTest#phaseA_seedCacheMeasureAndPersistOffline`, `#phaseB_processRestartReconnectAndReconcileExactlyOnce`, and `#phaseD_localEmailAuthenticationAndSignOut` was invoked separately with `-e auditLocal true` on both serials. Named Firebase app and a hard demo-project guard prevent default live clients. PhaseB is preceded by an actual targeted `am force-stop com.clarity.finance`; storage is retained. Ten immutable proposals survive, reconcile to10 expenses/10 receipts, then replay produces no extras. Ordinary baseline income/crypto/project counters are unchanged.
- API26 `#phaseC_timedExpenseCaptureAndFeedbackAfterFamiliarisation`: actual production navigation/form, real queue/DataStore, one warmup +10 measured saves, no automatic remote submission during timing. All11 proposals later acknowledge in the local emulator. First five whole-capture times934,915,866,834,866ms. Ten UI feedback observations150,133,99,116,132,116,100,84,150,100ms. These are controlled automation metrics, not human interaction speed.
- API26 cache1000 dashboard times(ms):388,270,295,254,245,253,246,262,289,267 (**10/10 <=2000**). API35:1272,2085,907,651,740,711,649,618,635,666 (**9/10 <=2000**). Both use real Firestore SDK cache,1000 expense rows plus separate income/crypto/current-parent baselines, exact expected totals and cached flags; no immediate fake substituted for cache. Durable-save timings alone are not mislabelled as UI feedback.
- Production: API26 Api26ProductionStartupTest with `-e api26ProductionAudit true` passed actual signed-out MainActivity startup and Activity recreation,2.972s. API35 `am start -W -n com.clarity.finance/.MainActivity` cold-start statusok,2339ms; UI dump confirmed Email/Password/Sign In/Register controls. Neither entered production credentials. Recreation is not process death.
- Final repository-only error/legacy-codec changes have focused/unit coverage; the screen selection uses unchanged fake graphs, so its passing evidence is reused for those branches rather than claimed as a new live/device error injection.

Device details: isolated API26 `emulator-5560`,Android8.0.0,x86_64; isolated API35 `emulator-5562`,Android15,x86_64,16KB pages. Both480x800 at240dpi (320dp width),font scale1.3, two virtual cores, SwiftShader. API26 uses1536MB RAM/API35 2048MB. Existing AVDs were reused with no wipe/uninstall and stopped normally afterward. Pixel_6a was not targeted; its storage/network/DNS workaround were untouched. Only API26 test-device Wi-Fi/data toggles were briefly attempted and restored to their original1/1 settings; they did not isolate all transports, so offline claims use the explicit SDK transport gate instead.

Initial failures were investigated rather than hidden: malformed-store fallback was exposed by a regression; an early fake Task exception cancelled its parent and was replaced with an independent suspending result; initial-account guard/rate-parser handling were fixed; Mockito nested/checked-exception stubbing and cancellation teardown helpers were corrected without weakening assertions; cache timing initially awaited income/surplus before crypto loaded and now waits for all independent sections. A Windows text helper temporarily damaged Unicode punctuation; it was restored and current screen assertions passed. Lint initially required explicit includeSubdomains=false on the debug loopback-only network allowance; fixed, not suppressed. The historical API35 ART/JIT crash remains unexplained. This run's20-case selection passed; later APK update caused a different system_server NoSuchMethodError in AbstractPerUserSystemService before instrumentation began. ActivityManager recovered and the targeted SDK retry passed; no app/build-stack workaround was invented.

Reused historical evidence, explicitly not rerun as live work: accepted pending-payment, crypto, reporting, savings and recurring live reports; previous API26/DI reports for unchanged design decisions; deterministic goal expiry and recurring rules/assertions are also included in the newly executed full U/R suites. Earlier 230-unit/124-rule milestones are superseded by this run's final automated results, not presented as new runs.

## Remaining work and manual acceptance

No FR01-FR21 implementation is intentionally left missing. Remaining technical acceptance limits are human task-speed/assistive-technology checks, genuine expired production credentials, broader device/process/network/account race permutations, unexplained API35 runtime instability, and separate publication/live acceptance. External assignment artefacts/submission remain unverified; this is not a 100% completion claim. No optional FR22-FR25 feature, visual redesign, notification service or new milestone was added.

After review and separate authorization/publication (no live writes authorized by this report):

1. Sign in to a designated test account; Home -> Sources,categories & recovery -> Add category/source. Confirm name/default, Save proposal, observe pending then confirmed. Rename; confirm previous amounts stay unchanged. Archive; verify absent from new selectors and visible in history. Do not archive a real unrelated choice for testing.
2. Dashboard -> Add Expense. Enter850,Transport,bank/card,nonfuture Colombo date,blank note. Save once/tap repeatedly; check one immutable record and pending/confirmed feedback. Income -> Add Income; confirm exact original amount/rate or actual credit. Inspect/edit/backdate/delete only authorized samples; verify old/new month totals and linked-receipt read-only project routing.
3. Pending Payments -> authorized sample -> Edit agreement/Add/Edit Milestone/Record Direct or Record. Check capacity and one receipt; no counter reset. Cancel an unpaid/partially-paid sample; preserve agreement/received values and release unpaid allocation only.
4. Crypto Results -> authorized gain/loss samples; check +60000-25000=+35000 and cash totals unchanged. Savings -> confirmed ledger contribution/withdrawal; recurring -> reviewed actual occurrence only. Do not invent additional live fixtures without authorization.
5. With cached confirmed targets, save authorized proposals offline. Confirm original totals remain. Force-stop/reopen preserving storage; reconnect while staying on Home. Verify same operation/record IDs acknowledge once, messages clear, and unrelated proposals remain isolated.
6. Switch accounts interactively; old rows/forms/messages disappear before new listeners load. Return and inspect only the original account's own proposals. Use safe online reconciliation/withdrawal for unknown outcomes; only a server-rejected proposal may be dismissed/corrected. Report access errors separately from connectivity.

## Current manifest and verification layers

Earlier 3 November evidence:254 unit tests,0 failures,0 errors; assembleDebug/assembleDebugAndroidTest successful; lintDebug0 errors/22 warnings;142 rules tests passing;20 isolated screen cases passing on each API; actual SDK cache/restart/Auth phases passing on both APIs; actual API26 UI feedback benchmark and production recreation passing. See the layer-specific limits above. All existing test cases remain; affected repository assertions were migrated to the new production queue/remote paths and strengthened with actual recovery retention.

`git diff --check` passed. Added files were separately checked for trailing whitespace; UTF-8/obvious-secret scans covered every manifest file. The four unrelated files, Firebase/local configuration, firebase.json, indexes and dependency/build files match baseline SHA-256. Index/staged diff remains empty and HEAD unchanged. No publication or Git mutation occurred. The updated protocol review ZIP contains the following87 accepted milestone files; each archived file is SHA-256 verified against its source. The final accepted artifact is clarity-finance-protocol-live-acceptance-review-20261104.zip; documentation receives only editorial sanitization during finalization. The publication review identifies the correction-only changes; private diagnostics and local configurations are excluded. Archive hashes/metadata are retained outside the archive to avoid a self-referential report hash.




- `FINAL_PROTOCOL_LIVE_ACCEPTANCE.md`
- `FINAL_PROTOCOL_PUBLICATION_REVIEW.md`
- `FINAL_REQUIREMENTS_AUDIT.md`
- `OFFLINE_SYNC_MATRIX.md`
- `app/src/androidTest/java/com/clarity/finance/CatalogueNavigationTest.kt`
- `app/src/androidTest/java/com/clarity/finance/FinalRequirementsDeviceTest.kt`
- `app/src/androidTest/java/com/clarity/finance/IncomeListAccessibilityTest.kt`
- `app/src/androidTest/java/com/clarity/finance/IsolatedAppGraph.kt`
- `app/src/androidTest/java/com/clarity/finance/PaymentNavigationTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/res/xml/local_emulator_network.xml`
- `app/src/main/java/com/clarity/finance/ClarityApplication.kt`
- `app/src/main/java/com/clarity/finance/data/local/SubmissionRecoveryStore.kt`
- `app/src/main/java/com/clarity/finance/data/model/Catalogue.kt`
- `app/src/main/java/com/clarity/finance/data/model/ExpectedPayment.kt`
- `app/src/main/java/com/clarity/finance/data/model/Expense.kt`
- `app/src/main/java/com/clarity/finance/data/model/Income.kt`
- `app/src/main/java/com/clarity/finance/data/model/MonthlyReporting.kt`
- `app/src/main/java/com/clarity/finance/data/model/RecurringExpense.kt`
- `app/src/main/java/com/clarity/finance/data/repository/CatalogueRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/CryptoRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FinancialDocuments.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FinancialOperation.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FinancialQueue.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirebaseAuthRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreCryptoRemote.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreCryptoRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreExpenseRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreFinancialRemote.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreIncomeRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestorePaymentRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreRecurringRemote.kt`
- `app/src/main/java/com/clarity/finance/data/repository/FirestoreReportingRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/IncomeRepository.kt`
- `app/src/main/java/com/clarity/finance/data/repository/SessionSyncCoordinator.kt`
- `app/src/main/java/com/clarity/finance/di/AppModules.kt`
- `app/src/main/java/com/clarity/finance/ui/CatalogueScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/HomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/crypto/CryptoScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/AddExpenseScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpenseFormContent.kt`
- `app/src/main/java/com/clarity/finance/ui/expenses/ExpensesScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/AddEditIncomeScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/income/IncomeListScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/AddPaymentScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ExpectedPaymentsScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/payments/ProjectDetailScreen.kt`
- `app/src/main/java/com/clarity/finance/ui/recurring/RecurringScreen.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/AddEditIncomeViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/AddExpenseViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/CatalogueViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/CryptoViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/DashboardViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/EditExpenseViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/ExpenseListViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/GoalViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/IncomeListViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/PaymentConfirmationViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/PaymentDetailViewModel.kt`
- `app/src/main/java/com/clarity/finance/viewmodel/RecurringViewModel.kt`
- `app/src/test/java/com/clarity/finance/data/local/SubmissionRecoveryStoreTest.kt`
- `app/src/test/java/com/clarity/finance/data/model/PaymentCapacityTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/AuthCancellationTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/CatalogueCacheReviewTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FinancialQueueTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FinancialRemoteFixture.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreCryptoRemoteTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreCryptoRepositoryTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestorePaymentRepositoryTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreRecurringRemoteTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/FirestoreReportingRepositoryTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/OrdinaryQueueRepositoryTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/ProtocolPublicationReviewTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/RecurringExpenseCompatibilityTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/RetainedTasksTest.kt`
- `app/src/test/java/com/clarity/finance/data/repository/SessionSyncCoordinatorTest.kt`
- `app/src/test/java/com/clarity/finance/di/AppModulesTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/AddExpenseViewModelTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/AgreementEditingTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/CatalogueViewModelTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/IncomeListViewModelTest.kt`
- `app/src/test/java/com/clarity/finance/viewmodel/OrdinaryRecoveryLifecycleTest.kt`
- `firestore-tests/final-requirements.test.js`
- `firestore-tests/test.js`
- `firestore.rules`
