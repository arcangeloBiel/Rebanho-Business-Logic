package com.jakatech.rebanhomais.kmp.data.preferencias

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPreferenciasRepositoryTest {

    @Test
    fun ca_06_2_a_fazenda_selecionada_continua_depois_de_reabrir_o_app() = runTest {
        val armazenamento = MapSettings()
        SettingsPreferenciasRepository(armazenamento).selecionarFazenda("f2")

        // Nova instância = app aberto de novo, lendo o que ficou guardado no aparelho.
        val reaberto = SettingsPreferenciasRepository(armazenamento)
        assertEquals("f2", reaberto.fazendaSelecionadaId.first())
    }

    @Test
    fun limpar_a_selecao_apaga_do_aparelho() = runTest {
        val armazenamento = MapSettings()
        val preferencias = SettingsPreferenciasRepository(armazenamento)
        preferencias.selecionarFazenda("f2")
        preferencias.selecionarFazenda(null)

        assertNull(preferencias.fazendaSelecionadaId.first())
        assertNull(SettingsPreferenciasRepository(armazenamento).fazendaSelecionadaId.first())
    }
}
