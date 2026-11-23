package com.jakatech.rebanhomais.kmp.domain.regra

import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class MotivoPendencia { PRIMEIRA_DOSE, PROXIMA_DOSE, REFORCO }

data class ProximaAplicacao(val data: LocalDate, val dose: Int, val motivo: MotivoPendencia)

data class PendenciaVacinal(
    val animalId: String,
    val brinco: String,
    val protocoloId: String,
    val nomeProtocolo: String,
    val dataPrevista: LocalDate,
    val dose: Int,
    val motivo: MotivoPendencia
) {
    fun atrasada(hoje: LocalDate): Boolean = dataPrevista < hoje
}

/** Calcula próximas doses e o que está pendente no rebanho, a partir dos protocolos da fazenda. */
object CalendarioSanitario {

    /** Próxima aplicação depois de [ultima], segundo o [protocolo]. Nulo quando o esquema terminou e não há reforço. */
    fun proximaAplicacao(protocolo: ProtocoloSanitario, ultima: AplicacaoVacina): ProximaAplicacao? {
        val intervalo = protocolo.intervaloEntreDosesDias
        if (ultima.dose < protocolo.doses && intervalo != null) {
            return ProximaAplicacao(
                data = ultima.data.plus(intervalo, DateTimeUnit.DAY),
                dose = ultima.dose + 1,
                motivo = MotivoPendencia.PROXIMA_DOSE
            )
        }
        val reforco = protocolo.reforcoDias ?: return null
        return ProximaAplicacao(
            data = ultima.data.plus(reforco, DateTimeUnit.DAY),
            dose = protocolo.doses,
            motivo = MotivoPendencia.REFORCO
        )
    }

    /**
     * Lista o que vence até `hoje + horizonteDias`, incluindo o que já está atrasado.
     *
     * - Animal sem nenhuma dose do protocolo: entra como primeira dose se tiver o sexo
     *   certo e estiver (ou entrar, dentro do horizonte) na faixa de idade.
     * - Animal com doses: entra pela próxima dose ou pelo reforço.
     * Animais inativos (vendidos, mortos, transferidos) são ignorados.
     */
    fun pendencias(
        animais: List<Animal>,
        protocolos: List<ProtocoloSanitario>,
        aplicacoes: List<AplicacaoVacina>,
        hoje: LocalDate,
        horizonteDias: Int
    ): List<PendenciaVacinal> {
        val limite = hoje.plus(horizonteDias, DateTimeUnit.DAY)
        val ultimaPorAnimalEProtocolo = aplicacoes
            .filter { it.protocoloId != null }
            .groupBy { it.animalId to it.protocoloId!! }
            .mapValues { (_, lista) -> lista.maxWith(compareBy<AplicacaoVacina> { it.data }.thenBy { it.dose }) }

        val resultado = mutableListOf<PendenciaVacinal>()
        for (animal in animais.filter { it.ativo }) {
            for (protocolo in protocolos) {
                val ultima = ultimaPorAnimalEProtocolo[animal.id to protocolo.id]
                val proxima = if (ultima == null) {
                    primeiraDose(animal, protocolo, hoje, limite)
                } else {
                    proximaAplicacao(protocolo, ultima)
                }
                if (proxima != null && proxima.data <= limite) {
                    resultado += PendenciaVacinal(
                        animalId = animal.id,
                        brinco = animal.brinco,
                        protocoloId = protocolo.id,
                        nomeProtocolo = protocolo.nome,
                        dataPrevista = proxima.data,
                        dose = proxima.dose,
                        motivo = proxima.motivo
                    )
                }
            }
        }
        return resultado.sortedWith(compareBy<PendenciaVacinal> { it.dataPrevista }.thenBy { it.brinco })
    }

    private fun primeiraDose(
        animal: Animal,
        protocolo: ProtocoloSanitario,
        hoje: LocalDate,
        limite: LocalDate
    ): ProximaAplicacao? {
        if (protocolo.sexoAlvo != null && protocolo.sexoAlvo != animal.sexo) return null

        val temFaixaDeIdade = protocolo.idadeMinimaMeses != null || protocolo.idadeMaximaMeses != null
        val nascimento = animal.dataNascimento
        if (temFaixaDeIdade && nascimento == null) return null

        val inicio = protocolo.idadeMinimaMeses?.let { nascimento!!.plus(it, DateTimeUnit.MONTH) }
        val fim = protocolo.idadeMaximaMeses?.let { nascimento!!.plus(it, DateTimeUnit.MONTH) }

        if (inicio != null && inicio > limite) return null // ainda não chegou na idade
        if (fim != null && fim <= hoje) return null // passou da idade

        return ProximaAplicacao(data = inicio ?: hoje, dose = 1, motivo = MotivoPendencia.PRIMEIRA_DOSE)
    }
}
