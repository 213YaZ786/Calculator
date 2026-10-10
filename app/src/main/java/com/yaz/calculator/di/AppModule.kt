package com.yaz.calculator.di

import com.yaz.calculator.data.advanced.AdvancedStore
import com.yaz.calculator.data.history.Ledger
import com.yaz.calculator.feature.advanced.AdvancedViewModel
import com.yaz.calculator.data.rates.Rates
import com.yaz.calculator.data.settings.SettingsStore
import com.yaz.calculator.data.sheets.SheetStore
import com.yaz.calculator.feature.calc.CalcViewModel
import com.yaz.calculator.feature.convert.ConvertViewModel
import com.yaz.calculator.feature.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Single composition root. */
val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single { SettingsStore(androidContext()) }
    single { Ledger(androidContext(), get()) }
    single { SheetStore(androidContext(), get()) }
    single { Rates(androidContext(), get()) }
    single { AdvancedStore(androidContext(), get()) }
    viewModelOf(::CalcViewModel)
    viewModelOf(::ConvertViewModel)
    viewModelOf(::AdvancedViewModel)
    viewModelOf(::SettingsViewModel)
}
