package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.SqlDriver

/** Banco SQLite em memória, já com o schema criado. Cada chamada devolve um banco novo e vazio. */
expect fun driverEmMemoria(): SqlDriver
