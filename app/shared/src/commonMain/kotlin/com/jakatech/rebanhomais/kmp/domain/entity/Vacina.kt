package com.jakatech.rebanhomais.kmp.domain.entity

data class Vacina(
    val id: String,
    val nome: String,
    val dataAplicacao: LocalDate,
    val proximaAplicacao: LocalDate? = null,
    val observacao: String? = null
)
