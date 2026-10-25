# Pending Payments and Project Milestones repairs

The completed repairs were applied to the existing project after reviewing an unverified candidate patch. Existing staged and unstaged work was retained. The candidate was not extracted over the repository. No dependency upgrades were needed.

This report is sanitized for source control. Private record IDs, account details, workstation paths, network addresses, and raw database/cache evidence remain in local diagnostics outside the committed reports.

## Resulting behavior

- Direct payments omit the optional milestone query argument; milestone payments supply an encoded string. The literal null route segment was removed.
- All money calculations use integer minor units and exact arithmetic. Display uses BigDecimal. Available capacity subtracts milestone allocation and direct receipts.
- Creation reconciles existing matching operations before writing. A retry cannot reset paid counters or allocate twice.
- Recovery records are scoped by UID, operation type, and record ID. Saved operation payloads are immutable; malformed recovery reads fail visibly.
- Account and target generations reject stale results. Suspended project recovery is cancelled on account changes.
- Save, load, payment, milestone, and cancellation waits are bounded. A timeout means an unknown outcome and retains the original retry identity. CancellationException propagates.
- Pending writes, cached snapshots, and server-confirmed records missing timestamps have different feedback. Device time never substitutes for a missing server timestamp.
- Milestone recovery precedes capacity validation, including when an earlier creation exhausted capacity. Retry displays and locks the saved details.
- Milestone cancellation preserves agreed/received amounts and releases only unpaid allocation. Repeating cancellation cannot release it again.
- Receipts and balance changes remain atomic. Linked income is read-only in the UI and protected from independent edit/delete by rules.
- Controls wrap, project details scroll, forms accommodate keyboards, and loading/errors/recovery have explicit feedback.

## Recorded verification

These checks were executed during repair and publication review, rather than inferred from compilation:

| Check | Recorded result |
| --- | --- |
| Android unit tests | 73 passed |
| Debug application and instrumentation APK builds | Passed |
| lintDebug | 0 errors, 20 warnings |
| Final Firestore emulator suite | 76 passed, including all 58 earlier tests |
| Android navigation at 320 dp | Passed through production navigation with isolated repositories |
| Opted-in live audit | Initial inventory and ten server checkpoints passed |
| Live UI checklist | Creation, direct/milestone payments, unpaid/partial cancellation, and project cancellation passed |

The online checklist did not simulate live network interruption or account switching. Coroutine unit tests with suspending fakes cover timeout/retry and stale account/target results separately.

From the repository root:

    .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain

Run the rules suite from its own directory because its rules path is relative:

    Push-Location firestore-tests
    npm.cmd test
    Pop-Location

## Connectivity and acknowledgement diagnosis

The Android build uses live Firebase project clarity-1c83e, default database, with TLS at firestore.googleapis.com. It does not automatically connect to the local emulator. Rules tests explicitly use demo-clarity-test at 127.0.0.1:8080.

The host resolved Firestore while the emulator DNS alias failed. A graceful restart of the same AVD with the host's currently working resolver and a cold boot fixed DNS without clearing app data:

    -avd <existing-avd> -dns-server <working-host-resolver> -no-snapshot-load -no-snapshot-save

This is a launch-only workaround. Stop the existing AVD gracefully first, preserve its data, and reassess the resolver after host network changes. No host networking or persistent AVD configuration was edited.

Once DNS worked, live project requests returned PERMISSION_DENIED. The published rules at that stage lacked project/milestone coverage and linked receipt fields. This was separate from DNS/UNAVAILABLE and navigation crashes. A fresh authenticated expense snapshot confirmed live connectivity.

The user subsequently published the reviewed rules. Live project reads and the online checklist then succeeded. Earlier rejected writes were not treated as successful acknowledgements or automatically recreated.

## Manual acceptance checklist

Use live sample records only when their income entries have been explicitly authorized.

1. Pending Payments → Add Payment → Project: save LKR 1000.00 and verify acknowledgement and persistence after reopening.
2. Record Direct → Confirm Payment: record 200.00; verify one receipt and 800.00 free capacity.
3. Allocate a 400.00 milestone and receive 150.00 against it. Verify total received 350.00, outstanding 650.00, and free capacity 400.00.
4. Cancel the partial milestone: preserve agreement 400.00/received 150.00, release 250.00, and verify free capacity 650.00. Add/cancel an unpaid 100.00 milestone; capacity returns to 650.00.
5. Dismiss a cancellation and verify no write. Confirm project cancellation; retain history and remove recording controls. Reopen and compare IDs, counters, receipt counts, and recovery.

See [the rules review](RULES_PUBLICATION_REVIEW.md) and [live acceptance evidence](LIVE_VERIFICATION.md).
