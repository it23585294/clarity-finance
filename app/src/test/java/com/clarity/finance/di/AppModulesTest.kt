package com.clarity.finance.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.model.User
import com.clarity.finance.data.repository.*
import com.clarity.finance.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.InstanceCreationException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mockito.Mockito.*
import java.lang.reflect.Proxy

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppModulesTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `production graph shares platform store repositories and Task retaining remotes without starting work`() {
        val context = mock(Context::class.java)
        val auth = mock(FirebaseAuth::class.java)
        val firestore = mock(FirebaseFirestore::class.java)
        @Suppress("UNCHECKED_CAST") val disk = mock(DataStore::class.java) as DataStore<Preferences>
        var disks = 0
        val graph = koinApplication {
            modules(platformModule(context), repositoryModule, module {
                single<FirebaseAuth> { auth }
                single<FirebaseFirestore> { firestore }
                single<DataStore<Preferences>> { disks++; disk }
            })
        }
        try {
            val koin = graph.koin
            assertSame(auth, koin.get<FirebaseAuth>())
            assertSame(firestore, koin.get<FirebaseFirestore>())
            assertSame(disk, koin.get<DataStore<Preferences>>())
            assertSame(koin.get<SubmissionRecoveryStore>(), koin.get<SubmissionRecoveryStore>())
            val services = listOf(AuthRepository::class, ExpenseRepository::class, IncomeRepository::class, PaymentRepository::class,
                CryptoRemote::class, CryptoRepository::class, ReportingRepository::class, GoalRemote::class, GoalRepository::class,
                RecurringRemote::class, RecurringRepository::class)
            for (type in services) assertSame(type.simpleName, koin.get<Any>(type), koin.get<Any>(type))
            assertEquals(1, disks)
            verifyNoInteractions(auth, firestore, disk)
        } finally { graph.close() }
    }

    @Test fun `every production ViewModel definition resolves from isolated interfaces and creates separate owners`() = runTest(dispatcher) {
        val auth = object : AuthRepository {
            override val authState = MutableStateFlow<User?>(null)
            override suspend fun login(email: String, password: String) = error("Unused")
            override suspend fun register(email: String, password: String) = error("Unused")
            override suspend fun logout() = error("Unused")
        }
        val graph = koinApplication {
            modules(viewModelModule, module {
                single<AuthRepository> { auth }
                single<ExpenseRepository> { unused() }
                single<IncomeRepository> { unused() }
                single<PaymentRepository> { unused() }
                single<CryptoRepository> { unused() }
                single<ReportingRepository> { unused() }
                single<GoalRepository> { unused() }
                single<RecurringRepository> { unused() }
                single<CatalogueRepository> { unused() }
                single<FinancialRecoveryAccess> { unused() }
            })
        }
        val owners = ViewModelStore()
        try {
            val types = listOf(AuthViewModel::class, HomeViewModel::class, ExpenseListViewModel::class, AddExpenseViewModel::class,
                EditExpenseViewModel::class, IncomeListViewModel::class, AddEditIncomeViewModel::class, PaymentListViewModel::class,
                AddPaymentViewModel::class, PaymentDetailViewModel::class, PaymentConfirmationViewModel::class, CryptoViewModel::class,
                DashboardViewModel::class, GoalViewModel::class, GoalCardViewModel::class, RecurringViewModel::class, CatalogueViewModel::class)
            for (type in types) {
                val a = graph.koin.get<ViewModel>(type)
                val b = graph.koin.get<ViewModel>(type)
                assertNotSame(type.simpleName, a, b)
                owners.put(type.simpleName + "-a", a); owners.put(type.simpleName + "-b", b)
            }
            runCurrent() // Signed-out ViewModels must not start financial listeners/recovery.
        } finally { owners.clear(); runCurrent(); graph.close() }
    }

    @Test fun `missing isolated binding fails instead of falling through to global production services`() {
        var globalResolutions = 0
        startKoin { modules(module { single<AuthRepository> { globalResolutions++; unused() } }) }
        val isolated = koinApplication { modules(viewModelModule) }
        try {
            try { isolated.koin.get<AddExpenseViewModel>(); fail("Missing fake must fail closed") }
            catch (_: InstanceCreationException) { }
            assertEquals(0, globalResolutions)
        } finally { isolated.close(); stopKoin() }
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
        _, method, _ -> error("Unexpected financial call in DI graph test: ${method.name}")
    } as T
}
