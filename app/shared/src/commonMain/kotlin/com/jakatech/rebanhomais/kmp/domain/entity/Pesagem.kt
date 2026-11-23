package com.jakatech.rebanhomais.kmp.domain.entity

data class Pesagem(
    val id: String,
    val data: LocalDate,
    val pesoKg: Double,
    val observacao: String? = null
)
