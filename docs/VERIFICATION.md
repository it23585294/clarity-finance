# Verification record

This is the consolidated evidence for Clarity Finance. It replaces milestone-by-milestone reports in the working tree. Application evidence applies to the protected source at `70b1e9e46944699da838101bd075afd08dbaa8fe`; the report/README consolidation changes no application, test, dependency, security-rule, index or Firebase behaviour. Earlier reports remain available in Git history. Counts below are recorded executions, not tests rerun for prose changes.

## Evidence levels and latest results

| Evidence | Actual result / interpretation |
|---|---|
| Latest full Android unit suite | **292 passed**, 0 failures/errors/skips. Recorded `:app:testDebugUnitTest`; domain, repository, lifecycle, DI, presentation and history assertions. |
| Final focused unit run | **51 passed**, overlapping the 292. Selectors: HistoryOrderTest, DashboardViewModelTest, CryptoViewModelTest, FirestoreReportingRepositoryTest and OrdinaryQueueRepositoryTest. |
| Latest app/test build and lint | `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` passed; **0 lint errors / 16 warnings**. Full combined unit/build/lint run completed in 2m34s. |
| Final isolated Android checks | **8 API26 cases passed**, 22.569s. CrossHistoryOrderingTest, HistoryMetadataTest and selected DashboardNavigationTest cases; fictional repository graphs rendering production navigation/screens. |
| Local Firestore rules | **147 passed**, full existing suite, reused from protocol publication review because the rules/protocol remained unchanged. Not new cloud checks. |
| Earlier design/ownership coverage | Isolated API26 auth, suspended submission, visibility/password-state exclusion, Koin ownership/SavedStateHandle, Back/context, native keyboard, 320dp/system2.0x and light/dark checks. Separate earlier runs overlap later ones and are not added as unique tests. |
| Earlier production API26 | Android8.0.0/API26 AOSP x86_64, production signed-out startup and separate Activity recreation passed. The compatibility run used480x800/240dpi/320dp and130% text; later UI runs used documented normal and2.0x configurations. Recreation is not process death. |
| SDK/cache/performance | Real local demo Firebase SDK/cache tests with1,000 expenses: cached data-ready <=2s in10/10 API26 and9/10 API35 trials; retained API35 miss2,085ms. Local feedback <=1s in10/10. These are historical readiness measurements, not fresh final-render/human timings. |
| Historical bounded live acceptance | Feature-specific server-backed comparisons and later app-wide protocol acceptance on an inspected separate device/designated account. No new live sessions during report consolidation. |

Security rules under review have SHA-256 `95D0DFF16C7814BD4434F76E14CCA034CB510B897BB73293053CA9241BD8ED29`. Local emulator success does not prove byte equality with deployed rules. Authenticated administrative comparison was unavailable; published behaviour was checked within bounded live scope.

## Requirement coverage

V means implemented and verified for the stated scenarios; L means implemented with verification limitations. These are scoped statuses, not universal acceptance or a predicted mark. Source paths are below `app/src/main/java/com/clarity/finance/`.

| Requirement | Production path and substantive evidence | Status |
|---|---|---|
| FR01 auth | FirebaseAuthRepository, AuthViewModel, ui/auth; validation/cancellation units and isolated suspended login/register/logout/back-stack checks | L: no fresh final live registration run |
| FR02 ownership | Owner repositories, SubmissionRecoveryStore, FinancialQueue, SessionSyncCoordinator and owner rules; negative rules, delayed old result and live basic account switch | V; not every in-flight live race |
| FR03 income | ui/income, AddEditIncomeViewModel, FirestoreIncomeRepository; create/edit/delete/backdate and protected linked inspection | V |
| FR04 conversion | Income/model validation; USD41 at300 gives12,300 and salary128,000 gives140,300; fixed basis/rate, invalid rate rejection | V |
| FR05 pending | ExpectedPayment and MonthlyReporting; pending excluded from income, cancelled parents excluded, parent counted once and currencies grouped | V |
| FR06 agreements | Payment ViewModels/repository, FinancialRemote; atomic receipts/counters, capacity, edits/cancel and nullable milestone navigation; bounded live receipts | V |
| FR07 crypto | CryptoReporting/repository/ViewModel; +60,000-25,000=+35,000, signed loss, cutoff, pending-original retention and overflow inspection | V |
| FR08 capture | AddExpenseViewModel and form; repeated Save, UID draft/recreation, labels/native IME; five correct human captures | L: exact timing absent |
| FR09 corrections | Expected versions and protected editors; category/date movement, deletion and linked income safeguards; recurring consumed history remains | V for supported actions |
| FR10 classification | Catalogue/form models; per-entry type and stable category IDs; correction changes breakdown once | V |
| FR11 history | ReportingRepository and DashboardViewModel; month/category/context, realtime edits/deletes/backdating, optional absent expense note compatibility | V |
| FR12 dashboard | MonthlyReporting;1,000-1,500=-500, unavailable inputs cannot fabricate surplus; crypto/pending/goal separate | V |
| FR13 insights | Exact category sum,40,000/10,000=80%/20%, complementary rounding, zero state and labelled bars | V |
| FR14 goal | Fixed savingsGoals/active identity; positive target/opening/deadline editing preserves start/ledger; parent/child bypass rules reject | V |
| FR15 ledger | Atomic entries/counter/receipts;11,200->21,200->20,000, concurrent nonnegative constraints, correction/delete and uncertain outcomes | V |
| FR16 schedule | SavingsMath; original anchor/leap clamp, deadline/today inclusion, ceil cents, N=0, reached and overfunded | V |
| FR17 recurring | Stable occurrence identity and atomic confirmation; rent34,000 once, unconfirmed utilities0, changed actual once; cancel and consumed deletion history | V |
| FR18 realtime | Listener metadata/cleanup, independent reporting and coordinator; SDK/live Home reconnection | L: process/session dependent |
| FR19 offline | Durable immutable UID/type/record proposals, retained Tasks/proofs/fences and restart evidence | L: not every action/race device-tested |
| FR20 errors | Validation, empty/cached/missing, rejected versus uncertain, overflow and local cleanup handling | L: human comprehension/TalkBack incomplete |
| FR21 catalogues | Stable add/rename/archive, unknown labels, historical corrections and inherited archived agreement sources; production-path rules/live | V |

FR21 exact source wording is "Allow source/category additions, renaming and archiving" (requirements PDF p4). It is included despite Should priority. FR22-25 are outside scope. NFR01 formal stopwatch target remains unverified; NFR02 uses historical measurements; NFR03 reliability has bounded restart evidence; NFR04 exactness has domain assertions; NFR05 owner/password protections have rule/lifecycle checks; NFR06 actual TalkBack remains unexecuted; NFR07 honest gaps, NFR08 architecture/Koin, NFR09 queries/listeners and NFR10 recoverable states have source/assertion evidence within these boundaries.

## Offline action matrix

All supported financial proposals persist before submission, keep immutable payload/IDs and originating UID, reconcile matching receipts first and preserve confirmed contributions during pending/rejected edits or deletions. A confirmed baseline/version is needed for an offline correction; uncached data does not become a fictional baseline. Workers run app-wide for the eligible signed-in process/session, not from a required feature-screen visit.

| Action | Persist/restart/reconnect and conflict behaviour |
|---|---|
| Expense create | FinancialQueue CREATE; stable target/operation; no confirmed expense before acceptance. |
| Expense edit | FinancialQueue UPDATE; old confirmed value retained, expected-version conflict explicit. |
| Expense delete | FinancialQueue DELETE; original contribution retained pending acknowledgement; retired identity prevents recreation. |
| Ordinary income create | Same queue; exact original/conversion payload persisted, separate from project-linked receipts. |
| Ordinary income edit | Same queue/version protection; inherited archived reference may be retained. |
| Ordinary income delete | Same queue; protected linked receipts cannot be independently removed. |
| Project/standalone create | Durable immutable agreement; parent initial counters cannot be bypassed. |
| Project edit | Expected version/capacity, no counter reset or new operation identity on retry. |
| Project cancel | Atomic status/allocation policy, accepted receipts preserved, collectible pending excluded. |
| Milestone create | Parent dependency and available allocation checked atomically. |
| Milestone edit | Expected version and parent capacity; no double allocation. |
| Milestone cancel | Preserve agreed/received, release unpaid allocation; no negative parent balance. |
| Direct/milestone receipt | Immutable linked identity/proof and parent counters; reconcile before mutable status/capacity. |
| Crypto create/edit/delete | Dedicated recovery integrated with session worker; old confirmed signed contribution retained. |
| Goal create/edit | Dedicated goal receipts and versions; authoritative expiry rejection/fence before safe deadline replacement. |
| Goal contribute/withdraw | Stable entry/operation, two-way aggregate relationship and nonnegative savings. |
| Goal entry correct/delete | Exact delta and expected versions; cannot consume confirmed funds twice or produce negative balance. |
| Recurring template create/edit/cancel | Dedicated immutable recovery; pending confirmation payload cannot silently change; cancellation preserves accepted history. |
| Recurring occurrence confirm | Stable deterministic occurrence, atomic expense link, matching reconciliation/conflicting result; consumed even after actual deletion. |
| Category/source add/rename/archive | Owner-scoped queue, stable references and server-enforced active/retained-selection rules. |
| Cached lists/history/dashboard | SDK cache/confirmed projections labelled; absent cache is unavailable, not confirmed zero. |

Process death preserves saved proposals; a later eligible app start resumes work. No immediate background/force-stopped execution guarantee, WorkManager/service or offline authentication is claimed. The five-second retry loop is not a latency SLA. Account changes cancel old workers, clear feedback and ignore stale callbacks; old proposals remain with their UID. Corrupt recovery is surfaced/retained. Access errors cannot prove an earlier outstanding attempt never committed; definitive correction/dismissal requires authoritative resolution.

## Historical live scope and human feedback

Bounded acceptance used authorized fixture labels and private server/local-recovery baselines. It verified catalogue add/rename/archive, ordinary create/correct/delete, archived inherited-source direct/milestone receipts, agreement edits/cancellations, recurring actual correction/backdate/delete/consumed history, offline storage-preserving restart and automatic Home reconnection. A basic interactive account switch retained the originating proposal. Original records and unrelated recovery were compared before/after.

Final protocol fixture-only deltas: **ordinary received income +LKR350, expenses0, recorded surplus +LKR350** in the receipt month. Crypto, savings and current collectible pending were unchanged. Private account totals/IDs are excluded. Earlier goal/crypto/recurring acceptance provides its own bounded evidence, not permission for further writes or cleanup.

The user familiarised, then made five correct human captures:400,850,100,250,1,200, blank notes, correct classifications and distinct IDs; total2,800. Read-only fixture snapshots verified correctness. The user looked at a clock approximately, not a strict stopwatch; no raw durations or formal >=4/5 within10s pass exist. No repeat run is requested. The user found History ordering unclear; the final immutable creation-time correction addresses it.

## Remaining limitations

- Actual TalkBack/focus-order/dialog listening and broader human status comprehension are unexecuted; automation is not human accessibility evidence.
- API35 has unresolved instrumentation ART/JIT/Compose SIGSEGV and separate Google/system-process/DeadSystem failures. Successful individual runs do not erase failures. A later attempt could not boot:12,288MB required,2,326.98MB available; no app/test ran then. No clean final full-suite API35 pass is claimed.
- No fresh final-render benchmark; historical SDK readiness is not perceived frame latency.
- Deployed-source equality was not independently authenticated. Broader live legacy migration, all server-in-flight account races and live deadline expiry were not executed; deterministic/local emulator coverage remains separate.
- Lint has16 warnings. There is no universal crash-free/security certification or guaranteed cloud/background timing.

## Safe reuse and this consolidation

See the [root README](../README.md) for setup and guarded execution. The protected source/test/tooling hash comparison, document checks and cleanup inventory are recorded in [REPOSITORY_CLEANUP.md](REPOSITORY_CLEANUP.md). No app/rules suite, live session or device command was run solely for this documentation consolidation. Updated prose must not be represented as a new acceptance run.
