package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.casodeuso.ManterSincronizado
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.model.RegistroComErro
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoRecebimento
import com.jakatech.rebanhomais.kmp.domain.model.Usuario
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.Conectividade
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class SincronizacaoFalsa : SincronizacaoRepository {
    private val estadoAtual = MutableStateFlow(EstadoDaSincronizacao(0, 0, null, null, false))
    override val estado: Flow<EstadoDaSincronizacao> = estadoAtual
    var chamadas = 0
    val falhas = ArrayDeque<ErroDominio>()

    fun gravou() = estadoAtual.update { it.copy(pendentes = it.pendentes + 1) }

    override fun observarRegistrosComErro(): Flow<List<RegistroComErro>> = flowOf(emptyList())
    override suspend fun enviarPendentes(): Resultado<ResumoDoEnvio> = error("não usado")
    override suspend fun receberNovidades(): Resultado<ResumoDoRecebimento> = error("não usado")
    override suspend fun apagarDadosLocais() = Unit
    override suspend fun sincronizar(): Resultado<ResumoDaSincronizacao> {
        chamadas++
        falhas.removeFirstOrNull()?.let { return Resultado.Falha(it) }
        return Resultado.Sucesso(ResumoDaSincronizacao(ResumoDoEnvio(0, 0, 0), ResumoDoRecebimento(0, 0, 0)))
    }
}

private class Rede(online: Boolean) : Conectividade {
    override val online = MutableStateFlow(online)
}

private class Sessao(logado: Boolean) : AuthRepository {
    override val sessao = MutableStateFlow<EstadoSessao>(if (logado) LOGADO else EstadoSessao.Deslogado)
    override suspend fun criarConta(email: String, senha: String, nome: String?) = Resultado.Sucesso(Unit)
    override suspend fun confirmarConta(email: String, codigo: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun reenviarCodigo(email: String) = Resultado.Sucesso(Unit)
    override suspend fun entrar(email: String, senha: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun pedirCodigoDeRecuperacao(email: String) = Resultado.Sucesso(Unit)
    override suspend fun redefinirSenha(email: String, codigo: String, novaSenha: String) = Resultado.Sucesso(Usuario("u1", email))
    override suspend fun sair() { sessao.value = EstadoSessao.Deslogado }

    companion object {
        val LOGADO = EstadoSessao.Logado(Usuario("u1", "ana@teste.com"))
    }
}

/** PRD Fase 4, fatia 3: sincronizar sozinho com o app aberto (RF-06, RN-08, RN-09). */
@OptIn(ExperimentalCoroutinesApi::class)
class ManterSincronizadoTest {
    private val sync = SincronizacaoFalsa()

    private fun TestScope.ligar(online: Boolean = true, logado: Boolean = true): Pair<Rede, Sessao> {
        val rede = Rede(online)
        val sessao = Sessao(logado)
        ManterSincronizado(sync, rede, sessao).iniciar(backgroundScope)
        runCurrent()
        return rede to sessao
    }

    private fun TestScope.passar(tempo: Duration) {
        advanceTimeBy(tempo)
        runCurrent()
    }

    @Test
    fun rf_06_ao_abrir_logado_e_com_internet_sincroniza_na_hora() = runTest {
        ligar()
        assertEquals(1, sync.chamadas)
    }

    @Test
    fun rf_06_sem_internet_nao_tenta_e_sincroniza_quando_ela_volta() = runTest {
        val (rede, _) = ligar(online = false)
        passar(30.minutes)
        assertEquals(0, sync.chamadas)

        rede.online.value = true
        runCurrent()
        assertEquals(1, sync.chamadas)
    }

    @Test
    fun rn_09_sem_usuario_nao_tenta_e_sincroniza_ao_entrar() = runTest {
        val (_, sessao) = ligar(logado = false)
        passar(30.minutes)
        assertEquals(0, sync.chamadas)

        sessao.sessao.value = Sessao.LOGADO
        runCurrent()
        assertEquals(1, sync.chamadas)

        sessao.sair()
        passar(30.minutes)
        assertEquals(1, sync.chamadas)
    }

    @Test
    fun rf_06_a_cada_5_minutos_com_o_app_aberto() = runTest {
        ligar()
        passar(5.minutes)
        assertEquals(2, sync.chamadas)
        passar(5.minutes)
        assertEquals(3, sync.chamadas)
    }

    @Test
    fun rf_06_depois_de_gravar_espera_3_s_e_junta_as_gravacoes_seguidas() = runTest {
        ligar()
        sync.gravou()
        runCurrent()
        passar(1.seconds)
        sync.gravou()
        runCurrent()
        passar(1.seconds)
        sync.gravou()
        runCurrent()
        assertEquals(1, sync.chamadas) // ainda esperando

        passar(1.seconds)
        assertEquals(2, sync.chamadas) // uma só para as três gravações

        passar(1.minutes)
        assertEquals(2, sync.chamadas)
    }

    @Test
    fun rn_08_falha_de_rede_tenta_de_novo_com_espera_crescente() = runTest {
        repeat(5) { sync.falhas.addLast(ErroDominio.SemConexao) }
        ligar()
        assertEquals(1, sync.chamadas)
        passar(30.seconds); assertEquals(2, sync.chamadas)
        passar(1.minutes); assertEquals(3, sync.chamadas)
        passar(2.minutes); assertEquals(4, sync.chamadas)
        passar(5.minutes); assertEquals(5, sync.chamadas)
        passar(5.minutes); assertEquals(6, sync.chamadas) // esta dá certo
        passar(4.minutes); assertEquals(6, sync.chamadas)
        passar(1.minutes); assertEquals(7, sync.chamadas) // de volta ao intervalo normal
    }

    @Test
    fun internet_cai_e_para_de_tentar() = runTest {
        val (rede, _) = ligar()
        rede.online.value = false
        runCurrent()
        passar(30.minutes)
        assertEquals(1, sync.chamadas)
    }
}
