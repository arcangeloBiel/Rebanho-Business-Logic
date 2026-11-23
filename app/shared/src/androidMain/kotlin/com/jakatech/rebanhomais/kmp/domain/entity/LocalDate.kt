package com.jakatech.rebanhomais.kmp.domain.entity

actual class LocalDate actual constructor(
    actual val year: Int,
    actual val month: Int,
    actual val day: Int
) {
    override fun toString(): String = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalDate) return false
        return year == other.year && month == other.month && day == other.day
    }

    override fun hashCode(): Int = 31 * (31 * year + month) + day
}
