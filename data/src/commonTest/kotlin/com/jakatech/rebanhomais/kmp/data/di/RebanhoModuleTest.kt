package com.jakatech.rebanhomais.kmp.data.di

import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class RebanhoModuleTest {

    @Test
    fun modulo_carrega_sem_erros() {
        val app = koinApplication { modules(rebanhoModule) }
        assertNotNull(app.koin)
        app.close()
    }
}
