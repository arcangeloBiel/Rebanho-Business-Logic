package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.fakes.HOJE
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.regra.CalendarioSanitario
import com.jakatech.rebanhomais.kmp.domain.regra.MotivoPendencia
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarioSanitarioTest {

    private val duasDosesComReforco = ProtocoloSanitario(
        id = "clostridiose", fazendaId = "f", nome = "Clostridiose",
        doses = 2, intervaloEntreDosesDias = 30, reforcoDias = 365
    )

    /** Protocolo só para fêmeas de 3 a 8 meses, dose única. */
    private val femeas3a8 = ProtocoloSanitario(
        id = "femeas", fazendaId = "f", nome = "Vacina de fêmeas jovens",
        sexoAlvo = Sexo.FEMEA, idadeMinimaMeses = 3, idadeMaximaMeses = 8
    )

    private fun animal(id: String, sexo: Sexo, mesesDeIdade: Int, status: StatusAnimal = StatusAnimal.ATIVO) = Animal(
        id = id, fazendaId = "f", brinco = id.uppercase(), sexo = sexo, origem = Origem.NASCIDO_NA_FAZENDA,
        dataNascimento = HOJE.minus(mesesDeIdade, DateTimeUnit.MONTH), status = status
    )

    private fun aplicacao(animalId: String, protocolo: ProtocoloSanitario, data: LocalDate, dose: Int) = AplicacaoVacina(
        id = "$animalId-$dose", fazendaId = "f", animalId = animalId, protocoloId = protocolo.id,
        nomeVacina = protocolo.nome, data = data, dose = dose
    )

    @Test
    fun segunda_dose_30_dias_depois_da_primeira() {
        val proxima = CalendarioSanitario.proximaAplicacao(
            duasDosesComReforco, aplicacao("a", duasDosesComReforco, LocalDate(2026, 11, 1), dose = 1)
        )!!
        assertEquals(LocalDate(2026, 12, 1), proxima.data)
        assertEquals(2, proxima.dose)
        assertEquals(MotivoPendencia.PROXIMA_DOSE, proxima.motivo)
    }

    @Test
    fun reforco_anual_depois_da_ultima_dose() {
        val proxima = CalendarioSanitario.proximaAplicacao(
            duasDosesComReforco, aplicacao("a", duasDosesComReforco, LocalDate(2026, 1, 10), dose = 2)
        )!!
        assertEquals(LocalDate(2027, 1, 10), proxima.data)
        assertEquals(MotivoPendencia.REFORCO, proxima.motivo)
    }

    @Test
    fun dose_unica_sem_reforco_nao_tem_proxima() {
        assertNull(CalendarioSanitario.proximaAplicacao(femeas3a8, aplicacao("a", femeas3a8, HOJE, dose = 1)))
    }

    @Test
    fun pendencias_de_primeira_dose_respeitam_sexo_e_idade() {
        val animais = listOf(
            animal("bezerra4m", Sexo.FEMEA, 4), // na faixa: pendente
            animal("bezerro4m", Sexo.MACHO, 4), // sexo errado
            animal("novilha10m", Sexo.FEMEA, 10), // passou da idade
            animal("bezerra1m", Sexo.FEMEA, 1), // ainda não chegou na idade
            animal("vendida", Sexo.FEMEA, 4, StatusAnimal.VENDIDO) // inativa
        )
        val pendencias = CalendarioSanitario.pendencias(animais, listOf(femeas3a8), emptyList(), HOJE, horizonteDias = 7)

        assertEquals(listOf("bezerra4m"), pendencias.map { it.animalId })
        assertEquals(MotivoPendencia.PRIMEIRA_DOSE, pendencias.single().motivo)
        assertTrue(pendencias.single().atrasada(HOJE), "entrou na faixa há um mês e não foi vacinada")
    }

    @Test
    fun animal_que_entra_na_faixa_dentro_do_horizonte_aparece() {
        // Nasceu há 3 meses menos 3 dias: completa 3 meses dentro dos próximos 7 dias.
        val quaseNaIdade = animal("b", Sexo.FEMEA, 3).copy(dataNascimento = HOJE.minus(3, DateTimeUnit.MONTH).minus(-3, DateTimeUnit.DAY))
        val pendencias = CalendarioSanitario.pendencias(listOf(quaseNaIdade), listOf(femeas3a8), emptyList(), HOJE, 7)
        assertEquals(1, pendencias.size)
        assertEquals(false, pendencias.single().atrasada(HOJE))
    }

    @Test
    fun pendencia_de_segunda_dose_e_ignorada_fora_do_horizonte() {
        val animais = listOf(animal("a", Sexo.MACHO, 12), animal("b", Sexo.MACHO, 12))
        val aplicacoes = listOf(
            aplicacao("a", duasDosesComReforco, HOJE.minus(28, DateTimeUnit.DAY), dose = 1), // 2ª dose em 2 dias
            aplicacao("b", duasDosesComReforco, HOJE.minus(5, DateTimeUnit.DAY), dose = 1) // 2ª dose em 25 dias
        )
        val pendencias = CalendarioSanitario.pendencias(animais, listOf(duasDosesComReforco), aplicacoes, HOJE, 7)
        assertEquals(listOf("a"), pendencias.map { it.animalId })
        assertEquals(2, pendencias.single().dose)
    }
}
