package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarLote
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EdicaoDeAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.EditarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoLote
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarLotes
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RenomearLote
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.fakes.AnimaisEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.IdsSequenciais
import com.jakatech.rebanhomais.kmp.domain.fakes.LotesEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.MinhasFazendasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.MovimentacoesEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PreferenciasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.RelogioFixo
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PRD Fase 3, fatia 4: lotes da fazenda e animais só em lotes da própria fazenda. */
class CasosDeLoteTest {
    private val animais = AnimaisEmMemoria()
    private val lotes = LotesEmMemoria()
    private val movimentacoes = MovimentacoesEmMemoria()
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()
    private val ids = IdsSequenciais()
    private val relogio = RelogioFixo()

    private val cadastrarLote = CadastrarLote(lotes, ids)
    private val renomear = RenomearLote(lotes)
    private val observar = ObservarLotes(lotes, minhasFazendas, preferencias)
    private val cadastrarAnimal = CadastrarAnimal(animais, movimentacoes, lotes, ids, relogio)
    private val editar = EditarAnimal(animais, lotes)

    private fun participar(fazendaId: String) {
        minhasFazendas.fazendas.value += FazendaDoUsuario(Fazenda(fazendaId, "Fazenda $fazendaId"), Papel.FUNCIONARIO)
    }

    private fun novoAnimal(brinco: String, loteId: String? = null, fazenda: String = "f1") = NovoAnimal(
        fazendaId = fazenda, brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10), loteId = loteId
    )

    @Test
    fun cadastra_lote_com_nome_aparado_e_recusa_nome_vazio() = runTest {
        val lote = cadastrarLote(NovoLote("f1", "  Pasto do rio ")).sucesso()
        assertEquals("Pasto do rio", lote.nome)
        assertEquals(lote, lotes.buscarPorId(lote.id))

        assertEquals("nome", cadastrarLote(NovoLote("f1", "   ")).falhaCom<ErroDominio.NomeObrigatorio>().campo)
        assertEquals(1, lotes.lotes.size)
    }

    @Test
    fun renomeia_e_nao_grava_de_novo_sem_mudanca() = runTest {
        val lote = cadastrarLote(NovoLote("f1", "Pasto 1")).sucesso()
        assertEquals("Piquete 1", renomear(lote.id, " Piquete 1 ").sucesso().nome)
        val antes = lotes.gravacoes
        renomear(lote.id, "Piquete 1").sucesso()
        assertEquals(antes, lotes.gravacoes)

        renomear(lote.id, "").falhaCom<ErroDominio.NomeObrigatorio>()
        renomear("nao-existe", "X").falhaCom<ErroDominio.LoteNaoEncontrado>()
    }

    @Test
    fun rn_01_lotes_da_fazenda_selecionada_em_ordem_de_nome() = runTest {
        cadastrarLote(NovoLote("f1", "pasto B")).sucesso()
        cadastrarLote(NovoLote("f1", "Pasto A")).sucesso()
        cadastrarLote(NovoLote("f2", "Outro")).sucesso()
        assertEquals(emptyList(), observar().first())

        participar("f1"); participar("f2"); preferencias.selecionarFazenda("f1")
        assertEquals(listOf("Pasto A", "pasto B"), observar().first().map { it.nome })

        preferencias.selecionarFazenda("f2")
        assertEquals(listOf("Outro"), observar().first().map { it.nome })
    }

    @Test
    fun animal_so_entra_em_lote_da_propria_fazenda() = runTest {
        val daFazenda = cadastrarLote(NovoLote("f1", "Pasto 1")).sucesso()
        val deOutra = cadastrarLote(NovoLote("f2", "Pasto 2")).sucesso()

        assertEquals(daFazenda.id, cadastrarAnimal(novoAnimal("A1", daFazenda.id)).sucesso().loteId)
        assertEquals(deOutra.id, cadastrarAnimal(novoAnimal("A2", deOutra.id)).falhaCom<ErroDominio.LoteNaoEncontrado>().loteId)
        cadastrarAnimal(novoAnimal("A3", "nao-existe")).falhaCom<ErroDominio.LoteNaoEncontrado>()
        assertTrue(animais.buscarPorBrinco("f1", "A2") == null)
    }

    @Test
    fun edicao_valida_so_o_lote_novo() = runTest {
        val pasto1 = cadastrarLote(NovoLote("f1", "Pasto 1")).sucesso()
        val outra = cadastrarLote(NovoLote("f2", "Pasto 2")).sucesso()
        val a = cadastrarAnimal(novoAnimal("A1", pasto1.id)).sucesso()

        fun edicao(loteId: String?, nome: String? = null) = EdicaoDeAnimal(a.id, nome, a.raca, a.aptidao, loteId, a.observacao)

        editar(edicao(outra.id)).falhaCom<ErroDominio.LoteNaoEncontrado>()
        assertEquals(null, editar(edicao(null)).sucesso().loteId) // tirar do lote sempre pode

        // O lote atual vale mesmo que tenha sumido do aparelho: corrigir o nome não trava.
        val b = cadastrarAnimal(novoAnimal("B1", pasto1.id)).sucesso()
        lotes.lotes.remove(pasto1.id)
        assertEquals("Mimosa", editar(EdicaoDeAnimal(b.id, "Mimosa", null, b.aptidao, pasto1.id, null)).sucesso().nome)
    }
}
