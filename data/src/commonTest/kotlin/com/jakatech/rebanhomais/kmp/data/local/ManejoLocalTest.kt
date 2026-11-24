package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.regra.MotivoPendencia
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
import kotlin.test.fail

/** PRD Fase 3, fatia 3: pesagens, protocolos e vacinas gravados no banco do aparelho, nos três alvos. */
class ManejoLocalTest {
    private val driver: SqlDriver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private var relogio = 0
    private val agora = { "2026-11-23T10:00:${(relogio++).toString().padStart(2, '0')}Z" }
    private val animais = AnimaisLocais(banco, agora = agora)
    private val movimentacoes = MovimentacoesLocais(banco, agora = agora)
    private val pesagens = PesagensLocais(banco, agora = agora)
    private val vacinas = VacinasLocais(banco, agora = agora)

    @AfterTest
    fun fechar() = driver.close()

    private fun coluna(sql: String): String? = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) },
        parameters = 0
    ).value

    private fun <T> Resultado<T>.ok(): T = when (this) {
        is Resultado.Sucesso -> valor
        is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
    }

    // Pesagens (RF-01, RN-02)

    @Test
    fun pesagem_gravada_volta_igual_pendente_e_em_ordem_de_data() = runTest {
        val outubro = Pesagem("p2", "f1", "a1", LocalDate(2026, 10, 31), 330.5, "após desmama")
        val setembro = Pesagem("p1", "f1", "a1", LocalDate(2026, 9, 30), 300.0)
        pesagens.salvar(outubro)
        pesagens.salvar(setembro)
        pesagens.salvar(Pesagem("p3", "f1", "outro", LocalDate(2026, 9, 1), 200.0))

        assertEquals(listOf(setembro, outubro), pesagens.listarPorAnimal("a1"))
        assertEquals(listOf(setembro, outubro), pesagens.observarPorAnimal("a1").first())
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM pesagens WHERE id = 'p2'"))
        assertEquals("2026-10-31", coluna("SELECT data FROM pesagens WHERE id = 'p2'"))
    }

    @Test
    fun rn_02_corrigir_pesagem_mantem_created_at_e_volta_a_ficar_pendente() = runTest {
        val p = Pesagem("p1", "f1", "a1", LocalDate(2026, 10, 1), 300.0)
        pesagens.salvar(p)
        driver.execute(null, "UPDATE pesagens SET sync_status = 'SINCRONIZADO'", 0)
        val criado = coluna("SELECT created_at FROM pesagens WHERE id = 'p1'")

        pesagens.salvar(p.copy(pesoKg = 305.0))

        assertEquals(305.0, pesagens.listarPorAnimal("a1").single().pesoKg)
        assertEquals(criado, coluna("SELECT created_at FROM pesagens WHERE id = 'p1'"))
        assertTrue(coluna("SELECT updated_at FROM pesagens WHERE id = 'p1'")!! > criado!!)
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM pesagens WHERE id = 'p1'"))
        assertEquals("1", coluna("SELECT count(*) FROM pesagens"))
    }

    // Protocolos

    @Test
    fun protocolo_gravado_volta_igual_e_lista_so_os_da_fazenda_por_nome() = runTest {
        val completo = ProtocoloSanitario(
            id = "pr1", fazendaId = "f1", nome = "clostridiose", doses = 2, intervaloEntreDosesDias = 30,
            reforcoDias = 365, sexoAlvo = Sexo.FEMEA, idadeMinimaMeses = 3, idadeMaximaMeses = 8
        )
        val simples = ProtocoloSanitario(id = "pr2", fazendaId = "f1", nome = "Aftosa")
        vacinas.salvarProtocolo(completo)
        vacinas.salvarProtocolo(simples)
        vacinas.salvarProtocolo(ProtocoloSanitario(id = "pr3", fazendaId = "f2", nome = "Raiva"))

        assertEquals(completo, vacinas.buscarProtocolo("pr1"))
        assertEquals(simples, vacinas.buscarProtocolo("pr2"))
        assertNull(vacinas.buscarProtocolo("nao-existe"))
        assertEquals(listOf("Aftosa", "clostridiose"), vacinas.listarProtocolos("f1").map { it.nome })
        assertEquals(listOf("Aftosa", "clostridiose"), vacinas.observarProtocolos("f1").first().map { it.nome })
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM protocolos_sanitarios WHERE id = 'pr1'"))

        driver.execute(null, "UPDATE protocolos_sanitarios SET sync_status = 'SINCRONIZADO'", 0)
        vacinas.salvarProtocolo(completo.copy(reforcoDias = null))
        assertNull(vacinas.buscarProtocolo("pr1")?.reforcoDias)
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM protocolos_sanitarios WHERE id = 'pr1'"))
        assertEquals("SINCRONIZADO", coluna("SELECT sync_status FROM protocolos_sanitarios WHERE id = 'pr2'"))
    }

    // Aplicações de vacina

    @Test
    fun aplicacoes_gravadas_pendentes_por_animal_e_por_fazenda() = runTest {
        val segunda = AplicacaoVacina("v2", "f1", "a1", "pr1", "Clostridiose", LocalDate(2026, 10, 31), dose = 2, loteProduto = "L-77")
        val primeira = AplicacaoVacina("v1", "f1", "a1", "pr1", "Clostridiose", LocalDate(2026, 10, 1), dose = 1)
        val avulsa = AplicacaoVacina("v3", "f1", "a2", null, "Vermífugo", LocalDate(2026, 10, 15), observacao = "lote novo")
        vacinas.salvarAplicacao(segunda)
        vacinas.salvarAplicacao(primeira)
        vacinas.salvarAplicacao(avulsa)
        vacinas.salvarAplicacao(AplicacaoVacina("v4", "f2", "b1", null, "Raiva", LocalDate(2026, 10, 1)))

        assertEquals(listOf(primeira, segunda), vacinas.listarAplicacoesPorAnimal("a1"))
        assertEquals(listOf(primeira, segunda), vacinas.observarAplicacoesPorAnimal("a1").first())
        assertEquals(listOf(primeira, avulsa, segunda), vacinas.listarAplicacoesPorFazenda("f1"))
        assertEquals(listOf(primeira, avulsa, segunda), vacinas.observarAplicacoesPorFazenda("f1").first())
        assertEquals("PENDENTE", coluna("SELECT sync_status FROM aplicacoes_vacina WHERE id = 'v3'"))
        assertNull(coluna("SELECT protocolo_id FROM aplicacoes_vacina WHERE id = 'v3'"))
    }

    @Test
    fun o4_aplicacoes_da_fazenda_se_atualizam_sozinhas() = runTest {
        val emissoes = Channel<List<AplicacaoVacina>>(Channel.UNLIMITED)
        val coleta = launch { vacinas.observarAplicacoesPorFazenda("f1").collect { emissoes.send(it) } }

        assertEquals(0, emissoes.receive().size)
        vacinas.salvarAplicacao(AplicacaoVacina("v1", "f1", "a1", null, "Raiva", LocalDate(2026, 10, 1)))
        assertEquals(listOf("v1"), emissoes.receive().map { it.id })
        coleta.cancel()
    }

    // Casos de uso de ponta a ponta no banco local (O1)

    private var proximoId = 0
    private val ids = GeradorDeId { "id-${++proximoId}" }
    private val hoje = Relogio { LocalDate(2026, 11, 23) }

    private suspend fun bezerro(): String = CadastrarAnimal(animais, movimentacoes, LotesLocais(banco, agora = agora), ids, hoje)(
        NovoAnimal(fazendaId = "f1", brinco = "B1", sexo = Sexo.MACHO, origem = Origem.NASCIDO_NA_FAZENDA,
            dataNascimento = LocalDate(2026, 3, 1))
    ).ok().id

    @Test
    fun ca_04_1_resumo_de_peso_reflete_a_pesagem_nova_em_modo_aviao() = runTest {
        val animalId = bezerro()
        val pesar = RegistrarPesagem(animais, pesagens, ids, hoje)
        val resumo = ObservarResumoDePeso(pesagens)(animalId)

        pesar(NovaPesagem(animalId, LocalDate(2026, 10, 1), 200.0)).ok()
        pesar(NovaPesagem(animalId, LocalDate(2026, 10, 31), 230.0)).ok()

        val atual = resumo.first()
        assertEquals(230.0, atual.ultimaPesagem!!.pesoKg)
        assertEquals(1.0, atual.ganhoMedioDiario!!, 0.0001)
        assertEquals("0", coluna("SELECT count(*) FROM pesagens WHERE sync_status <> 'PENDENTE'"))
    }

    @Test
    fun ca_04_2_e_ca_06_1_segunda_dose_aparece_nas_pendencias_na_data_certa() = runTest {
        val animalId = bezerro()
        val protocolo = CadastrarProtocolo(vacinas, ids)(
            NovoProtocolo(fazendaId = "f1", nome = "Clostridiose", doses = 2, intervaloEntreDosesDias = 30)
        ).ok()

        val registrada = RegistrarVacinacao(animais, vacinas, ids, hoje)(
            NovaVacinacao(animalId, LocalDate(2026, 10, 28), protocoloId = protocolo.id)
        ).ok()
        assertEquals(1, registrada.aplicacao.dose)

        val pendencia = ListarPendenciasVacinais(animais, vacinas, hoje)("f1").single()
        assertEquals(LocalDate(2026, 11, 27), pendencia.dataPrevista)
        assertEquals(2, pendencia.dose)
        assertEquals(MotivoPendencia.PROXIMA_DOSE, pendencia.motivo)

        // A 2ª dose deduzida do histórico gravado no aparelho.
        val segunda = RegistrarVacinacao(animais, vacinas, ids, hoje)(
            NovaVacinacao(animalId, LocalDate(2026, 11, 23), protocoloId = protocolo.id)
        ).ok()
        assertEquals(2, segunda.aplicacao.dose)
        assertTrue(ListarPendenciasVacinais(animais, vacinas, hoje)("f1").isEmpty())
    }
}
