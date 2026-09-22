package com.jakatech.rebanhomais.kmp.model

import kotlinx.serialization.Serializable

@Serializable
data class AnimalDto(
    val id: String? = null,
    val nome: String,
    val tipo: String, // BOI, VACA, BEZERRO, etc.
    val raca: String? = null,
    val dataNascimento: String? = null, // Formato "YYYY-MM-DD"
    val sexo: String, // MACHO, FEMEA
    val pesoAtual: Double? = null,
    val status: String = "ATIVO", // ATIVO, VENDIDO, FALECIDO
    val lote: String? = null,
    val vacinas: List<VacinaDto> = emptyList(),
    val historicoPesagem: List<PesagemDto> = emptyList()
)

@Serializable
data class VacinaDto(
    val id: String? = null,
    val nome: String,
    val dataAplicacao: String,
    val proximaDose: String? = null,
    val observacao: String? = null
)

@Serializable
data class PesagemDto(
    val id: String? = null,
    val data: String,
    val pesoKg: Double,
    val observacao: String? = null
)

@Serializable
data class ApiResponse<T>(
    val sucesso: Boolean,
    val mensagem: String,
    val dados: T? = null
)
