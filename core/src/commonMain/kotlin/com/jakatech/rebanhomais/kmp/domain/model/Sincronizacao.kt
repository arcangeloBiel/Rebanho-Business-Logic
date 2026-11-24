package com.jakatech.rebanhomais.kmp.domain.model

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio

/** Como está a cópia do aparelho em relação ao servidor (PRD Fase 4, RF-07, HU-04). */
data class EstadoDaSincronizacao(
    /** Registros gravados no aparelho que ainda não chegaram ao servidor. */
    val pendentes: Int,
    /** Registros que o servidor recusou; ficam fora do envio até serem corrigidos (RN-06). */
    val comErro: Int,
    /** Hora do último envio completo, em ISO 8601 (UTC). Nulo se nunca houve. */
    val ultimoEnvio: String?,
    /** Hora do último recebimento completo das novidades do servidor, em ISO 8601 (UTC). */
    val ultimoRecebimento: String?,
    /** Um envio está acontecendo agora. */
    val sincronizando: Boolean
) {
    /** Nada só no aparelho: pode trocar de celular sem perder dados (CA-04.2). */
    val tudoSalvo: Boolean get() = pendentes == 0 && comErro == 0
}

enum class TipoDeRegistro { LOTE, ANIMAL, MOVIMENTACAO, PESAGEM, PROTOCOLO, VACINACAO }

/**
 * Um registro que o servidor recusou (CA-04.3).
 * [referencia] ajuda a achar o registro na tela: brinco do animal, nome do lote ou do protocolo,
 * ou a data do manejo.
 */
data class RegistroComErro(
    val tipo: TipoDeRegistro,
    val id: String,
    val referencia: String,
    val erro: ErroDominio
)

/** O que um recebimento fez (RF-04, RF-05). */
data class ResumoDoRecebimento(
    /** Linhas do servidor gravadas no aparelho. */
    val recebidos: Int,
    /** Linhas que o aparelho não aplicou porque tem uma alteração ainda não enviada (RN-04). */
    val mantidasLocais: Int,
    /** Tabelas paradas por brinco em conflito com um animal só deste aparelho. */
    val conflitos: Int
)

/** Envio seguido de recebimento (RF-06, RF-11). */
data class ResumoDaSincronizacao(val envio: ResumoDoEnvio, val recebimento: ResumoDoRecebimento)

/** O que um envio fez (RF-01). */
data class ResumoDoEnvio(
    val enviados: Int,
    val recusados: Int,
    /** Pendentes que não foram enviados porque dependem de um registro com erro (RN-06). */
    val aguardando: Int
)
