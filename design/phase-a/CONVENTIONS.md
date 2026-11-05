# Clarity Finance design foundation and rollout

Final rollout status (5 November 2026): Phases A-D are complete. The approved light primary is #237D68 and pale mint is #EEF9F3, with the reviewed dark roles. All production destinations inherit the single system-following `ClarityTheme`. References below to temporary adapters and future phases describe the original Phase A checkpoint; the adapters were removed in Phase D and are not a second design system. Current verification and limitations are in `FINAL_APP_ACCEPTANCE.md`.

Original checkpoint: the intended outcome is one coherent design throughout the app, including authentication. Phase A establishes the foundation; the remaining screens keep their accepted behavior and presentation until their review phase. `PilotTheme` is a temporary scope adapter to `ClarityDesignTheme`, not an alternative palette to maintain permanently.

## Shared conventions

- System-following explicit light/dark green-teal palettes; primary means main action/selection, never proof of financial success. Surface/background distinguish grouping, with text and labels carrying meaning independently of colour. Error panels use error roles. Cached/unknown proposals are neutral, not automatically red or green.
- Material 3 type roles: 30/36 sp primary monetary figure, 24/30 page heading, 22/28 period, 16/22 section/value, 14/20 supporting labels, 12/18 compact qualifications. Font scale is never reduced to fit a fixed card. Default platform font family, no new font dependency.
- Spacing uses 4/8/12/16/24 dp; 8/12/20 dp small/medium/large corners. Cards group related facts; not every fact needs a card. Buttons identify an action, links expose secondary tasks, and icon-only information/calendar/period controls have explicit names. Minimum touch targets remain 48 dp.
- Amounts use `PresentationFormat` only at the presentation boundary: exact Long/BigDecimal, grouped digits, two decimals, preserved sign. Input stays ungrouped and exact; no parsing, storage or arithmetic changes. No ellipsis/max-lines for essential monetary values.
- Display dates use `d MMM yyyy`; the selected period uses `MMMM yyyy`. Manual expense-date input remains ISO `yyyy-MM-dd`. The calendar converts date-only values with UTC epoch days while the selectable upper date is the existing Asia/Colombo today; it does not infer the transaction date from upload time or UTC today.
- Overview groups received income, expenses and recorded surplus. Crypto, collectible pending original-currency balances and explicit goal savings stay separate. Surplus is not a bank balance or automatic savings. No fabricated charts, counts, last-sync time or empty-goal values.
- History prioritises transactions, retains selected month/mode/category/scroll, and labels the all-category monthly total explicitly. Inspection is read-only before Edit; Delete always confirms. Linked receipts retain project routing.
- Focused forms hide the shell. Draft fields remain in the existing UID-scoped retained ViewModel, outside the durable operation queue. Activity recreation is covered; a new submission owner cannot resurrect submitted fields as a fresh unsent proposal. Unsaved draft process-death persistence is not promised.
- At narrow widths or enlarged fonts, controls reflow and content scrolls. Navigation stays outside the scroll; its four labelled destinations become two rows at 320 dp/2.0x. Amounts/labels wrap rather than shrink. Calendar switches to editable date mode at enlarged font; manual ISO entry is always available.
- Status copy follows actual state: local admission is not remote success; cached is not necessarily offline; unknown outcomes remain unknown; pending edits/deletions retain confirmed contribution. Independent errors do not suppress valid other sections. No status eligibility, queue or retry behaviour changes.

## Review phases and acceptance gates

| Phase | Scope | Acceptance gate |
|---|---|---|
| A (this pass) | Foundation, Overview, expense capture/inspection, monthly History; shell and simple unchanged-feature hubs | Initial-scroll cash measures and transactions, context Back, amount/date formatting/calendar, repeated Save, UID changes, recreation, 320 dp/2.0x, keyboard, both themes; no backend changes. |
| B | Income, projects/milestones/receipts, category/source management | Reuse the same components/tokens. Test foreign original/converted values, inherited/archived sources, linked-receipt routing, optional milestone argument, cancellations and exact balances; do not weaken safeguards. |
| C | Savings, recurring and crypto | Reuse foundation. Preserve ledger/history/schedules, suggested versus actual expenses, consumed occurrences, signed separate results, overflow inspection, unknown/rejected feedback; no financial logic redesign. |
| D | Authentication and remaining shared surfaces, final consistency | Reuse foundation on login/register without persisting passwords. Test keyboard, validation, logout/back-stack cleanup, all feature links, both system themes. Remove temporary scope adapters after all destinations adopt the foundation; replace old global palette rather than maintaining competing systems. |

Each later phase requires its own reviewed change set, fake-only navigation/ownership tests, narrow/enlarged and theme captures, form-state/account-change checks and truthful status regressions. Actual TalkBack/human usability and contrast review remain explicit human acceptance work; screenshots and semantics tests alone do not establish them. No later-phase implementation is authorized by this document.
