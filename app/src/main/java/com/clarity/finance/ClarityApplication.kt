package com.clarity.finance

import android.app.Application
import com.clarity.finance.di.platformModule
import com.clarity.finance.di.repositoryModule
import com.clarity.finance.di.viewModelModule
import org.koin.core.context.startKoin

class ClarityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val graph = startKoin {
            modules(platformModule(this@ClarityApplication), repositoryModule, viewModelModule)
        }
        graph.koin.get<com.clarity.finance.data.repository.SessionSyncCoordinator>().start()
    }
}
