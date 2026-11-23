package com.jakatech.rebanhomais.kmp.domain.model

import kotlinx.datetime.LocalDate

data class Pesagem(
    val id: String,
    val fazendaId: String,
    val animalId: String,
    val data: LocalDate,
    val pesoKg: Double,
    val observacao: String? = null
)

/**
 * Como uma vacina deve ser aplicada na fazenda.
 *
 * Exemplo: 2 doses com 30 dias de intervalo e reforço anual
 * → `doses = 2, intervaloEntreDosesDias = 30, reforcoDias = 365`.
 * Os protocolos são cadastrados pelo produtor ou técnico; o app não
 * impõe um calendário oficial.
 */
data class ProtocoloSanitario(
    val id: String,
    val fazendaId: String,
    val nome: String,
    /** Quantas doses compõem o esquema inicial. */
    val doses: Int = 1,
    /** Dias entre uma dose e a seguinte no esquema inicial. Obrigatório quando há mais de uma dose. */
    val intervaloEntreDosesDias: Int? = null,
    /** Dias até o reforço, contados a partir da última dose. Nulo = sem reforço. */
    val reforcoDias: Int? = null,
    /** Só para fêmeas ou só para machos. Nulo = ambos. */
    val sexoAlvo: Sexo? = null,
    /** Faixa de idade em que a primeira dose deve ser dada. Nulo = sem limite. */
    val idadeMinimaMeses: Int? = null,
    val idadeMaximaMeses: Int? = null
)

data class AplicacaoVacina(
    val id: String,
    val fazendaId: String,
    val animalId: String,
    /** Nulo quando a vacina foi lançada sem protocolo (aplicação avulsa). */
    val protocoloId: String?,
    val nomeVacina: String,
    val data: LocalDate,
    /** Número da dose dentro do protocolo (1, 2…). */
    val dose: Int = 1,
    /** Partida/lote do produto, útil para fiscalização. */
    val loteProduto: String? = null,
    val observacao: String? = null
)

enum class TipoMovimentacao(val entrada: Boolean, val statusResultante: StatusAnimal?) {
    NASCIMENTO(entrada = true, statusResultante = null),
    COMPRA(entrada = true, statusResultante = null),
    VENDA(entrada = false, statusResultante = StatusAnimal.VENDIDO),
    MORTE(entrada = false, statusResultante = StatusAnimal.MORTO),
    TRANSFERENCIA(entrada = false, statusResultante = StatusAnimal.TRANSFERIDO)
}

/** Entrada ou saída de um animal da fazenda: o histórico que a rastreabilidade (PNIB) pede. */
data class Movimentacao(
    val id: String,
    val fazendaId: String,
    val animalId: String,
    val tipo: TipoMovimentacao,
    val data: LocalDate,
    /** Vendedor, comprador ou fazenda de destino. */
    val contraparte: String? = null,
    val valor: Double? = null,
    val observacao: String? = null
)
