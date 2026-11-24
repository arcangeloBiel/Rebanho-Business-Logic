package com.jakatech.rebanhomais.kmp.domain.model

import kotlinx.datetime.LocalDate

enum class Sexo { FEMEA, MACHO }

enum class Aptidao { CORTE, LEITE, MISTA }

enum class Origem { NASCIDO_NA_FAZENDA, COMPRADO }

enum class StatusAnimal { ATIVO, VENDIDO, MORTO, TRANSFERIDO }

/**
 * Animal identificado individualmente por brinco, como exige o PNIB.
 *
 * A categoria (bezerro, novilha, vaca…) não é guardada: ela é calculada
 * pela idade, sexo e histórico com [com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeCategoria].
 */
data class Animal(
    val id: String,
    val fazendaId: String,
    /** Número do brinco, já normalizado (maiúsculas, sem espaços). Único na fazenda. */
    val brinco: String,
    val sexo: Sexo,
    val origem: Origem,
    val dataNascimento: LocalDate? = null,
    val nome: String? = null,
    val raca: String? = null,
    val aptidao: Aptidao = Aptidao.MISTA,
    val maeId: String? = null,
    val paiId: String? = null,
    val loteId: String? = null,
    /** Macho mantido para reprodução (touro). */
    val reprodutor: Boolean = false,
    val status: StatusAnimal = StatusAnimal.ATIVO,
    val observacao: String? = null
) {
    val ativo: Boolean get() = status == StatusAnimal.ATIVO
}
