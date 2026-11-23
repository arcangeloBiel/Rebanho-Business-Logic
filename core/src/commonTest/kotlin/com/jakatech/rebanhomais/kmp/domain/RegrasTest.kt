package com.jakatech.rebanhomais.kmp.domain

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoBrincoInvalido
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoProtocoloInvalido
import com.jakatech.rebanhomais.kmp.domain.fakes.HOJE
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.regra.CategoriaAnimal
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeBrinco
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeCategoria
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeData
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDePeso
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeProtocolo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegrasDeCategoriaTest {
    private fun nascidoHa(meses: Int) = HOJE.minus(meses, DateTimeUnit.MONTH)

    private fun categoria(sexo: Sexo, meses: Int?, jaPariu: Boolean = false, reprodutor: Boolean = false) =
        RegrasDeCategoria.calcular(sexo, meses?.let { nascidoHa(it) }, HOJE, jaPariu, reprodutor)

    @Test fun bezerra_ate_12_meses() = assertEquals(CategoriaAnimal.BEZERRA, categoria(Sexo.FEMEA, 6))
    @Test fun novilha_depois_de_12_meses_sem_parto() = assertEquals(CategoriaAnimal.NOVILHA, categoria(Sexo.FEMEA, 20))
    @Test fun vaca_depois_do_primeiro_parto() = assertEquals(CategoriaAnimal.VACA, categoria(Sexo.FEMEA, 30, jaPariu = true))
    @Test fun vaca_mesmo_sem_data_de_nascimento() = assertEquals(CategoriaAnimal.VACA, categoria(Sexo.FEMEA, null, jaPariu = true))
    @Test fun bezerro_ate_12_meses() = assertEquals(CategoriaAnimal.BEZERRO, categoria(Sexo.MACHO, 11))
    @Test fun garrote_entre_12_e_24_meses() = assertEquals(CategoriaAnimal.GARROTE, categoria(Sexo.MACHO, 12))
    @Test fun boi_a_partir_de_24_meses() = assertEquals(CategoriaAnimal.BOI, categoria(Sexo.MACHO, 30))
    @Test fun touro_quando_reprodutor_adulto() = assertEquals(CategoriaAnimal.TOURO, categoria(Sexo.MACHO, 30, reprodutor = true))
    @Test fun reprodutor_jovem_ainda_e_garrote() = assertEquals(CategoriaAnimal.GARROTE, categoria(Sexo.MACHO, 18, reprodutor = true))
    @Test fun sem_idade_fica_indefinida() = assertEquals(CategoriaAnimal.INDEFINIDA, categoria(Sexo.FEMEA, null))
}

class RegrasDeBrincoTest {
    @Test fun normaliza_espacos_e_maiusculas() = assertEquals("AB12", RegrasDeBrinco.normalizar(" ab 12 "))
    @Test fun aceita_letras_numeros_e_hifen() = assertNull(RegrasDeBrinco.validar("BR-0042"))
    @Test fun rejeita_vazio() = assertEquals(MotivoBrincoInvalido.VAZIO, RegrasDeBrinco.validar("")?.motivo)
    @Test fun rejeita_caractere_invalido() =
        assertEquals(MotivoBrincoInvalido.CARACTERE_INVALIDO, RegrasDeBrinco.validar("A/1")?.motivo)
    @Test fun rejeita_muito_longo() =
        assertEquals(MotivoBrincoInvalido.MUITO_LONGO, RegrasDeBrinco.validar("1".repeat(21))?.motivo)
}

class RegrasDeDataTest {
    @Test fun rejeita_data_no_futuro() =
        assertEquals(ErroDominio.DataNoFuturo, RegrasDeData.validarManejo(LocalDate(2026, 11, 24), HOJE, null))

    @Test fun rejeita_antes_do_nascimento() = assertEquals(
        ErroDominio.DataAntesDoNascimento,
        RegrasDeData.validarManejo(LocalDate(2026, 1, 1), HOJE, LocalDate(2026, 2, 1))
    )

    @Test fun aceita_hoje() = assertNull(RegrasDeData.validarManejo(HOJE, HOJE, LocalDate(2026, 2, 1)))
}

class RegrasDePesoTest {
    private fun pesagem(dia: Int, mes: Int, kg: Double) =
        Pesagem(id = "p$dia$mes", fazendaId = "f", animalId = "a", data = LocalDate(2026, mes, dia), pesoKg = kg)

    @Test fun rejeita_peso_zero_negativo_e_absurdo() {
        assertEquals(ErroDominio.PesoInvalido(0.0), RegrasDePeso.validar(0.0))
        assertEquals(ErroDominio.PesoInvalido(-5.0), RegrasDePeso.validar(-5.0))
        assertEquals(ErroDominio.PesoInvalido(2500.0), RegrasDePeso.validar(2500.0))
        assertNull(RegrasDePeso.validar(450.0))
    }

    @Test fun ganho_medio_diario_entre_primeira_e_ultima() {
        // 200 kg em 1/jan, 260 kg em 31/jan (30 dias): 2,0 kg/dia
        val lista = listOf(pesagem(31, 1, 260.0), pesagem(1, 1, 200.0), pesagem(15, 1, 225.0))
        assertEquals(2.0, RegrasDePeso.ganhoMedioDiario(lista)!!, 0.0001)
    }

    @Test fun ganho_recente_usa_as_duas_ultimas() {
        // 225 kg em 15/jan, 260 kg em 31/jan (16 dias): 2,1875 kg/dia
        val lista = listOf(pesagem(1, 1, 200.0), pesagem(15, 1, 225.0), pesagem(31, 1, 260.0))
        assertEquals(2.1875, RegrasDePeso.ganhoRecente(lista)!!, 0.0001)
    }

    @Test fun sem_ganho_com_uma_pesagem() = assertNull(RegrasDePeso.ganhoMedioDiario(listOf(pesagem(1, 1, 200.0))))
}

class RegrasDeProtocoloTest {
    private fun protocolo(
        doses: Int = 1,
        intervalo: Int? = null,
        reforco: Int? = null,
        min: Int? = null,
        max: Int? = null
    ) = ProtocoloSanitario("p", "f", "Vacina X", doses, intervalo, reforco, null, min, max)

    private fun motivo(p: ProtocoloSanitario) = (RegrasDeProtocolo.validar(p) as? ErroDominio.ProtocoloInvalido)?.motivo

    @Test fun dose_unica_valida() = assertNull(RegrasDeProtocolo.validar(protocolo()))
    @Test fun duas_doses_exigem_intervalo() = assertEquals(MotivoProtocoloInvalido.INTERVALO_OBRIGATORIO, motivo(protocolo(doses = 2)))
    @Test fun zero_doses_invalido() = assertEquals(MotivoProtocoloInvalido.DOSES_MENOR_QUE_UM, motivo(protocolo(doses = 0)))
    @Test fun reforco_negativo_invalido() = assertEquals(MotivoProtocoloInvalido.REFORCO_NAO_POSITIVO, motivo(protocolo(reforco = -1)))
    @Test fun idade_minima_maior_que_maxima() =
        assertEquals(MotivoProtocoloInvalido.IDADE_MINIMA_MAIOR_QUE_MAXIMA, motivo(protocolo(min = 8, max = 3)))
    @Test fun nome_obrigatorio() =
        assertEquals(ErroDominio.NomeObrigatorio("nome"), RegrasDeProtocolo.validar(protocolo().copy(nome = " ")))
}
