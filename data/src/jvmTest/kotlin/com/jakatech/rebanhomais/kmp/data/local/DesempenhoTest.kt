package com.jakatech.rebanhomais.kmp.data.local

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.NovoAnimal
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

/**
 * PRD Fase 3, RNF-02 (fatia 4): fazenda com 5.000 animais.
 * Lista em até 300 ms e cadastro em até 100 ms. Roda no CI (JVM, SQLite em memória);
 * a medição num Android de entrada continua manual, como diz a tabela de métricas do PRD.
 */
class DesempenhoTest {
    private val driver = driverEmMemoria()
    private val banco = RebanhoDatabase(driver)
    private val animais = AnimaisLocais(banco)
    private val movimentacoes = MovimentacoesLocais(banco)
    private val lotes = LotesLocais(banco)

    @AfterTest
    fun fechar() = driver.close()

    private suspend fun povoar(fazendaId: String, quantidade: Int) {
        repeat(quantidade) { i ->
            animais.salvar(
                Animal(
                    id = "$fazendaId-$i", fazendaId = fazendaId, brinco = "BR${i.toString().padStart(5, '0')}",
                    sexo = if (i % 2 == 0) Sexo.FEMEA else Sexo.MACHO, origem = Origem.NASCIDO_NA_FAZENDA,
                    dataNascimento = LocalDate(2025, 1, 1), nome = if (i % 10 == 0) "Mimosa $i" else null,
                    status = if (i % 20 == 0) StatusAnimal.VENDIDO else StatusAnimal.ATIVO
                )
            )
        }
    }

    /** Mediana de [vezes] medições, depois de aquecer: um pico isolado do CI não reprova o teste. */
    private suspend fun mediana(vezes: Int = 7, bloco: suspend () -> Unit): Duration {
        repeat(2) { bloco() }
        val tempos = List(vezes) { measureTime { bloco() } }
        return tempos.sorted()[vezes / 2]
    }

    @Test
    fun rnf_02_lista_de_5000_animais_em_ate_300_ms() = runTest(timeout = 3.minutes) {
        povoar("f1", 5_000)
        povoar("f2", 1_000) // outra fazenda no mesmo aparelho não pode pesar na lista

        var tamanho = 0
        val lista = mediana { tamanho = animais.observarPorFazenda("f1").first().size }
        assertEquals(5_000, tamanho)
        assertTrue(lista <= 300.milliseconds, "Lista de 5.000 animais levou $lista (limite 300 ms)")

        val busca = mediana { tamanho = animais.observarPorFazenda("f1", "mimosa").first().size }
        assertEquals(500, tamanho)
        assertTrue(busca <= 300.milliseconds, "Busca em 5.000 animais levou $busca (limite 300 ms)")
    }

    @Test
    fun rnf_02_cadastro_em_ate_100_ms_com_5000_animais() = runTest(timeout = 3.minutes) {
        povoar("f1", 5_000)
        var n = 0
        val cadastrar = CadastrarAnimal(
            animais, movimentacoes, lotes, GeradorDeId { "novo-${n++}" }, Relogio { LocalDate(2026, 11, 24) }
        )
        var brinco = 0

        val cadastro = mediana {
            val r = cadastrar(
                NovoAnimal(fazendaId = "f1", brinco = "NV${brinco++}", sexo = Sexo.FEMEA, origem = Origem.NASCIDO_NA_FAZENDA,
                    dataNascimento = LocalDate(2026, 11, 1))
            )
            assertTrue(r is Resultado.Sucesso, "Cadastro falhou: $r")
        }
        assertTrue(cadastro <= 100.milliseconds, "Cadastro com 5.000 animais levou $cadastro (limite 100 ms)")
    }
}
