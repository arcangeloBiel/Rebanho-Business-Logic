package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoSessao
import com.jakatech.rebanhomais.kmp.domain.repositorio.AuthRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.Conectividade
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sincroniza sozinho enquanto o app está aberto (PRD Fase 4, fatia 3, RF-06):
 *
 * - logo que há usuário logado e internet: ao abrir o app, ao voltar a internet, ao entrar na conta;
 * - [esperaAposGravar] depois de uma gravação local (várias seguidas viram uma sincronização só);
 * - a cada [intervalo] sem falhas;
 * - depois de uma falha de rede, de novo após 30 s, 1 min, 2 min e 5 min, e daí a cada 5 min (RN-08).
 *
 * Sem internet ou sem usuário logado, não tenta nada (RN-09). O app chama [iniciar] quando vai para a
 * frente da tela e cancela o Job quando vai para o fundo: com o app fechado não há sincronização.
 */
class ManterSincronizado(
    private val sincronizacao: SincronizacaoRepository,
    private val conectividade: Conectividade,
    private val auth: AuthRepository,
    private val intervalo: Duration = 5.minutes,
    private val esperaAposGravar: Duration = 3.seconds,
    private val esperasAposFalha: List<Duration> = listOf(30.seconds, 1.minutes, 2.minutes, 5.minutes)
) {
    fun iniciar(escopo: CoroutineScope): Job = escopo.launch { executar() }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun executar() {
        val logado = auth.sessao.map { it is EstadoSessao.Logado }.distinctUntilChanged()
        combine(logado, conectividade.online.distinctUntilChanged()) { comUsuario, comInternet -> comUsuario && comInternet }
            .distinctUntilChanged()
            .collectLatest { pode -> if (pode) sincronizarEnquantoPuder() }
    }

    /** Roda até o usuário sair ou a internet cair, quando o collectLatest cancela. */
    private suspend fun sincronizarEnquantoPuder() = coroutineScope {
        val gravou = Channel<Unit>(Channel.CONFLATED)
        launch {
            var anterior: Int? = null
            sincronizacao.estado.map { it.pendentes }.distinctUntilChanged().collect { pendentes ->
                val antes = anterior
                if (antes != null && pendentes > antes) gravou.trySend(Unit)
                anterior = pendentes
            }
        }

        var falhas = 0
        var espera = Duration.ZERO
        while (true) {
            val porGravacao = withTimeoutOrNull(espera) { gravou.receive() } != null
            if (porGravacao) {
                delay(esperaAposGravar)
                gravou.tryReceive() // gravações durante a espera entram nesta mesma sincronização
            }
            espera = when (sincronizacao.sincronizar()) {
                is Resultado.Sucesso -> {
                    falhas = 0
                    intervalo
                }
                is Resultado.Falha -> esperasAposFalha[minOf(falhas++, esperasAposFalha.lastIndex)]
            }
        }
    }
}
