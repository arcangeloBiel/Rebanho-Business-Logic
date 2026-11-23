package com.jakatech.rebanhomais.kmp.domain.entity

data class Animal(
    val id: String,
    val nome: String,
    val tipo: TipoAnimal,
    val raca: String?,
    val dataNascimento: LocalDate?,
    val sexo: Sexo,
    val pesoAtual: Double?,
    val status: StatusAnimal,
    val vacinas: List<Vacina> = emptyList(),
    val historicoPesagem: List<Pesagem> = emptyList(),
    val lote: String? = null
)