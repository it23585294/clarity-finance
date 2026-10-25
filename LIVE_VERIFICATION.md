# Live acceptance verification

The accepted checklist ran on 26 October 2026 against clarity-1c83e, default database, using the existing signed-in Android session. The user authorized a labeled LKR 1000.00 project and LKR 200.00 direct plus 150.00 milestone income entries.

This report omits private account details, record IDs, exact record labels/times, and workstation/network information. Original reports and raw evidence remain local and untracked.

## Inventory and audit method

Before any live write, both previously rejected project IDs were absent on the server and no saved project/milestone/payment/cancellation attempt remained. An existing cancelled project, three standalone receipts, and an unrelated pending expense recovery record were preserved. Rejected projects were not recreated from incomplete payloads.

The read-only Android audit asserted the expected project, TLS endpoint, Source.SERVER, no cache result, and no pending writes. It used the existing owner session without exporting credentials or switching endpoints. All checklist writes used the existing app UI.

## Exact observed balances

Values are LKR; validation used integer minor units and Decimal display.

| Completed step | Received | Allocated | Outstanding | Unallocated | Linked receipts |
| --- | ---: | ---: | ---: | ---: | ---: |
| Create 1000.00 project | 0.00 | 0.00 | 1000.00 | 1000.00 | 0 |
| Direct receipt 200.00 | 200.00 | 0.00 | 800.00 | 800.00 | 1 |
| Allocate milestone 400.00 | 200.00 | 400.00 | 800.00 | 400.00 | 1 |
| Milestone receipt 150.00 | 350.00 | 400.00 | 650.00 | 400.00 | 2 |
| Dismiss cancellation | 350.00 | 400.00 | 650.00 | 400.00 | 2 |
| Cancel partial milestone | 350.00 | 150.00 | 650.00 | 650.00 | 2 |
| Add unpaid milestone 100.00 | 350.00 | 250.00 | 650.00 | 550.00 | 2 |
| Cancel unpaid milestone | 350.00 | 150.00 | 650.00 | 650.00 | 2 |
| Cancel project | 350.00 | 150.00 | 650.00 | 650.00 | 2 |
| Reopen and reread server | 350.00 | 150.00 | 650.00 | 650.00 | 2 |

The paid milestone retained agreement 400.00/received 150.00 and released 250.00. The unpaid milestone retained agreement 100.00/received 0.00 and released 100.00. Both milestones and the project ended cancelled with receipts/history retained and recording controls removed.

## Results and limits

- Initial inventory and ten live checkpoints passed.
- One sample project, two stable milestone IDs, and two unchanged linked receipt IDs remained. Reopening produced no extra records or increments.
- Received 20000 + 15000 exactly matched 35000 linked receipt minor units. Retained allocation was 15000; free capacity was 100000 - 15000 - 20000 = 65000.
- Dismissing cancellation changed no fields, server timestamps, or receipts.
- Existing records and the pending expense recovery payload remained unchanged.
- No unresolved project/payment/milestone/cancellation attempt remained.
- Monitored Logcat had no new permission, unavailable/host-resolution, write-failure, or fatal-exception messages during the checklist.
- First instrumentation startup after test-APK installation sometimes returned Process crashed before a result. Subsequent read-only runs passed; no corresponding navigation/payment failure occurred. This startup issue was not isolated further.

This online checklist did not simulate live network interruption or account switching. Coroutine tests with suspending fakes verify timeout/retry and stale account/target behavior separately.

## Reusable read-only audit

[LiveFirestoreAuditTest.kt](app/src/androidTest/java/com/clarity/finance/LiveFirestoreAuditTest.kt) requires liveAudit=true and an existing signed-in session. Without explicit opt-in it skips before Firebase access. It never signs in or writes Firestore data. Optional checkProjectIds takes comma-separated IDs supplied locally; none are embedded in the source.

    .\gradlew.bat :app:assembleDebugAndroidTest --console=plain
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w -r -e class com.clarity.finance.LiveFirestoreAuditTest -e liveAudit true -e auditLabel review -e checkProjectIds "<optional-project-ids>" com.clarity.finance.test/androidx.test.runner.AndroidJUnitRunner

JSON is saved to the app cache as live-firestore-<label>.json. Treat runtime output as private and keep copies in an ignored diagnostic directory. Never add database/cache exports or raw audit output to Git.

During acceptance, the test APK built, eleven live read-only invocations passed, exact-balance/duplicate checks passed, and default invocation without opt-in skipped. The separate 320 dp navigation test uses isolated repositories and does not establish live payment success.

No Firebase deployment or application-feature/dependency change was made during acceptance. See [the repair report](REPAIR_VERIFICATION.md) and [rules review](RULES_PUBLICATION_REVIEW.md).
