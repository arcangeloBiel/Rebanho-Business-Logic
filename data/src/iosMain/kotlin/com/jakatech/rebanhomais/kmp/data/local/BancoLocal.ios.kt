package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val moduloDoBancoLocal: Module = module {
    single<SqlDriver> { NativeSqliteDriver(RebanhoDatabase.Schema, NOME_DO_BANCO) }
}
