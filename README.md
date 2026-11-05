# Clarity Finance

A manual personal-finance application for received income, expenses, pending agreements and milestones, realised crypto results, one explicit savings goal, and user-confirmed recurring expenses. Built with Kotlin, Jetpack Compose, Koin and Firebase.

## Current application and evidence

The signed-in landing is Overview, with History / Plans / More navigation and a prominent Add Expense action. The shared teal design follows the system light/dark setting. Cash surplus, crypto, current pending balances and recorded savings remain separate; the app does not transfer money or verify a bank balance.

- [Final FR01-FR21 acceptance assessment and limitations](FINAL_APP_ACCEPTANCE.md)
- [Completed human capture feedback and isolated review procedure](HUMAN_ACCEPTANCE.md)
- [Design conventions and rollout checkpoints](design/phase-a/CONVENTIONS.md)
- [Phase B](PHASE_B_UI_UX_IMPLEMENTATION.md), [Phase C](PHASE_C_UI_UX_IMPLEMENTATION.md), [Phase D](PHASE_D_UI_UX_IMPLEMENTATION.md), and [approved colours](COLOUR_REVIEW.md)

History sorts actual transaction dates descending, then immutable server creation time descending, with a stable ID fallback for equal/unknown times. Filters preserve that order. Server-saved time can differ from original offline-entry order. App-wide sync runs for an eligible signed-in process/session; it resumes after reopening and is not guaranteed while force-stopped. Pending proposals never silently become confirmed totals.

Production source uses Firebase; isolated Android tests and the opt-in debug human-review activity use fictional repositories. Before starting any debug/instrumentation build on an existing device, inspect saved default auth/recovery: Application starts synchronization before a test body. The guarded local launcher is documented in HUMAN_ACCEPTANCE.md. Never treat fixture acknowledgement as cloud evidence. Credentials/configuration, build outputs and review archives are excluded from source finalization.

Human five-capture correctness is verified, but timing was approximate clock observation, not a measured stopwatch pass. TalkBack remains unexecuted and API35 instability remains documented. Original assignment PDFs remain the authority for submission/report requirements; phase reports retain actual failures and corrected reruns.

## Setup Instructions

1. **Clone the repository**
2. **Add Firebase Configuration**: Obtain `google-services.json` from the Firebase Console (for the Android app `com.clarity.finance`) and place it in the `app/` directory. (This file is ignored by git for security).
3. **Build the Project**: Run `./gradlew assembleDebug` to build.

## Expense Data Schema (Firestore)

Expenses are stored at `/users/{userId}/expenses/{expenseId}`.

- `amountMinor` (Number): The amount in LKR cents (strictly positive). E.g., `1000` means 10.00 LKR.
- `currency` (String): Must be `"LKR"`.
- `categoryId` (String): Stable catalogue ID, e.g. `"food_dining"`, `"transport"`, `"other"`, or an owner-scoped custom ID; labels can be renamed/archived.
- `paymentChannelId` (String): E.g., `"cash"`, `"primary_bank_card"`.
- `spendingType` (String): `"committed"` or `"discretionary"`.
- `expenseDate` (String): Date of expense in `yyyy-MM-dd` ISO format.
- `note` (String, Optional): Max 500 characters.
- `createdAt` (Timestamp): Server timestamp of creation.
- `updatedAt` (Timestamp): Server timestamp of last update.

*Pending proposals are persisted in the UID-scoped durable recovery queue before submission. SDK pending-write/cache metadata is also inspected; it does not by itself establish confirmation. Read-only creation timestamps are not transaction dates or new write fields.*

## Security Rules Deployment

To enforce the strict data model above, deploy the security rules:
```bash
firebase deploy --only firestore:rules
```

## Pending Payments and Project Milestones

Pending payments support standalone agreements, project milestones, direct receipts, and cancellation that retains payment history. Amounts use exact integer minor units, and linked income receipts are protected from independent edits and deletion.

- [Repair behavior and maintenance checks](REPAIR_VERIFICATION.md)
- [Reviewed security rules and publication procedure](RULES_PUBLICATION_REVIEW.md)
- [Sanitized live acceptance results and opt-in read-only audit](LIVE_VERIFICATION.md)

The accepted online checklist does not establish live network-interruption or account-switch behavior. Those scenarios have separate coroutine unit coverage. The live audit is skipped unless explicitly opted in and requires an existing signed-in session.

## Realised crypto results

Authenticated Home includes Crypto Results for separate LKR gains/losses, backdated edits/deletion, and labelled monthly/YTD reporting. See [FR07 behavior, recovery, rules, verification and manual checks](CRYPTO_RESULTS.md). The reviewed rules were manually published by the user before the accepted [live checklist](FR07_LIVE_ACCEPTANCE.md) and [sanitized live results](FR07_LIVE_RESULTS.md). The later operation-feedback correction has separate automated verification; no additional live samples were created for it.

## Monthly dashboard, history and spending insights

Home → Dashboard shows monthly recorded income, expenses and cash surplus, separate realised crypto, and current pending balances by currency. Monthly history reuses existing editors; category drill-down and spending insights update through Firestore listeners. See [FR11–13/FR18 behavior, queries, verification and manual checks](MONTHLY_REPORTING.md). Cached/unavailable data and unsynchronised proposals are labelled explicitly. Goals and recurring expenses remain outside this milestone.

The [sanitized read-only live results](MONTHLY_REPORTING_LIVE_RESULTS.md) record server/UI comparisons and cached restart/reconnection separately from the later optional-note/crypto-overflow corrections and their 142 unit/four isolated dashboard Android checks.
