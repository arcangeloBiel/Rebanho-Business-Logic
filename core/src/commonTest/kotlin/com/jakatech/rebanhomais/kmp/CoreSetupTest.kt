package com.jakatech.rebanhomais.kmp

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Confirma que o módulo core compila e enxerga as bibliotecas base em todas as plataformas. */
class CoreSetupTest {

    @Test
    fun saudacao() {
        assertEquals("Hello, Rebanho!", sayHello("Rebanho"))
    }

    @Test
    fun kotlinx_datetime_disponivel() {
        assertEquals("2026-11-23", LocalDate(2026, 11, 23).toString())
    }
}
