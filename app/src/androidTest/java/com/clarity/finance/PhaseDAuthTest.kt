package com.clarity.finance

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.User
import com.clarity.finance.ui.auth.LoginScreen
import com.clarity.finance.ui.theme.ClarityTheme
import com.clarity.finance.viewmodel.AuthViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseDAuthTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(data: PhaseDFixtures) {
        val graph = data.graph()
        compose.setContent { val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                ClarityTheme { Box(Modifier.width(390.dp).fillMaxHeight()) { IsolatedAppNavigation(graph) } }
            }
        }
    }
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun field(text: String, value: String) = compose.onNodeWithText(text).performScrollTo().performTextReplacement(value)
    private fun error(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    private fun displayedPassword(label: String): String {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.text.text
    }

    @Test fun initialSessionShowsNeutralLoadingWithoutInventingAnAccountOrBalance() {
        val auth = object : com.clarity.finance.data.repository.AuthRepository {
            override val authState = kotlinx.coroutines.flow.flow<User?> { kotlinx.coroutines.awaitCancellation() }
            override suspend fun login(email: String, password: String): Result<User> = kotlin.error("Unexpected login")
            override suspend fun register(email: String, password: String): Result<User> = kotlin.error("Unexpected registration")
            override suspend fun logout(): Unit = kotlin.error("Unexpected logout")
        }
        val graph = isolatedAppGraph(auth)
        compose.setContent { ClarityTheme { IsolatedAppNavigation(graph) } }
        compose.onNodeWithText("Opening Clarity").assertIsDisplayed()
        compose.onNodeWithText("Email").assertDoesNotExist()
        compose.onNodeWithText("LKR 0.00").assertDoesNotExist()
    }

    @Test fun validationVisibilityAndSuspendedLoginKeepExistingSafeguards() {
        val data = PhaseDFixtures().apply { admission = CompletableDeferred(); authResult = Result.failure(IllegalStateException("Fictional failure")) }
        show(data); click("Sign In"); error("Please enter both email and password.")
        field("Email", "not-an-email"); field("Password", "fictional-password"); click("Sign In"); error("Invalid email format.")
        compose.onNodeWithContentDescription("Show password").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        assertEquals("fictional-password", displayedPassword("Password"))
        compose.onNodeWithContentDescription("Hide password").performScrollTo().performClick()
        assertNotEquals("fictional-password", displayedPassword("Password"))
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
        field("Email", "fixture@example.test")
        compose.onNodeWithText("Sign In").performScrollTo().performTouchInput { doubleClick() }
        compose.onNodeWithText("Sign In").assertIsNotEnabled()
        compose.onNodeWithText("Password").performImeAction()
        compose.runOnIdle { assertEquals(1, data.loginCalls); data.admission!!.complete(Unit) }
        error("Email or password is incorrect.")
        compose.runOnIdle { assertNull(data.session.value); assertEquals(1, data.loginCalls) }
    }
    @Test fun registrationValidationSuspensionAndNavigationClearPasswords() {
        val data = PhaseDFixtures().apply { admission = CompletableDeferred(); authResult = Result.failure(IllegalStateException("network")) }
        show(data); click("Create an account"); click("Create Account"); error("Please fill in all fields.")
        field("Email", "fixture@example.test"); field("Password", "123"); field("Confirm Password", "123")
        click("Create Account"); error("Password must be at least 6 characters.")
        field("Password", "fictional-one"); field("Confirm Password", "fictional-two")
        click("Create Account"); error("Passwords do not match.")
        compose.onNodeWithContentDescription("Show password").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        assertEquals("fictional-one", displayedPassword("Password"))
        assertEquals("fictional-two", displayedPassword("Confirm Password"))
        field("Confirm Password", "fictional-one"); click("Create Account")
        compose.onNodeWithText("Create Account").assertIsNotEnabled()
        compose.onNodeWithText("Confirm Password").performImeAction()
        compose.runOnIdle { assertEquals(1, data.registerCalls); data.admission!!.complete(Unit) }
        error("Network error. Please check your connection.")
        click("Back to sign in")
        compose.onNodeWithText("Password").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("Show password").assertExists()
        click("Create an account"); compose.onNodeWithText("Password").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithText("Confirm Password").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
    @Test fun passwordAndVisibilityAreNotSavedForRestoration() {
        val data = PhaseDFixtures(); val vm = AuthViewModel(data.auth)
        val restore = StateRestorationTester(compose)
        restore.setContent { ClarityTheme { LoginScreen(vm, {}) } }
        field("Password", "fictional-secret"); compose.onNodeWithContentDescription("Show password").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Password").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("Show password").assertExists()
        compose.runOnIdle { assertEquals(0, data.loginCalls) }
    }
    @Test fun logoutClearsPreviousAccountDestinationsAndBackExitsSignedOutRoot() {
        val data = PhaseDFixtures().apply { signIn() }; show(data)
        compose.pilotClick("Savings Goal"); compose.onNodeWithText("Learning fund").assertExists()
        compose.pilotClick("Back"); compose.pilotClick("More"); compose.pilotClick("Sign Out")
        compose.onNodeWithText("Email").assertExists()
        compose.onNodeWithText("Learning fund").assertDoesNotExist()
        compose.onNodeWithText("Fictional review account").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, data.logoutCalls); data.session.value = User("other-isolated-owner", "Second fictional account") }
        compose.pilotClick("Savings Goal"); compose.onNodeWithText("Learning fund").assertDoesNotExist()
        compose.pilotClick("Back"); compose.pilotClick("More"); compose.pilotClick("Sign Out")
        compose.onNodeWithText("Email").assertExists()
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        compose.waitUntil(5000) { compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }
}
