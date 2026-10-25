# Clarity Finance

A personal finance application for tracking expenses and managing budgets. Built with Android, Kotlin, Jetpack Compose, and Firebase.

## Setup Instructions

1. **Clone the repository**
2. **Add Firebase Configuration**: Obtain `google-services.json` from the Firebase Console (for the Android app `com.clarity.finance`) and place it in the `app/` directory. (This file is ignored by git for security).
3. **Build the Project**: Run `./gradlew assembleDebug` to build.

## Expense Data Schema (Firestore)

Expenses are stored at `/users/{userId}/expenses/{expenseId}`.

- `amountMinor` (Number): The amount in LKR cents (strictly positive). E.g., `1000` means 10.00 LKR.
- `currency` (String): Must be `"LKR"`.
- `categoryId` (String): E.g., `"Food & Dining"`, `"Transport"`, `"Other"`.
- `paymentChannelId` (String): E.g., `"Cash"`, `"Primary bank/card"`.
- `spendingType` (String): `"committed"` or `"discretionary"`.
- `expenseDate` (String): Date of expense in `yyyy-MM-dd` ISO format.
- `note` (String, Optional): Max 500 characters.
- `createdAt` (Timestamp): Server timestamp of creation.
- `updatedAt` (Timestamp): Server timestamp of last update.

*Note on Local Pending Writes: The UI relies on `snapshot.metadata.hasPendingWrites()` to identify and render offline pending records.*

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
