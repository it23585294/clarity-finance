package com.clarity.finance.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.clarity.finance.data.local.SubmissionRecoveryStore
import com.clarity.finance.data.local.dataStore
import com.clarity.finance.data.repository.*
import com.clarity.finance.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.viewmodel.factory.KoinViewModelFactory
import kotlin.reflect.KClass

/** Lazy process singletons. Reuses the existing Context.dataStore delegate and recovery file. */
fun platformModule(context: Context) = module {
    single<Context> { context.applicationContext }
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    single<DataStore<Preferences>> { get<Context>().dataStore }
}

/** Remotes retain in-flight Tasks; neither they nor recovery repositories are screen-scoped. */
val repositoryModule = module {
    single { SubmissionRecoveryStore(get()) }
    single<kotlinx.coroutines.CoroutineScope> { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    single<FinancialRemote> {
        val auth = get<FirebaseAuth>()
        FirestoreFinancialRemote(get()) { auth.currentUser?.uid }
    }
    single { FinancialQueue(get(), get()) }
    single<FinancialRecoveryAccess> { get<FinancialQueue>() }
    single<CatalogueRepository> { FirestoreCatalogueRepository(get(), get()) }
    single { SessionSyncCoordinator(get(), listOf(
        SyncWorker("transactions") { uid -> get<FinancialQueue>().synchronize(uid) },
        SyncWorker("crypto") { uid -> get<CryptoRepository>().synchronize(uid) },
        SyncWorker("savings") { uid -> get<GoalRepository>().synchronize(uid) },
        SyncWorker("recurring") { uid -> get<RecurringRepository>().synchronize(uid) }
    ), get()) }
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    single<ExpenseRepository> { FirestoreExpenseRepository(get(), get(), get()) }
    single<IncomeRepository> { FirestoreIncomeRepository(get(), get(), get()) }
    single<PaymentRepository> { FirestorePaymentRepository(get(), get()) }
    single<CryptoRemote> {
        val auth = get<FirebaseAuth>()
        FirestoreCryptoRemote(get(), { auth.currentUser?.uid }, get())
    }
    single<CryptoRepository> { FirestoreCryptoRepository(get(), get()) }
    single<ReportingRepository> { FirestoreReportingRepository(get(), get()) }
    single<GoalRemote> {
        val auth = get<FirebaseAuth>()
        FirestoreGoalRemote(get()) { auth.currentUser?.uid }
    }
    single<GoalRepository> { FirestoreGoalRepository(get(), get()) }
    single<RecurringRemote> {
        val auth = get<FirebaseAuth>()
        FirestoreRecurringRemote(get()) { auth.currentUser?.uid }
    }
    single<RecurringRepository> { FirestoreRecurringRepository(get(), get()) }
}

/** Definitions are factories; Android's owning ViewModelStore controls reuse and clearing. */
val viewModelModule = module {
    factory<ViewModelProvider.Factory> { (type: KClass<out ViewModel>) -> KoinViewModelFactory(type, this) }
    viewModel { AuthViewModel(get()) }
    viewModel { CatalogueViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { ExpenseListViewModel(get(), get()) }
    viewModel { AddExpenseViewModel(get(), get()) }
    viewModel { EditExpenseViewModel(get(), get()) }
    viewModel { IncomeListViewModel(get(), get()) }
    viewModel { AddEditIncomeViewModel(get(), get()) }
    viewModel { PaymentListViewModel(get(), get()) }
    viewModel { AddPaymentViewModel(get(), get()) }
    viewModel { PaymentDetailViewModel(get(), get()) }
    viewModel { PaymentConfirmationViewModel(get(), get()) }
    viewModel { CryptoViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { GoalViewModel(get(), get(), synchronize = false) }
    viewModel { GoalCardViewModel(get(), get()) }
    viewModel { RecurringViewModel(get(), get(), synchronize = false) }
}
