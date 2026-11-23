package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.ConfirmarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CriarConta
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Entrar
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObservarSessao
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ReenviarCodigo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.Sair
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.fakes.falhaCom
import com.jakatech.rebanhomais.kmp.domain.fakes.sucesso
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
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
    override suspend fun sair() {
        chamadas += "sair"; sessao.value = EstadoSessao.Deslogado
    }
}

class CasosDeContaTest {
    private val auth = AuthFalso()

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

    // HU-03

    @Test
    fun ca_03_1_sair_deixa_a_sessao_deslogada() = runTest {
        auth.sessao.value = EstadoSessao.Logado(Usuario("u1", "ana@teste.com"))
        Sair(auth)()
        assertIs<EstadoSessao.Deslogado>(ObservarSessao(auth)().first())
        assertEquals(listOf("sair"), auth.chamadas)
    }

    @Test
    fun estado_logado_comeca_sem_fazendas_ate_a_fatia_3() {
        val logado = EstadoSessao.Logado(Usuario("u1", "ana@teste.com"))
        assertTrue(logado.fazendas.isEmpty())
        assertNull(logado.fazendaSelecionada)
        assertEquals(false, logado.semConexao)
    }
}
