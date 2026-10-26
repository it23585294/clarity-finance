# FR07 publication review and live acceptance plan

Prepared 2026-10-26 against the actual working tree based on 7c23b31. This preserves the original review and execution plan; the later authorized run is recorded in [FR07_LIVE_RESULTS.md](FR07_LIVE_RESULTS.md), and the subsequent banner correction in [CRYPTO_RESULTS.md](CRYPTO_RESULTS.md). No application, rule or test correction was needed during this review. Existing uncommitted FR07 work was preserved.

## Exact publication artifact

Publish the complete repository-root [firestore.rules](firestore.rules) to project **clarity-1c83e**, Firestore database **(default)**. Do not publish only the crypto fragment.

SHA-256 of the reviewed local file bytes:

    2DDCFE4A8EDC356D6F17C52E26BB1A7655E259FA85F00C54850FEE7584D2EE5D

Verify locally before copying:

    Get-FileHash .\firestore.rules -Algorithm SHA256

For manual publication: open the Firebase Console, confirm clarity-1c83e, select Firestore Database → (default) → Rules, retain a copy of the currently published rules, and replace the complete editor contents with this file. Review the replacement and publish manually when ready. Console/export line-ending normalization may change an exported byte hash; compare the complete source as well. No deployment was performed by the agent.

The app continues to use live clarity-1c83e. The demo-clarity-test emulator is separate and its passing tests do not prove live publication or connectivity. No new composite index is required: retain the default ascending single-field index on cryptoResults.resultDate. The pre-existing firebase.json reference to a missing firestore.indexes.json remains unchanged.

## Payload and security review

Production writes originate in FirestoreCryptoRemote.apply, in transactions, at users/{uid}/cryptoResults/{resultId}. No crypto payload is written to ordinary financial collections.

| Concern | Actual payload and reviewed enforcement |
| --- | --- |
| Owner isolation | Read/query/create/update/delete require authentication and request.auth.uid == userId. Production checks the active UID before starting and again inside transaction callbacks. Unauthenticated/cross-user CRUD and collection queries have emulator coverage. |
| Creation schema | Exactly direction, amountMinor, resultDate, note, lastOperationId, createdAt and updatedAt. UI-only ID, sync, error and deleting fields are not serialized to Firestore. Rules require all seven fields and reject additional fields, including link/sign/total/currency fields. |
| Direction and amount | gain/loss plus positive integer LKR minor units. No persisted signed amount or total. Client parses unsigned plain decimals exactly; rules validate the resulting positive Firestore integer, not the original input string. No arbitrary ceiling below the representation's integer limit. |
| Optional note | Blank form notes serialize as an empty string. Rules require a string of at most 500 characters, matching valid repository payloads. |
| Operation ID | Production generates UUIDs. Rules require a nonempty string of at most 128 characters. Stable IDs and matching replay reconciliation are repository responsibilities; rules are not an operation-history ledger preventing an owner deliberately creating distinct records. |
| Creation timestamps | Both timestamps are server transforms and must equal request.time on creation. |
| Updates | transaction.update sends the six mutable fields, omitting createdAt. Rules evaluate the complete post-update document, require updatedAt == request.time, and preserve createdAt exactly. Optimistic version checks happen in the repository transaction. |
| Deletion | An owner transaction deletes the document without a data payload. Rules permit owner deletion; repository checks the original updatedAt, or reconciles an already absent document. |
| Date shape | Rules require zero-padded YYYY-MM-DD, then separately parse components. Regex alone is not treated as calendar validation. |
| Calendar | Years start at 1; month/day bounds are checked, including 30-day months and Gregorian leap years: divisible by 400, or divisible by 4 but not 100. Valid 2000/2024 leap days and invalid 1900/2023 leap days have emulator coverage. |
| Colombo future cutoff | Rules compare components to request.time + 330 minutes, not UTC's date or the client's upload date. Today is allowed; tomorrow is denied. At 18:29:59 UTC it is 23:59:59 Colombo; at 18:30:00 UTC it is the next Colombo day. |

The existing expense, standalone-income, project, milestone, linked-receipt and atomic allocation/payment policy was compared with 7c23b31 after removing only the added crypto helper functions and match block. All existing policy text is identical after normalizing line endings. Existing rule tests are retained.

No demonstrated rule/payload defect was found, so neither application/rule/test changes nor suite reruns were warranted for this preparation task.

## Verification evidence and limits

Existing results were checked in the local reports, XML and logs; they were not rerun for this review:

| Previously executed check | Recorded result |
| --- | --- |
| npm.cmd test -- --grep 'FR07 realised crypto', from firestore-tests | 8 passed |
| npm.cmd test, from firestore-tests, local Firestore emulator | 84 passed, including all previous 76 tests |
| Android unit suite | 101 passed, 0 failures/errors |
| Debug application/test APK builds | Passed |
| lintDebug | 0 errors, 20 warnings |
| Crypto and existing payment Android navigation, 320 dp/API 37 | 2 passed using isolated repositories |

Rules tests cover owner CRUD, immutable createdAt, exact schemas/types/notes/timestamps, date validity, today's Colombo date, tomorrow rejection and ordinary-financial isolation. Fixed-clock client tests cover month/year rollover at 18:30 UTC. The precise rollover instant was not simulated in the rules emulator; the +330-minute rule expression was inspected. Do not present client clock tests as a simulated-request-time rules test.

At the time of this review, live scenarios were unexecuted. The subsequent authorized run exercised them as detailed in FR07_LIVE_RESULTS.md. The retained LiveFirestoreAuditTest now inspects cryptoResults, ordinary finances and scoped recovery; it is explicitly opt-in and read-only. Its successful live Source.SERVER checkpoints are separate from the earlier mocked/emulator checks above. The later banner correction raised automated unit coverage to 111 and isolated Android coverage to three tests; it did not repeat live acceptance. The checklist below requires fresh explicit authorization for any future samples and interactive account access.

## Before live execution

- Have the user publish the reviewed rules manually and confirm the target project/database. Verify that the device reaches live Firestore. Report UNAVAILABLE/DNS separately from PERMISSION_DENIED. Neither compilation nor an IP ping is a live document acknowledgement.
- Obtain explicit authorization for the sample records below and their edits/deletions. This document grants no authorization to execute them. A second signed-in device/session and a second account are needed for the conflict/account tests; the user signs in interactively, without sharing credentials in chat.
- Inspect server crypto documents and owner-scoped CRYPTO_CREATE/CRYPTO_UPDATE/CRYPTO_DELETE recovery attempts before creating samples. Reconcile matching existing attempts first. Do not recreate an operation merely because its wait timed out.
- Use a unique note prefix such as FR07-acceptance-<session>-GAIN/LOSS/OFFLINE-1, and record the server document IDs. Do not overwrite or delete unrelated records.
- Record the existing crypto monthly/YTD totals and document ID sets, and the ordinary income/expense record IDs, counts and exact totals for each comparison period. Use those same periods afterward. If the account has other crypto results, compare the sample contribution/delta, not an assumed zero account balance.
- Keep UID/record IDs, local recovery exports and screenshots private in an ignored local diagnostic location. The UI does not expose operation IDs. For before/after identity evidence, use read-only debug recovery inspection of the submission_records preference in files/datastore/expense_recovery.preferences_pb: restrict inspection to the intended UID and CRYPTO_* types. The legacy JSON key expenseId is the result record ID; payloadJson contains result.id and operationId. If this inspection is unavailable, mark pre-reconnect identity verification incomplete rather than infer it from card count.

## Button-by-button checklist

Let Y be the current Colombo year, M the chosen current reporting month, and D an actual non-future day in M. The examples 2026-10-01, 2026-09-30 and 2025-12-31 are all past dates at review time; adjust them if executing later. All numerical expectations below describe only the labelled sample contribution, added to the recorded account baseline.

### 1. AT03 and ordinary-financial isolation

1. Home → Crypto Results → Add Result → Gain → Positive LKR amount 60000 → date D → unique GAIN note → Save Result. Tap Save twice quickly once, then await acknowledgement.
2. Inspect the card: status must become Confirmed by server. In Console, verify one document, direction gain, amountMinor 6000000, date D, a stable lastOperationId, and resolved server timestamps. A local-save message or cached card is not sufficient.
3. Add Result → Loss → 25000 → date D → unique LOSS note → Save Result. Verify a distinct single document with direction loss and amountMinor 2500000.
4. Confirm sample month/YTD contribution **+35000.00**: 6000000 - 2500000 = 3500000 minor units. Current-year cutoff is today; a past reporting year ends December 31. Future reporting years are unavailable.
5. Reopen Crypto Results; verify the same two IDs and contributions. Ordinary income/expense counts, IDs and exact totals for the original periods must remain unchanged. No linked income receipt, expense, project allocation/payment or goal entry should have been created.

### 2. Backdating across month/year boundaries

1. Open confirmed GAIN → Edit Result → change only its date to the prior month's last day → Save Result. Choose two already elapsed months within the same year for this first comparison, such as September 30/October 1 in 2026.
2. Current sample month becomes **-25000.00**, prior sample month **+60000.00**, and that year's sample YTD remains **+35000.00**. Confirm the result ID/createdAt are unchanged and updatedAt advances.
3. Edit GAIN to December 31 of Y-1. Current Y contribution becomes **-25000.00**, and Y-1 contribution becomes **+60000.00**, with the past-year cutoff explicitly December 31.
4. Edit GAIN from December 31 of Y-1 to January 1 of Y. Verify it leaves the old year's December and enters Y's January exactly once. Current Y contribution returns to **+35000.00**.
5. Restore GAIN's date to D, returning the current sample month to **+35000.00**. Compare ordinary income/expense data for the same original periods again.

### 3. Confirmed and pending edits

1. Open GAIN → Edit Result → change amount to 50000 → Save Result. After server acknowledgement, sample month/YTD is **+25000.00**. Restore 60000 and await acknowledgement, returning to **+35000.00**.
2. While online and confirmed, open GAIN → Edit Result, then disconnect the test emulator's network before saving 55000. Verify the emulator is actually offline; do not change host DNS, Firebase targets, rules or app storage.
3. Save Result. The saved proposal must appear pending while the original confirmed/cached GAIN still contributes 60000, so sample net remains **+35000.00**. Cached totals must be labelled accordingly. Do not re-enter the result after a timeout.
4. Reconnect with Crypto Results open. Only after server confirmation should GAIN contribute 55000 and the sample net become **+30000.00**. Same result ID; createdAt retained. Restore 60000 online, returning to **+35000.00**.

### 4. Rejected edits/deletions without changing live rules

Use a controlled stale-version conflict, not a security-rule deployment or weakening. This tests a rejected proposal; it is not a PERMISSION_DENIED test.

1. Session A: open confirmed GAIN → Edit Result while online, then go offline and save a proposed amount change.
2. Session B, the same owner: change only GAIN's note through the app and wait for acknowledgement. Preserve the unique test prefix, amount and date. The changed server updatedAt makes A's original version stale without changing its financial contribution.
3. Reconnect A. The saved edit should become Change rejected with a changed-elsewhere error; original GAIN/LOSS contribution remains **+35000.00**. Inspect both server state and totals. Repeated Retry saved change must not duplicate or force through a stale edit.
4. Dismiss rejected proposal. Only the rejected local proposal should disappear; the confirmed record and contribution remain. Reopen the latest server version before another edit.
5. Repeat for LOSS: A opens Delete Result while confirmed, goes offline before Confirm Delete, then queues it; B changes only LOSS's note and waits for acknowledgement. Reconnect A. Expect Deletion rejected, LOSS still present, sample net still **+35000.00**. Dismiss rejected proposal; do not delete the actual server record as part of dismissal.
6. If a real PERMISSION_DENIED occurs instead, record that separately and stop additional writes for that case. Compare published policy and UID/path/payload; do not label a permission failure a stale-version conflict or weaken rules to obtain a pass.

### 5. Offline creation → real process restart → reconnect

1. Disconnect only the test device/emulator. Add three labelled results dated D: gain 100, loss 40, gain 20. Wait for each durable local-save message and confirm three pending proposals; they must not increase confirmed totals yet.
2. Privately record each local recovery result ID, operation ID, direction, minor amount (10000/4000/2000), date and UID before restarting. Preserve any unrelated recovery records.
3. Actually stop the process, not just navigate back. With the device selected in ADB, use:

       adb shell am force-stop com.clarity.finance

4. Relaunch using the launcher icon while still offline → Home → Crypto Results. Verify the same three saved IDs/operation IDs and payloads remain pending. Do not uninstall, clear storage, wipe the AVD or enter the samples again.
5. Reconnect and leave Crypto Results open so its owner-scoped synchronizer runs. Verify exactly three server documents with those same IDs/lastOperationIds, resolved timestamps, and no duplicate records. Their confirmed sample contribution is **+80.00** (8000 minor units), making the combined sample contribution **+35080.00** if AT03 records are restored.
6. Force-stop/reopen once more online and verify unchanged server ID sets/counts/totals and no unresolved matching crypto attempts. Repeated Retry or reopening must not add another contribution.

### 6. Account switching with a pending operation

1. With explicit authorization for an additional sample, prepare a labelled pending A operation offline and record its identity separately from the three offline samples above. Record its amount when calculating expected totals; otherwise this case must remain marked not executed.
2. Close the form/details → Back to Home → Sign Out. Reconnect and have the user sign into B interactively → Crypto Results. B must show only B's history, proposals, selection/forms and messages; no A content may flash or remain cached in the displayed state.
3. Compare owner-scoped server paths and local recovery. A's attempt must not become a B document or be retargeted to B. An already sent A transaction can still finish after sign-out; if it does, it must remain under A with the same identity. That is distinct from a stale callback exposing A's data in B.
4. Sign out B → sign into A → Crypto Results. Reconcile A's original attempt, rather than recreating it. Verify one result and exactly one contribution. Record any step blocked by unavailable account access or connectivity as unexecuted.

### 7. Pending deletion, confirmation and final reconciliation

1. After restoring the AT03 pair, inspect LOSS → Delete Result → Keep Result. Verify server fields/timestamps and all contributions remain unchanged.
2. Open Delete Result while confirmed/online, then disconnect before Confirm Delete. Confirm locally. LOSS's original -25000 contribution must remain in confirmed/cached totals while Deletion pending is shown. A timeout does not prove deletion failed or was cancelled.
3. Reconnect with Crypto Results open. After server absence is confirmed, LOSS's contribution is removed exactly once: the AT03 pair's remaining sample contribution becomes **+60000.00**. The three confirmed offline samples, if retained, add another +80.00. Keep other authorized test cases and original baseline separate.
4. Reopen/retry as applicable; confirm LOSS stays absent, GAIN unchanged, no duplicate contributions, and no matching unresolved deletion remains. Recheck ordinary income/expense IDs/counts/totals against the original same-period baseline.
5. Record final IDs, operations, exact minor-unit sums, timestamps, pending/failed counts and results. Any cleanup must be limited to labelled authorized samples and documented; do not silently remove unrelated records.

## Outstanding assignment tasks outside FR07 live acceptance

- **Hilt/Koin migration:** original brief Section 5 specifies Hilt (preferred) or Koin; the app still uses manual AppContainer/ViewModelFactory DI. This remains outstanding and was not bundled into FR07.
- **API 26 runtime verification:** currently recorded Android navigation is API 37. Installing/running on API 26 and recording the result remains outstanding.

No dashboard, goals, recurring expenses, optional crypto integrations, dependency upgrades, Firebase target changes, commits or pushes are included in this preparation.
