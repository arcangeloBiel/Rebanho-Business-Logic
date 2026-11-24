package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

actual fun driverEmMemoria(): SqlDriver = inMemoryDriver(RebanhoDatabase.Schema)
