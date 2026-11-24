package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarMinhasFazendas
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovaFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.SelecionarFazenda
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.fakes.IdsSequenciais
import com.jakatech.rebanhomais.kmp.domain.fakes.MinhasFazendasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PreferenciasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeFazenda
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Só a parte de sessão importa aqui. */
private class SessaoFixa(estado: EstadoSessao) : AuthRepository {
    override val sessao = MutableStateFlow(estado)
    override suspend fun criarConta(email: String, senha: String, nome: String?) = Resultado.Sucesso(Unit)
    override suspend fun confirmarConta(email: String, codigo: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun reenviarCodigo(email: String) = Resultado.Sucesso(Unit)
    override suspend fun entrar(email: String, senha: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun sair() { sessao.value = EstadoSessao.Deslogado }
}

class CasosDeFazendaTest {
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()
    private val cadastrar = CadastrarFazenda(minhasFazendas, preferencias, IdsSequenciais())

    private fun item(id: String, papel: Papel = Papel.DONO) = FazendaDoUsuario(Fazenda(id, "Fazenda $id"), papel)

    // HU-05

    @Test
    fun ca_05_1_cria_a_fazenda_como_dono_e_ja_seleciona() = runTest {
        val criada = cadastrar(NovaFazenda(nome = " Sítio Boa Vista ", uf = " mg ", municipio = " Uberaba ")).sucesso()
        assertEquals("Sítio Boa Vista", criada.fazenda.nome)
        assertEquals("MG", criada.fazenda.uf)
        assertEquals("Uberaba", criada.fazenda.municipio)
        assertEquals(Papel.DONO, criada.papel)
        assertEquals(listOf(criada), minhasFazendas.fazendas.value)
        assertEquals(criada.fazenda.id, preferencias.fazendaSelecionadaId.value)
    }

    @Test
    fun ca_05_3_nome_em_branco_falha_sem_chamar_o_servidor() = runTest {
        cadastrar(NovaFazenda(nome = "   ")).falhaCom<ErroDominio.NomeObrigatorio>()
        assertTrue(minhasFazendas.fazendas.value.isEmpty())
    }

    @Test
    fun uf_invalida_falha_sem_chamar_o_servidor() = runTest {
        cadastrar(NovaFazenda(nome = "Sítio", uf = "Minas")).falhaCom<ErroDominio.UfInvalida>()
        cadastrar(NovaFazenda(nome = "Sítio", uf = "M1")).falhaCom<ErroDominio.UfInvalida>()
        assertTrue(minhasFazendas.fazendas.value.isEmpty())
    }

    @Test
    fun falha_no_servidor_nao_muda_a_selecao() = runTest {
        preferencias.selecionarFazenda("anterior")
        minhasFazendas.falhaAoCriar = Resultado.Falha(ErroDominio.SemConexao)
        cadastrar(NovaFazenda(nome = "Sítio")).falhaCom<ErroDominio.SemConexao>()
        assertEquals("anterior", preferencias.fazendaSelecionadaId.value)
    }

    // HU-06

    @Test
    fun ca_06_1_lista_as_fazendas_com_o_papel_em_cada_uma() = runTest {
        minhasFazendas.fazendas.value = listOf(item("a"), item("b", Papel.TECNICO), item("c", Papel.FUNCIONARIO))
        val lista = ListarMinhasFazendas(minhasFazendas)().sucesso()
        assertEquals(listOf(Papel.DONO, Papel.TECNICO, Papel.FUNCIONARIO), lista.map { it.papel })
    }

    @Test
    fun ca_06_2_selecionar_guarda_a_escolha() = runTest {
        minhasFazendas.fazendas.value = listOf(item("a"), item("b", Papel.TECNICO))
        val escolhida = SelecionarFazenda(minhasFazendas, preferencias)("b").sucesso()
        assertEquals(Papel.TECNICO, escolhida.papel)
        assertEquals("b", preferencias.fazendaSelecionadaId.value)
    }

    @Test
    fun selecionar_fazenda_que_nao_e_minha_falha() = runTest {
        minhasFazendas.fazendas.value = listOf(item("a"))
        val erro = SelecionarFazenda(minhasFazendas, preferencias)("x").falhaCom<ErroDominio.FazendaNaoEncontrada>()
        assertEquals("x", erro.fazendaId)
        assertNull(preferencias.fazendaSelecionadaId.value)
    }

    // RN-07 e o estado da sessão

    @Test
    fun rn_07_regra_de_selecao() {
        val lista = listOf(item("a"), item("b"))
        assertEquals("b", RegrasDeFazenda.escolherSelecionada(lista, "b")?.fazenda?.id)
        assertEquals("a", RegrasDeFazenda.escolherSelecionada(lista, "removida")?.fazenda?.id)
        assertEquals("a", RegrasDeFazenda.escolherSelecionada(lista, null)?.fazenda?.id)
        assertNull(RegrasDeFazenda.escolherSelecionada(emptyList(), "a"))
    }

    @Test
    fun sessao_logada_traz_as_fazendas_e_a_selecionada() = runTest {
        val auth = SessaoFixa(EstadoSessao.Logado(Usuario("u1", "ana@teste.com")))
        minhasFazendas.fazendas.value = listOf(item("a"), item("b"))
        preferencias.selecionarFazenda("b")

        val logado = assertIs<EstadoSessao.Logado>(ObservarSessao(auth, minhasFazendas, preferencias)().first())
        assertEquals(2, logado.fazendas.size)
        assertEquals("b", logado.fazendaSelecionada?.fazenda?.id)
    }

    @Test
    fun sessao_deslogada_nao_carrega_fazendas() = runTest {
        val auth = SessaoFixa(EstadoSessao.Deslogado)
        minhasFazendas.fazendas.value = listOf(item("a"))
        assertIs<EstadoSessao.Deslogado>(ObservarSessao(auth, minhasFazendas, preferencias)().first())
    }
}
