package com.jakatech.rebanhomais.kmp.data.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.data.local.AnimaisLocais
import com.jakatech.rebanhomais.kmp.data.local.PesagensLocais
import com.jakatech.rebanhomais.kmp.data.local.RebanhoDatabase
import com.jakatech.rebanhomais.kmp.data.local.driverEmMemoria
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoRecebimento
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Um aparelho: banco em memória, repositórios e sincronizador apontando para o mesmo servidor falso. */
private class Aparelho(servidor: ServidorDoRebanho, val fazendas: FazendasFalsas, lote: Int) {
    val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private var relogio = 0
    private val agora = { "2026-11-24T10:${(relogio / 60).toString().padStart(2, '0')}:${(relogio++ % 60).toString().padStart(2, '0')}Z" }
    val animais = AnimaisLocais(banco, agora = agora)
    val pesagens = PesagensLocais(banco, agora = agora)
    val sync = SincronizacaoLocal(driver, servidor, MapSettings(), fazendas, agora = agora, tamanhoDoLote = lote)

    fun coluna(sql: String): String? = driver.executeQuery(
        null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0
    ).value
}

/** PRD Fase 4, fatia 2: recebimento do que os outros aparelhos lançaram. */
class RecebimentoTest {
    private val servidor = ServidorFalso()
    private val aparelhos = mutableListOf<Aparelho>()

    private fun aparelho(vararg fazendas: String = arrayOf("f1"), lote: Int = 500) =
        Aparelho(servidor, FazendasFalsas(*fazendas), lote).also { aparelhos += it }

    @AfterTest
    fun fechar() = aparelhos.forEach { it.driver.close() }

    private fun animal(id: String, brinco: String, fazenda: String = "f1") = Animal(
        id = id, fazendaId = fazenda, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10)
    )

    /** Linha de animal como o Supabase devolve (tipos do Postgres, com updated_at do servidor). */
    private fun animalNoServidor(id: String, brinco: String, fazenda: String = "f1", deletedAt: String? = null): JsonObject =
        buildJsonObject {
            put("id", id); put("fazenda_id", fazenda); put("brinco", brinco); put("sexo", "MACHO")
            put("origem", "COMPRADO"); put("data_nascimento", "2025-08-01"); put("nome", "Do servidor")
            put("raca", null as String?); put("aptidao", "CORTE"); put("mae_id", null as String?)
            put("pai_id", null as String?); put("lote_id", null as String?); put("reprodutor", true)
            put("status", "ATIVO"); put("observacao", null as String?)
            put("created_at", "2026-11-20T09:00:00.123456+00:00"); put("deleted_at", deletedAt)
        }

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
    }

    // HU-02

    @Test
    fun ca_02_1_o_que_outro_aparelho_enviou_aparece_aqui() = runTest {
        val funcionario = aparelho()
        val dono = aparelho()
        funcionario.animais.salvar(animal("a1", "A1"))
        funcionario.pesagens.salvar(Pesagem("p1", "f1", "a1", LocalDate(2026, 10, 1), 312.5, "curral"))
        funcionario.sync.sincronizar().ok()

        val emissoes = Channel<List<Animal>>(Channel.UNLIMITED)
        val coleta = launch { dono.animais.observarPorFazenda("f1").collect { emissoes.send(it) } }
        assertEquals(0, emissoes.receive().size)

        val resumo = dono.sync.receberNovidades().ok()

        assertEquals(2, resumo.recebidos)
        var lista = emissoes.receive()
        while (lista.isEmpty()) lista = emissoes.receive()
        assertEquals(listOf("A1"), lista.map { it.brinco }) // a tela se atualiza sozinha (O4)
        coleta.cancel()
        assertEquals(animal("a1", "A1"), dono.animais.buscarPorId("a1"))
        assertEquals(listOf(Pesagem("p1", "f1", "a1", LocalDate(2026, 10, 1), 312.5, "curral")), dono.pesagens.listarPorAnimal("a1"))
        assertEquals("SINCRONIZADO", dono.coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
        assertTrue(dono.sync.estado.first().tudoSalvo)
        assertNotNull(dono.sync.estado.first().ultimoRecebimento)
    }

    @Test
    fun tipos_do_postgres_viram_os_do_aparelho() = runTest {
        val dono = aparelho()
        servidor.colocar("animais", animalNoServidor("a1", "A1"))

        dono.sync.receberNovidades().ok()

        val a = assertNotNull(dono.animais.buscarPorId("a1"))
        assertEquals(Sexo.MACHO, a.sexo)
        assertEquals(Origem.COMPRADO, a.origem)
        assertEquals(LocalDate(2025, 8, 1), a.dataNascimento)
        assertTrue(a.reprodutor)
        assertNull(a.raca)
    }

    @Test
    fun ca_02_2_exclusao_feita_em_outro_aparelho_some_daqui() = runTest {
        val dono = aparelho()
        servidor.colocar("animais", animalNoServidor("a1", "A1"))
        dono.sync.receberNovidades().ok()
        assertNotNull(dono.animais.buscarPorId("a1"))

        servidor.colocar("animais", animalNoServidor("a1", "A1", deletedAt = "2026-11-24T12:30:00+00:00"))
        dono.sync.receberNovidades().ok()

        assertNull(dono.animais.buscarPorId("a1"))
        assertEquals(emptyList(), dono.animais.listarPorFazenda("f1"))
        assertEquals("a1", dono.animais.buscarPorBrinco("f1", "A1")?.id) // continua guardado (RN-05)
    }

    @Test
    fun ca_02_3_fazenda_que_saiu_da_lista_e_apagada_do_aparelho() = runTest {
        val tecnico = aparelho("f1", "f2")
        servidor.colocar("animais", animalNoServidor("a1", "A1", "f1"), animalNoServidor("b1", "B1", "f2"))
        tecnico.sync.receberNovidades().ok()
        tecnico.animais.salvar(animal("b2", "B2", "f2")) // pendente da f2: o servidor recusaria (RN-11)

        tecnico.fazendas.fazendas.value = tecnico.fazendas.fazendas.value.filter { it.fazenda.id == "f1" }
        tecnico.sync.receberNovidades().ok()

        assertEquals(listOf("A1"), tecnico.animais.listarPorFazenda("f1").map { it.brinco })
        assertEquals("0", tecnico.coluna("SELECT count(*) FROM animais WHERE fazenda_id = 'f2'"))
        assertEquals("0", tecnico.coluna("SELECT count(*) FROM marcadores_sync WHERE fazenda_id = 'f2'"))
    }

    // HU-03

    @Test
    fun ca_03_1_carga_inicial_em_lotes_mesmo_com_tudo_no_mesmo_instante() = runTest {
        val novo = aparelho(lote = 2)
        // 7 animais gravados numa só transação no servidor: mesmo updated_at para todos.
        servidor.colocar("animais", *Array(7) { animalNoServidor("a$it", "A$it") })

        assertEquals(7, novo.sync.receberNovidades().ok().recebidos)

        assertEquals(7, novo.animais.listarPorFazenda("f1").size)
        val buscasDeAnimais = servidor.buscas.filter { it.first == "animais" }.map { it.second }
        assertIs<FiltroDeBusca.Tudo>(buscasDeAnimais.first())
        assertTrue(buscasDeAnimais.size <= 6, "buscas demais: $buscasDeAnimais")
    }

    @Test
    fun ca_03_2_carga_interrompida_continua_sem_baixar_de_novo() = runTest {
        val novo = aparelho(lote = 2)
        servidor.colocar("animais", animalNoServidor("a0", "A0"))
        servidor.pularSegundos(10)
        servidor.colocar("animais", animalNoServidor("a1", "A1"))
        servidor.pularSegundos(10)
        servidor.colocar("animais", *Array(3) { animalNoServidor("a${it + 2}", "A${it + 2}") })
        // lotes e protocolos (2 buscas vazias), depois 1 lote de animais (a0, a1), e então cai.
        servidor.falharBuscaApartirDe = 4

        assertIs<ErroDominio.SemConexao>(assertIs<Resultado.Falha>(novo.sync.receberNovidades()).erro)
        assertEquals(2, novo.animais.listarPorFazenda("f1").size)
        assertNull(novo.sync.estado.first().ultimoRecebimento)

        servidor.falharBuscaApartirDe = null
        val antes = servidor.buscas.size
        // a1 volta pela janela de 5 s do marcador; a0, mais antigo, não é baixado de novo.
        assertEquals(4, novo.sync.receberNovidades().ok().recebidos)
        assertEquals(5, novo.animais.listarPorFazenda("f1").size)
        val retomada = servidor.buscas.drop(antes).first { it.first == "animais" }.second
        assertIs<FiltroDeBusca.DepoisDe>(retomada) // não começou do zero
    }

    @Test
    fun recebimento_sem_novidades_nao_reaplica_nada_alem_da_janela_de_5_segundos() = runTest {
        val dono = aparelho()
        servidor.colocar("animais", animalNoServidor("a1", "A1"))
        dono.sync.receberNovidades().ok()
        servidor.buscas.clear()

        dono.sync.receberNovidades().ok()

        assertEquals(6, servidor.buscas.size) // uma busca por tabela (RNF-02)
        assertIs<FiltroDeBusca.DepoisDe>(servidor.buscas.single { it.first == "animais" }.second)
    }

    // RN-04

    @Test
    fun rn_04_alteracao_local_nao_enviada_nao_e_sobrescrita() = runTest {
        val dono = aparelho()
        servidor.colocar("animais", animalNoServidor("a1", "A1"))
        dono.sync.receberNovidades().ok()
        val local = dono.animais.buscarPorId("a1")!!.copy(nome = "Alterado aqui")
        dono.animais.salvar(local)

        servidor.colocar("animais", animalNoServidor("a1", "A1")) // outra versão chega do servidor
        val resumo = dono.sync.receberNovidades().ok()

        assertEquals(ResumoDoRecebimento(recebidos = 0, mantidasLocais = 1, conflitos = 0), resumo)
        assertEquals("Alterado aqui", dono.animais.buscarPorId("a1")?.nome)
        assertEquals("PENDENTE", dono.coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))

        // Depois do envio, a versão do servidor (a deste aparelho) volta e fica sincronizada.
        dono.sync.sincronizar().ok()
        assertEquals("Alterado aqui", dono.animais.buscarPorId("a1")?.nome)
        assertEquals("SINCRONIZADO", dono.coluna("SELECT sync_status FROM animais WHERE id = 'a1'"))
    }

    // R-01, CA-05.1: dois aparelhos com o mesmo brinco sem sinal

    @Test
    fun brinco_repetido_so_neste_aparelho_fica_com_erro_e_o_do_servidor_espera() = runTest {
        val dono = aparelho()
        dono.animais.salvar(animal("meu", "77"))
        servidor.colocar("animais", animalNoServidor("dele", "77"))

        val resumo = dono.sync.receberNovidades().ok()

        assertEquals(1, resumo.conflitos)
        assertNull(dono.animais.buscarPorId("dele"))
        assertEquals("23505", dono.coluna("SELECT sync_erro_codigo FROM animais WHERE id = 'meu'"))
        assertEquals(ErroDominio.BrincoDuplicado("77"), dono.sync.observarRegistrosComErro().first().single().erro)

        // Corrigido o brinco do animal daqui, o do servidor entra.
        dono.animais.salvar(animal("meu", "78"))
        dono.sync.receberNovidades().ok()
        assertEquals("77", dono.animais.buscarPorId("dele")?.brinco)
    }

    // RN-09 e sincronizar

    @Test
    fun rn_09_sem_sessao_nao_recebe_nem_apaga_nada() = runTest {
        val dono = aparelho()
        dono.animais.salvar(animal("a1", "A1"))
        dono.fazendas.erro = ErroDominio.SemPermissao // lista de fazendas sem usuário logado

        assertIs<ErroDominio.SemSessao>(assertIs<Resultado.Falha>(dono.sync.receberNovidades()).erro)
        assertNotNull(dono.animais.buscarPorId("a1"))
    }

    @Test
    fun sincronizar_nao_recebe_se_o_envio_parou() = runTest {
        val dono = aparelho()
        dono.animais.salvar(animal("a1", "A1"))
        servidor.indisponivelNaTabela = "animais"

        assertIs<ErroDominio.SemConexao>(assertIs<Resultado.Falha>(dono.sync.sincronizar()).erro)
        assertTrue(servidor.buscas.isEmpty())
    }

    @Test
    fun horas_do_servidor_viram_utc_com_z_e_o_marcador_recua_5_segundos() {
        assertEquals("2026-11-24T12:00:00.000123Z", normalizar("2026-11-24T12:00:00.000123+00:00"))
        assertEquals("2026-11-24T11:59:55.000123Z", recuar("2026-11-24T12:00:00.000123Z"))
        assertEquals("nao-e-data", normalizar("nao-e-data"))
    }
}
