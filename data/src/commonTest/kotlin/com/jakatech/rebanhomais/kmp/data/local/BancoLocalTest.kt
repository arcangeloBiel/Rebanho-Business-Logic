package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** PRD Fase 3, fatia 1: o banco abre nos três alvos com as tabelas e as regras certas. */
class BancoLocalTest {
    private val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)

    @AfterTest
    fun fechar() = driver.close()

    private fun executar(sql: String) {
        driver.execute(null, sql, 0)
    }

    private fun texto(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0
    ).value

    private fun inserirAnimal(id: String, fazenda: String, brinco: String, sexo: String = "FEMEA") = executar(
        """
        INSERT INTO animais (id, fazenda_id, brinco, sexo, origem, created_at, updated_at)
        VALUES ('$id', '$fazenda', '$brinco', '$sexo', 'NASCIDO_NA_FAZENDA', '2026-11-23T10:00:00Z', '2026-11-23T10:00:00Z')
        """.trimIndent()
    )

    @Test
    fun rf_09_cria_as_seis_tabelas_do_rebanho_vazias() {
        assertEquals(0L, banco.loteQueries.contar().executeAsOne())
        assertEquals(0L, banco.animalQueries.contar().executeAsOne())
        assertEquals(0L, banco.movimentacaoQueries.contar().executeAsOne())
        assertEquals(0L, banco.pesagemQueries.contar().executeAsOne())
        assertEquals(0L, banco.protocoloSanitarioQueries.contar().executeAsOne())
        assertEquals(0L, banco.aplicacaoVacinaQueries.contar().executeAsOne())
    }

    @Test
    fun rn_02_registro_novo_nasce_pendente_de_sincronizacao() {
        inserirAnimal("a1", "f1", "A001")
        assertEquals("PENDENTE", texto("SELECT sync_status FROM animais WHERE id = 'a1'"))
        assertEquals("ATIVO", texto("SELECT status FROM animais WHERE id = 'a1'"))
        assertEquals("MISTA", texto("SELECT aptidao FROM animais WHERE id = 'a1'"))
    }

    @Test
    fun brinco_unico_por_fazenda_tambem_no_aparelho() {
        inserirAnimal("a1", "f1", "A001")
        inserirAnimal("b1", "f2", "A001") // outra fazenda: pode
        assertFails { inserirAnimal("a2", "f1", "A001") }
        assertEquals(2L, banco.animalQueries.contar().executeAsOne())
    }

    @Test
    fun valores_fora_do_dominio_sao_recusados() {
        assertFails { inserirAnimal("a1", "f1", "A001", sexo = "OUTRO") }
        assertFails {
            executar(
                """
                INSERT INTO pesagens (id, fazenda_id, animal_id, data, peso_kg, created_at, updated_at)
                VALUES ('p1', 'f1', 'a1', '2026-11-23', 0, 'x', 'x')
                """.trimIndent()
            )
        }
        inserirAnimal("a9", "f1", "Z9")
        assertFails { executar("UPDATE animais SET sync_status = 'ENVIANDO' WHERE id = 'a9'") }
    }
}
