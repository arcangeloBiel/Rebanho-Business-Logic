package com.jakatech.rebanhomais.kmp.domain.entity

/**
 * Representação multiplataforma de data (ano, mês, dia).
 * Cada plataforma fornece sua própria implementação via actual.
 */
expect class LocalDate(year: Int, month: Int, day: Int) {
    val year: Int
    val month: Int
    val day: Int
}

fun parseLocalDate(isoString: String?): LocalDate? {
    if (isoString.isNullOrBlank()) return null
    val parts = isoString.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return LocalDate(year, month, day)
}

