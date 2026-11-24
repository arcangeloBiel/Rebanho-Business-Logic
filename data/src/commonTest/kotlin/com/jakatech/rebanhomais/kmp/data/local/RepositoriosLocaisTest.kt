package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD Fase 3, fatia 2: animais e movimentações gravados no banco do aparelho, nos três alvos. */
class RepositoriosLocaisTest {
    private val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private var relogio = 0
    private val agora = { "2026-11-23T10:00:0${relogio++}Z" }
    private val animais = AnimaisLocais(banco, agora = agora)
    private val movimentacoes = MovimentacoesLocais(banco, agora = agora)

    @AfterTest
    fun fechar() = driver.close()

    private fun animal(id: String, brinco: String, fazenda: String = "f1", nome: String? = null, status: StatusAnimal = StatusAnimal.ATIVO) =
        Animal(
            id = id, fazendaId = fazenda, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
            dataNascimento = LocalDate(2026, 1, 10), nome = nome, status = status
        )

    private fun coluna(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) },
        parameters = 0
    ).value

    // RF-01, RN-02

    @Test
    fun ca_01_1_animal_gravado_volta_igual_e_pendente() = runTest {
        val a = animal("a1", "A1", nome = "Mimosa").copy(maeId = "m1", reprodutor = false, raca = "Nelore")
        animais.salvar(a)
        assertEquals(a, animais.buscarPorId("a1"))
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
        assertEquals("2026-01-10", coluna("SELECT data_nascimento FROM animais WHERE id = 'a1'"))
    }

    @Test
    fun rn_02_alterar_mantem_created_at_e_volta_a_ficar_pendente() = runTest {
        animais.salvar(animal("a1", "A1"))
        driver.execute(null, "UPDATE animais SET sync_status = 'SINCRONIZADO' WHERE id = 'a1'", 0)
        val criado = coluna("SELECT created_at FROM animais WHERE id = 'a1'")

        animais.salvar(animal("a1", "A1", nome = "Estrela"))

        assertEquals("Estrela", animais.buscarPorId("a1")?.nome)
        assertEquals(criado, coluna("SELECT created_at FROM animais WHERE id = 'a1'"))
        assertTrue(coluna("SELECT updated_at FROM animais WHERE id = 'a1'")!! > criado!!)
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
    }

    @Test
    fun ca_01_2_brinco_ocupado_mesmo_por_animal_vendido_ou_excluido() = runTest {
        animais.salvar(animal("a1", "A1", status = StatusAnimal.VENDIDO))
        animais.salvar(animal("a2", "A2"))
        animais.excluir("a2")
        assertEquals("a1", animais.buscarPorBrinco("f1", "A1")?.id)
        assertEquals("a2", animais.buscarPorBrinco("f1", "A2")?.id)
        assertNull(animais.buscarPorBrinco("f2", "A1"))
    }

    // RF-03

    @Test
    fun ca_02_1_lista_da_fazenda_com_ativos_primeiro_e_por_brinco() = runTest {
        animais.salvar(animal("a", "C3"))
        animais.salvar(animal("b", "A1"))
        animais.salvar(animal("c", "B2", status = StatusAnimal.VENDIDO))
        animais.salvar(animal("d", "A0", fazenda = "f2"))

        assertEquals(listOf("A1", "C3", "B2"), animais.observarPorFazenda("f1").first().map { it.brinco })
        assertEquals(listOf("A1", "C3", "B2"), animais.listarPorFazenda("f1").map { it.brinco })
    }

    @Test
    fun ca_02_2_busca_sem_diferenca_de_maiusculas_e_sem_curinga() = runTest {
        animais.salvar(animal("a", "A10", nome = "Mimosa"))
        animais.salvar(animal("b", "B20", nome = "Estrela"))
        animais.salvar(animal("c", "C30", nome = "100%"))

        assertEquals(listOf("A10"), animais.observarPorFazenda("f1", "a1").first().map { it.brinco })
        assertEquals(listOf("A10"), animais.observarPorFazenda("f1", " MIMO ").first().map { it.brinco })
        assertEquals(listOf("C30"), animais.observarPorFazenda("f1", "0%").first().map { it.brinco })
        assertEquals(emptyList(), animais.observarPorFazenda("f1", "_").first())
    }

    @Test
    fun ca_02_3_lista_se_atualiza_sozinha_depois_de_cada_cadastro() = runTest {
        val emissoes = Channel<List<Animal>>(Channel.UNLIMITED)
        val coleta = launch { animais.observarPorFazenda("f1").collect { emissoes.send(it) } }

        assertEquals(0, emissoes.receive().size)
        animais.salvar(animal("a", "A1"))
        assertEquals(listOf("A1"), emissoes.receive().map { it.brinco })
        animais.excluir("a")
        assertEquals(0, emissoes.receive().size)
        coleta.cancel()
    }

    // RF-06, RN-03

    @Test
    fun ca_03_3_excluido_some_das_buscas_mas_continua_no_aparelho_pendente() = runTest {
        animais.salvar(animal("mae", "M1"))
        animais.salvar(animal("filha", "F1").copy(maeId = "mae"))
        driver.execute(null, "UPDATE animais SET sync_status = 'SINCRONIZADO'", 0)

        animais.excluir("filha")

        assertNull(animais.buscarPorId("filha"))
        assertEquals(listOf("M1"), animais.listarPorFazenda("f1").map { it.brinco })
        assertEquals(0, animais.contarFilhos("mae"))
        assertEquals("2", coluna("SELECT count(*) FROM animais"))
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'filha'"))
        assertTrue(coluna("SELECT deleted_at FROM animais WHERE id = 'filha'") != null)
    }

    // RF-07

    @Test
    fun movimentacoes_gravadas_pendentes_e_em_ordem_de_data() = runTest {
        val venda = Movimentacao("m2", "f1", "a1", TipoMovimentacao.VENDA, LocalDate(2026, 11, 20), "Frigorífico", 3500.5, "à vista")
        val nascimento = Movimentacao("m1", "f1", "a1", TipoMovimentacao.NASCIMENTO, LocalDate(2026, 1, 10))
        movimentacoes.salvar(venda)
        movimentacoes.salvar(nascimento)
        movimentacoes.salvar(Movimentacao("m3", "f1", "outro", TipoMovimentacao.COMPRA, LocalDate(2026, 2, 1)))

        assertEquals(listOf(nascimento, venda), movimentacoes.listarPorAnimal("a1"))
        assertEquals(listOf(nascimento, venda), movimentacoes.observarPorAnimal("a1").first())
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM movimentacoes WHERE id = 'm2'"))

        movimentacoes.salvar(venda.copy(valor = 3600.0))
        assertEquals(3600.0, movimentacoes.listarPorAnimal("a1").last().valor)
        assertEquals("3", coluna("SELECT count(*) FROM movimentacoes"))
    }
}
