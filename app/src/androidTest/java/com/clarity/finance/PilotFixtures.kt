package com.clarity.finance

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.io.File

/** Fictional, deterministic test graph. It never includes production repositories or workers. */
class PilotFixtures : ReportingRepository, ExpenseRepository {
    val today = SavingsMath.today().toString()
    val session = MutableStateFlow<User?>(User("pilot-a", "Fictional test account"))
    val auth = object : AuthRepository {
        override val authState = session
        override suspend fun login(email: String, password: String) = error("Unused")
        override suspend fun register(email: String, password: String) = error("Unused")
        override suspend fun logout() { authState.value = null }
    }
    val rows = MutableStateFlow(listOf(
        Expense("rent", 3400000, categoryId = "housing", paymentChannelId = "cash", spendingType = "committed", expenseDate = today, note = "Rent", updatedAt = Timestamp(1, 0)),
        Expense("groceries", 650000, categoryId = "food_dining", paymentChannelId = "cash", spendingType = "committed", expenseDate = today, note = "Groceries", updatedAt = Timestamp(1, 0)),
        Expense("transport", 450000, categoryId = "transport", paymentChannelId = "cash", spendingType = "discretionary", expenseDate = today, note = "Transport", updatedAt = Timestamp(1, 0))))
    val status = MutableStateFlow(ReportingRecovery())
    var cached = false
    var unavailable = false
    var reportingStarts = 0
    val submitted = mutableListOf<Expense>()
    var admission: CompletableDeferred<Unit>? = null
    override fun expenses(uid: String, month: ReportingMonth): Flow<ReportingBatch<Expense>> {
        reportingStarts++
        return rows.map {
        ReportingBatch(if (unavailable) null else if (uid == "pilot-a") it.filter { r -> month.contains(r.expenseDate) } else emptyList(), cached,
            error = if (unavailable) "Expenses unavailable in this isolated example" else null)
    }
    }
    override fun income(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(if (uid == "pilot-a") listOf(
        Income(id = "salary", sourceId = "salary", receiptDate = today, currency = "LKR", originalAmountStr = "120000", lkrAmountMinor = 12000000,
            conversionBasis = "native_lkr", conversionRateStr = "1", note = "Fictional salary")) else emptyList(), cached))
    override fun crypto(uid: String, month: ReportingMonth) = flowOf(ReportingBatch(emptyList<CryptoResult>(), cached))
    override fun projects(uid: String) = flowOf(ReportingBatch(emptyList<ExpectedPayment>(), cached))
    override fun recovery(uid: String) = if (uid == "pilot-a") status else flowOf(ReportingRecovery())
    override fun getExpenses(userId: String) = rows
    override suspend fun getExpense(userId: String, expenseId: String) = rows.value.firstOrNull { it.id == expenseId }
        ?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Expense not found or already deleted."))
    override suspend fun enqueueExpense(userId: String, expense: Expense): Result<Unit> {
        submitted += expense
        admission?.await() // genuinely suspending local admission, no blocking Mockito answer
        status.value = ReportingRecovery(pending = 1)
        return Result.success(Unit)
    }
    override suspend fun updateExpense(userId: String, expense: Expense, originalUpdatedAt: Timestamp): Result<Unit> {
        rows.value = rows.value.filterNot { it.id == expense.id } + expense.copy(updatedAt = Timestamp(2, 0))
        return Result.success(Unit)
    }
    override suspend fun deleteExpense(userId: String, expenseId: String, originalUpdatedAt: Timestamp): Result<Unit> {
        rows.value = rows.value.filterNot { it.id == expenseId }; return Result.success(Unit)
    }
    override fun retryFailedExpense(userId: String, expense: Expense) = error("No recovery retry in visual fixtures")
    fun graph() = isolatedAppGraph(auth = auth, expense = this, reporting = this)
}

fun ComposeTestRule.pilotScreenshot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("pilotScreenshots") != "true") return
    val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "pilot-screenshots").apply { mkdirs() }
    val suffix = InstrumentationRegistry.getArguments().getString("pilotScreenshotSuffix").orEmpty()
    File(dir, "$name$suffix.png").outputStream().use { stream ->
        val hasDialog = onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        // Compose cannot capture a Dialog window below API 28. The inspected, fake-only
        // device permits a full-window capture here; this never captures a sign-in flow.
        if (hasDialog && android.os.Build.VERSION.SDK_INT < 28) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100, 2000)
            // Platform dialog/IME animations do not use Compose's test clock.
            android.os.SystemClock.sleep(400)
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
            return
        }
        val target = when {
            hasDialog -> onAllNodes(isDialog()).onLast()
            onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty() -> onAllNodes(isPopup()).onLast()
            else -> onRoot()
        }
        target.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
}

/** Navigation selector adaptation; feature assertions and fixture behavior remain unchanged. */
fun ComposeTestRule.pilotClick(requested: String) {
    val label = when (requested) { "Dashboard" -> "Overview"; "Add Payment" -> "Add agreement"; "Previous month" -> "Previous month"; "Separate crypto history" -> "Crypto history"; "Monthly history" -> "History"; "Sources, categories & recovery" -> "Categories & Sources"; else -> requested }
    val matcher = (hasText(label) or hasContentDescription(label)) and hasClickAction()
    if (label == "Spending insights" && onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
        onNode(hasText("History") and hasClickAction()).performClick()
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) onNodeWithContentDescription("History options").performClick()
    }
    if (label == "All recorded expenses" && onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
        onNodeWithContentDescription("History options").performClick()
    }
    if (label in listOf("Housing", "Transport", "Food & Dining", "All categories") && onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
        onNodeWithTag("history-category-filter").performScrollTo().performClick()
    }
    if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
        val hub = when (label) { "Pending Payments", "Savings Goal", "Recurring Expenses" -> "Plans"; "Crypto Results", "Categories & Sources" -> "More"; else -> null }
        if (hub != null) onNode(hasText(hub) and hasClickAction()).performClick()
    }
    val node = onNode(matcher)
    try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo().assertIsDisplayed() }
    node.performClick()
}
