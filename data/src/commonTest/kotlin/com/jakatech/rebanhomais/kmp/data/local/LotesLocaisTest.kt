package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD Fase 3, fatia 4: lotes gravados no banco do aparelho, nos três alvos. */
class LotesLocaisTest {
    private val driver: SqlDriver = driverEmMemoria()
    private var relogio = 0
    private val lotes = LotesLocais(RebanhoDatabase(driver), agora = { "2026-11-24T10:00:${(relogio++).toString().padStart(2, '0')}Z" })

    @AfterTest
    fun fechar() = driver.close()

    private fun coluna(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) },
        parameters = 0
    ).value

    @Test
    fun lote_gravado_volta_igual_pendente_e_so_na_propria_fazenda_por_nome() = runTest {
        lotes.salvar(Lote("l2", "f1", "pasto B"))
        lotes.salvar(Lote("l1", "f1", "Pasto A"))
        lotes.salvar(Lote("l3", "f2", "Outro"))

        assertEquals(Lote("l1", "f1", "Pasto A"), lotes.buscarPorId("l1"))
        assertNull(lotes.buscarPorId("nao-existe"))
        assertEquals(listOf("Pasto A", "pasto B"), lotes.listarPorFazenda("f1").map { it.nome })
        assertEquals(listOf("Pasto A", "pasto B"), lotes.observarPorFazenda("f1").first().map { it.nome })
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM lotes WHERE id = 'l1'"))
    }

    @Test
    fun rn_02_renomear_mantem_created_at_e_volta_a_ficar_pendente() = runTest {
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))
        driver.execute(null, "UPDATE lotes SET sync_status = 'SINCRONIZADO'", 0)
        val criado = coluna("SELECT created_at FROM lotes WHERE id = 'l1'")

        lotes.salvar(Lote("l1", "f1", "Piquete 1"))

        assertEquals("Piquete 1", lotes.buscarPorId("l1")?.nome)
        assertEquals(criado, coluna("SELECT created_at FROM lotes WHERE id = 'l1'"))
        assertTrue(coluna("SELECT updated_at FROM lotes WHERE id = 'l1'")!! > criado!!)
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM lotes WHERE id = 'l1'"))
        assertEquals("1", coluna("SELECT count(*) FROM lotes"))
    }

    @Test
    fun lote_excluido_nao_aparece() = runTest {
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))
        driver.execute(null, "UPDATE lotes SET deleted_at = '2026-11-24T11:00:00Z' WHERE id = 'l1'", 0)
        assertNull(lotes.buscarPorId("l1"))
        assertEquals(emptyList(), lotes.listarPorFazenda("f1"))
    }

    @Test
    fun o4_lista_de_lotes_se_atualiza_sozinha() = runTest {
        val emissoes = Channel<List<Lote>>(Channel.UNLIMITED)
        val coleta = launch { lotes.observarPorFazenda("f1").collect { emissoes.send(it) } }

        assertEquals(0, emissoes.receive().size)
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))
        assertEquals(listOf("Pasto 1"), emissoes.receive().map { it.nome })
        coleta.cancel()
    }
}
