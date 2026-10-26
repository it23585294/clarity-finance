package com.clarity.finance

import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.dataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Read-only live audit. Uses the existing session; never signs in or writes Firestore data. */
class LiveFirestoreAuditTest {
    @Test fun readServerStateAndRecovery() = runBlocking {
        assumeTrue("Live audit requires explicit opt-in and an existing signed-in session",
            InstrumentationRegistry.getArguments().getString("liveAudit") == "true")
        withTimeout(60000L) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val firestore = FirebaseFirestore.getInstance()
            assertEquals("clarity-1c83e", FirebaseApp.getInstance().options.projectId)
            assertEquals("firestore.googleapis.com", firestore.firestoreSettings.host)
            assertTrue(firestore.firestoreSettings.isSslEnabled)
            suspend fun serverQuery(query: Query): QuerySnapshot {
                repeat(3) { attempt ->
                    try { return query.get(Source.SERVER).await() }
                    catch (e: FirebaseFirestoreException) {
                        if (attempt == 2 || e.code !in setOf(FirebaseFirestoreException.Code.UNAVAILABLE,
                                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)) throw e
                        println("LIVE_AUDIT_RETRY transient_server_read attempt=" + (attempt + 1))
                        delay(2000L)
                    }
                }
                error("Server read did not complete")
            }
            val uid = checkNotNull(FirebaseAuth.getInstance().currentUser) { "User must already be signed in" }.uid
            val root = firestore.collection("users").document(uid)
            val projects = serverQuery(root.collection("expectedPayments"))
            assertFalse(projects.metadata.isFromCache)
            assertFalse(projects.metadata.hasPendingWrites())
            val output = JSONObject().put("projectId", "clarity-1c83e").put("uid", uid)
                .put("auditedAtUtc", java.time.Instant.now().toString())
            fun document(doc: DocumentSnapshot, fields: List<String>): JSONObject {
                assertFalse(doc.metadata.isFromCache)
                assertFalse(doc.metadata.hasPendingWrites())
                val data = JSONObject().put("id", doc.id).put("exists", doc.exists())
                fields.forEach { field -> data.put(field, doc.get(field) ?: JSONObject.NULL) }
                listOf("createdAt", "updatedAt").forEach { field ->
                    val timestamp = doc.getTimestamp(field)
                    data.put(field, timestamp?.let { java.time.Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()).toString() } ?: JSONObject.NULL)
                }
                return data
            }
            val projectFields = listOf("type", "title", "sourceId", "currency", "dueDate", "note",
                "agreedAmountMinor", "allocatedAmountMinor", "directReceivedAmountMinor",
                "milestoneReceivedAmountMinor", "isCancelled", "lastReceiptId", "lastMilestoneId", "lastMilestoneOpId")
            val projectArray = JSONArray()
            for (project in projects.documents) {
                val data = document(project, projectFields)
                val milestones = serverQuery(project.reference.collection("milestones"))
                assertFalse(milestones.metadata.isFromCache)
                assertFalse(milestones.metadata.hasPendingWrites())
                val children = JSONArray()
                milestones.documents.forEach { child ->
                    children.put(document(child, listOf("title", "dueDate", "agreedAmountMinor",
                        "receivedAmountMinor", "isCancelled", "lastReceiptId", "lastMilestoneOpId")))
                }
                data.put("milestones", children)
                projectArray.put(data)
            }
            output.put("projects", projectArray)
            val old = JSONArray()
            val requestedIds = InstrumentationRegistry.getArguments().getString("checkProjectIds", "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            for (id in requestedIds) {
                old.put(document(root.collection("expectedPayments").document(id).get(Source.SERVER).await(), emptyList()))
            }
            output.put("previouslyRejectedProjectIds", old)
            val receipts = serverQuery(root.collection("incomeReceipts"))
            assertFalse(receipts.metadata.isFromCache)
            assertFalse(receipts.metadata.hasPendingWrites())
            val receiptArray = JSONArray()
            receipts.documents.forEach { receipt ->
                receiptArray.put(document(receipt, listOf("sourceId", "currency", "receiptDate",
                    "originalAmountStr", "originalAmountMinor", "lkrAmountMinor", "conversionBasis",
                    "conversionRateStr", "linkedPaymentId", "linkedMilestoneId", "note")))
            }
            output.put("receipts", receiptArray)
            val expenses = serverQuery(root.collection("expenses"))
            assertFalse(expenses.metadata.isFromCache)
            assertFalse(expenses.metadata.hasPendingWrites())
            val expenseArray = JSONArray()
            expenses.documents.forEach { expense ->
                expenseArray.put(document(expense, listOf("amountMinor", "currency", "expenseDate",
                    "categoryId", "paymentChannelId", "spendingType", "note")))
            }
            output.put("expenses", expenseArray)
            val crypto = serverQuery(root.collection("cryptoResults"))
            assertFalse(crypto.metadata.isFromCache)
            assertFalse(crypto.metadata.hasPendingWrites())
            val cryptoArray = JSONArray()
            crypto.documents.forEach { result ->
                cryptoArray.put(document(result, listOf("direction", "amountMinor", "resultDate", "note", "lastOperationId")))
            }
            output.put("cryptoResults", cryptoArray)
            val recovery = SubmissionRecoveryStore(context.dataStore)
            val attempts = JSONArray()
            for (type in listOf("CREATE_PROJECT", "CREATE_MILESTONE", "PAYMENT_DIRECT", "PAYMENT_MILESTONE",
                "CANCEL_MILESTONE", "INCOME", "EXPENSE", "CRYPTO_CREATE", "CRYPTO_UPDATE", "CRYPTO_DELETE")) {
                recovery.getRecords(uid, type).forEach { record ->
                    attempts.put(JSONObject().put("type", type).put("recordId", record.recordId)
                        .put("status", record.status).put("errorReason", record.errorReason ?: JSONObject.NULL)
                        .put("payload", JSONObject(record.payloadJson)))
                }
            }
            output.put("recovery", attempts)
            val label = InstrumentationRegistry.getArguments().getString("auditLabel", "latest")
                .replace(Regex("[^a-zA-Z0-9-]"), "")
            val file = File(context.cacheDir, "live-firestore-$label.json")
            file.writeText(output.toString(2))
            println("LIVE_AUDIT projects=" + projects.size() + " receipts=" + receipts.size() +
                " expenses=" + expenses.size() + " crypto=" + crypto.size() + " recovery=" + attempts.length() + " file=" + file.name)
        }
    }
}
