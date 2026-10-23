package com.clarity.finance

import android.app.Application
import com.clarity.finance.di.AppContainer

class ClarityApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
