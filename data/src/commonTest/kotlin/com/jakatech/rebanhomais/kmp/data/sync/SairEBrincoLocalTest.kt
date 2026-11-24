package com.jakatech.rebanhomais.kmp.data.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.data.local.AnimaisLocais
import com.jakatech.rebanhomais.kmp.data.local.LotesLocais
import com.jakatech.rebanhomais.kmp.data.local.PesagensLocais
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.driverEmMemoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CorrigirBrinco
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** PRD Fase 4, fatia 4: apagar tudo ao sair e a marca de "já esteve no servidor" (RN-12). */
class SairEBrincoLocalTest {
    private val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private var relogio = 0
    private val agora = { "2026-11-24T10:${(relogio / 60).toString().padStart(2, '0')}:${(relogio++ % 60).toString().padStart(2, '0')}Z" }
    private val animais = AnimaisLocais(banco, agora = agora)
    private val pesagens = PesagensLocais(banco, agora = agora)
    private val lotes = LotesLocais(banco, agora = agora)
    private val servidor = ServidorFalso()
    private val settings = MapSettings()
    private val sync = SincronizacaoLocal(driver, servidor, settings, FazendasFalsas("f1"), agora = agora)

    @AfterTest
    fun fechar() = driver.close()

    private fun contar(tabela: String): String? = driver.executeQuery(
        null, "SELECT count(*) FROM $tabela", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0
    ).value

    private fun animal(id: String, brinco: String) = Animal(
        id = id, fazendaId = "f1", brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10)
    )

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
    }

    @Test
    fun rnf_05_apagar_dados_locais_nao_deixa_nada_do_rebanho_no_aparelho() = runTest {
        lotes.salvar(Lote("l1", "f1", "Pasto"))
        animais.salvar(animal("a1", "A1"))
        pesagens.salvar(Pesagem("p1", "f1", "a1", LocalDate(2026, 10, 1), 300.0))
        sync.sincronizar().ok()
        animais.salvar(animal("a2", "A2")) // pendente
        assertTrue(sync.estado.first().ultimoEnvio != null)

        sync.apagarDadosLocais()

        for (tabela in TABELAS_DE_SINCRONIZACAO.map { it.nome } + "marcadores_sync") {
            assertEquals("0", contar(tabela), "sobrou algo em $tabela")
        }
        val estado = sync.estado.first()
        assertTrue(estado.tudoSalvo)
        assertNull(estado.ultimoEnvio)
        assertNull(estado.ultimoRecebimento)
        assertNull(settings.getStringOrNull(SincronizacaoLocal.CHAVE_ULTIMO_ENVIO))
    }

    @Test
    fun rn_12_animal_aceito_pelo_servidor_fica_marcado() = runTest {
        animais.salvar(animal("a1", "A1"))
        assertFalse(animais.jaEstaNoServidor("a1"))

        sync.enviarPendentes().ok()

        assertTrue(animais.jaEstaNoServidor("a1"))
        animais.salvar(animal("a1", "A1").copy(nome = "Editada")) // edição depois do envio não apaga a marca
        assertTrue(animais.jaEstaNoServidor("a1"))
    }

    @Test
    fun rn_12_animal_que_veio_do_servidor_fica_marcado() = runTest {
        servidor.colocar("animais", buildJsonObject {
            put("id", "b1"); put("fazenda_id", "f1"); put("brinco", "B1"); put("sexo", "FEMEA")
            put("origem", "COMPRADO"); put("aptidao", "MISTA"); put("reprodutor", false); put("status", "ATIVO")
            put("created_at", "2026-11-20T09:00:00+00:00")
        })

        sync.receberNovidades().ok()

        assertTrue(animais.jaEstaNoServidor("b1"))
    }

    @Test
    fun ca_05_1_e_rn_12_brinco_recusado_e_corrigido_e_reenviado() = runTest {
        animais.salvar(animal("a1", "123"))
        servidor.recusarIds = setOf("a1")
        sync.enviarPendentes().ok()
        assertEquals(1, sync.estado.first().comErro)

        servidor.recusarIds = emptySet()
        assertEquals("124", CorrigirBrinco(animais)("a1", "124").ok().brinco)
        assertEquals(1, sync.estado.first().pendentes) // sem erro, de volta à fila (CA-05.2)

        sync.enviarPendentes().ok()
        assertTrue(sync.estado.first().tudoSalvo)
        assertEquals(
            ErroDominio.BrincoJaSincronizado,
            assertIs<Resultado.Falha>(CorrigirBrinco(animais)("a1", "125")).erro
        )
    }
}
