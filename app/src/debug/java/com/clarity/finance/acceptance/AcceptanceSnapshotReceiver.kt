package com.clarity.finance.acceptance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** Read-only snapshot of synthetic memory. Never reads production data or stores trial timings. */
class AcceptanceSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra("isolatedAcceptance", false)) return
        val f = AcceptanceSession.fixture
        val created = f.rows.value.filter { it.id in f.createdIds }
        resultData = JSONObject().put("fixtureSessionId", f.sessionId).put("memoryOnly", true)
            .put("createdCount", created.size)
            .put("created", JSONArray(created.map { JSONObject().put("id", it.id).put("amountMinor", it.amountMinor)
                .put("date", it.expenseDate).put("categoryId", it.categoryId).put("spendingType", it.spendingType)
                .put("paymentChannelId", it.paymentChannelId).put("noteBlank", it.note.isNullOrBlank()) }))
            .put("pendingExampleRetained", f.rows.value.any { it.id == f.pending.id && it.isPending }).toString()
    }
}
