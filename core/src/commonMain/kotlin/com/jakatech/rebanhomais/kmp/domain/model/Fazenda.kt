package com.jakatech.rebanhomais.kmp.domain.model

/** Propriedade rural. Todos os dados do rebanho pertencem a uma fazenda, não a um usuário. */
data class Fazenda(
    val id: String,
    val nome: String,
    val municipio: String? = null,
    val uf: String? = null,
    /** Código da propriedade na agência de defesa agropecuária do estado, quando houver. */
    val codigoPropriedade: String? = null
)

enum class Papel {
    /** Dono da fazenda: faz tudo, inclusive convidar pessoas. */
    DONO,

    /** Funcionário: lança manejos do dia a dia. */
    FUNCIONARIO,

    /** Técnico ou veterinário que acompanha a fazenda. */
    TECNICO;

    val podeEditarRebanho: Boolean get() = true
    val podeGerenciarMembros: Boolean get() = this == DONO
    val podeExcluirRegistros: Boolean get() = this == DONO
}

/** Ligação entre um usuário e uma fazenda, com o papel dele nela. */
data class Membro(
    val fazendaId: String,
    val usuarioId: String,
    val papel: Papel
)

data class Lote(
    val id: String,
    val fazendaId: String,
    val nome: String
)
