package com.clarity.finance

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.viewmodel.DashboardViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.koin.core.context.GlobalContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Explicitly opt-in LOCAL emulator writers. Never resolves the default live Firestore/Auth clients.
 * A host runner force-stops the dedicated AVD app BETWEEN A and B. No clear/uninstall/wipe is needed.
 */
class FinalRequirementsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun local(): Pair<FirebaseAuth, FirebaseFirestore> {
        assumeTrue("Requires an explicit local-emulator fixture run", InstrumentationRegistry.getArguments().getString("auditLocal") == "true")
        val options = FirebaseOptions.Builder().setProjectId("demo-clarity-final-audit-device")
            .setApplicationId("1:1234567890:android:0123456789abcdef").setApiKey("local-emulator-only-key").build()
        val app = FirebaseApp.getApps(context).find { it.name == "final-requirements-local" }
            ?: FirebaseApp.initializeApp(context, options, "final-requirements-local")
        check(app.name != FirebaseApp.DEFAULT_APP_NAME && app.options.projectId!!.startsWith("demo-"))
        val auth = FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2", 9099) }
        val db = FirebaseFirestore.getInstance(app).also { it.useEmulator("10.0.2.2", 8080) }
        return auth to db
    }
    private fun store() = SubmissionRecoveryStore(GlobalContext.get().get<DataStore<Preferences>>())
    private fun operation(index: Int) = FinancialOperation("audit_offline_op_$index", "EXPENSE", "CREATE", "audit_offline_e_$index",
        expense = Expense("audit_offline_e_$index", 100, categoryId = "transport", paymentChannelId = "cash",
            spendingType = "discretionary", expenseDate = "2026-01-31", note = "Local audit offline restart fixture"))
    private fun expense(index: Int) = mapOf("amountMinor" to 100L, "currency" to "LKR", "categoryId" to "other",
        "paymentChannelId" to "cash", "spendingType" to "discretionary", "expenseDate" to "2026-01-31",
        "note" to "Local audit baseline $index", "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
    private fun metrics(values: Map<String, Any>) {
        val file = java.io.File(context.filesDir, "final_requirements_metrics.txt")
        file.appendText(values.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
    }
    @Test fun phaseA_seedCacheMeasureAndPersistOffline() = runBlocking {
        withTimeout(120000) {
            val (auth, db) = local()
            db.enableNetwork().await()
            val checkpoint = context.getSharedPreferences("final_requirements_checkpoint", Context.MODE_PRIVATE).getString("owner", null)
            check(auth.currentUser == null || auth.currentUser?.uid == checkpoint) { "Unrelated local test account; stop before writing." }
            val resumed = auth.currentUser != null
            val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
            context.getSharedPreferences("final_requirements_checkpoint", Context.MODE_PRIVATE).edit().putString("owner", uid).commit()
            val root = db.collection("users").document(uid)
            if (resumed) {
                val existing = root.collection("expenses").get(Source.SERVER).await()
                check(existing.size() == 1000 && existing.documents.all { it.id.startsWith("audit_baseline_e_") && it.getString("note")?.startsWith("Local audit baseline ") == true }) { "Baseline changed; inspect rather than overwriting fixtures." }
                check(store().getAllRecords(uid).isEmpty()) { "Saved local work already exists; do not reseed." }
            } else {
            repeat(2) { batchIndex ->
                val batch = db.batch()
                repeat(500) { index -> val i = batchIndex * 500 + index; batch.set(root.collection("expenses").document("audit_baseline_e_$i"), expense(i)) }
                batch.commit().await()
            }
            val batch = db.batch()
            batch.set(root.collection("incomeReceipts").document("audit_baseline_income"), mapOf("sourceId" to "salary", "receiptDate" to "2026-01-31",
                "currency" to "LKR", "originalAmountStr" to "2000", "lkrAmountMinor" to 200000L, "conversionBasis" to "native_lkr", "conversionRateStr" to "1", "note" to "Local audit baseline",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            batch.set(root.collection("cryptoResults").document("audit_baseline_crypto"), mapOf("direction" to "gain", "amountMinor" to 100L, "resultDate" to "2026-01-31",
                "note" to "Local audit baseline", "lastOperationId" to "audit_baseline_crypto", "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            batch.set(root.collection("expectedPayments").document("audit_baseline_project"), mapOf("type" to "project", "title" to "Local audit baseline", "sourceId" to "freelance",
                "currency" to "LKR", "dueDate" to "2026-12-31", "agreedAmountMinor" to 10000L, "allocatedAmountMinor" to 0L, "directReceivedAmountMinor" to 0L,
                "milestoneReceivedAmountMinor" to 0L, "isCancelled" to false, "note" to "", "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            batch.commit().await()
            }
            // Warm the exact date-bounded production queries from server, not a fake cache.
            val reports = FirestoreReportingRepository(db, store()); val month = ReportingMonth(java.time.YearMonth.of(2026, 1))
            reports.expenses(uid, month).first { !it.fromCache && it.records?.size == 1000 }
            reports.income(uid, month).first { !it.fromCache && it.records?.size == 1 }
            reports.crypto(uid, month).first { !it.fromCache && it.records?.size == 1 }
            reports.projects(uid).first { !it.fromCache && it.records?.size == 1 }
            db.disableNetwork().await()
            val authRepository = object : AuthRepository {
                override val authState = MutableStateFlow<User?>(User(uid, "local-fixture@example.test"))
                override suspend fun login(email: String, password: String) = error("Unused")
                override suspend fun register(email: String, password: String) = error("Unused")
                override suspend fun logout() = error("Unused")
            }
            val clock = Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"), ZoneOffset.UTC)
            val timings = mutableListOf<Long>()
            repeat(10) {
                val start = SystemClock.elapsedRealtime(); val owners = ViewModelStore()
                val vm = withContext(Dispatchers.Main) { DashboardViewModel(authRepository, FirestoreReportingRepository(db, store()), clock).also { owners.put("dashboard", it) } }
                try {
                    val state = vm.state.first { it.surplusText != null && it.cryptoText != null && it.pendingAmounts != null }
                    assertEquals("1000.00", state.expensesText); assertEquals("2000.00", state.incomeText); assertEquals("1000.00", state.surplusText)
                    assertEquals("+1.00", state.cryptoText); assertEquals(mapOf("LKR" to "100.00"), state.pendingAmounts)
                    assertTrue(state.expenses.fromCache); assertEquals(1000, state.expenses.records!!.size)
                    timings += SystemClock.elapsedRealtime() - start
                } finally { withContext(Dispatchers.Main) { owners.clear() } }
            }
            metrics(mapOf("cache_1000_dashboard_ms" to timings.joinToString(","), "cache_under_2000ms" to timings.count { it <= 2000 }))
            assertTrue("At least 9/10 cached dashboard loads must finish in 2 seconds", timings.count { it <= 2000 } >= 9)
            val remote = FirestoreFinancialRemote(db) { auth.currentUser?.uid }; val queue = FinancialQueue(remote, store())
            val saveTimes = mutableListOf<Long>()
            repeat(10) { i ->
                val op = operation(i); val start = SystemClock.elapsedRealtime()
                queue.submit(uid, op); queue.submit(uid, op) // Duplicate tap has the same identity/payload.
                saveTimes += SystemClock.elapsedRealtime() - start
            }
            assertEquals((0..9).map(::operation), queue.pending(uid).map { it.operation })
            assertEquals(1000, root.collection("expenses").get(Source.CACHE).await().size())
            metrics(mapOf("offline_durable_save_ms" to saveTimes.joinToString(","), "save_under_1000ms" to saveTimes.count { it <= 1000 }))
            assertTrue("At least 9/10 durable local saves must finish within 1 second", saveTimes.count { it <= 1000 } >= 9)
            // Network deliberately remains disabled in this process. Host force-stops it before phase B.
        }
    }
    @Test fun phaseB_processRestartReconnectAndReconcileExactlyOnce() = runBlocking {
        withTimeout(90000) {
            val (auth, db) = local(); val uid = auth.currentUser?.uid ?: error("Original local emulator account did not survive restart.")
            check(uid == context.getSharedPreferences("final_requirements_checkpoint", Context.MODE_PRIVATE).getString("owner", null))
            db.disableNetwork().await()
            val queue = FinancialQueue(FirestoreFinancialRemote(db) { auth.currentUser?.uid }, store())
            assertEquals((0..9).map(::operation), queue.pending(uid).map { it.operation })
            val root = db.collection("users").document(uid)
            assertEquals(1000, root.collection("expenses").get(Source.CACHE).await().size())
            metrics(mapOf("restart_retained_operations" to queue.pending(uid).size))
            db.enableNetwork().await()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val coordinator = SessionSyncCoordinator(object : AuthRepository {
                    override val authState = MutableStateFlow<User?>(User(uid, "local-fixture@example.test"))
                    override suspend fun login(email: String, password: String) = error("Unused")
                    override suspend fun register(email: String, password: String) = error("Unused")
                    override suspend fun logout() = error("Unused")
                }, listOf(SyncWorker("transactions") { queue.synchronize(it) }), scope)
                coordinator.start(); coordinator.start()
                while (queue.pending(uid).isNotEmpty()) delay(50)
                assertEquals(1010, root.collection("expenses").get(Source.SERVER).await().size())
                assertEquals(10, root.collection("syncOperations").get(Source.SERVER).await().size())
                for (i in 0..9) {
                    val op = operation(i)
                    queue.submit(uid, op) // Even an acknowledged replay is reconciled before any new mutation.
                }
                while (queue.pending(uid).isNotEmpty()) delay(50)
                assertEquals(1010, root.collection("expenses").get(Source.SERVER).await().size())
                assertEquals(10, root.collection("syncOperations").get(Source.SERVER).await().size())
                assertEquals(200000L, root.collection("incomeReceipts").document("audit_baseline_income").get(Source.SERVER).await().getLong("lkrAmountMinor"))
                assertEquals(100L, root.collection("cryptoResults").document("audit_baseline_crypto").get(Source.SERVER).await().getLong("amountMinor"))
                assertEquals(0L, root.collection("expectedPayments").document("audit_baseline_project").get(Source.SERVER).await().getLong("directReceivedAmountMinor"))
                metrics(mapOf("restart_reconciled_expenses" to 10, "restart_duplicate_receipts" to 0, "unrelated_finances_unchanged" to true))
            } finally { scope.cancel(); db.enableNetwork().await() }
        }
    }
    @Test fun phaseC_timedExpenseCaptureAndFeedbackAfterFamiliarisation() {
        val (auth, db) = local()
        val uid = auth.currentUser?.uid ?: error("Run local baseline/restart phases first.")
        check(uid == context.getSharedPreferences("final_requirements_checkpoint", Context.MODE_PRIVATE).getString("owner", null))
        val disk = store(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val queue = FinancialQueue(FirestoreFinancialRemote(db) { auth.currentUser?.uid }, disk)
        val baselineCount = runBlocking {
            assertTrue(queue.pending(uid).isEmpty()); db.enableNetwork().await()
            val count = db.collection("users").document(uid).collection("expenses").get(Source.SERVER).await().size()
            db.disableNetwork().await(); count
        }
        val owner = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(User(uid, "local-fixture@example.test"))
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() = error("Unused")
        }
        val graph = isolatedAppGraph(owner, expense = FirestoreExpenseRepository(db, queue, scope), recovery = queue)
        try {
            compose.setContent { com.clarity.finance.ui.theme.ClarityTheme { IsolatedAppNavigation(graph) } }
            compose.onNodeWithText("Expenses").performScrollTo().performClick()
            val times = mutableListOf<Long>(); val feedback = mutableListOf<Long>()
            repeat(11) { attempt ->
                val start = SystemClock.elapsedRealtime()
                compose.onNodeWithText("Add Expense").performClick()
                compose.onNodeWithText("Amount (LKR)").performTextInput("850")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNodeWithText("Save", substring = false).performScrollTo()
                val saveStart = SystemClock.elapsedRealtime()
                compose.onNodeWithText("Save", substring = false).performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("Add Expense").fetchSemanticsNodes().size == 1 }
                if (attempt > 0) {
                    times += SystemClock.elapsedRealtime() - start // One familiarisation attempt, note blank.
                    feedback += SystemClock.elapsedRealtime() - saveStart
                }
            }
            runBlocking { assertEquals(11, queue.pending(uid).size) }
            metrics(mapOf("five_UI_capture_ms" to times.take(5).joinToString(","), "capture_under_10000ms" to times.take(5).count { it <= 10000 }, "ten_UI_save_feedback_ms" to feedback.joinToString(","), "feedback_under_1000ms" to feedback.count { it <= 1000 }))
            assertTrue("At least 4/5 controlled capture attempts must finish within ten seconds", times.take(5).count { it <= 10000 } >= 4)
            assertTrue("At least 9/10 actual local-save UI feedback observations must finish within one second", feedback.count { it <= 1000 } >= 9)
            runBlocking {
                db.enableNetwork().await()
                val worker = scope.launch { queue.synchronize(uid) }
                try { withTimeout(60000) { while (queue.pending(uid).isNotEmpty()) delay(50) } }
                finally { worker.cancelAndJoin() }
                assertEquals(baselineCount + 11, db.collection("users").document(uid).collection("expenses").get(Source.SERVER).await().size())
            }
        } finally { scope.cancel(); runBlocking { db.enableNetwork().await() } }
    }
    @Test fun phaseD_localEmailAuthenticationAndSignOut() = runBlocking {
        withTimeout(30000) {
            local() // Explicit opt-in and demo-only guard; this auth client has a separate SDK namespace.
            val base = FirebaseApp.getInstance("final-requirements-local")
            val app = FirebaseApp.getApps(context).find { it.name == "final-requirements-auth-only" }
                ?: FirebaseApp.initializeApp(context, base.options, "final-requirements-auth-only")
            val sdk = FirebaseAuth.getInstance(app).also { it.useEmulator("10.0.2.2", 9099) }
            check(sdk.currentUser == null) { "Unrelated local auth session; do not sign it out." }
            val repository = FirebaseAuthRepository(sdk)
            val email = "local-audit-${java.util.UUID.randomUUID()}@example.test"
            val password = "Local-emulator-fixture-only-123!" // Public dummy, never a live account credential.
            val registered = repository.register(email, password).getOrThrow()
            assertEquals(email, registered.email); assertEquals(registered.uid, repository.authState.first { it != null }!!.uid)
            repository.logout(); assertNull(repository.authState.first())
            assertTrue(repository.login(email, "incorrect-local-fixture").isFailure)
            assertNull(sdk.currentUser)
            assertEquals(registered.uid, repository.login(email, password).getOrThrow().uid)
            repository.logout(); assertNull(repository.authState.first())
            metrics(mapOf("local_email_register_login_wrong_password_logout" to true))
        }
    }
}
