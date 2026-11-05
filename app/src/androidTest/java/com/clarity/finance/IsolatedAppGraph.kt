package com.clarity.finance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.clarity.finance.data.repository.*
import com.clarity.finance.di.viewModelModule
import com.clarity.finance.ui.navigation.AppNavigation
import kotlinx.coroutines.flow.flowOf
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.lang.reflect.Proxy

/** Standalone Koin context: never loads production platform/repository modules or falls back globally. */
fun isolatedAppGraph(
    auth: AuthRepository,
    expense: ExpenseRepository = unusedRepository(),
    income: IncomeRepository = unusedRepository(),
    payment: PaymentRepository = unusedRepository(),
    crypto: CryptoRepository = unusedRepository(),
    reporting: ReportingRepository = emptyReportingObserver(),
    goal: GoalRepository = emptyGoalObserver(),
    recurring: RecurringRepository = unusedRepository(),
    catalogue: CatalogueRepository = object : CatalogueRepository {
        override fun observe(uid: String) = flowOf(com.clarity.finance.data.model.CatalogueSnapshot(com.clarity.finance.data.model.CatalogueDefaults.entries(), false))
    },
    recovery: FinancialRecoveryAccess = object : FinancialRecoveryAccess {
        override fun observe(uid: String) = flowOf(FinancialRecovery())
        override suspend fun submit(uid: String, operation: FinancialOperation) = error("Unexpected isolated write")
        override suspend fun resolve(uid: String, operation: FinancialOperation) = error("Unexpected isolated resolution")
        override suspend fun dismissRejected(uid: String, operation: FinancialOperation) = error("Unexpected isolated dismissal")
    }
): KoinApplication = koinApplication {
    modules(viewModelModule, module {
        single<AuthRepository> { auth }
        single<ExpenseRepository> { expense }
        single<IncomeRepository> { income }
        single<PaymentRepository> { payment }
        single<CryptoRepository> { crypto }
        single<ReportingRepository> { reporting }
        single<GoalRepository> { goal }
        single<RecurringRepository> { recurring }
        single<CatalogueRepository> { catalogue }
        single<FinancialRecoveryAccess> { recovery }
    })
}

@Composable
fun IsolatedAppNavigation(graph: KoinApplication) {
    DisposableEffect(graph) { onDispose { graph.close() } }
    // Every ViewModel resolution receives this root explicitly, including inside NavHost destinations.
    AppNavigation(graph = graph.koin)
}

/** Dashboard's goal card observes only; an unexpected savings action fails instead of touching Firebase. */
private fun emptyGoalObserver(): GoalRepository = Proxy.newProxyInstance(
    GoalRepository::class.java.classLoader, arrayOf(GoalRepository::class.java)
) { _, method, _ ->
    check(method.name == "observe") { "Unexpected isolated goal call: ${method.name}" }
    flowOf(GoalSnapshot(known = true, entries = emptyList()))
} as GoalRepository

private inline fun <reified T> unusedRepository(): T = Proxy.newProxyInstance(
    T::class.java.classLoader, arrayOf(T::class.java)
) { _, method, _ -> error("Unexpected isolated repository call: ${method.name}") } as T

/** Explicit fake observers for the new overview landing; never resolves a live repository. */
private fun emptyReportingObserver() = object : ReportingRepository {
    override fun expenses(uid: String, month: com.clarity.finance.data.model.ReportingMonth) = flowOf(ReportingBatch(emptyList<com.clarity.finance.data.model.Expense>(), false))
    override fun income(uid: String, month: com.clarity.finance.data.model.ReportingMonth) = flowOf(ReportingBatch(emptyList<com.clarity.finance.data.model.Income>(), false))
    override fun crypto(uid: String, month: com.clarity.finance.data.model.ReportingMonth) = flowOf(ReportingBatch(emptyList<com.clarity.finance.data.model.CryptoResult>(), false))
    override fun projects(uid: String) = flowOf(ReportingBatch(emptyList<com.clarity.finance.data.model.ExpectedPayment>(), false))
    override fun recovery(uid: String) = flowOf(ReportingRecovery())
}
