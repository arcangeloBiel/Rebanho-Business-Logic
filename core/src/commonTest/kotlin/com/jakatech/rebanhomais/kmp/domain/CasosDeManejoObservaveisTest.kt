package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EventoDoHistorico
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaVacinacao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarHistoricoDoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.fakes.AnimaisEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.HOJE
import com.jakatech.rebanhomais.kmp.domain.fakes.IdsSequenciais
import com.jakatech.rebanhomais.kmp.domain.fakes.MinhasFazendasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.MovimentacoesEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PesagensEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PreferenciasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.RelogioFixo
import com.jakatech.rebanhomais.kmp.domain.fakes.VacinasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import com.jakatech.rebanhomais.kmp.domain.regra.MotivoPendencia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD Fase 3, fatia 3: pesagens, vacinas e pendências que se atualizam sozinhas (HU-04, HU-06, RF-07, RF-08). */
class CasosDeManejoObservaveisTest {
    private val animais = AnimaisEmMemoria()
    private val movimentacoes = MovimentacoesEmMemoria()
    private val pesagens = PesagensEmMemoria()
    private val vacinas = VacinasEmMemoria()
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()
    private val ids = IdsSequenciais()
    private val relogio = RelogioFixo()

    private val cadastrar = CadastrarAnimal(animais, movimentacoes, ids, relogio)
    private val pesar = RegistrarPesagem(animais, pesagens, ids, relogio)
    private val vacinar = RegistrarVacinacao(animais, vacinas, ids, relogio)
    private val novoProtocolo = CadastrarProtocolo(vacinas, ids)
    private val saida = RegistrarSaida(animais, movimentacoes, ids, relogio)
    private val resumo = ObservarResumoDePeso(pesagens)
    private val historico = ObservarHistoricoDoAnimal(movimentacoes, pesagens, vacinas)
    private val pendencias = ObservarPendenciasVacinais(animais, vacinas, minhasFazendas, preferencias, relogio)

    private fun participar(fazendaId: String, papel: Papel = Papel.DONO) {
        minhasFazendas.fazendas.value += FazendaDoUsuario(Fazenda(fazendaId, "Fazenda $fazendaId"), papel)
    }

    private suspend fun novo(brinco: String, fazenda: String = "f1"): Animal = cadastrar(
        NovoAnimal(fazendaId = fazenda, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
            dataNascimento = LocalDate(2026, 1, 10))
    ).sucesso()

    private suspend fun duasDoses(fazenda: String = "f1"): ProtocoloSanitario = novoProtocolo(
        NovoProtocolo(fazendaId = fazenda, nome = "Clostridiose", doses = 2, intervaloEntreDosesDias = 30)
    ).sucesso()

    // HU-04

    @Test
    fun ca_04_1_resumo_de_peso_reflete_a_pesagem_nova_sem_recarregar() = runTest {
        val a = novo("A1")
        val fluxo = resumo(a.id)
        assertEquals(0, fluxo.first().totalDePesagens)

        pesar(NovaPesagem(a.id, LocalDate(2026, 10, 1), 300.0)).sucesso()
        val depoisDaPrimeira = fluxo.first()
        assertEquals(300.0, depoisDaPrimeira.ultimaPesagem!!.pesoKg)
        assertNull(depoisDaPrimeira.ganhoMedioDiario)

        pesar(NovaPesagem(a.id, LocalDate(2026, 10, 31), 330.0)).sucesso()
        val depoisDaSegunda = fluxo.first()
        assertEquals(330.0, depoisDaSegunda.ultimaPesagem!!.pesoKg)
        assertEquals(1.0, depoisDaSegunda.ganhoMedioDiario!!, 0.0001)
        assertEquals(2, depoisDaSegunda.totalDePesagens)
    }

    @Test
    fun ca_04_3_nao_pesa_nem_vacina_animal_vendido() = runTest {
        val a = novo("A1")
        saida(NovaSaida(a.id, TipoMovimentacao.VENDA, HOJE)).sucesso()
        assertEquals(StatusAnimal.VENDIDO, pesar(NovaPesagem(a.id, HOJE, 300.0)).falhaCom<ErroDominio.AnimalInativo>().status)
        assertEquals(StatusAnimal.VENDIDO, vacinar(NovaVacinacao(a.id, HOJE, nomeVacina = "Raiva")).falhaCom<ErroDominio.AnimalInativo>().status)
        assertTrue(pesagens.pesagens.isEmpty())
        assertTrue(vacinas.aplicacoes.isEmpty())
    }

    // HU-06

    @Test
    fun ca_04_2_e_ca_06_1_pendencias_da_fazenda_selecionada_se_atualizam_a_cada_dose() = runTest {
        participar("f1"); preferencias.selecionarFazenda("f1")
        val a = novo("A1")
        val protocolo = duasDoses()
        val fluxo = pendencias()

        val antes = fluxo.first().single()
        assertEquals(MotivoPendencia.PRIMEIRA_DOSE, antes.motivo)
        assertEquals(HOJE, antes.dataPrevista)

        vacinar(NovaVacinacao(a.id, LocalDate(2026, 10, 28), protocoloId = protocolo.id)).sucesso()

        val depois = fluxo.first().single()
        assertEquals(MotivoPendencia.PROXIMA_DOSE, depois.motivo)
        assertEquals(2, depois.dose)
        assertEquals(LocalDate(2026, 11, 27), depois.dataPrevista)
        assertTrue(!depois.atrasada(HOJE))
    }

    @Test
    fun ca_06_1_lista_atrasadas_e_ignora_o_que_vence_depois_de_7_dias() = runTest {
        participar("f1"); preferencias.selecionarFazenda("f1")
        val atrasado = novo("A1"); val longe = novo("B2")
        val protocolo = duasDoses()
        vacinar(NovaVacinacao(atrasado.id, LocalDate(2026, 10, 1), protocoloId = protocolo.id)).sucesso() // 2ª dose em 31/10
        vacinar(NovaVacinacao(longe.id, LocalDate(2026, 11, 20), protocoloId = protocolo.id)).sucesso()   // 2ª dose em 20/12

        val lista = pendencias().first()
        assertEquals(listOf("A1"), lista.map { it.brinco })
        assertTrue(lista.single().atrasada(HOJE))
    }

    @Test
    fun rn_01_pendencias_so_da_fazenda_selecionada_e_vazias_sem_fazenda() = runTest {
        novo("A1", fazenda = "f1"); novo("B1", fazenda = "f2")
        duasDoses("f1"); duasDoses("f2")
        assertEquals(emptyList(), pendencias().first()) // usuário sem fazendas

        participar("f1"); participar("f2")
        preferencias.selecionarFazenda("f2")
        assertEquals(listOf("B1"), pendencias().first().map { it.brinco })

        preferencias.selecionarFazenda("f1")
        assertEquals(listOf("A1"), pendencias().first().map { it.brinco })

        minhasFazendas.limpar()
        assertEquals(emptyList(), pendencias().first())
    }

    // Histórico (RF-07)

    @Test
    fun historico_junta_entrada_pesagens_e_vacinas_em_ordem_de_data() = runTest {
        val a = novo("A1") // nascimento em 10/01
        pesar(NovaPesagem(a.id, LocalDate(2026, 5, 1), 150.0)).sucesso()
        vacinar(NovaVacinacao(a.id, LocalDate(2026, 3, 1), nomeVacina = "Raiva")).sucesso()
        pesar(NovaPesagem(a.id, LocalDate(2026, 1, 10), 32.0)).sucesso() // mesmo dia do nascimento

        val eventos = historico(a.id).first()
        assertEquals(
            listOf(LocalDate(2026, 1, 10), LocalDate(2026, 1, 10), LocalDate(2026, 3, 1), LocalDate(2026, 5, 1)),
            eventos.map { it.data }
        )
        assertIs<EventoDoHistorico.Movimentou>(eventos[0]) // entrada antes do manejo do mesmo dia
        assertEquals(32.0, assertIs<EventoDoHistorico.Pesou>(eventos[1]).pesagem.pesoKg)
        assertEquals("Raiva", assertIs<EventoDoHistorico.Vacinou>(eventos[2]).aplicacao.nomeVacina)
        assertEquals(150.0, assertIs<EventoDoHistorico.Pesou>(eventos[3]).pesagem.pesoKg)
    }
}
