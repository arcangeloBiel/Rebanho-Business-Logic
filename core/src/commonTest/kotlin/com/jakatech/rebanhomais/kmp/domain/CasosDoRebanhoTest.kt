package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EdicaoDeAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EditarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EventoDoHistorico
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ExcluirAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarHistoricoDoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarRebanho
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
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
import com.jakatech.rebanhomais.kmp.domain.model.Aptidao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD Fase 3, fatia 2: rebanho observável, edição, exclusão e histórico. */
class CasosDoRebanhoTest {
    private val animais = AnimaisEmMemoria()
    private val movimentacoes = MovimentacoesEmMemoria()
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()
    private val ids = IdsSequenciais()
    private val relogio = RelogioFixo()

    private val cadastrar = CadastrarAnimal(animais, movimentacoes, ids, relogio)
    private val observar = ObservarRebanho(animais, minhasFazendas, preferencias)
    private val editar = EditarAnimal(animais)
    private val excluir = ExcluirAnimal(animais, minhasFazendas)
    private val saida = RegistrarSaida(animais, movimentacoes, ids, relogio)
    private val historico = ObservarHistoricoDoAnimal(movimentacoes, PesagensEmMemoria(), VacinasEmMemoria())

    private fun participar(fazendaId: String, papel: Papel = Papel.DONO) {
        minhasFazendas.fazendas.value += FazendaDoUsuario(Fazenda(fazendaId, "Fazenda $fazendaId"), papel)
    }

    private suspend fun novo(brinco: String, fazenda: String = "f1", nome: String? = null): Animal = cadastrar(
        NovoAnimal(fazendaId = fazenda, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
            dataNascimento = LocalDate(2026, 1, 10), nome = nome)
    ).sucesso()

    // HU-02

    @Test
    fun ca_02_1_so_animais_da_fazenda_selecionada_ativos_primeiro_e_por_brinco() = runTest {
        participar("f1"); participar("f2"); preferencias.selecionarFazenda("f1")
        novo("C3"); novo("A1"); val b2 = novo("B2"); novo("Z9", fazenda = "f2")
        saida(NovaSaida(b2.id, TipoMovimentacao.VENDA, HOJE)).sucesso()

        assertEquals(listOf("A1", "C3", "B2"), observar().first().map { it.brinco })
    }

    @Test
    fun ca_02_2_busca_por_parte_do_brinco_ou_do_nome_sem_diferenca_de_maiusculas() = runTest {
        participar("f1"); preferencias.selecionarFazenda("f1")
        novo("A10", nome = "Mimosa"); novo("B20", nome = "Estrela"); novo("A11")

        assertEquals(listOf("A10", "A11"), observar("a1").first().map { it.brinco })
        assertEquals(listOf("A10"), observar("  mimo ").first().map { it.brinco })
    }

    @Test
    fun ca_02_3_lista_se_atualiza_sozinha_e_acompanha_a_busca_digitada() = runTest {
        participar("f1"); preferencias.selecionarFazenda("f1")
        val busca = MutableStateFlow("")
        val fluxo = observar(busca)
        assertEquals(emptyList(), fluxo.first())

        novo("A1"); novo("B2")
        assertEquals(2, fluxo.first().size)
        busca.value = "b"
        assertEquals(listOf("B2"), fluxo.first().map { it.brinco })
    }

    @Test
    fun rn_01_sem_fazenda_ou_depois_de_sair_a_lista_fica_vazia() = runTest {
        novo("A1")
        assertEquals(emptyList(), observar().first()) // usuário sem fazendas
        participar("f1")
        assertEquals(1, observar().first().size)     // única fazenda é escolhida sozinha (RN-07)
        minhasFazendas.limpar()
        assertEquals(emptyList(), observar().first())
    }

    @Test
    fun ca_02_4_animal_excluido_nao_aparece() = runTest {
        participar("f1"); preferencias.selecionarFazenda("f1")
        val a = novo("A1"); novo("B2")
        excluir(a.id).sucesso()
        assertEquals(listOf("B2"), observar().first().map { it.brinco })
    }

    // HU-03

    @Test
    fun ca_03_1_corrige_dados_descritivos_sem_tocar_no_brinco() = runTest {
        val a = novo("A1")
        val editado = editar(EdicaoDeAnimal(a.id, nome = " Mimosa ", raca = "Nelore", aptidao = Aptidao.CORTE, loteId = "l1", observacao = " "))
            .sucesso()
        assertEquals("Mimosa", editado.nome)
        assertEquals("Nelore", editado.raca)
        assertEquals(Aptidao.CORTE, editado.aptidao)
        assertEquals("l1", editado.loteId)
        assertNull(editado.observacao)
        assertEquals("A1", editado.brinco)
        assertEquals(editado, animais.buscarPorId(a.id))
    }

    @Test
    fun edicao_sem_mudanca_nao_grava_de_novo() = runTest {
        val a = novo("A1")
        val antes = animais.gravacoes
        editar(EdicaoDeAnimal(a.id, a.nome, a.raca, a.aptidao, a.loteId, a.observacao)).sucesso()
        assertEquals(antes, animais.gravacoes)
    }

    @Test
    fun nao_edita_animal_vendido_nem_inexistente() = runTest {
        val a = novo("A1")
        saida(NovaSaida(a.id, TipoMovimentacao.VENDA, HOJE)).sucesso()
        val edicao = EdicaoDeAnimal(a.id, "X", null, Aptidao.MISTA, null, null)
        assertEquals(StatusAnimal.VENDIDO, editar(edicao).falhaCom<ErroDominio.AnimalInativo>().status)
        editar(edicao.copy(animalId = "nao-existe")).falhaCom<ErroDominio.AnimalNaoEncontrado>()
    }

    @Test
    fun ca_03_2_funcionario_e_tecnico_nao_excluem() = runTest {
        participar("f1", Papel.FUNCIONARIO); participar("f2", Papel.TECNICO)
        val a = novo("A1", fazenda = "f1"); val b = novo("B1", fazenda = "f2")
        assertIs<ErroDominio.SemPermissao>(excluir(a.id).falhaCom<ErroDominio>())
        assertIs<ErroDominio.SemPermissao>(excluir(b.id).falhaCom<ErroDominio>())
        assertTrue(animais.excluidos.isEmpty())
    }

    @Test
    fun ca_03_3_dono_exclui_e_o_animal_continua_guardado() = runTest {
        participar("f1")
        val a = novo("A1")
        excluir(a.id).sucesso()
        assertNull(animais.buscarPorId(a.id))
        assertEquals(a, animais.buscarPorBrinco("f1", "A1")) // brinco continua ocupado
        excluir(a.id).falhaCom<ErroDominio.AnimalNaoEncontrado>()
    }

    @Test
    fun nao_exclui_animal_de_fazenda_que_nao_e_minha() = runTest {
        val a = novo("A1", fazenda = "outra")
        excluir(a.id).falhaCom<ErroDominio.FazendaNaoEncontrada>()
    }

    // Histórico (RF-07)

    @Test
    fun ca_01_3_e_ca_05_1_historico_mostra_entrada_e_venda_em_ordem_de_data() = runTest {
        val a = novo("A1")
        saida(NovaSaida(a.id, TipoMovimentacao.VENDA, HOJE, contraparte = "Frigorífico", valor = 3500.0)).sucesso()

        val eventos = historico(a.id).first()
        assertEquals(
            listOf(TipoMovimentacao.NASCIMENTO, TipoMovimentacao.VENDA),
            eventos.map { (it as EventoDoHistorico.Movimentou).movimentacao.tipo }
        )
        assertEquals(listOf(LocalDate(2026, 1, 10), HOJE), eventos.map { it.data })
        val venda = (eventos.last() as EventoDoHistorico.Movimentou).movimentacao
        assertEquals("Frigorífico", venda.contraparte)
        assertEquals(3500.0, venda.valor)
    }
}
