package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** No Android o banco precisa do Context: o app inicia o Koin com androidContext(this). */
internal actual val moduloDoBancoLocal: Module = module {
    single<SqlDriver> { AndroidSqliteDriver(RebanhoDatabase.Schema, androidContext(), NOME_DO_BANCO) }
}
