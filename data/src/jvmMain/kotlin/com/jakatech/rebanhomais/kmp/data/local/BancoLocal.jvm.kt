package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Properties

/** JVM: usado por testes e ferramentas; grava num arquivo na pasta atual. */
internal actual val moduloDoBancoLocal: Module = module {
    single<SqlDriver> {
        JdbcSqliteDriver(url = "jdbc:sqlite:$NOME_DO_BANCO", properties = Properties(), schema = RebanhoDatabase.Schema)
    }
}
