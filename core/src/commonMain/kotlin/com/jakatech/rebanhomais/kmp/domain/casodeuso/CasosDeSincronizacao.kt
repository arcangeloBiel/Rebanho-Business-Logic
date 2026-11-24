package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.EstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.RegistroComErro
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import kotlinx.coroutines.flow.Flow

/*
 * Sincronização com o servidor (PRD Fase 4). Fatia 1: envio e estado.
 */

/** HU-04: quantos registros ainda estão só no aparelho, quantos foram recusados e quando foi o último envio. */
class ObservarEstadoDaSincronizacao(private val sincronizacao: SincronizacaoRepository) {
    operator fun invoke(): Flow<EstadoDaSincronizacao> = sincronizacao.estado
}

/** HU-04 e HU-05: os registros recusados pelo servidor, com o motivo, para a tela de pendências. */
class ObservarRegistrosComErro(private val sincronizacao: SincronizacaoRepository) {
    operator fun invoke(): Flow<List<RegistroComErro>> = sincronizacao.observarRegistrosComErro()
}

/** RF-11: o usuário pede para sincronizar agora. Fatia 1: só o envio. */
class SincronizarAgora(private val sincronizacao: SincronizacaoRepository) {
    suspend operator fun invoke(): Resultado<ResumoDoEnvio> = sincronizacao.enviarPendentes()
}
