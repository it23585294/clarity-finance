package com.clarity.finance

import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.AuthRepository
import com.clarity.finance.ui.theme.ClarityTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production auth screens and Koin ViewModel with fake auth only; invalid values never reach a backend. */
class AuthAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Volatile private var composeImeHeight = 0
    private lateinit var hostView: View
    private var backendCalls = 0
    private val auth = object : AuthRepository {
        override val authState = MutableStateFlow<User?>(null)
        override suspend fun login(email: String, password: String): Result<User> {
            backendCalls++; return Result.failure(IllegalStateException("Unexpected backend call"))
        }
        override suspend fun register(email: String, password: String): Result<User> {
            backendCalls++; return Result.failure(IllegalStateException("Unexpected backend call"))
        }
        override suspend fun logout() { error("Unused") }
    }

    private fun show() {
        // Match MainActivity's edge-to-edge + manifest adjustResize, rather than the test Activity's default pan mode.
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val graph = isolatedAppGraph(auth)
        compose.setContent {
            hostView = LocalView.current
            val density = LocalDensity.current
            val ime = WindowInsets.ime.getBottom(density)
            SideEffect { composeImeHeight = ime }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                ClarityTheme {
                    Scaffold(Modifier.fillMaxSize()) { padding ->
                        Box(Modifier.width(320.dp).padding(padding)) { IsolatedAppNavigation(graph) }
                    }
                }
            }
        }
    }

    @Test fun signedOutValidationControlsRemainReachableWithEnlargedText() {
        show()
        click("Sign In")
        text("Please enter both email and password.")
        compose.onNodeWithText("Email").performTextReplacement("not-an-email")
        compose.onNodeWithText("Password").performTextReplacement("sample1")
        click("Sign In")
        text("Invalid email format.")
        click("Don't have an account? Register")
        click("Create Account")
        text("Please fill in all fields.")
        compose.onNodeWithText("Email").performTextReplacement("api26-check@example.invalid")
        compose.onNodeWithText("Password", substring = false).performTextReplacement("123")
        compose.onNodeWithText("Confirm Password").performTextReplacement("123")
        click("Create Account")
        text("Password must be at least 6 characters.")
        compose.onNodeWithText("Password", substring = false).performTextReplacement("sample1")
        compose.onNodeWithText("Confirm Password").performTextReplacement("sample2")
        click("Create Account")
        text("Passwords do not match.")
        click("Already have an account? Login")
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, backendCalls); assertNull(auth.authState.value) }
    }

    @Test fun keyboardKeepsRegistrationFieldsAndBothFormsActionsReachable() {
        show()
        click("Don't have an account? Register")
        compose.onNodeWithText("Email").performClick().performTextInput("not-an-email")
        awaitKeyboard()
        val confirm = compose.onNodeWithText("Confirm Password")
        confirm.performScrollTo().performClick().performTextInput("123")
        confirm.assertIsFocused()
        awaitKeyboard()
        val create = compose.onNode(hasText("Create Account") and hasClickAction())
        revealAboveKeyboard(create)
        aboveKeyboard(create)
        create.performClick()
        text("Please fill in all fields.")
        click("Already have an account? Login")
        compose.onNodeWithText("Email").performClick().performTextInput("not-an-email")
        awaitKeyboard()
        val signIn = compose.onNode(hasText("Sign In") and hasClickAction())
        revealAboveKeyboard(signIn)
        aboveKeyboard(signIn)
        signIn.performClick()
        text("Please enter both email and password.")
        compose.runOnIdle { assertEquals(0, backendCalls) }
    }

    private fun awaitKeyboard() {
        compose.waitUntil(5_000) { keyboardHeight() > 0 }
        compose.runOnIdle { ViewCompat.requestApplyInsets(hostView) }
        compose.waitUntil(5_000) { composeImeHeight > 0 && composeImeHeight == keyboardHeight() }
    }

    private fun revealAboveKeyboard(node: SemanticsNodeInteraction) {
        node.performScrollTo()
        // API 26 focus panning can leave semantics scrolling beneath the IME. Exercise the user's exposed swipe area.
        compose.onNode(hasScrollAction()).performTouchInput {
            swipe(Offset(center.x, 220.dp.toPx()), Offset(center.x, 30.dp.toPx()), 400)
        }
        node.assertIsDisplayed()
    }

    private fun keyboardHeight(): Int {
        var height = 0
        compose.runOnIdle {
            height = ViewCompat.getRootWindowInsets(hostView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return height
    }
    @Suppress("DEPRECATION") // Physical display height, independent of adjustResize on API 26.
    private fun aboveKeyboard(node: SemanticsNodeInteraction) {
        val height = keyboardHeight()
        assertTrue("Real software keyboard must remain visible", height > 0)
        val bottom = node.fetchSemanticsNode().boundsInWindow.bottom
        var message = ""
        var above = false
        compose.runOnIdle {
            val display = DisplayMetrics()
            hostView.display.getRealMetrics(display)
            val keyboardTop = display.heightPixels - height
            val onScreen = IntArray(2)
            val inWindow = IntArray(2)
            hostView.getLocationOnScreen(onScreen)
            hostView.getLocationInWindow(inWindow)
            val screenBottom = bottom + onScreen[1] - inWindow[1]
            message = "Action screen bottom $screenBottom must be above keyboard top $keyboardTop (root ${hostView.rootView.height}, view ${hostView.height}, IME $height)"
            above = screenBottom <= keyboardTop + 1
        }
        assertTrue(message, above)
    }

    private fun reachable(node: SemanticsNodeInteraction) {
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo().assertIsDisplayed() }
    }
    private fun click(label: String) {
        val node = compose.onNode(hasText(label) and hasClickAction())
        reachable(node); node.performClick()
    }
    private fun text(label: String) { val node = compose.onNodeWithText(label); reachable(node); node.assertIsDisplayed() }
}
