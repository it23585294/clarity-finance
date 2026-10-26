package com.clarity.finance.data.repository

import com.clarity.finance.data.model.CryptoResult
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class CryptoReconciliationTest {
    private val result = CryptoResult("id", "gain", 6000000, "2025-01-01", createdAt = Timestamp(1, 0), updatedAt = Timestamp(2, 0))
    private val create = CryptoOperation("op", "CREATE", result)

    @Test fun `create retry reconciles committed matching record without overwriting timestamps`() {
        assertTrue(create.requiresWrite(null))
        assertFalse(create.requiresWrite(result.copy(lastOperationId = "op")))
        assertThrows(IllegalStateException::class.java) { create.requiresWrite(result) }
        assertThrows(IllegalStateException::class.java) { create.requiresWrite(result.copy(lastOperationId = "op", note = "changed")) }
    }

    @Test fun `update checks the original version and reconciles its operation on replay`() {
        val edit = create.copy(kind = "UPDATE", result = result.copy(direction = "loss", resultDate = "2024-12-31"), expectedUpdatedAt = result.updatedAt)
        assertTrue(edit.requiresWrite(result))
        assertFalse(edit.requiresWrite(edit.result.copy(lastOperationId = "op", updatedAt = Timestamp(3, 0))))
        assertThrows(IllegalStateException::class.java) { edit.requiresWrite(result.copy(updatedAt = Timestamp(4, 0))) }
        assertThrows(IllegalStateException::class.java) { edit.requiresWrite(null) }
    }

    @Test fun `delete absent is already complete and cannot delete a modified version`() {
        val delete = create.copy(kind = "DELETE", expectedUpdatedAt = result.updatedAt)
        assertTrue(delete.requiresWrite(result))
        assertFalse(delete.requiresWrite(null))
        assertThrows(IllegalStateException::class.java) { delete.requiresWrite(result.copy(updatedAt = Timestamp(3, 0))) }
    }
}
