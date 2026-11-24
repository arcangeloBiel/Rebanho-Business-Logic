package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.ConfirmarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CriarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Entrar
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.PedirRecuperacaoDeSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RedefinirSenha
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ReenviarCodigo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.fakes.MinhasFazendasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.PreferenciasEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.SincronizacaoEmMemoria
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Repositório falso: registra as chamadas e devolve o que o teste mandar. */
private class AuthFalso : AuthRepository {
    val chamadas = mutableListOf<String>()
    var respostaCriar: Resultado<Unit> = Resultado.Sucesso(Unit)
    var respostaEntrar: Resultado<Usuario> = Resultado.Sucesso(Usuario("u1", "ana@teste.com"))
    var respostaConfirmar: Resultado<Usuario> = Resultado.Sucesso(Usuario("u1", "ana@teste.com"))
    override val sessao = MutableStateFlow<EstadoSessao>(EstadoSessao.Deslogado)

    override suspend fun criarConta(email: String, senha: String, nome: String?): Resultado<Unit> {
        chamadas += "criarConta($email,$nome)"; return respostaCriar
    }
    override suspend fun confirmarConta(email: String, codigo: String): Resultado<Usuario> {
        chamadas += "confirmarConta($email,$codigo)"; return respostaConfirmar
    }
    override suspend fun reenviarCodigo(email: String): Resultado<Unit> {
        chamadas += "reenviarCodigo($email)"; return Resultado.Sucesso(Unit)
    }
    override suspend fun entrar(email: String, senha: String): Resultado<Usuario> {
        chamadas += "entrar($email)"; return respostaEntrar
    }
    var respostaPedirRecuperacao: Resultado<Unit> = Resultado.Sucesso(Unit)
    var respostaRedefinir: Resultado<Usuario> = Resultado.Sucesso(Usuario("u1", "ana@teste.com"))
    override suspend fun pedirCodigoDeRecuperacao(email: String): Resultado<Unit> {
        chamadas += "pedirCodigoDeRecuperacao($email)"; return respostaPedirRecuperacao
    }
    override suspend fun redefinirSenha(email: String, codigo: String, novaSenha: String): Resultado<Usuario> {
        chamadas += "redefinirSenha($email,$codigo)"; return respostaRedefinir
    }
    override suspend fun sair() {
        chamadas += "sair"; sessao.value = EstadoSessao.Deslogado
    }
}

class CasosDeContaTest {
    private val auth = AuthFalso()
    private val minhasFazendas = MinhasFazendasEmMemoria()
    private val preferencias = PreferenciasEmMemoria()

    // HU-01

    @Test
    fun ca_01_1_cria_conta_com_email_normalizado() = runTest {
        val conta = CriarConta(auth)("  Ana@Teste.COM ", "senhaforte", " Ana ").sucesso()
        assertEquals("ana@teste.com", conta.email)
        assertEquals(listOf("criarConta(ana@teste.com,Ana)"), auth.chamadas)
    }

    @Test
    fun ca_01_2_email_ja_cadastrado_vem_do_servidor() = runTest {
        auth.respostaCriar = Resultado.Falha(ErroDominio.EmailJaCadastrado)
        CriarConta(auth)("ana@teste.com", "senhaforte").falhaCom<ErroDominio.EmailJaCadastrado>()
    }

    @Test
    fun ca_01_3_senha_curta_falha_sem_chamar_o_servidor() = runTest {
        val erro = CriarConta(auth)("ana@teste.com", "1234567").falhaCom<ErroDominio.SenhaFraca>()
        assertEquals(8, erro.minimoDeCaracteres)
        assertTrue(auth.chamadas.isEmpty())
    }

    @Test
    fun ca_01_4_email_mal_formatado_falha_sem_chamar_o_servidor() = runTest {
        CriarConta(auth)("ana.teste.com", "senhaforte").falhaCom<ErroDominio.EmailInvalido>()
        CriarConta(auth)("ana@teste", "senhaforte").falhaCom<ErroDominio.EmailInvalido>()
        CriarConta(auth)("ana @teste.com", "senhaforte").falhaCom<ErroDominio.EmailInvalido>()
        assertTrue(auth.chamadas.isEmpty())
    }

    @Test
    fun ca_01_5_confirma_com_codigo_de_6_digitos_aceitando_espacos() = runTest {
        ConfirmarConta(auth)("Ana@teste.com", " 123 456 ").sucesso()
        assertEquals(listOf("confirmarConta(ana@teste.com,123456)"), auth.chamadas)
    }

    @Test
    fun ca_01_6_codigo_mal_digitado_falha_sem_chamar_o_servidor() = runTest {
        ConfirmarConta(auth)("ana@teste.com", "12345").falhaCom<ErroDominio.CodigoInvalido>()
        ConfirmarConta(auth)("ana@teste.com", "12a456").falhaCom<ErroDominio.CodigoInvalido>()
        assertTrue(auth.chamadas.isEmpty())
    }

    @Test
    fun ca_01_6_codigo_vencido_vem_do_servidor() = runTest {
        auth.respostaConfirmar = Resultado.Falha(ErroDominio.CodigoInvalido)
        ConfirmarConta(auth)("ana@teste.com", "123456").falhaCom<ErroDominio.CodigoInvalido>()
    }

    @Test
    fun reenviar_codigo_normaliza_o_email() = runTest {
        ReenviarCodigo(auth)(" ANA@teste.com").sucesso()
        assertEquals(listOf("reenviarCodigo(ana@teste.com)"), auth.chamadas)
    }

    // HU-02

    @Test
    fun ca_02_1_entra_com_email_normalizado() = runTest {
        val usuario = Entrar(auth)(" Ana@Teste.com ", "qualquer-senha").sucesso()
        assertEquals("u1", usuario.id)
        assertEquals(listOf("entrar(ana@teste.com)"), auth.chamadas)
    }

    @Test
    fun ca_02_2_credenciais_invalidas_vem_do_servidor() = runTest {
        auth.respostaEntrar = Resultado.Falha(ErroDominio.CredenciaisInvalidas)
        Entrar(auth)("ana@teste.com", "errada").falhaCom<ErroDominio.CredenciaisInvalidas>()
    }

    @Test
    fun login_nao_exige_8_caracteres_mas_recusa_senha_vazia() = runTest {
        auth.respostaEntrar = Resultado.Sucesso(Usuario("u1", "ana@teste.com"))
        Entrar(auth)("ana@teste.com", "curta").sucesso()
        Entrar(auth)("ana@teste.com", "").falhaCom<ErroDominio.CredenciaisInvalidas>()
        assertEquals(1, auth.chamadas.size)
    }

    // HU-04

    @Test
    fun ca_04_1_pede_o_codigo_com_email_normalizado() = runTest {
        PedirRecuperacaoDeSenha(auth)(" Ana@Teste.com ").sucesso()
        assertEquals(listOf("pedirCodigoDeRecuperacao(ana@teste.com)"), auth.chamadas)
    }

    @Test
    fun ca_04_1_mesma_resposta_para_email_sem_conta() = runTest {
        // Qualquer recusa que revele se a conta existe vira sucesso (RN-03).
        auth.respostaPedirRecuperacao = Resultado.Falha(ErroDominio.ErroInesperado("user_not_found"))
        PedirRecuperacaoDeSenha(auth)("ninguem@teste.com").sucesso()
    }

    @Test
    fun ca_04_1_sem_internet_ou_muitas_tentativas_aparecem() = runTest {
        auth.respostaPedirRecuperacao = Resultado.Falha(ErroDominio.SemConexao)
        PedirRecuperacaoDeSenha(auth)("ana@teste.com").falhaCom<ErroDominio.SemConexao>()
        auth.respostaPedirRecuperacao = Resultado.Falha(ErroDominio.MuitasTentativas)
        PedirRecuperacaoDeSenha(auth)("ana@teste.com").falhaCom<ErroDominio.MuitasTentativas>()
    }

    @Test
    fun ca_04_2_redefine_com_codigo_e_senha_nova_validos() = runTest {
        RedefinirSenha(auth)("ANA@teste.com", "123 456", "senhanova1").sucesso()
        assertEquals(listOf("redefinirSenha(ana@teste.com,123456)"), auth.chamadas)
    }

    @Test
    fun ca_04_3_codigo_vencido_vem_do_servidor() = runTest {
        auth.respostaRedefinir = Resultado.Falha(ErroDominio.CodigoInvalido)
        RedefinirSenha(auth)("ana@teste.com", "123456", "senhanova1").falhaCom<ErroDominio.CodigoInvalido>()
    }

    @Test
    fun redefinir_valida_codigo_e_senha_antes_de_chamar_o_servidor() = runTest {
        RedefinirSenha(auth)("ana@teste.com", "123", "senhanova1").falhaCom<ErroDominio.CodigoInvalido>()
        RedefinirSenha(auth)("ana@teste.com", "123456", "curta").falhaCom<ErroDominio.SenhaFraca>()
        assertTrue(auth.chamadas.isEmpty())
    }

    // HU-03

    @Test
    fun ca_03_1_sair_deixa_a_sessao_deslogada_e_limpa_fazendas_e_selecao() = runTest {
        auth.sessao.value = EstadoSessao.Logado(Usuario("u1", "ana@teste.com"))
        minhasFazendas.criar(Fazenda("f1", "Sítio"))
        preferencias.selecionarFazenda("f1")

        Sair(auth, minhasFazendas, preferencias, SincronizacaoEmMemoria())().sucesso()

        assertIs<EstadoSessao.Deslogado>(ObservarSessao(auth, minhasFazendas, preferencias)().first())
        assertTrue(minhasFazendas.fazendas.value.isEmpty())
        assertNull(preferencias.fazendaSelecionadaId.value)
        assertEquals(listOf("sair"), auth.chamadas)
    }

    @Test
    fun logado_sem_fazendas_fica_sem_selecionada() = runTest {
        auth.sessao.value = EstadoSessao.Logado(Usuario("u1", "ana@teste.com"))
        val logado = assertIs<EstadoSessao.Logado>(ObservarSessao(auth, minhasFazendas, preferencias)().first())
        assertTrue(logado.fazendas.isEmpty())
        assertNull(logado.fazendaSelecionada)
        assertEquals(false, logado.semConexao)
    }
}
