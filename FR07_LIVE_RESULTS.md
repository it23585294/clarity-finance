# FR07 live acceptance results

Executed on 26 October 2026 against live Firebase project clarity-1c83e, database (default), on the existing Pixel_6a API 37 emulator. The user authorized labelled crypto samples and their edits/deletions. No live rules were deployed or changed by the agent.

This report omits account identity, sample record IDs/session identifiers and unrelated record IDs/payloads. Private server snapshots, recovery evidence, UI dumps and exact ordinary-finance baselines remain under ignored build directories.

## Baseline and method

Before the first sample write, an explicitly opted-in, read-only Android audit asserted the project ID, firestore.googleapis.com, TLS, Source.SERVER, no cache result and no pending SDK writes. It read crypto, income, expenses, projects/milestones and UID-scoped recovery.

Baseline: zero crypto records, zero crypto recovery attempts, five income receipts, two expenses, two projects, and one pre-existing pending expense recovery record. Existing financial fields/timestamps and unrelated recovery were preserved. All ordinary financial snapshots were compared with the original baseline; monetary validation used integer minor units and Decimal display.

Most writes used the real application UI and production repositories. To reproduce stale-version rejection without changing rules, a temporary, explicitly opted-in instrumentation actor changed only a labelled sample's note/operation ID/update timestamp while its old proposal was saved and the UI process was stopped. Its transaction asserted the current UID, live target, exact session prefix and sample ID, and verified direction, amount, date and createdAt remained unchanged. This is a real live conflict test, not a mocked rejection, and does not represent two physical devices.

## Actual results

| Scenario | Observed result |
| --- | --- |
| Live access and AT03 | Gain 60000 and loss 25000 produced month/YTD +35000.00, two distinct server IDs, and resolved timestamps. Rapid repeated Save produced one gain record. |
| Month backdating | Gain moved from October to September 30: October -25000.00, September +60000.00, 2026 YTD +35000.00. |
| Year backdating | Gain moved to December 31, 2025: 2026 YTD -25000.00 and 2025 +60000.00, with a December 31 cutoff. Moving it to January 1, 2026 restored 2026 YTD +35000.00. The date was then restored to October 26. |
| Confirmed amount edit | Gain 50000 produced net +25000.00; restoring 60000 returned +35000.00. Result ID and createdAt stayed fixed. |
| Pending edit | Offline proposal 60000 → 55000 retained the original +35000.00 confirmed/cached net. Reconnection confirmed the same result/operation and changed net to +30000.00; gain was restored to 60000. |
| Rejected edit | A note-only concurrent change made the queued edit stale. The UI showed Change rejected and the changed-elsewhere reason while net stayed +35000.00. Retry retained the original payload and was rejected again without a server overwrite. Dismissal removed only the local proposal. |
| Rejected deletion | A note-only concurrent change made the queued loss deletion stale. The loss remained, Deletion rejected appeared, and net stayed +35000.00. Dismissal preserved the confirmed server record. |
| Offline creation and real process restart | Gain 100, loss 40 and gain 20 were saved offline. After adb force-stop and offline relaunch, all three exact payloads, result IDs and operation IDs remained pending. |
| Reconnect and duplicate prevention | The same three saved IDs/op IDs became exactly three server documents and added +80.00, producing +35080.00. Another process restart/reread changed no document, timestamp or contribution. |
| Dismiss deletion | Keep Result changed no sample fields, timestamps or totals. |
| Pending/confirmed deletion | Offline deletion of the 25000 loss retained its contribution and +35080.00 net. Reconnect removed that one record once, resulting in +60080.00 and four remaining samples. Rereading changed nothing. |
| Account B isolation | After interactive sign-in, a different Firebase UID had zero server crypto records and month/YTD 0.00. No A pending proposal/history appeared. A's saved LKR 1 account-switch payload remained identical and UID-scoped. |
| Return to account A | The original UID's saved +1.00 sample resumed once with the exact saved result/operation IDs, producing +60081.00. Only that extra sample was then deleted; a fresh server read restored the original four samples and +60080.00 with no unresolved crypto attempts. |

The original gain/loss IDs and creation timestamps remained stable through edits and conflicts. No unrelated income, expense, project, milestone or receipt changed. The original expense recovery payload/status also remained unchanged. No PERMISSION_DENIED occurred in the successful live CRUD/conflict checks; stale-version rejection was reported separately from connectivity.

## Connectivity findings

The initial live read failed before writing samples because the restarted AVD had lost its DNS workaround. The host resolved Firestore; the emulator's default DNS alias did not. Relaunching the same AVD with the working host resolver restored real Source.SERVER access without clearing app data. Recovery bytes were identical before/after the restart.

Later Logcat isolated an additional problem: attempts to Firestore's IPv6 address returned ECONNREFUSED while IPv4 worked. A temporary loopback-only DNS relay omits AAAA answers and forwards IPv4 queries to the working host resolver; only the AVD is launched with that relay as its DNS server. The Firebase hostname, TLS verification, project/database and host DNS settings are unchanged. Data/recovery again survived the graceful AVD restart unchanged.

The relay and emulator launch are temporary local diagnostics under build. The relay must remain running while this AVD uses it; it can be stopped after the emulator is closed. Cold server reads still sometimes timed out initially and succeeded on a subsequent read. Such failures were retained as connectivity failures, never accepted as successful server acknowledgements. The read-only audit now retries only UNAVAILABLE/DEADLINE_EXCEEDED up to three times within its existing overall timeout; every attempt remains Source.SERVER, and permission failures are not retried as transient failures. SDK metadata and successful server reads, not IP ping, supplied acceptance evidence.

## Verification performed during this acceptance run

- Built the extended read-only audit with :app:assembleDebugAndroidTest and installed the test APK with adb install -r, preserving application data.
- Built a local-only conflict actor through an ignored Gradle init script; no application dependency/build configuration was changed.
- Drove real production screens with ADB taps/text input. Used adb shell am force-stop com.clarity.finance for actual process-restart checks and emulator-only Wi-Fi/mobile-data toggles for offline cases.
- Executed opted-in LiveFirestoreAuditTest against live Firestore for server checkpoints; added crypto/expenses and crypto recovery types to its existing read-only output.
- Validated the private checkpoints, exact sums, identity/timestamp stability and unrelated-data equality with a private one-off diagnostic validator (not retained as a reusable test). Main-checklist validation passed across 21 server checkpoints, including the baseline/connectivity checkpoint.
- Interactive sign-ins and four additional server snapshots verified account isolation, original-owner recovery and cleanup, for 25 successful live snapshots overall. Failed/time-limited read attempts are separate from these successes.
- Restored the normal read-only audit APK after using the local conflict actor; the temporary writer is not included in the final installed test APK.

Principal commands used:

    .\gradlew.bat :app:assembleDebugAndroidTest --console=plain
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w -r -e class com.clarity.finance.LiveFirestoreAuditTest -e liveAudit true -e auditLabel fr07-<checkpoint> com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner
    adb shell am force-stop com.clarity.finance
    adb shell svc wifi disable
    adb shell svc data disable
    adb shell svc wifi enable
    adb shell svc data enable

The generated conflict actor was invoked only with explicit liveCryptoConflict opt-in, the baseline owner UID, and an existing labelled sample ID/prefix. Raw arguments containing identity are retained only in local diagnostics.

Earlier 101 unit tests, 84 Firestore emulator tests, app/test builds, lint (0 errors/20 warnings), and isolated Android navigation remain separately recorded in CRYPTO_RESULTS.md. They were not rerun to claim this live acceptance. The live results above are independent of mocked/emulator evidence.

## Sample inventory

The authorized samples used a unique FR07 acceptance session prefix and the run date. Exact notes, document IDs and operation IDs remain in private evidence. This is the inventory at the end of that run, rather than a new server read during finalization.

| Sample role | Signed LKR | End-of-run state |
| --- | ---: | --- |
| GAIN, including concurrent note change | +60000.00 | Confirmed, retained |
| OFFLINE-1 | +100.00 | Confirmed, retained |
| OFFLINE-2 | -40.00 | Confirmed, retained |
| OFFLINE-3 | +20.00 | Confirmed, retained |
| LOSS, including concurrent note change | -25000.00 | Confirmed deleted |
| ACCOUNT-SWITCH | +1.00 | Resumed once under A, then confirmed deleted |

Four confirmed server samples total +60080.00. Both the original -25000.00 loss and the temporary +1.00 account-switch sample are confirmed absent. No unresolved crypto operation remained at the end of the live run.

## Remaining limits and scope

All requested live scenarios above were executed. A deliberately in-flight cross-account completion race and an OS low-memory kill were not forced; restart used a real force-stop. No live permission-denied write was deliberately manufactured: observed rejected proposals were stale-version conflicts. Hilt/Koin migration and API 26 runtime verification remain outstanding assignment tasks outside this milestone.

Emulator networking showed occasional cold-read timeouts despite the temporary DNS/IPv4 workaround. During this live run, a stale local-save banner could still say awaiting confirmation after rows were confirmed; row status, server metadata and the audits determined actual completion. The subsequently accepted focused correction derives feedback from current operations/recovery and clears only matching completed operations, preserving other pending/rejected/unknown outcomes and clearing messages on account changes.

That later correction passed **111 unit tests** (the earlier 101 plus 10 feedback regressions), assembleDebug, assembleDebugAndroidTest and lintDebug (0 errors, 20 warnings). Three isolated Android tests passed at 320 dp/API 37: feedback transitions, crypto navigation and existing payment navigation. These tests used fake repositories and created no live records. They are separate from the **25 earlier successful live server snapshots** above. Live interruption/account-switch scenarios were not rerun after the banner correction. The unchanged 84-test Firestore emulator result was reused.

Application logic, rules, dependencies and Firebase targets were not changed during live acceptance. No dashboard, goals, recurring expenses or optional features were added. No commits, pushes or deployments were performed during that live run. Subsequent commit preparation changed only documentation, used the applicable recorded verification and made no live-data or deployment changes.
