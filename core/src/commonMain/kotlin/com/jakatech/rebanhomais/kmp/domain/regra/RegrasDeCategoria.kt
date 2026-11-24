package com.jakatech.rebanhomais.kmp.domain.regra

import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.monthsUntil

enum class CategoriaAnimal { BEZERRO, BEZERRA, GARROTE, NOVILHA, BOI, TOURO, VACA, INDEFINIDA }

/**
 * Calcula a categoria do animal em vez de pedir que o produtor a digite.
 *
 * Faixas usadas:
 * - fêmea: bezerra até [MESES_FIM_CRIA]; depois novilha; vaca depois do primeiro parto;
 * - macho: bezerro até [MESES_FIM_CRIA]; garrote até [MESES_ADULTO_MACHO];
 *   depois boi, ou touro se for reprodutor.
 */
object RegrasDeCategoria {
    const val MESES_FIM_CRIA = 12
    const val MESES_ADULTO_MACHO = 24

    fun idadeEmMeses(dataNascimento: LocalDate?, hoje: LocalDate): Int? =
        dataNascimento?.monthsUntil(hoje)?.takeIf { it >= 0 }

    fun calcular(
        sexo: Sexo,
        dataNascimento: LocalDate?,
        hoje: LocalDate,
        jaPariu: Boolean,
        reprodutor: Boolean
    ): CategoriaAnimal {
        val meses = idadeEmMeses(dataNascimento, hoje)
        return when (sexo) {
            Sexo.FEMEA -> when {
                jaPariu -> CategoriaAnimal.VACA
                meses == null -> CategoriaAnimal.INDEFINIDA
                meses < MESES_FIM_CRIA -> CategoriaAnimal.BEZERRA
                else -> CategoriaAnimal.NOVILHA
            }

            Sexo.MACHO -> when {
                meses == null -> if (reprodutor) CategoriaAnimal.TOURO else CategoriaAnimal.INDEFINIDA
                meses < MESES_FIM_CRIA -> CategoriaAnimal.BEZERRO
                meses < MESES_ADULTO_MACHO -> CategoriaAnimal.GARROTE
                reprodutor -> CategoriaAnimal.TOURO
                else -> CategoriaAnimal.BOI
            }
        }
    }
}
