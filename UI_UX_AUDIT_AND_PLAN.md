# Clarity Finance — UI/UX audit and proposed redesign

Design review date: 4 November 2026. Inspected baseline: `d38965de521aa1a02eade787c0d3ae95545beeeb`.

**Recommendation:** make the financial overview the authenticated landing screen, put a labelled Add Expense action within immediate reach, and give history and saved changes a consistent visual language. Keep the accepted financial models, confirmation rules and synchronization eligibility intact. Start with **Dashboard → Add Expense → History/expense inspection** before restyling the other features.

This is a source-based audit and an original static design proposal. No production code, tests, dependencies, rules, indexes, authentication, financial data or recovery behavior were changed. No Android device was queried or launched; Pixel_6a was untouched. The wireframes contain fictional data only. They are not screenshots of the current app, a runnable replacement app, or proof of runtime accessibility.

## 1. Scope, sources and evidence standard

No applicable `AGENTS.md` was found at the drive/repository roots or in the project source tree. HEAD matches the requested baseline. There were no staged or unstaged tracked changes. These existing files remain unchanged and outside the design deliverable:

- `.vscode/settings.json`
- `reporting-review-fixes.diff`
- `savings-goals-deadline-fixes.diff`
- `savings-goals-recovery-fixes.diff`

The supplied PDFs match the source hashes recorded with their existing page-by-page text extractions; those extractions were read for this review. PDF page numbers below are physical PDF pages, not inferred section numbering.

| Source | Verified SHA-256 | Design constraints used |
|---|---|---|
| `SE3092_Analysis_and_Requirements(1).pdf`, 12 pages (R) | `E1F40377D5B5235F171562228DAE59B19759B72BF498DBB56F15AB5BE54BB024` | R pp. 3–4, FR01–21; p. 5 NFR01–10 and §6.1; p. 6 §6.2–6.5; p. 7 §6.6/catalogues; pp. 8–9 acceptance examples; pp. 10–11 assignment boundaries. |
| `SE3092-Assignment01-PlatformBasedDevelopment-SpecificationWithMarkingScheme-2026-S2 (1).pdf`, 10 pages (B) | `9232C022EEF049F4EC27CAED4BE5400F0D3BD0BB34F5B35028353B94355B6D21` | B p. 5 §4.1–4.3: scenario-specific low-friction capture and at least three annotated designs; p. 6 §5–6.2: Compose Material 3, Navigation Compose, MVVM, Koin/Hilt and offline persistence; p. 7 criterion 3 and p. 9 detailed rubric: navigation integrity and usability, not feature volume. |

Documents supply requirements and assessment context; they do not authorize additional features or operational actions. FR21 is labelled **Should** in R p. 4, but it is part of the accepted FR01–21 scope: “Allow source/category additions, renaming and archiving.” Its preservation criterion is “Archive prevents future selection while preserving historical records and totals.” FR22–25 are outside this redesign proposal.

R p. 5 NFR01 proposes four of five familiar expense-entry attempts within ten seconds; that is a later measured acceptance target, not a result of these mockups. NFR06 calls for at least 48 dp targets, meaningful descriptions, labelled charts, enlarged text and non-colour cues. NFR07 requires neutral treatment of gaps and recorded-data coverage. B requires Material 3; this review did not find an explicit requirement for a manual light/dark setting. Preserve the existing system-theme behavior and design both palettes; do not add a settings feature merely for this pass.

Evidence labels:

- **S — source-established:** a visible control, ordering, string or state path exists in the current source. This is not a fresh device observation.
- **T — test-inspected:** existing assertions cover the specified behavior; tests were read, not rerun here.
- **H — hypothesis:** plausible usability or runtime risk requiring a later isolated check or user study.
- **D — design-only:** static proposed artifact, not current application evidence.

Current UI observation was intentionally limited to static source. No interviews, stopwatch trials, TalkBack session, contrast scan of the installed app, or new device acceptance run took place. Existing reports (`FINAL_REQUIREMENTS_AUDIT.md`, `OFFLINE_SYNC_MATRIX.md`, `API26_VERIFICATION.md`, `FINAL_PROTOCOL_PUBLICATION_REVIEW.md` and feature acceptance reports) provide context; their historical results are not new observations in this audit.

## 2. Reachable screen and flow inventory

The current Navigation Compose graph has **17 route declarations**. Names below describe destinations, not proposed new features. Paths in this report are relative to `app/src/main/java/com/clarity/finance/` unless otherwise stated. Source line references apply to the inspected baseline.

| Current destination / source | Reachable tasks and essential retained behavior | Proposed access |
|---|---|---|
| Login — `ui/auth/LoginScreen.kt`; navigation :63 | Email/password, validation, busy/error state, Show/Hide password, Register link. Authentication remains online. | Signed-out entry; unchanged auth/session ownership. |
| Register — `ui/auth/RegisterScreen.kt`; navigation :71 | Email, password, confirmation, validation, return to sign in. | From Login; no extra identity providers/reset feature. |
| Home — `ui/HomeScreen.kt`; navigation :79 | Signed-in identity, eight feature buttons and Sign Out. | Overview becomes landing destination; account/sign-out moves to More. |
| Financial dashboard — `ui/dashboard/DashboardScreen.kt`; navigation :97 | Month, overview, monthly history, spending insights, Add Expense; cash measures, separate crypto, current collectible balances and current goal card. | Overview; history and insights remain directly reachable. |
| Expense list — `ui/expenses/ExpensesScreen.kt`; navigation :206 (`dashboard`) | Recorded expenses, add, edit, errors/cache/pending feedback, saved-change link. This is distinct from the month-bounded dashboard history. | History → Expenses. Preserve an explicit All recorded expenses destination for the existing list scope; do not silently turn it into a monthly subset. |
| Add Expense — `ui/expenses/AddExpenseScreen.kt` + `ExpenseFormContent.kt`; navigation :223 | Positive LKR amount, category, spending type, channel, actual date, optional note; durable local acceptance and return. | One tap from Overview and expense History. |
| Edit Expense — `ui/expenses/EditExpenseScreen.kt`; navigation :231 | Inspect existing values, correct/backdate, delete confirmation, stale/missing record handling. Recurring linkage must survive correction/deletion. | History row → inspection presentation → existing edit/delete actions. |
| Received income — `ui/income/IncomeListScreen.kt`; navigation :182 | Month, confirmed/source totals, original currency, linked-receipt project routing, pending/cache/error states and add. | History → Income → Add Income; secondary Overview income link. |
| Add/edit income — `ui/income/AddEditIncomeScreen.kt`; navigation :194 | Source, receipt date, original currency/amount, historical confirmed rate or actual LKR credit, note, preview, edit/delete/stale states. | Income history. Retain conversion basis and linked-receipt restrictions. |
| Pending payments — `ui/payments/ExpectedPaymentsScreen.kt`; navigation :128 | Project/standalone agreements, received/outstanding amounts, errors, add and saved changes. | Plans → Pending Payments; direct Overview balance-card link. |
| Add pending payment — `ui/payments/AddPaymentScreen.kt`; navigation :138 | Standalone/project, title, source, currency, agreement, due date, note. This creates no received income. | Pending Payments → Add. Suggested button wording: Add agreement. |
| Project details — `ui/payments/ProjectDetailScreen.kt`; navigation :146 | Agreement inspection/edit, direct receipt, add/inspect/edit/cancel milestone, project cancellation, receipt/capacity states. | Existing agreement row; preserve all actions and cancellation conditions. |
| Record payment — `ui/payments/PaymentConfirmationScreen.kt`; navigation :166 | Optional milestone, exact original amount, conversion basis, actual receipt date/note, immutable proposal feedback. | Project: Record Direct or milestone: Record. Never encode a literal `null` segment. |
| Crypto — `ui/crypto/CryptoScreen.kt`; navigation :124 | Add/inspect/edit/delete realised gain/loss, month/YTD cutoff, separate history, pending/rejected/uncertain recovery. | More → Crypto Results; separate Overview card link. |
| Savings — `ui/goals/GoalScreen.kt`; navigation :120 | One goal, editing, opening allocation, contribution/withdrawal ledger, inspect/correct/delete entries, schedule, overfunding/reached/overdue and deadline recovery. | Plans → Savings Goal; direct Overview goal card. |
| Recurring — `ui/recurring/RecurringScreen.kt`; navigation :114 | Template CRUD/cancel, selection/details, paged due/missed/confirmed history, review actual payment, route to actual expense. No automatic expense. | Plans → Recurring Expenses. |
| Catalogue and recovery — `ui/CatalogueScreen.kt`; navigation :96 | Add/rename/archive category/source; defaults; transaction/project/catalogue proposal review, rejection dismissal or safe online resolution. | More → Categories & Sources and separately labelled Saved Changes. Initially link to existing sections; do not create another queue/store. |

### Dialogs and non-route views

| Surface | Inventory and current source |
|---|---|
| Dashboard | Overview/history/insights internal pages, category chips/drill-down, independent reporting errors; crypto inspection dialog at `DashboardScreen.kt:166`. |
| Shared forms | Category/source/channel menus; currency and spending-type controls; inline loading, field validation, submission and conflict panels. Keep every required field and the ability to backdate. |
| Expense and income | Delete confirmation (`EditExpenseScreen.kt:99`, `AddEditIncomeScreen.kt:238`); stale-record warning and Reload action; missing/deleted record state. |
| Projects | Agreement/milestone editor (`ProjectDetailScreen.kt:37`); cancellation error (:73), Keep/Cancel Project (:83), Keep/Cancel Milestone (:110), Add Milestone (:309). |
| Crypto | Full form dialog, inspect details (:89), Keep/Delete result (:104), saved operation panels and month/year controls. |
| Savings | Goal/entry form dialog (:149), Keep/Confirm entry deletion (:185), contribution schedule dialog (:192), rejected/unknown proposal and safe deadline revision states. |
| Recurring | Template/actual confirmation form (:137), Keep/Confirm cancellation (:173), template list/detail selection, 12-month history window and template pagination, confirmation-consumed/expense-deleted state. |
| Catalogue | Add/edit dialog (:62), Keep active/Archive confirmation (:72), queued proposal cards with state-eligible actions. |
| Account/session | Session initialization spinner; sign-in/out transitions; old-account records/forms/feedback must clear. There is no account-switcher feature to invent: retain sign out then sign in. |

Major flows traced: auth → Home; quick expense → durable acceptance → pending review → confirmed history → edit/backdate/delete; ordinary/foreign income → confirmed conversion → source history; agreement → milestone/direct receipt → linked income → cancellation; crypto reporting → result edits; explicit savings ledger → schedule; template → due review → actual expense → correction/deletion with consumed confirmation; catalogue rename/archive → new selection/history distinction; offline restart → process-level sync → scoped reconciliation. See §6 for the required state distinctions.

## 3. Prioritized findings

P1 = first-slice/high-impact work; P2 = subsequent consistency or interaction work. There is **no newly observed P0 financial/protocol defect** from this design review. “Source-established” identifies the interface fact; the scale of user frustration remains a hypothesis until tested.

| ID / priority / kind | Screen and concrete task | Evidence | Impact and proposed improvement |
|---|---|---|---|
| UX01 / P1 / navigation | Open app to understand this month or record a ride | S: `HomeScreen.kt:60–95` has eight repeated feature buttons; `AppNavigation.kt:49–59,79–95` starts authenticated users on Home. Dashboard has its own Add Expense FAB (:48). | Home adds an intermediate choice before the everyday task. Land on Overview; retain one prominent Add Expense action. Do not claim the current dashboard lacks quick-add. |
| UX02 / P1 / hierarchy | Read income, expenses and surplus | S: dashboard :55–67 places period/navigation controls and goal card before the cash cards; :74–93 repeats long explanatory paragraphs. | Monthly cash information competes with current-plan data. Put the three cash measures together first; keep short qualifiers visible and detailed explanations available without removing meanings. |
| UX03 / P1 / navigation/copy | Find a saved edit or uncertain transaction | S: Home :95 calls the destination “Sources, categories & recovery”; Catalogue :48–58 exposes recovery there, while crypto/goals/recurring have their own panels. | Users must know an internal grouping to find a saved change. Separate navigation labels for catalogues and Saved Changes; use a hub of existing panels, not a fictional unified global count. |
| UX04 / P1 / status comprehension | Know whether a save changed totals | S: Catalogue :54 prints `group · kind · status`; :58 says “Reconcile or withdraw safely (online)”; crypto :74–75 and recurring :49 use protocol-heavy wording. | The distinctions are correct but require technical interpretation. Use “Saved on this device”, “Waiting for confirmation”, “Confirmation unknown” and “Change not applied” under the exact existing eligibility rules. Expose detail/action explanation rather than hiding uncertainty. |
| UX05 / P1 / forms | Record an ordinary expense without a note | S: `ExpenseFormContent.kt:58–160` orders amount/category/channel/type/ISO date/note; save feedback/error at :164–181. R FR08/NFR01 emphasize low effort. | Date typing and the long form can slow capture (H; not timed). Prioritize amount/date, show current classifications clearly, offer a labelled calendar plus exact manual date entry. Keep amount blank on new forms and all classifications reviewable. |
| UX06 / P1 / history | Inspect a category entry, then return | S: dashboard :121–150 places expense, income and separate crypto histories within one scroll; internal page saved at :33. Row callbacks open editors. | The report-to-record connection is useful but scan-heavy. Add explicit Expenses/Income modes, date grouping, category filter and visible Insights access. Preserve month/category/scroll when returning, plus separate crypto history. |
| UX07 / P1 / accessibility risk | Choose committed/discretionary by tapping its label | S: `ExpenseFormContent.kt:133–140` uses separate `RadioButton` and `Text` inside a non-selectable `Row`. T: existing tests inspect values/layout, not this full label hit area or TalkBack name. | Only the control declares the click action; label activation/group semantics need a focused isolated check. Proposed single selectable labelled row/chip with ≥48 dp target. Do not report a fresh screen-reader failure without measuring it. |
| UX08 / P2 / visual system | Recognize screens across devices | S: `Theme.kt:38–50` enables wallpaper-derived dynamic palettes on Android 12+, purple fallback on API26; `Type.kt` customizes only bodyLarge; screen headers vary between TopAppBar, Row and FlowRow. | Brand/hierarchy varies by platform and screen. Adopt one explicit semantic palette and type scale in both themes; reuse Material components. Current Material colours are not alleged to fail contrast. |
| UX09 / P1 / responsive risk | Read long source/category names and large amounts | S: income item :125–128 puts an unconstrained date/source Text and amount in a Row; headers at :39–40 similarly combine title and Back. T: `IncomeListAccessibilityTest:31–67` already protects a usable scrolling viewport at 320 dp/1.3x. | Long labels can compete horizontally (H). Stack amount below text at narrow widths/enlarged fonts; preserve the accepted viewport repair rather than treating it as unfixed. Never ellipsize the only full amount. |
| UX10 / P2 / forms/feedback | Understand a stale edit | S: expense :68 and income :76 say saving “would overwrite” changes; accepted writes are version checked. | Wording suggests silent overwrite is allowed. Propose “This record changed elsewhere. Review the latest version before saving.” Keep the draft/reload/conflict behavior; this is a copy correction proposal, not authority to change version checks. |
| UX11 / P2 / interaction | Record a direct or milestone receipt | S: project :205–274 has multiple actions with generic milestone “Record”/“Cancel”; payment confirmation :69–112 exposes historical conversion/date fields. T: `PaymentNavigationTest` checks both optional-milestone routes and agreement editors at narrow width. | Use “Record receipt” with the target title in context; group main receipt action above secondary agreement/cancellation actions. Do not hide conversion choices or capacity/server-confirmation reasons when disabled. |
| UX12 / P2 / history labels | Inspect inherited milestone source after archive | S: Add Milestone `ProjectDetailScreen.kt:323` displays raw `${project.sourceId}` alongside currency; catalogue label helpers are used elsewhere. | A stable ID is not a readable source name. Propose current known name plus Archived badge, or explicit unknown-label text/ID when unavailable. Preserve inherited-source eligibility and immutable identity. This is a presentation finding, not a schema repair. |
| UX13 / P2 / recurring review | Find the next missed payment and understand what is paid | S: recurring :98–124 shows several correct but long schedule/cancellation/confirmation paragraphs before rows. | Use distinct Suggested, Due/Overdue, Confirmed and Actual expense corrected/deleted labels. Put a short “Review actual payment” action on due rows; show policy details in an inspectable section. Preserve paging, original confirmation payload and all-due cancellation policy. |
| UX14 / P2 / savings hierarchy | Inspect actual progress and next opportunity | S: goal :109–127 lists start/deadline/saved/target/remaining/progress/required/N/next date separately. | Present saved/target/progress together, then remaining and contribution schedule. Keep overfunding, uncapped numeric percentage, overdue/reached labels and explicit allocation explanation; no “on track”, automatic transfer or forecast. |
| UX15 / P2 / crypto reporting | Distinguish month from YTD, or inspect valid records during overflow | S: crypto :45–55 has four month/year buttons and cutoff text; dashboard history no longer gates valid rows on aggregate availability. T: `DashboardNavigationTest:145–175` checks overflow inspection and malformed-record error. | Use one selected-month header, a clearly labelled YTD year/cutoff summary and signed Gain/Loss rows. Preserve independently inspectable valid rows when only the total overflows; malformed records remain explicit errors. |
| UX16 / P2 / catalogue management | Rename/archive without losing history | S: Catalogue :35–43 uses internal kinds/default strings and lists active/archived items together. T: `CatalogueNavigationTest` verifies stable IDs and unknown/archived selection blocking. | Separate Categories / Income sources visually; show human-readable defaults and Archived badges. New forms list active choices only; permitted historical corrections retain their existing archived value. Unknown catalogue is not an active “Other” fallback. |
| UX17 / P2 / auth/account | Sign in on a narrow screen or change account safely | S: auth uses scroll/IME padding and Show/Hide controls; Home :54–57 exposes account identity prominently, Sign Out is a peer feature button. T: `AuthAccessibilityTest:75–122` protects keyboard reachability and invalid inputs making no backend calls. | Use a quiet, consistent auth layout; place account identity/sign-out in More. Preserve validation, keyboard scrolling and password clearing; never capture credentials in review artifacts. |
| UX18 / P1 / error clarity | Read partial/uncached reporting | S: dashboard :73–76,:185–190 retains unavailable/awaiting/cache states, independent sections, and retry. | Consolidate duplicate copy only within genuinely identical scopes. Keep an unavailable income/expense section from becoming zero or yielding a surplus; leave unrelated sections usable. Offer a scoped retry, not a button that suggests resubmitting financial proposals. |
| UX19 / P2 / destructive interaction | Delete/cancel without accidental loss | S: all feature delete/cancel dialogs described in §2; linked receipts route to projects. | Standardize contextual title, amount/date, Keep and explicit destructive verb. Preserve per-feature consequences: cancel milestone releases unpaid allocation only; cancel template stops due unconfirmed items; expense deletion never releases a recurring identity. No swipe-only destructive action or unimplemented Undo. |
| UX20 / P2 / accessibility evidence gap | Use the app with 2.0x text and TalkBack | T: selected tests use 320 dp/1.3x and keyboard assertions; no complete current-app semantic/contrast audit was executed in this task. | Add later 1.0/1.3/2.0x layout, TalkBack, contrast and focus traversal gates. Static drawings do not close this evidence gap. |

### Functional risks recorded separately — no repair in this task

**F01: unsubmitted Add Expense draft retention (P1, source-established state storage; runtime hypothesis).** `AddExpenseScreen.kt:20–25` holds form fields in `remember(formState.accountKey)`, not saveable state or a ViewModel draft. A normal Activity recreation can discard that unsubmitted draft. This is distinct from an already accepted durable proposal, which must survive process interruption. No fresh recreation reproduction was run. Before implementation, add a focused isolated draft-recreation test; any repair needs its own reviewed scope and must remain UID-keyed, without persisting passwords or migrating recovery protocols.

**F02: accessibility semantics/label activation (P1 risk, UX07).** Verify the spending-type label hit region and radio group naming with Compose semantics and TalkBack. The source establishes the split control/label, but no claim is made about actual spoken output here.

No changes were made to address either risk. No conclusion about a financial corruption, lost acknowledged record, protocol defect or new security failure is supported by this static UX audit.

## 4. Market references and selective lessons

Official pages were browsed on **4 November 2026**. Four products were selected for different tasks; none supplies a matching end-to-end financial model. Descriptions below are short paraphrases. Public screenshots were inspected for Actual's schedule form, Monarch's transaction list and YNAB's goal screen; those third-party images are not redistributed in the deliverable. No competitor app was installed or tested, and screenshots/rollouts may differ by platform/version.

| Reference and direct source | Specific pattern worth adapting | Clarity boundary |
|---|---|---|
| **YNAB** — [Add transactions](https://support.ynab.com/en_us/how-to-add-transactions-in-ynab-HyDwA_byi?mobile-help=true), [Scheduled transactions](https://support.ynab.com/scheduled-transactions-a-guide-BygrAIFA9?mobile-help=true), [official feature screenshots](https://www.ynab.com/features) | Amount-first entry and a prominent entry affordance reduce the initial decision. Scheduled items have a review/approval distinction. The public goal screenshot groups amount explanation separately from target feedback. | Adopt entry hierarchy and review language only. Do not import envelope budgeting, account balances, auto-approval, bank-cleared terminology or their goal arithmetic. Clarity confirms actual recurring payments explicitly. |
| **Monarch** — [tracking, reports, recurring and dashboard screenshots](https://www.monarch.com/features/tracking) | The inspected transaction screenshot groups rows by date, aligns amounts and uses persistent named destinations. Overview-to-category drill-down suggests a useful information hierarchy. | Borrow grouping and readable row density; not brand assets, net worth, holdings, bank linking, AI, subscription management, dashboard customization or chart volume. A monthly Clarity surplus is not net worth. |
| **Actual Budget** — [Schedules and illustrated editor](https://actualbudget.org/docs/schedules/), [UI/server-status overview](https://actualbudget.org/docs/tour/user-interface/), [sync-error explanations](https://actualbudget.org/docs/getting-started/sync/) | The inspected editor makes schedule details and upcoming dates explicit. Its manual-approval option demonstrates that a schedule need not be spending. Documentation distinguishes server state and actionable sync problems. | Keep Clarity monthly anchor/clamping and confirm-once protocol. Do not copy automatic posting, approximate matching, missed-month skipping, budgets, reset/revert-sync actions or a server indicator unsupported by Clarity's state. Use state explanation + eligible action, not their recovery mechanics. |
| **Wallet / BudgetBakers** — [Goal setup](https://support.budgetbakers.com/hc/en-us/articles/7181571852690-Setting-up-Goals), [public expense/analysis overview](https://budgetbakers.com/en/products/wallet/features/) | Goal name, target, already allocated amount and desired date form a comprehensible group. The help page explicitly says the app does not transfer funds. Numeric category labels alongside visuals are useful. | Keep one goal and Clarity's confirmed ledger/schedule. Do not adopt multiple goals, bank sync, budgets, automatic categorization, forecasts or colour-only analysis. Goal setup is a documentation reference; no Wallet goal screenshot was available on that help page. |

Adopt an independently drawn visual system and Android conventions, not a competitor's screen composition. The proposed four-destination structure is a design inference for Clarity, not a claim that research proved it optimal.

Accessibility references: [Android: minimum touch targets and labels](https://developer.android.com/guide/topics/ui/accessibility/apps) and [W3C WCAG 2.2 contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum). Use ≥48 dp interactive regions and at least 4.5:1 normal-text contrast; do not mistake either a static ratio check or default Material components for a complete accessibility audit.

## 5. One proposed design direction

### Navigation and ownership

Use four labelled top-level destinations: **Overview · History · Plans · More**. “Plans” is a hub for existing agreements, recurring obligations and the single savings goal; it adds no planning engine or forecasting feature. A bottom NavigationBar suits compact portrait screens. Keep labels visible; at large font sizes, give the bar enough height and reflow rather than shrinking text or hiding labels. If the four labels do not pass the 320 dp/2.0x gate, choose an accessible stacked/top-level menu variant before release. Wide layouts can use a rail later, without making it part of the first slice.

| Top level | Content / complete reachability contract |
|---|---|
| Overview | Selected-month received income, expenses and recorded cash surplus first; one Add Expense action; links to Income and Spending insights; separate realised crypto net; current collectible balances by currency; current savings card. No fake goal or zero values when data is unknown. |
| History | Month-bound Expenses / Income; category filter; Insights; explicit All recorded expenses route for existing full-list behavior. Income has Add Income and linked-receipt routing. Crypto has its own destination/history and a clearly labelled link, not an ordinary cash tab total. |
| Plans | Pending Payments → agreements/milestones/direct or milestone receipts; Savings Goal → ledger/schedule; Recurring Expenses → templates/due review/confirmation history. Each also remains accessible through its Overview card where applicable. |
| More | Crypto Results; Categories & Sources; Saved Changes; current account and Sign Out. Saved Changes initially links the existing financial/catalogue panel and the crypto, savings and recurring recovery panels, with explicit scopes. Do not imply the financial queue count covers all four stores. |

For the pilot, a minimal change to authenticated landing/visible navigation can coexist with links to all existing screens. Do not replace every feature route at once. Existing optional milestone argument, IDs, linked receipt route and saved-state keys remain valid. Focused forms hide top-level navigation; Back/Cancel returns to the originating list with its selected month/category/scroll and prevents accidental submission.

Retain Koin graph and existing NavBackStackEntry ownership. A destination recomposition must reuse its ViewModel; a different record needs the correct owner/arguments, and a popped entry must clean up. Do not move per-record ViewModels into a global singleton, duplicate tab workers/listeners, or have a new shell call sync startup. `ClarityApplication.kt:12–15` already starts the process singleton; `SessionSyncCoordinator.kt:18–46` owns UID workers. Account changes clear drafts, open dialogs, selected record, old-user feedback and old list state before showing the new user's data. Preserve existing generation checks.

### Visual system

Direction: a quiet, contemporary ledger with warm neutral surfaces, deep teal actions, generous whitespace and strong numeric hierarchy. Avoid gradients, decorative financial illustrations and attention-grabbing “available money” hero balances. Use native Material 3 cards, fields, dialogs, chips and NavigationBar; retain system/Roboto fonts and existing dependencies.

| Token | Light | Dark | Purpose |
|---|---|---|---|
| Canvas / surface | `#F4F7F6` / `#FFFFFF` | `#101B19` / `#192724` | Background and grouped content. |
| Main text / secondary text | `#182B29` / `#506560` | `#ECF4F0` / `#B2C5BD` | Amounts, headings / supporting labels. |
| Primary / on-primary | `#166553` / `#FFFFFF` | `#8BD5BC` / `#0D382B` | Primary action and selected controls. |
| Selected surface | `#E3F1EA` | `#203D33` | Selected tab/type without colour as the sole cue. |
| Negative / attention text | `#A83732` / `#765610` | `#FFB4AB` / `#E5C47A` | Loss/error / pending/unknown, always accompanied by words/icons. |
| Attention surface / control outline | `#FFF3D6` / `#82978D` | `#332B17` / `#718B7E` | Informational state panels / field boundaries. |

Twenty sRGB contrast pair checks are recorded in [visual-tokens.json](design/ui-ux/visual-tokens.json). Proposed normal-text pairs have minimum **5.78:1**; control outline/surface is **3.11:1** light and **4.20:1** dark. These checks cover the listed opaque pairs only. Alpha states, focus indicators, charts, system bars, disabled states and the implemented theme need separate validation. The inactive future-month arrow in the mockups is deliberately dimmed; no essential information depends on it.

Use a 4/8 dp spacing rhythm: 16 dp compact screen gutters, 16 dp card padding, 12–16 dp between related controls and 24 dp between major groups. Main actions are at least 48 dp high (52–56 preferred). Moderate 12–16 dp corner radii distinguish cards and fields without an over-carded page. Keep surfaces predominantly flat; reserve elevation for overlays.

Type roles: body 16 sp/24 line height; supporting/label 14 sp/20; section title 20 sp/28; screen title 24 sp/32; main amount 28–32 sp/36–40. Respect platform font scaling and do not encode fixed heights around text. Large values can wrap onto their own line; keep the whole number/currency inspectable. Right-align amounts in wide rows, stack on narrow rows, and use consistent two-decimal BigDecimal-derived display with grouping where the presentation formatter permits. No new arithmetic or conversion in Composables.

Use a small, consistent outlined icon family from existing Compose capabilities. Every action has a visible label or meaningful screen-reader name; decorative icons are excluded from semantics. Income/expense/crypto are identified by labels and signed values, not green/red alone. Do not add coin logos, bank/account metaphors or wallet balances that suggest integrations.

### Forms, confirmations and accessibility

- Amount first; actual date defaults to today in Asia/Colombo even if entering from a different reporting month. Display a readable date plus exact date-picker/manual-entry value. Preserve nonfuture actual-date validation, real calendar validation and existing conversion precision.
- Keep category, spending type and channel visible and editable. A proposed visually compact control still needs a full labelled touch region. Preserve current catalogue eligibility, unknown-data blocking and archived-reference exceptions. Do not introduce merchant memory, suggestions, silent scenario defaults or new category inference.
- Optional notes are labelled optional with the existing 500-character convention. Put field-specific errors next to the field and a summary only when necessary. Keep entered values visible; move focus to the first invalid field and announce the error. Do not validate by silently coercing invalid money.
- Scroll forms and dialog bodies above IME/system insets. Keyboard Next traverses all required controls; Done does not bypass validation. Save/Cancel and confirmation buttons must remain reachable with an open keyboard at 320 dp and enlarged text. Avoid nested non-scrolling regions consuming the entire viewport.
- During local acceptance disable repeat submission and show a labelled busy state. After durable acceptance, return with truthful local-save feedback. A queued immutable proposal is reviewed through recovery, not silently edited in the original form or given a new operation ID.
- Inspection clearly separates confirmed values from proposed edits/deletions. For an initial pilot, reuse the existing edit destination/state and callbacks behind an inspection presentation; do not create a parallel record model or force a new data-fetch route. If a separate read-only route is later justified, review its owner/draft/back-stack contract first.
- Delete/cancel/archive dialogs identify the item and consequence. Use Keep + Delete/Cancel/Archive, not generic Yes/OK. Preserve receipt immutability, allocation constraints and existing safe-resolution eligibility. No new undo/recreation behavior.
- TalkBack order: title → period/status → amount summary → actions → rows. Read full currency, amount, date and row state; merge descriptive row semantics while exposing separate edit/delete controls. Announce validation/save outcome once, and avoid announcing every worker poll. Provide textual category totals/percentages with bars; no colour-only charts.
- Follow system light/dark preference in both API26 fallback and newer devices. Recommend stable branded semantic palettes instead of wallpaper-dependent colours for consistent review; this is a future theme decision, not a current contrast defect. No manual theme preference is required for this pilot.

## 6. Truthful state and synchronization language

This table maps presentation to existing state; it does **not** authorize new state transitions, cancellation semantics or recovery operations. Catalogue, financial, crypto, goal and recurring state machines have different evidence/eligibility; do not collapse them into one Boolean “synced”.

| Existing evidence/state | Proposed short copy / display | Actions and safety |
|---|---|---|
| Reading data, no usable snapshot | Loading recorded transactions… | Skeleton/progress plus stable navigation; never substitute 0. |
| Authoritatively known empty period | No expenses recorded for November. | Add Expense; explain selected month. Distinguish known empty from unavailable cache. |
| Usable cached snapshot | Showing saved data. It may be incomplete or out of date. | Keep figures labelled. A cached flag alone does not prove the device is offline. Preserve independent freshness by section when snapshots differ. |
| Uncached/listener failure | Expenses unavailable. Connect or try loading again. | Retain safe prior snapshot where existing state permits; income and goal may remain usable. No surplus if income/expense is unknown or invalid. Retry reads is distinct from retrying a saved mutation. |
| Local acceptance in progress | Saving on this device… | Disable repeated Save. Do not navigate as if accepted before durable persistence succeeds. |
| Durably saved, not acknowledged | Saved on this device. Waiting for confirmation. | Show proposed amount/date separately and original confirmed contribution unchanged. No “expense recorded” assertion yet. |
| Remote outcome uncertain | Confirmation unknown. This change may already have reached the server. | Existing reconcile/safe-withdraw path only; no unrestricted dismiss, replacement, duplicate save or promise of cancellation. A timeout/access error is not proof of rejection. |
| Definitive rejection/conflict established | Change was not applied. Your confirmed record is unchanged. | Show the actual reason and existing correction/dismissal path. Do not offer this simply because an exception resembles permission/auth failure. |
| Matching acknowledgement/reconciliation | Expense recorded / Change confirmed. | Remove only that operation's pending message; preserve every other unresolved operation. Use a brief accessible acknowledgement, not a permanent “awaiting” or “success” banner. |
| Remote accepted, local cleanup/store issue | Confirmed change; saved-change cleanup needs attention. | Reflect repository evidence if exposed. Never suggest a fresh duplicate write or invent confirmation when evidence is unavailable. Preserve diagnostic detail without account/record IDs in the primary UI. |
| Auth/account changed | Sign in to check saved changes for this account. | Clear old-user forms, lists and messages immediately. Do not reinterpret retained operations as rejected or move them to the new account. |
| Catalogue unavailable/stale | Choices unavailable / Showing saved choices; server checks still apply. | No fabricated active category/source. History shows last trusted label with honest staleness or an unknown-label fallback, not a guessed built-in name. |

Keep “Based on recorded transactions” and the reporting month visible. Explain briefly: cash surplus is ordinary received income minus expenses, not a bank balance; crypto is realised result; savings are user-recorded allocations; pending is current collectible original-currency balance. Goal and pending cards must say current, independent of the selected reporting month.

“Saved Changes” is a navigation label, not a new synchronization controller. Count only the stores actually represented by available state; list other feature panels explicitly if no safe aggregate exists. Show small contextual status close to the affected record, with a scoped overview summary when relevant. Avoid stacking a generic global banner and repeated full paragraphs on every card. If one operation confirms while another is unresolved, the latter remains visible. Resolve/dismiss actions must be explained in plain language while invoking the same existing guarded callback.

Automatic sync is **process/session-dependent**: workers run while the app process has an authenticated session, including on Home; reopening resumes recovery. Do not promise immediate background execution when closed/force-stopped, display a fabricated last-sync time, or treat Activity recreation as process death. The design requires no worker, DataStore, repository, schema or protocol changes.

## 7. Annotated pilot designs

Open the [static review gallery](design/ui-ux/index.html). It has no JavaScript, service worker, remote assets, forms that submit, or duplicated financial logic. Original editable SVGs contain numbered annotations; long captures describe scrollable content, not fixed screen heights.

| Artifact | Review purpose |
|---|---|
| [01 — Overview](design/ui-ux/01-overview.svg) | What appears first: month, recorded-data qualifier, Add Expense, then the cash group; separate crypto/current pending/goal cards. |
| [02 — Add Expense](design/ui-ux/02-add-expense.svg) | Amount-first entry, readable actual date, visible classifications, optional note, honest save explanation. Filled example is user-entered, not a new default. |
| [03 — History](design/ui-ux/03-history.svg) | Date grouping, Expenses/Income, Insights, category filter, full amounts, inspect action and retained context. |
| [04 — Expense inspection](design/ui-ux/04-expense-inspection.svg) | Confirmed status, complete fields, explicit edit/delete actions, Back to the originating month/filter. |
| [05 — Cached + pending](design/ui-ux/05-pending-cached.svg) | Saved data is labelled and a local proposal remains outside confirmed totals. No false success or guaranteed background sync. |
| [06 — Dark counterpart](design/ui-ux/06-overview-dark.svg) | Same hierarchy and financial meanings using the proposed dark semantic palette. |
| [07 — 320 dp enlarged study](design/ui-ux/07-narrow-enlarged.svg) | Single-column controls and scroll contract at approximately 1.3x typography; not a runtime proof or substitute for 2.0x testing. |
| [08 — Edit state variants](design/ui-ux/08-change-state-variants.svg) | Alternatives for pending, unknown and rejected edit feedback, with the original confirmed contribution retained. |

### Fictional specimen data and exact sequence

All specimens use November 2026; fictional “today” is 4 November in Asia/Colombo. Future-month navigation is disabled in the current-month drawings, consistent with existing eligibility. Data below is invented for design review, not taken from an account or supplied as production defaults.

| Confirmed baseline | Amount / meaning |
|---|---|
| Ordinary received income | LKR 120,000.00. |
| Rent, 1 Nov | LKR 34,000.00; Housing; committed. |
| Groceries, 2 Nov | LKR 6,500.00; Food & dining; committed per entry. |
| Transport, 3 Nov | LKR 4,500.00; Transport; discretionary. |
| Total expenses / cash surplus | LKR 45,000.00 / LKR 75,000.00. |
| Spending split | Committed 40,500.00 (90%); discretionary 4,500.00 (10%). |
| Separate realised crypto net | LKR −2,500.00. |
| Current collectible pending | LKR 20,000.00 and USD 150.00, shown separately. |
| Current goal allocation | LKR 21,200.00 of 490,000.00; unrelated to this month's surplus. |

Flow: tap Add Expense → enter **750.00**, actual date **4 Nov**, choose Food & dining / Discretionary / Cash, optionally note “Team lunch” → Save. A new form's amount is blank; no scenario amount is prefilled. After durable local acceptance, the pending card shows 750 separately; expenses stay 45,000 and surplus 75,000. After matching confirmation, history contains exactly one new row, expenses become **45,750**, surplus **74,250**, and Food & dining drill-down is **7,250** (two rows). Crypto, pending balances and goal allocations do not change. Pending edit of that confirmed 750 to 900, or pending deletion, retains 750 until the existing protocol establishes an outcome.

What this reduces: one launcher decision, repeated explanatory text, and visual hunting for an amount/date or save action. It does not claim a measured ten-second entry, fewer required financial fields, or changed recovery eligibility. Existing destinations stay reachable through the map in §5, with direct cards and named secondary links.

### Likely implementation touch points — future work only

Pilot: `ui/HomeScreen.kt`, `ui/dashboard/DashboardScreen.kt`, `ui/expenses/{ExpenseFormContent,AddExpenseScreen,EditExpenseScreen,ExpensesScreen}.kt`, `ui/navigation/AppNavigation.kt`, `ui/theme/{Color,Type,Theme}.kt`; perhaps small shared presentation components under `ui/`. Income navigation must remain reachable and linked-receipt callbacks unchanged. Preserve `di/InjectedViewModel.kt` and existing Koin ownership semantics; any need to edit injection/VM state is separately justified and tested, not assumed necessary for visual polish.

Later presentation phases: existing `ui/income/`, `ui/payments/`, `ui/crypto/`, `ui/goals/`, `ui/recurring/`, `ui/CatalogueScreen.kt` and auth screens. Business calculations stay in existing domain/ViewModels/repositories. No redesign requires new chart libraries, repositories, schemas, Firebase targets, catalogue IDs or recovery stores.

## 8. Phased implementation and acceptance gates

Do not start implementation without review of this proposal. Keep slices small enough to compare the accepted production behavior before and after each change.

| Phase | Work | Required regression/acceptance checks |
|---|---|---|
| 0 — Design agreement | Review navigation names, one visual direction and state copy; resolve F01/F02 in an isolated check before promising draft retention. | Trace all 17 existing route tasks through the new map. Inspect ViewModel owners, SavedStateHandle arguments and Back behavior. Review empty/partial-cache/unknown-state copy with no live sessions. |
| **1 — Pilot** | **Overview → Add Expense → expense History/inspection**. Apply semantic tokens, cash hierarchy, single entry action, date/field hierarchy and scoped saved-change feedback. Keep links to every accepted feature. | Production-path fake navigation: correct month/category/scroll after Back; Add from an old month defaults to today's actual date; update/backdate moves the correct periods; delete Keep/Confirm works. Rapid double Save produces one durable immutable operation; no success before local acceptance/ack distinction. Pending/unknown/rejected edits retain original totals; one confirmation does not clear another proposal's feedback. Test draft recreation separately from accepted-proposal restart. |
| 2 — Income, projects and catalogues | Apply the same row/form/inspection language to income, agreements, milestones, receipts and management; clearly named Saved Changes entry. | LKR/USD fixed historical conversion; linked receipt goes to its project and cannot be directly edited/deleted; direct vs nullable milestone routes still work. Archive removes only new choices, history/corrections/inherited receipts retain references; missing catalogue blocks new selection honestly. Project cancellation/allocation/receipt eligibility unchanged; long labels and currency amounts wrap. |
| 3 — Crypto, goals, recurring | Restyle existing progress and review/history surfaces with clear suggested/confirmed separation. | Crypto loss/YTD cutoff and aggregate overflow inspection; goal opening/ledger/schedule/reached/overdue/overfunding without surplus allocation; recurring review edited amount once, cancelled due policy and consumed history after actual expense correction/deletion. No new recurring cadence, automation, notifications or forecasts. |
| 4 — Cross-feature accessibility and usability acceptance | Complete themes, focus, semantics, narrow/large-font states, evidence collection and neutral copy. | 320/360/411 dp, 1.0/1.3/2.0x text, short viewport + open keyboard, landscape and system bars. ≥48 dp non-overlapping targets; all full amounts/labels accessible; TalkBack labels/traversal/live feedback; light/dark contrast/focus/selected-state measurements. Time five representative familiar expense attempts, record actual results against NFR01; do not invent a usability study. |

Common gates for every phase:

1. **Ownership/lifecycle:** retain destination-scoped ViewModels, typed/null route arguments and disposal; recomposition and Activity recreation do not duplicate initialization. SavedStateHandle retains the intended target. Test process restart independently; do not reuse an Activity-recreation assertion as restart evidence.
2. **Submission and recovery:** duplicate taps/reopen keep identity and immutable payload; local save/storage failure retains truthful feedback; pending ≠ confirmed; access/timeouts with prior outstanding work ≠ definitive rejection. Use genuinely suspending isolated fakes and existing protocol tests, not a new UI-side queue. No financial writes for visual acceptance.
3. **Account/month/target changes:** clear old-user forms, dialogs, labels, amounts and messages; delayed callbacks cannot restore them. Preserve reporting selection only within the same account as intended. Month/filter changes cancel old reporting scopes and do not erase another operation's status.
4. **Reporting:** ordinary income/expenses/surplus independent of crypto, savings and current collectible balances; parent outstanding counted once; mixed currencies separate; category totals/split exact. Unavailable/malformed/overflow data is not zero; valid individual crypto rows remain inspectable when only aggregation fails.
5. **Navigation and destructive actions:** all existing entry points remain reachable; Back does not submit or resubmit; draft and filter context preserved appropriately. Keep/Confirm works with keyboard and enlarged text. Linked receipt and consumed occurrence protections remain intact.
6. **Safety:** tests use fake-only graphs or designated isolated local-emulator data. Inspect saved auth/recovery **before Application startup** on a separate device, because instrumentation can start production synchronization before its test body. Do not use Pixel_6a or a live account to inspect the redesign. No writes, rule publication, schema migration or recovery processing is part of this design task.

Existing test assertions worth retaining include `DashboardNavigationTest` (category/backdate/delete/quick-add, independent unavailable sections, linked receipt, crypto overflow/malformed rows), `PaymentNavigationTest` (direct/milestone/editor navigation), `AuthAccessibilityTest` (validation/keyboard), `IncomeListAccessibilityTest` (nonzero scroll viewport), `CryptoFeedbackUiTest` (operation-derived feedback), `GoalNavigationTest`, `RecurringNavigationTest`, `CatalogueNavigationTest` and `DiViewModelOwnershipTest` (owner reuse/pop and arguments). They are a regression base, not sufficient evidence for the new visual design.

The accepted API26 work and Koin migration are completed baseline work, not new proposed milestones. A stable newer-device verification run is still needed for changed UI. Keep the documented API35 native instrumentation/system instability unresolved until evidence identifies and resolves it; do not claim a clean full-suite API35 pass. The 2.0x/TalkBack/ten-second usability gates above remain unexecuted.

## 9. Decisions for review

The proposal uses sensible defaults and needs no operational permission now. Before implementation, the useful design decisions are:

- Approve the **Overview / History / Plans / More** structure, particularly whether “Plans” clearly describes existing agreements, the savings goal and recurring obligations. If the label tests poorly, choose another hub name without changing feature scope.
- Approve **stable teal light/dark palettes** rather than wallpaper-derived accents. Keep system theme following; do not introduce a theme settings feature.
- Confirm the proposed inspection-first presentation is preferable to immediately showing the existing editor on a history tap. The first implementation can reuse the current owner/state and edit callback; any extra route requires its own navigation-state review.

These are design preferences, not unresolved financial definitions. No bank linking, budgets, AI, holdings/trading, subscriptions, multiple goals, automatic transfers, notifications, merchant memory, password reset, new reporting arithmetic or recovery framework is proposed.

## 10. Work performed and deliverable verification

Performed now: Git HEAD/status/diff inspection; source route/screen/dialog/state inspection; reading relevant existing test assertions and requirement pages; attachment SHA verification; official web research and three public screenshot inspections; generation of eight original annotated SVGs, a static HTML gallery and token/contrast data; local headless-browser rendering for visual review; artifact structure/link checks and source-preservation verification; sanitized ZIP packaging with SHA-256 manifest/member comparison.

No Gradle, Android unit, instrumentation, Firestore emulator or live acceptance suite was run in this design-only task. Existing test names above describe assertions inspected, not executed results. No claim about deployed rules, live balances or runtime synchronization was made. Headless browser rendering used only local design SVGs with a fresh profile; it did not start the Android Application. An initial sandboxed browser render was blocked by Windows access restrictions; the permitted local render then succeeded. Initial direct screenshot downloads were network-blocked; the authorized public-image downloads succeeded. Research images and browser diagnostics are ignored build output and excluded from the review archive.

Source-preservation checks compare SHA-256 for all **203 tracked files plus the four pre-existing unrelated files** captured before design creation. `git diff --check` and tracked diff checks must stay empty. Final packaging contains only the report, eight SVGs, `design/ui-ux/index.html`, `design/ui-ux/visual-tokens.json` and a relative-path SHA-256 manifest. The manifest deliberately excludes its own recursive checksum; the ZIP-member comparison separately verifies its exact bytes too. Review the accompanying manifest for final file sizes and hashes.

Verification ledger for this pass:

| Check executed | Result |
|---|---|
| `git rev-parse HEAD`, `git status --short`, staged/unstaged diff inspection | Requested baseline; no original tracked edits; four unrelated files preserved. |
| Python SHA-256 comparison of attachments and extracted source metadata | Both supplied PDFs match the recorded hashes listed in §1. |
| Python artifact validation (UTF-8, trailing whitespace, SVG XML/title/description, local links, HTML parser) | Eleven report/design files valid; eight SVGs; no scripts, submit controls or remote gallery assets. |
| Static sRGB contrast calculation | Twenty listed pairs pass their 4.5:1 text / 3:1 control-outline targets. |
| Fictional sample consistency assertions | Baseline 45,000 expenses / 75,000 surplus; confirmed 750 changes these to 45,750 / 74,250; category subtotal 7,250. |
| Local Chrome `--headless --disable-background-networking --screenshot` renders, fresh profiles | All eight designs rendered for visual review; no Android Application or account session opened. |
| Privacy scan | No email/account identifiers, credentials, private keys, bearer tokens or personal local-user paths in deliverable files. Source evidence paths and fictional labels are retained. |
| `git diff --check`, SHA comparison of the 207 protected files | No whitespace errors or changes to protected files. New artifacts also checked for trailing whitespace. |
| Review ZIP member list and SHA-256 comparison | Twelve files: eleven report/design files plus manifest; every archived member matches its current source bytes. |

Archive: `D:\clarity-finance-ui-ux-review-20261104.zip` (a new filename is used if the name already exists). `UI_UX_REVIEW_MANIFEST.json` lists the eleven content files by repository-relative path, size and SHA-256; its own bytes are verified directly against the archive member. Public reference screenshots, browser profiles and helper scripts are excluded.

The artifacts and this report are uncommitted design preparation. Implementation, device testing, live acceptance and visual usability measurements remain future work after review.
