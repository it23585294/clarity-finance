package com.clarity.finance.data.local

import com.clarity.finance.data.model.Expense
import com.clarity.finance.data.model.Income
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SubmissionRecordTest {

    private val gson = Gson()

    @Test
    fun `legacy JSON parses without type and defaults to EXPENSE`() {
        val legacyJson = """
            {
              "uid": "uid1",
              "expenseId": "e1",
              "payloadJson": "{}",
              "status": "PENDING"
            }
        """.trimIndent()

        val record = gson.fromJson(legacyJson, SubmissionRecord::class.java)
        
        assertEquals("uid1", record.uid)
        assertEquals("e1", record.recordId)
        assertEquals("EXPENSE", record.resolvedType)
        assertNull(record.type)
    }

    @Test
    fun `toExpense filters out non-expense records`() {
        val record = SubmissionRecord("uid", "r1", "{}", "PENDING", type = "INCOME")
        assertNull(record.toExpense())
    }

    @Test
    fun `toExpense converts valid EXPENSE record`() {
        val expense = Expense(id = "e1", amountMinor = 100L, categoryId = "c1", paymentChannelId = "p1", spendingType = "t1", expenseDate = "2023-01-01", note = "")
        val record = SubmissionRecord.fromExpense("uid", expense, "PENDING")
        val parsed = record.toExpense()
        
        assertNotNull(parsed)
        assertEquals("e1", parsed?.id)
        assertEquals(true, parsed?.isPending)
    }
}
