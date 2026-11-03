package com.clarity.finance

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.clarity.finance.viewmodel.AuthViewModel
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.dataStore
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.runBlocking

/** Explicitly opted-in, signed-out production startup only. Never signs in or invokes financial operations. */
class Api26ProductionStartupTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun signedOutProductionActivityRecreationRetainsKoinAndAuthOwner() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("api26ProductionAudit") == "true")
        assertEquals("Only the dedicated API 26 device is allowed", 26, Build.VERSION.SDK_INT)
        val firebaseAuth = FirebaseAuth.getInstance()
        assertNull("Do not launch with an existing production account", firebaseAuth.currentUser)
        val graph = GlobalContext.get()
        val disk = graph.get<DataStore<Preferences>>()
        val recovery = graph.get<SubmissionRecoveryStore>()
        assertSame(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext.dataStore, disk)
        runBlocking { assertTrue(recovery.getRecords("api26-local-probe", "EXPENSE").isEmpty()) }
        val factory = graph.get<ViewModelProvider.Factory> { parametersOf(AuthViewModel::class) }
        lateinit var initialActivity: MainActivity
        lateinit var initialViewModel: AuthViewModel
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Welcome back").assertIsDisplayed()
            scenario.onActivity {
                initialActivity = it
                initialViewModel = ViewModelProvider(it, factory)[AuthViewModel::class.java]
            }
            scenario.recreate()
            compose.onNodeWithText("Welcome back").assertIsDisplayed()
            scenario.onActivity {
                assertNotSame("Activity must really be recreated", initialActivity, it)
                assertSame("Application Koin context must be retained", graph, GlobalContext.get())
                assertSame("Auth ViewModel must not initialize a second instance", initialViewModel,
                    ViewModelProvider(it, factory)[AuthViewModel::class.java])
                assertSame(firebaseAuth, graph.get<FirebaseAuth>())
                assertSame(disk, graph.get<DataStore<Preferences>>())
                assertSame(recovery, graph.get<SubmissionRecoveryStore>())
            }
            assertNull(firebaseAuth.currentUser)
        }
    }
}
