package com.jakatech.rebanhomais.kmp.domain.regra

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoBrincoInvalido
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoProtocoloInvalido
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

object RegrasDeBrinco {
    const val TAMANHO_MAXIMO = 20
    private val permitido = Regex("^[A-Z0-9-]+$")

    /** " ab 12 " vira "AB12": o mesmo brinco digitado de jeitos diferentes conta como um só. */
    fun normalizar(brinco: String): String =
        brinco.filterNot { it.isWhitespace() }.uppercase()

    /** Recebe o brinco já normalizado. Retorna o erro, ou nulo se estiver válido. */
    fun validar(brinco: String): ErroDominio.BrincoInvalido? = when {
        brinco.isEmpty() -> ErroDominio.BrincoInvalido(MotivoBrincoInvalido.VAZIO)
        brinco.length > TAMANHO_MAXIMO -> ErroDominio.BrincoInvalido(MotivoBrincoInvalido.MUITO_LONGO)
        !permitido.matches(brinco) -> ErroDominio.BrincoInvalido(MotivoBrincoInvalido.CARACTERE_INVALIDO)
        else -> null
    }
}

object RegrasDeData {
    /** Valida a data de um manejo: não pode ser no futuro nem antes do nascimento do animal. */
    fun validarManejo(data: LocalDate, hoje: LocalDate, nascimento: LocalDate?): ErroDominio? = when {
        data > hoje -> ErroDominio.DataNoFuturo
        nascimento != null && data < nascimento -> ErroDominio.DataAntesDoNascimento
        else -> null
    }
}

object RegrasDePeso {
    const val PESO_MAXIMO_KG = 2000.0

    fun validar(pesoKg: Double): ErroDominio.PesoInvalido? =
        if (pesoKg.isNaN() || pesoKg <= 0.0 || pesoKg > PESO_MAXIMO_KG) ErroDominio.PesoInvalido(pesoKg) else null

    /** Ganho médio diário (kg/dia) entre a primeira e a última pesagem. Nulo com menos de duas datas diferentes. */
    fun ganhoMedioDiario(pesagens: List<Pesagem>): Double? {
        val ordenadas = pesagens.sortedBy { it.data }
        return ganhoEntre(ordenadas.firstOrNull(), ordenadas.lastOrNull())
    }

    /** Ganho médio diário (kg/dia) entre as duas pesagens mais recentes. */
    fun ganhoRecente(pesagens: List<Pesagem>): Double? {
        val ordenadas = pesagens.sortedBy { it.data }
        if (ordenadas.size < 2) return null
        return ganhoEntre(ordenadas[ordenadas.size - 2], ordenadas.last())
    }

    private fun ganhoEntre(inicio: Pesagem?, fim: Pesagem?): Double? {
        if (inicio == null || fim == null) return null
        val dias = inicio.data.daysUntil(fim.data)
        if (dias <= 0) return null
        return (fim.pesoKg - inicio.pesoKg) / dias
    }
}

object RegrasDeProtocolo {
    fun validar(protocolo: ProtocoloSanitario): ErroDominio? {
        val motivo = when {
            protocolo.nome.isBlank() -> return ErroDominio.NomeObrigatorio("nome")
            protocolo.doses < 1 -> MotivoProtocoloInvalido.DOSES_MENOR_QUE_UM
            protocolo.doses > 1 && protocolo.intervaloEntreDosesDias == null -> MotivoProtocoloInvalido.INTERVALO_OBRIGATORIO
            (protocolo.intervaloEntreDosesDias ?: 1) <= 0 -> MotivoProtocoloInvalido.INTERVALO_NAO_POSITIVO
            (protocolo.reforcoDias ?: 1) <= 0 -> MotivoProtocoloInvalido.REFORCO_NAO_POSITIVO
            (protocolo.idadeMinimaMeses ?: 0) < 0 || (protocolo.idadeMaximaMeses ?: 0) < 0 -> MotivoProtocoloInvalido.IDADE_NEGATIVA
            protocolo.idadeMinimaMeses != null && protocolo.idadeMaximaMeses != null &&
                protocolo.idadeMinimaMeses > protocolo.idadeMaximaMeses -> MotivoProtocoloInvalido.IDADE_MINIMA_MAIOR_QUE_MAXIMA
            else -> null
        }
        return motivo?.let { ErroDominio.ProtocoloInvalido(it) }
    }
}
