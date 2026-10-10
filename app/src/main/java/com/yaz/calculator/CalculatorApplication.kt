package com.yaz.calculator

import android.app.Application
import com.yaz.calculator.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CalculatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nothing of the app starts in an isolated process.
        if (android.os.Process.isIsolated()) return
        com.yaz.calculator.engine.giac.Giac.attach(this)
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@CalculatorApplication)
            modules(appModule)
        }
    }
}
