package com.jakatech.rebanhomais.kmp.data.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.data.local.AnimaisLocais
import com.jakatech.rebanhomais.kmp.data.local.LotesLocais
import com.jakatech.rebanhomais.kmp.data.local.MovimentacoesLocais
import com.jakatech.rebanhomais.kmp.data.local.PesagensLocais
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.VacinasLocais
import com.jakatech.rebanhomais.kmp.data.local.driverEmMemoria
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.TipoDeRegistro
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Servidor de mentira: guarda pelo id (como o upsert do Supabase) e responde o que o teste mandar. */
private class ServidorFalso : ServidorDoRebanho {
    val tabelas = mutableMapOf<String, MutableMap<String, JsonObject>>()
    val chamadas = mutableListOf<Pair<String, List<String>>>()
    var recusarIds = setOf<String>()
    var indisponivelNaTabela: String? = null
    var semSessao = false
    var aoGravar: suspend () -> Unit = {}

    override suspend fun gravar(tabela: String, linhas: List<JsonObject>): RespostaDoServidor {
        val ids = linhas.map { it.getValue("id").jsonPrimitive.content }
        chamadas += tabela to ids
        aoGravar()
        if (semSessao) return RespostaDoServidor.SemSessao
        if (tabela == indisponivelNaTabela) return RespostaDoServidor.Indisponivel("sem sinal")
        if (ids.any { it in recusarIds }) return RespostaDoServidor.Recusado("23505", "duplicate key value violates unique constraint")
        val destino = tabelas.getOrPut(tabela) { mutableMapOf() }
        linhas.forEachIndexed { i, json -> destino[ids[i]] = json }
        return RespostaDoServidor.Aceito
    }
}

/** PRD Fase 4, fatia 1: envio do aparelho para o servidor. */
class EnvioTest {
    private val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private var relogio = 0
    private val agora = { "2026-11-24T10:${(relogio / 60).toString().padStart(2, '0')}:${(relogio++ % 60).toString().padStart(2, '0')}Z" }
    private val lotes = LotesLocais(banco, agora = agora)
    private val animais = AnimaisLocais(banco, agora = agora)
    private val movimentacoes = MovimentacoesLocais(banco, agora = agora)
    private val pesagens = PesagensLocais(banco, agora = agora)
    private val vacinas = VacinasLocais(banco, agora = agora)
    private val servidor = ServidorFalso()
    private val settings = MapSettings()
    private val envio = SincronizacaoLocal(driver, servidor, settings, agora = agora, tamanhoDoLote = 2)

    @AfterTest
    fun fechar() = driver.close()

    private fun coluna(sql: String): String? = driver.executeQuery(
        null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0
    ).value

    private fun animal(id: String, brinco: String, mae: String? = null, lote: String? = null) = Animal(
        id = id, fazendaId = "f1", brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10), maeId = mae, loteId = lote
    )

    private fun pesagem(id: String, animal: String) = Pesagem(id, "f1", animal, LocalDate(2026, 10, 1), 300.0)

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
    }

    // HU-01

    @Test
    fun ca_01_1_pendentes_vao_ao_servidor_e_ficam_sincronizados() = runTest {
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))
        animais.salvar(animal("a1", "A1", lote = "l1"))
        pesagens.salvar(pesagem("p1", "a1"))
        assertEquals(3, envio.estado.first().pendentes)

        assertEquals(ResumoDoEnvio(enviados = 3, recusados = 0, aguardando = 0), envio.enviarPendentes().ok())

        assertEquals(setOf("l1"), servidor.tabelas["lotes"]!!.keys)
        assertEquals(setOf("a1"), servidor.tabelas["animais"]!!.keys)
        assertEquals(setOf("p1"), servidor.tabelas["pesagens"]!!.keys)
        assertEquals("SINCRONIZADO", coluna("SELECT sync_status FROM pesagens WHERE id = 'p1'"))
        val estado = envio.estado.first()
        assertTrue(estado.tudoSalvo)
        assertNotNull(estado.ultimoEnvio)
        assertFalse(estado.sincronizando)
    }

    @Test
    fun ca_01_2_ordem_das_dependencias_maes_antes_das_crias() = runTest {
        // Cadastrados fora de ordem no aparelho: manejos e a cria antes da mãe e do lote.
        vacinas.salvarAplicacao(AplicacaoVacina("v1", "f1", "cria", "pr1", "Clostridiose", LocalDate(2026, 10, 1)))
        pesagens.salvar(pesagem("p1", "cria"))
        animais.salvar(animal("cria", "C1", mae = "mae", lote = "l1"))
        animais.salvar(animal("mae", "M1"))
        vacinas.salvarProtocolo(ProtocoloSanitario("pr1", "f1", "Clostridiose", doses = 2, intervaloEntreDosesDias = 30))
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))

        envio.enviarPendentes().ok()

        assertEquals(
            listOf("lotes", "protocolos_sanitarios", "animais", "pesagens", "aplicacoes_vacina"),
            servidor.chamadas.map { it.first }
        )
        assertEquals(listOf("mae", "cria"), servidor.chamadas.single { it.first == "animais" }.second)
    }

    @Test
    fun json_tem_as_colunas_do_supabase_sem_updated_at_e_com_tipos_certos() = runTest {
        animais.salvar(animal("a1", "A1").copy(reprodutor = false))
        animais.excluir("a1")

        envio.enviarPendentes().ok()

        val json = servidor.tabelas["animais"]!!.getValue("a1")
        assertEquals(TABELAS_DE_SINCRONIZACAO.single { it.nome == "animais" }.colunas.map { it.first }.toSet(), json.keys)
        assertNull(json["updated_at"])
        assertNull(json["sync_status"])
        assertEquals(false, json.getValue("reprodutor").jsonPrimitive.booleanOrNull)
        assertEquals("2026-01-10", json.getValue("data_nascimento").jsonPrimitive.content)
        assertTrue(json.getValue("deleted_at") is JsonPrimitive && json.getValue("deleted_at") != JsonNull) // exclusão vai junto (RN-05)
        assertEquals(JsonNull, json.getValue("mae_id"))
    }

    @Test
    fun lotes_de_ate_500_registros_no_mesmo_envio() = runTest {
        repeat(5) { animais.salvar(animal("a$it", "A$it")) } // tamanhoDoLote = 2 neste teste
        envio.enviarPendentes().ok()
        assertEquals(listOf(2, 2, 1), servidor.chamadas.map { it.second.size })
    }

    @Test
    fun ca_01_3_queda_no_meio_mantem_o_que_foi_aceito_e_retoma_sem_duplicar() = runTest {
        lotes.salvar(Lote("l1", "f1", "Pasto 1"))
        animais.salvar(animal("a1", "A1"))
        servidor.indisponivelNaTabela = "animais"

        val r = envio.enviarPendentes()
        assertIs<ErroDominio.SemConexao>(assertIs<Resultado.Falha>(r).erro)
        assertEquals("SINCRONIZADO", coluna("SELECT sync_status FROM lotes WHERE id = 'l1'"))
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
        assertEquals(1, envio.estado.first().pendentes)
        assertNull(envio.estado.first().ultimoEnvio)

        servidor.indisponivelNaTabela = null
        servidor.chamadas.clear()
        envio.enviarPendentes().ok()
        assertEquals(listOf("animais"), servidor.chamadas.map { it.first }) // o lote não vai de novo
        assertTrue(envio.estado.first().tudoSalvo)
    }

    @Test
    fun rn_02_alterado_durante_o_envio_continua_pendente() = runTest {
        animais.salvar(animal("a1", "A1"))
        servidor.aoGravar = {
            servidor.aoGravar = {}
            animais.salvar(animal("a1", "A1").copy(nome = "Mimosa"))
        }

        envio.enviarPendentes().ok()

        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
        envio.enviarPendentes().ok()
        assertEquals("Mimosa", servidor.tabelas["animais"]!!.getValue("a1").getValue("nome").jsonPrimitive.content)
        assertEquals("SINCRONIZADO", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
    }

    // HU-05, RN-06

    @Test
    fun ca_05_1_recusado_fica_com_erro_e_o_resto_segue() = runTest {
        animais.salvar(animal("a1", "A1"))
        animais.salvar(animal("a2", "123")) // o servidor já tem o 123 de outro aparelho
        animais.salvar(animal("a3", "A3"))
        pesagens.salvar(pesagem("p2", "a2")) // depende do recusado: espera
        pesagens.salvar(pesagem("p3", "a3"))
        servidor.recusarIds = setOf("a2")

        val resumo = envio.enviarPendentes().ok()

        assertEquals(ResumoDoEnvio(enviados = 3, recusados = 1, aguardando = 1), resumo)
        assertEquals(setOf("a1", "a3"), servidor.tabelas["animais"]!!.keys)
        assertEquals(setOf("p3"), servidor.tabelas["pesagens"]!!.keys)
        assertEquals("23505", coluna("SELECT sync_erro_codigo FROM animais WHERE id = 'a2'"))
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM pesagens WHERE id = 'p2'"))
        assertNull(coluna("SELECT sync_erro_codigo FROM pesagens WHERE id = 'p2'"))

        val estado = envio.estado.first()
        assertEquals(1, estado.comErro)
        assertEquals(1, estado.pendentes)
        assertFalse(estado.tudoSalvo)

        val erro = envio.observarRegistrosComErro().first().single()
        assertEquals(TipoDeRegistro.ANIMAL, erro.tipo)
        assertEquals("a2", erro.id)
        assertEquals(ErroDominio.BrincoDuplicado("123"), erro.erro)

        // Próximo envio: o recusado não vai de novo, nem o que depende dele.
        servidor.chamadas.clear()
        envio.enviarPendentes().ok()
        assertTrue(servidor.chamadas.isEmpty())
    }

    @Test
    fun cria_de_mae_recusada_espera_sem_ir_ao_servidor() = runTest {
        animais.salvar(animal("mae", "123"))
        animais.salvar(animal("cria", "C1", mae = "mae"))
        servidor.recusarIds = setOf("mae")

        val resumo = envio.enviarPendentes().ok()

        assertEquals(ResumoDoEnvio(enviados = 0, recusados = 1, aguardando = 1), resumo)
        assertTrue(servidor.chamadas.none { it.second == listOf("cria") })
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'cria'"))
        assertNull(coluna("SELECT sync_erro_codigo FROM animais WHERE id = 'cria'"))
    }

    @Test
    fun ca_05_2_corrigido_volta_a_ficar_pendente_e_e_reenviado() = runTest {
        animais.salvar(animal("a2", "123"))
        servidor.recusarIds = setOf("a2")
        envio.enviarPendentes().ok()
        assertEquals(1, envio.estado.first().comErro)

        servidor.recusarIds = emptySet()
        animais.salvar(animal("a2", "123").copy(nome = "Corrigida")) // qualquer gravação local limpa o erro

        assertNull(coluna("SELECT sync_erro_codigo FROM animais WHERE id = 'a2'"))
        assertEquals(1, envio.estado.first().pendentes)
        envio.enviarPendentes().ok()
        assertTrue(envio.estado.first().tudoSalvo)
        assertEquals(emptyList(), envio.observarRegistrosComErro().first())
    }

    // RN-09

    @Test
    fun rn_09_sem_sessao_para_e_nada_e_marcado() = runTest {
        animais.salvar(animal("a1", "A1"))
        servidor.semSessao = true

        val r = envio.enviarPendentes()

        assertIs<ErroDominio.SemSessao>(assertIs<Resultado.Falha>(r).erro)
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
        assertNull(coluna("SELECT sync_erro_codigo FROM animais WHERE id = 'a1'"))
    }

    @Test
    fun sem_pendentes_nao_chama_o_servidor() = runTest {
        envio.enviarPendentes().ok()
        assertTrue(servidor.chamadas.isEmpty())
        assertTrue(envio.estado.first().tudoSalvo)
    }

    // RN-07 e RN-08: classificação das respostas do Supabase

    @Test
    fun classificacao_das_respostas_do_supabase() {
        assertEquals(RespostaDoServidor.SemSessao, classificar(401, null, null))
        assertEquals(RespostaDoServidor.SemSessao, classificar(400, "PGRST301", "JWT expired"))
        assertIs<RespostaDoServidor.Indisponivel>(classificar(503, null, null))
        assertIs<RespostaDoServidor.Indisponivel>(classificar(429, null, null))
        assertEquals(RespostaDoServidor.Recusado("23505", "dup"), classificar(409, "23505", "dup"))
        assertEquals(RespostaDoServidor.Recusado("42501", "rls"), classificar(403, "42501", "rls"))
        assertEquals(ErroDominio.SemPermissao, erroDoRegistro(TipoDeRegistro.PESAGEM, "2026-10-01", "42501", "rls"))
        assertIs<ErroDominio.ErroInesperado>(erroDoRegistro(TipoDeRegistro.LOTE, "Pasto", "23505", "dup"))
    }
}
