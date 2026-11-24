package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CorrigirBrinco
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoBrincoInvalido
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.fakes.AnimaisEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.MinhasFazendasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PreferenciasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.SincronizacaoEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Conta : AuthRepository {
    override val sessao = MutableStateFlow<EstadoSessao>(EstadoSessao.Logado(Usuario("u1", "ana@teste.com")))
    override suspend fun criarConta(email: String, senha: String, nome: String?) = Resultado.Sucesso(Unit)
    override suspend fun confirmarConta(email: String, codigo: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun reenviarCodigo(email: String) = Resultado.Sucesso(Unit)
    override suspend fun entrar(email: String, senha: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun pedirCodigoDeRecuperacao(email: String) = Resultado.Sucesso(Unit)
    override suspend fun redefinirSenha(email: String, codigo: String, novaSenha: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun sair() { sessao.value = EstadoSessao.Deslogado }
}

/** PRD Fase 4, fatia 4: sair sem perder dados sem aviso (HU-06) e corrigir brinco recusado (HU-05, RN-12). */
class SairECorrigirTest {
    private val conta = Conta()
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()

    private suspend fun sairCom(sync: SincronizacaoEmMemoria, confirmar: Boolean = false): Resultado<Unit> {
        minhasFazendas.criar(Fazenda("f1", "Sítio"))
        preferencias.selecionarFazenda("f1")
        return Sair(conta, minhasFazendas, preferencias, sync)(confirmarPerda = confirmar)
    }

    // HU-06

    @Test
    fun ca_06_1_com_internet_envia_antes_e_sai_apagando_tudo() = runTest {
        val sync = SincronizacaoEmMemoria(pendentes = 3)

        sairCom(sync).sucesso()

        assertEquals(1, sync.envios)
        assertTrue(sync.apagou)
        assertIs<EstadoSessao.Deslogado>(conta.sessao.value)
        assertTrue(minhasFazendas.fazendas.value.isEmpty())
        assertNull(preferencias.fazendaSelecionadaId.value)
    }

    @Test
    fun ca_06_2_sem_internet_avisa_quantos_e_nao_sai_nem_apaga() = runTest {
        val sync = SincronizacaoEmMemoria(pendentes = 3, comErro = 1).apply { online = false }

        val erro = sairCom(sync).falhaCom<ErroDominio.DadosNaoEnviados>()

        assertEquals(ErroDominio.DadosNaoEnviados(pendentes = 3, comErro = 1), erro)
        assertFalse(sync.apagou)
        assertIs<EstadoSessao.Logado>(conta.sessao.value)
        assertEquals("f1", preferencias.fazendaSelecionadaId.value)
    }

    @Test
    fun rn_10_registro_recusado_tambem_segura_a_saida_mesmo_com_internet() = runTest {
        val sync = SincronizacaoEmMemoria(comErro = 2)
        assertEquals(ErroDominio.DadosNaoEnviados(0, 2), sairCom(sync).falhaCom<ErroDominio.DadosNaoEnviados>())
    }

    @Test
    fun ca_06_2_confirmada_a_perda_sai_e_apaga() = runTest {
        val sync = SincronizacaoEmMemoria(pendentes = 3).apply { online = false }

        sairCom(sync, confirmar = true).sucesso()

        assertTrue(sync.apagou)
        assertIs<EstadoSessao.Deslogado>(conta.sessao.value)
    }

    // HU-05, RN-12

    private val animais = AnimaisEmMemoria()
    private val corrigir = CorrigirBrinco(animais)

    private suspend fun animal(id: String, brinco: String): Animal = Animal(
        id = id, fazendaId = "f1", brinco = brinco, sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = LocalDate(2026, 1, 10)
    ).also { animais.salvar(it) }

    @Test
    fun rn_12_corrige_o_brinco_de_animal_que_nunca_foi_ao_servidor() = runTest {
        val a = animal("a1", "123")
        val corrigido = corrigir(a.id, " 124 ").sucesso()
        assertEquals("124", corrigido.brinco)
        assertEquals("124", animais.buscarPorId("a1")?.brinco)
    }

    @Test
    fun rn_12_animal_ja_sincronizado_nao_muda_de_brinco() = runTest {
        val a = animal("a1", "123")
        animais.noServidor += a.id
        corrigir(a.id, "124").falhaCom<ErroDominio.BrincoJaSincronizado>()
        assertEquals("123", animais.buscarPorId("a1")?.brinco)
    }

    @Test
    fun correcao_valida_o_brinco_novo() = runTest {
        val a = animal("a1", "123")
        animal("a2", "200")
        assertEquals("200", corrigir(a.id, "200").falhaCom<ErroDominio.BrincoDuplicado>().brinco)
        assertEquals(MotivoBrincoInvalido.VAZIO, corrigir(a.id, "  ").falhaCom<ErroDominio.BrincoInvalido>().motivo)
        corrigir("nao-existe", "1").falhaCom<ErroDominio.AnimalNaoEncontrado>()
        val gravacoes = animais.gravacoes
        assertEquals(a, corrigir(a.id, "123").sucesso()) // mesmo brinco: nada a gravar
        assertEquals(gravacoes, animais.gravacoes)
    }
}
