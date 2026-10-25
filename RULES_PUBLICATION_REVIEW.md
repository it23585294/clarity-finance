# Firestore rules publication review

The reviewed source is [firestore.rules](firestore.rules), for clarity-1c83e, database (default). The user reported publishing it; [live verification](LIVE_VERIFICATION.md) confirmed the online operations. The agent performed no Firebase deployment.

Reviewed SHA-256:

    1EB3469111132FB11F14247CF0B64A58F9D5DEC7EBAC7BB4956C1D27DE5DBC55

## Demonstrated defects and corrections

The baseline passed 58 tests. Regressions against unchanged rules produced five passes and twelve failures, demonstrating defects before editing:

| Defect | Correction |
| --- | --- |
| Zero-counter parents could seed pointers and authorize unallocated children or uncredited receipts, including combined creation. | Production creation field allowlists; milestones require an existing active parent and an exact allocation increase. |
| Stale matching receipt pointers could authorize a receipt/increment without corresponding counter changes. | Existing records and matching before/after parent/child deltas are required in the same atomic operation. |
| Cancelled projects could accept allocations, milestones, or agreement edits. | Parent activity is checked before/after creation/edit. Existing cancellation may only release unpaid allocation. |
| Standalone income could carry an orphan milestone link. | Non-null milestone links require a valid parent link and atomic validation. |
| Repository blank expense notes were omitted but rules required them. | Notes may be absent; present notes remain typed/bounded. Owner, schema, amount, category, channel, date, and timestamp checks remain. |

Owner-only access, linked receipt immutability, integer minor units, receipt uniqueness, counter bounds, and paired allocation/payment changes remain enforced. No broad allow rule was introduced.

## Production write compatibility

- Expense owner CRUD accepts serialized payloads with or without optional notes and protects creation time.
- Standalone LKR, USD/rate, and USD/actual-credit income retain owner CRUD. Linked receipts remain independently immutable.
- Parent creation requires zero initial allocation/received counters. Matching retries reconcile by reading before writing.
- Direct receipts pair with exact direct-counter increases within capacity, matching source/currency and owner.
- Milestone creation/edit pairs child IDs, operation IDs, and allocation deltas on an active project.
- Milestone receipts pair with parent and child received-counter increases.
- Cancellation preserves agreed/received history and releases only unpaid allocation. Repeated release is rejected.
- Anonymous and cross-user reads, queries, and atomic writes remain rejected.

## Actual tests

From firestore-tests, with the emulator at 127.0.0.1:8080:

    npm.cmd test
    npm.cmd test -- --grep "publication regressions"

| Executed check | Result |
| --- | --- |
| Baseline full suite | 58 passed |
| Reproductions against unchanged rules | 5 passed, 12 failed |
| First focused run after corrections | 17 passed |
| Final full suite | 76 passed: original 58 plus 18 regressions |

All successful runs exited 0. No existing tests were removed or loosened. Coverage includes production-shaped transactions, receipt replay, duplicate increments, overpayment, cancellation replay, unpaid release, standalone compatibility, owner queries, and numeric/timestamp limits.

## Console publication and maintenance

1. Open [Firebase Console for clarity-1c83e](https://console.firebase.google.com/project/clarity-1c83e/overview) and confirm the project ID.
2. Open Firestore, choose (default), then Rules.
3. Save the published source and replace the complete editor contents with the reviewed local file. Review validation and the replacement.
4. Publish only when separately authorized. [Firebase Console documentation](https://firebase.google.com/docs/firestore/security/get-started#use_the_firebase_console).
5. After propagation, repeat [the manual checklist](REPAIR_VERIFICATION.md). Publication does not restore rejected writes; reconcile server state and saved attempts before recreating records.

Keep published policy aligned with this source. Later policy edits require emulator tests and a newly recorded artifact hash.
