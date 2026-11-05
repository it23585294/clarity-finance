package com.clarity.finance

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.*
import com.clarity.finance.data.repository.*
import com.clarity.finance.ui.theme.ClarityTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PhaseBInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun show(data: PhaseBFixtures, narrow: Boolean = false) {
        val graph = data.graph()
        compose.setContent { val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, if (narrow) 2f else 1f)) {
                ClarityTheme { Box(Modifier.width(if (narrow) 320.dp else 390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    private fun click(label: String) = compose.pilotClick(label)
    private fun field(label: String, value: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun incomeList() { click("History"); click("Received Income") }
    private fun project() { click("Pending Payments"); compose.onNodeWithText("Studio website").performScrollTo().performClick() }

    @Test fun foreignIncomeActualCreditAndRepeatedSaveUseExistingConversionPayload() {
        val data = PhaseBFixtures().apply { admission = CompletableDeferred() }; show(data)
        incomeList(); click("Add Income")
        click("Currency"); click("USD")
        field("Original Amount", "100.50")
        click("Actual LKR")
        compose.onNodeWithText("Actual LKR").assertIsSelected()
        field("Actual LKR Credited", "31000.25")
        compose.onNode(hasText("Add Income") and hasClickAction()).performScrollTo().performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertEquals(1, data.savedIncome.size)
            val saved = data.savedIncome.single()
            assertEquals("100.50", saved.originalAmountStr); assertEquals(3100025L, saved.lkrAmountMinor)
            assertEquals("actual_credit", saved.conversionBasis); assertNull(saved.conversionRateStr)
            data.admission!!.complete(Unit)
        }
        compose.onNodeWithText("Received Income").assertIsDisplayed()
    }
    @Test fun incomeInspectionBackdateKeepAndConfirmedDeletePreserveListContextAndLinkedRouting() {
        val data = PhaseBFixtures(); show(data); incomeList()
        compose.onNodeWithText("Note: Fictional overseas work").performScrollTo().performClick()
        compose.onNodeWithText("USD 100.00").assertIsDisplayed()
        compose.onNodeWithText("Consulting (archived)").assertIsDisplayed()
        click("Edit income")
        field("Original Amount", "120")
        val previous = LocalDate.parse(data.today).minusMonths(1).withDayOfMonth(1).toString()
        field("Receipt Date (yyyy-MM-dd)", previous); click("Save Changes")
        compose.runOnIdle {
            val saved = data.savedIncome.single()
            assertEquals("custom_consulting", saved.sourceId); assertEquals("310", saved.conversionRateStr)
            assertEquals(previous, saved.receiptDate); assertEquals(3720000L, saved.lkrAmountMinor)
        }
        click("Previous month")
        compose.onNodeWithText("Note: Fictional overseas work").performScrollTo().performClick()
        click("Delete"); compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(3, data.incomes.value.size) }
        click("Delete"); compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(2, data.incomes.value.size) }
        click("Next month")
        compose.onNodeWithText("Note: Fictional linked receipt").performScrollTo().performClick()
        compose.onNodeWithText("Agreement details").assertIsDisplayed()
        compose.onNodeWithText("Studio website").assertIsDisplayed()
    }
    @Test fun directAndMilestoneReceiptsKeepNullableIdentityAndInheritedArchivedSource() {
        val data = PhaseBFixtures(); show(data); project()
        click("Record payment"); field("Amount (LKR)", "10"); click("Confirm Payment")
        compose.runOnIdle { assertNull(data.receipts.single().first); assertEquals("custom_consulting", data.receipts.single().second.sourceId); assertEquals(1000L, data.receipts.single().second.lkrAmountMinor) }
        click("Record milestone payment"); field("Amount (LKR)", "20"); click("Confirm Payment")
        compose.runOnIdle { assertEquals("milestone", data.receipts.last().first); assertEquals(2000L, data.receipts.last().second.lkrAmountMinor); assertEquals(2, data.receipts.size) }
    }
    @Test fun foreignReceiptKeepsOriginalCurrencyRateAndActualReceiptDate() {
        val data = PhaseBFixtures()
        data.projects.value = data.projects.value.map { it.copy(currency = "USD") }
        show(data); project()
        compose.onNodeWithText("USD 850.00").assertIsDisplayed()
        click("Record payment"); field("Amount (USD)", "10.25")
        field("Confirmed LKR per USD", "300.25")
        val date = LocalDate.parse(data.today).minusDays(1).toString()
        field("Receipt Date (yyyy-MM-dd)", date); click("Confirm Payment")
        compose.runOnIdle {
            val saved = data.receipts.single().second
            assertEquals("USD", saved.currency); assertEquals("10.25", saved.originalAmountStr)
            assertEquals("300.25", saved.conversionRateStr); assertEquals(date, saved.receiptDate)
            assertEquals(307756L, saved.lkrAmountMinor); assertNull(data.receipts.single().first)
        }
    }
    @Test fun partialMilestoneCancellationKeepsAgreementAndReceivedHistoryAndProjectKeepIsSafe() {
        val data = PhaseBFixtures(); show(data); project()
        click("Cancel milestone")
        compose.onNodeWithText("Only the unpaid allocation of LKR 300.00", substring = true).assertIsDisplayed()
        click("Keep Milestone")
        compose.runOnIdle { assertFalse(data.milestones.value.single().isCancelled) }
        click("Cancel milestone"); click("Confirm Cancel")
        compose.runOnIdle { assertTrue(data.milestones.value.single().isCancelled); assertEquals(40000L, data.milestones.value.single().agreedAmountMinor); assertEquals(10000L, data.milestones.value.single().receivedAmountMinor) }
        click("Cancel agreement"); click("Keep agreement")
        compose.runOnIdle { assertFalse(data.projects.value.single().isCancelled) }
        click("Cancel agreement"); click("Confirm Cancel")
        compose.runOnIdle { assertTrue(data.projects.value.single().isCancelled); assertEquals(15000L, data.projects.value.single().totalReceivedAmountMinor) }
        compose.onNodeWithText("Record payment").assertDoesNotExist()
    }
    @Test fun recoveryEligibilityDistinguishesUnknownFromRejectedAndAccountChangesClearIt() {
        val data = PhaseBFixtures(); show(data)
        click("Categories & Sources"); click("Saved changes")
        compose.onNodeWithText("Confirmation unknown", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Dismiss rejected proposal").assertCountEquals(1)
        click("Check outcome or withdraw safely")
        compose.runOnIdle { assertEquals(listOf("uncertain"), data.resolutions); assertTrue(data.dismissals.isEmpty()) }
        click("Dismiss rejected proposal")
        compose.runOnIdle { assertEquals(listOf("rejected"), data.dismissals) }
        compose.pilotScreenshot("phase-b-after-recovery-eligibility")
        compose.runOnIdle { data.session.value = User("phase-b-other", "Other fictional account") }
        compose.onNodeWithText("Confirmation unknown", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Dismiss rejected proposal").assertDoesNotExist()
    }
    @Test fun missingCatalogueDoesNotEnableNewAgreementSourceAndPendingKeepsRecordedIncome() {
        val data = PhaseBFixtures().apply { sources.value = CatalogueSnapshot() }
        show(data); click("Pending Payments"); click("Add agreement")
        compose.onNodeWithText("Source label unavailable (freelance)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        click("Back"); click("Back"); incomeList()
        compose.runOnIdle { data.incomes.value += data.incomes.value.first().copy(id = "pending", isPending = true, lkrAmountMinor = 75000, originalAmountStr = "750") }
        compose.onNodeWithText("LKR 131,150.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not yet confirmed: LKR 750.00").assertIsDisplayed()
        compose.pilotScreenshot("phase-b-after-pending-missing-catalogue")
    }
    @Test fun enlargedLongLabelsAndLargeAmountsKeepProjectActionsAndFormControlsReachable() {
        val data = PhaseBFixtures()
        data.projects.value = listOf(data.projects.value.single().copy(title = "A deliberately long agreement name for accessible project inspection at enlarged text", agreedAmountMinor = 99999999999))
        show(data, true)
        click("Pending Payments"); compose.onNodeWithText(data.projects.value.single().title).performScrollTo().performClick()
        compose.onNodeWithText("LKR 999,999,849.99").performScrollTo().assertIsDisplayed()
        click("Add milestone")
        compose.onNode(hasText("Consulting (archived)") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        field("Milestone Title *", "Enlarged sample")
        field("Agreed Amount *", "10")
        compose.onNode(hasText("Add") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.pilotScreenshot("phase-b-after-enlarged-milestone")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        click("Record payment"); field("Amount (LKR)", "10")
        compose.onNodeWithText("Confirm Payment").performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Back")
        compose.onNodeWithText(data.projects.value.single().title).performScrollTo().assertIsDisplayed()
    }

    @Test fun confirmedRateIncomeRetainsExactRateAndConvertedMinorUnits() {
        val data = PhaseBFixtures(); show(data); incomeList(); click("Add Income")
        click("Currency"); click("USD")
        field("Original Amount", "25.75"); field("Confirmed LKR per USD", "310.25")
        click("Add Income")
        compose.runOnIdle {
            val saved = data.savedIncome.single()
            assertEquals("25.75", saved.originalAmountStr); assertEquals("310.25", saved.conversionRateStr)
            assertEquals("confirmed_rate", saved.conversionBasis); assertEquals(798894L, saved.lkrAmountMinor)
        }
    }

    @Test fun sourcesRenameArchiveAndNewAgreementExclusionPreserveIdentity() {
        val data = PhaseBFixtures(); show(data); click("Categories & Sources"); click("Sources")
        click("Add source"); field("Name", "Fictional tutoring"); click("USD"); click("Save proposal")
        val stableId = data.writes.single().targetId
        click("Rename / edit Fictional tutoring"); field("Name", "Fictional lessons"); click("Save proposal")
        compose.runOnIdle { assertEquals(stableId, data.writes.last().targetId) }
        click("Archive Fictional lessons"); click("Keep active")
        compose.runOnIdle { assertEquals(2, data.writes.size) }
        click("Archive Fictional lessons"); click("Archive")
        compose.runOnIdle { assertEquals(stableId, data.writes.last().targetId); assertTrue(data.writes.last().catalogue!!.archived) }
        compose.onNodeWithText("Fictional lessons (archived)").performScrollTo().assertIsDisplayed()
        click("Back"); click("Pending Payments"); click("Add agreement"); click("Source")
        compose.onNodeWithText("Fictional lessons (archived)").assertDoesNotExist()
        compose.onNodeWithText("Consulting (archived)").assertDoesNotExist()
        compose.onNode(hasText("Freelance") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test fun unresolvedReceiptLocksOriginalPayloadAndIgnoresLateOldAccountResult() {
        val data = PhaseBFixtures().apply {
            unresolvedReceipt = incomes.value.last().copy(id = "stable-unresolved", originalAmountStr = "10", lkrAmountMinor = 1000)
            admission = CompletableDeferred()
        }
        show(data); project(); click("Record payment")
        compose.onNodeWithText("Amount (LKR)").assertIsNotEnabled()
        compose.onNodeWithText("Receipt Date (yyyy-MM-dd)").assertIsNotEnabled()
        compose.onNodeWithText("A saved payment is unresolved", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retry Payment").performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertEquals(1, data.receipts.size); assertNull(data.receipts.single().first)
            assertEquals("stable-unresolved", data.receipts.single().second.id)
            assertEquals(data.unresolvedReceipt, data.receipts.single().second)
            data.session.value = User("phase-b-other", "Other fictional account")
        }
        compose.runOnIdle { data.admission!!.complete(Unit) }
        compose.onNodeWithText("Confirmation still unknown in isolated fixture").assertDoesNotExist()
        compose.onNodeWithText("Retry Payment").assertDoesNotExist()
        compose.onNodeWithText("Record payment").assertDoesNotExist()
    }

    @Test fun agreementAndMilestoneEditsKeepInheritedSourceAndReceivedAmounts() {
        val data = PhaseBFixtures(); show(data); project()
        click("Edit agreement"); field("Title", "Revised studio website")
        field("Agreed amount (LKR)", "1200"); click("Save proposal")
        compose.runOnIdle {
            val p = data.projects.value.single(); assertEquals("Revised studio website", p.title)
            assertEquals("custom_consulting", p.sourceId); assertEquals(120000L, p.agreedAmountMinor)
            assertEquals(40000L, p.allocatedAmountMinor); assertEquals(15000L, p.totalReceivedAmountMinor)
        }
        click("Edit milestone"); field("Title", "Revised design approval")
        field("Agreed amount (LKR)", "450"); click("Save proposal")
        compose.runOnIdle {
            val ms = data.milestones.value.single(); assertEquals("Revised design approval", ms.title)
            assertEquals(45000L, ms.agreedAmountMinor); assertEquals(10000L, ms.receivedAmountMinor)
            assertEquals("project", ms.paymentId)
        }
    }

    @Test fun agreementAndMilestoneCreationUseExistingCallbacksAndValidatedPayloads() {
        val data = PhaseBFixtures(); show(data); click("Pending Payments"); click("Add agreement")
        click("Project"); field("Title *", "Fictional new project"); field("Agreed Amount *", "2500")
        field("Due Date (yyyy-MM-dd) *", data.today); click("Save")
        val created = data.projects.value.single { it.title == "Fictional new project" }
        compose.runOnIdle { assertEquals(250000L, created.agreedAmountMinor); assertEquals(0L, created.totalReceivedAmountMinor); assertEquals(0L, created.allocatedAmountMinor) }
        compose.onNodeWithText("Fictional new project").performScrollTo().performClick()
        click("Add milestone"); field("Milestone Title *", "Fictional new milestone")
        field("Agreed Amount *", "500"); field("Due Date (yyyy-MM-dd) *", data.today)
        compose.onNode(hasText("Add") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle {
            val ms = data.milestones.value.single { it.title == "Fictional new milestone" }
            assertEquals(created.id, ms.paymentId); assertEquals(50000L, ms.agreedAmountMinor); assertEquals(0L, ms.receivedAmountMinor)
        }
    }

    @Test fun accountChangeDuringSuspendedIncomeSubmissionClearsFormAndIgnoresCompletion() {
        val data = PhaseBFixtures().apply { admission = CompletableDeferred() }
        show(data); incomeList(); click("Add Income"); field("Original Amount", "25")
        click("Add Income")
        compose.runOnIdle { assertEquals(1, data.savedIncome.size); data.session.value = User("phase-b-other", "Other fictional account") }
        compose.runOnIdle { data.admission!!.complete(Unit) }
        compose.onNodeWithText("Fictional salary").assertDoesNotExist()
        compose.onNodeWithText("25").assertDoesNotExist()
        compose.onNodeWithText("Income details").assertDoesNotExist()
        compose.onNode(hasText("Overview") and hasClickAction()).assertIsDisplayed()
    }
}
