# Human acceptance - isolated final UI

Status: the user completed familiarisation and all five captures. Timing was an approximate glance at a clock, not a strict stopwatch measurement. Correct records are independently verified; the formal NFR01 timing criterion remains unverified. TalkBack remains unexecuted. **The user has replaced any request to repeat human acceptance: the procedure below is historical evidence, not a request for another timed run.** This is not live-Firebase acceptance or a release/submission build.

## Safe build and session

Use only the already inspected **Clarity_UI_Pilot_API26, emulator-5564** window. Never use Pixel_6a or enter real account credentials. The current debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It includes the production screens, navigation, ViewModels and approved light/dark design, with an explicitly selected in-memory repository graph for this activity. No production Firebase/recovery/sync binding is available through that graph. The normal production application still initializes first, so the saved-auth/recovery check must happen **before launching**, not inside a test body.

From the repository, the guarded launcher is:

```powershell
python.exe tools/acceptance/isolated_review.py --serial emulator-5564
```

It checks the AVD identity and saved authentication/recovery before starting the opt-in debug activity. If it stops, do not clear, retry, migrate or discard anything; report the stop. Do not substitute another serial. No installation, sign-in or storage reset is performed by this script.

Require the top banner **ISOLATED ACCEPTANCE - Fictional, memory-only data - No cloud** (the on-screen separators may be dots). Without that banner, stop. The fixture is held in process memory: Activity recreation retains it, but process death resets it. This host cannot establish offline durability, real server acknowledgement, authentication security or sync performance. Those have separate recorded evidence. Do not use its simulated acknowledgement as a claim of cloud success.

Initial current-month fictional values:

| Measure | LKR |
| --- | ---: |
| Received income | 140,300.00 |
| Recorded expenses | 50,000.00 |
| Recorded cash surplus | 90,300.00 |
| Separate crypto net | +35,000.00 |
| Recorded goal allocation | 11,200.00 |
| Current collectible project outstanding | 40,000.00 |

Current income is salary 128,000 plus USD 41 at fixed LKR 300 = 12,300. The project's two earlier receipts belong to the previous month. Expenses are rent 34,000, groceries 6,000 and archived-category leisure 10,000 (40,000 committed / 10,000 discretionary). An additional **pending example 850** is excluded from recorded expenses. A rejected catalogue rename leaves recorded history unchanged. Recurring rent is a suggested template, not another expense. The goal uses the AT07 values; it does not grow from surplus.

Only ordinary expense creation/correction/deletion is writable in this fixture. Inspect the other features; do not try to create income, crypto, savings, projects, receipts, recurring confirmations or catalogue changes. Unsupported fixture actions are deliberately unavailable/read-only. Do not retry or dismiss the seeded saved-change examples during the timed run.

## 1. Familiarise, without timing

1. From Overview, find Add Expense without guidance first. Record where you looked and anything confusing.
2. Practise recording an expense (e.g. LKR 50, Food & Dining, Cash, Discretionary, today, no note). Return to Overview.
3. Explore the form and selectors until familiar. Practice is excluded from the five measured attempts; do not remove slow trials later as "practice".
4. Tell the reviewer you are ready. The reviewer records a read-only fixture snapshot/count before the measured trials. Use an independent stopwatch or human-reviewed recording; no instrumentation timing substitutes for your observation.

## 2. Completed five-capture procedure (retained for traceability)

The exact requirement (R PDF p5, NFR01) is **after familiarisation, at least four of five representative attempts within ten seconds from dashboard, optional note omitted**. Start each attempt on the ready Overview. Start timing with your first Add Expense interaction; stop when Save gives local acceptance/returns to Overview. This endpoint measures capture feedback, not a server acknowledgement. Keep today as the date and leave Note blank. Do not pre-fill the amount before starting. Record all five first attempts, including mistakes, assistance, failures and retries. If a run is interrupted, mark it interrupted; do not call it a pass.

| Attempt | Actual expense task | Seconds (human) | Saved correctly? | Error/assistance/observation |
| --- | --- | --- | --- | --- |
| 1 | Groceries 400; Food & Dining; Cash; Committed | Rough clock observation only; not stopwatch measured | Yes, snapshot verified | Emulator mouse/keyboard friction reported overall |
| 2 | PickMe 850; Transport; Primary bank/card; Discretionary | Rough clock observation only; not stopwatch measured | Yes, snapshot verified | No per-attempt error/assistance data supplied |
| 3 | Coffee 100; Food & Dining; Cash; Discretionary | Rough clock observation only; not stopwatch measured | Yes, snapshot verified | No per-attempt error/assistance data supplied |
| 4 | Bus 250; Transport; Cash; Committed | Rough clock observation only; not stopwatch measured | Yes, snapshot verified | No per-attempt error/assistance data supplied |
| 5 | Electricity 1,200; Utilities; Secondary bank/card; Committed | Rough clock observation only; not stopwatch measured | Yes, snapshot verified | No per-attempt error/assistance data supplied |

Verify each selection, including any suggested spending type. The five intended records total **LKR 2,800**, increasing recorded expenses by 2,800 and decreasing surplus by 2,800 relative to the post-practice baseline. Income, crypto, goal savings and current pending balances must stay unchanged. Check five distinct IDs, no duplicate record for a repeated Save, and blank notes using the read-only snapshot:

```powershell
python.exe tools/acceptance/isolated_review.py --serial emulator-5564 --snapshot
```

Snapshot IDs are fictional and may be retained privately; report only counts/deltas. If the fixture session ID changes, the process restarted: the measured sequence must be marked interrupted rather than combined with a different baseline. No automatic test supplied the human duration claims above. The post-practice/post-trial snapshots retain the same session: one LKR3,000 practice record plus five distinct correct trials. Recorded expenses53,000 ->55,800 and surplus87,300 ->84,500. The pending example remains excluded. Exact timings are not invented.

## 3. Find, inspect and explain

Without saving other features, try these tasks and note whether you needed help:

- Find your new expense in History. Filter its category, inspect it, and return. Did the month/filter/position stay useful? Open All recorded expenses and return.
- Find Received Income and inspect the USD 41 example. Explain the original currency, stored rate and LKR amount. Find the prior-month linked project receipt and its protected project route.
- Find Plans, the agreement, milestone, Savings Goal schedule and Recurring Expenses. Explain why template amounts and goal allocations do not change monthly surplus.
- Find More -> Saved Changes -> Saved changes. Identify the pending expense and rejected rename. Explain whether either is already included in recorded totals, what is still unresolved and what the panel covers. Do not resolve/dismiss these samples.
- Locate Crypto Results and distinguish gains/losses from ordinary received income.
- Inspect an expense's Delete confirmation; choose Keep/Cancel. Do not unintentionally delete a timed sample.
- Briefly describe "recorded cash surplus" in your own words. It must not be understood as a verified bank balance, automatically saved money or safe-to-spend amount.

## 4. Actual TalkBack, not semantics automation

TalkBack is **not installed** on the inspected API26 AOSP device. This check is currently **blocked/unexecuted**. Do not equate Compose semantics assertions, UIAutomator, successful keyboard tests or this checklist with TalkBack use. A suitable isolated TalkBack-capable device/service must be agreed and safety-inspected before Application startup. Do not install unofficial accessibility APKs or sign into a real account to obtain one. The attempted separate API35 boot was blocked by host disk capacity.

When a safe TalkBack-capable fixture session is available, the human should:

1. Enable TalkBack through Settings; note its version, device, theme and font size. Stop instrumentation first so UiAutomation cannot suppress the service.
2. Explore Overview amounts and navigation. Check spoken labels/selected state, reading order, currency/signs and absence of meaningless duplicate controls.
3. Open Add Expense; navigate fields, category/channel/type selectors and calendar/manual date. Check focus, keyboard entry, Save/Cancel and no focus trap with the real keyboard.
4. Submit an invalid/blank amount; check whether the error is discoverable/announced and recovery is clear. Correct it; a permitted additional sample must be recorded separately from the five timed trials.
5. Inspect history, information dialogs and Delete -> Keep. Check dialog title, focus containment, dismiss/confirm labels and focus return. Check long labels and full monetary amounts at enlarged text, including 320 dp / system font 2.0x if practical.
6. Explain pending versus recorded results by listening alone. Record any skipped/unlabelled action, incorrect announcement or unreachable control.
7. Restore only settings changed for the check to their captured baseline. Report the actual observations, including failures; do not repeat until failures disappear.

## Return observations

The user found all recorded trials in History and reported arbitrary-looking same-day order plus missing recorded time. Finding H-01 and its approved scoped correction are in FINAL_APP_ACCEPTANCE.md. Timing method is now known to be approximate clock observation; no raw stopwatch values exist for these attempts. Formal measured timing, other navigation/status observations and actual TalkBack remain open. No passwords, personal finance screenshots or real account identifiers should be sent.
