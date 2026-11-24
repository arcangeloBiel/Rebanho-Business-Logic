package com.jakatech.rebanhomais.kmp.data.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PRD Fase 4, RNF-04: o envio manda exatamente as colunas do Supabase, menos updated_at
 * (gravado pelo servidor, RN-03). Se o schema remoto mudar, este teste aponta o que falta.
 */
class SchemaDeEnvioTest {

    private fun colunasNoSupabase(): Map<String, Set<String>> {
        val pasta = File("../supabase/migrations")
        assertTrue(pasta.isDirectory, "migrations do Supabase não encontradas em ${pasta.absolutePath}")
        val sql = pasta.listFiles { f -> f.name.endsWith(".sql") }!!.sortedBy { it.name }.joinToString("\n") { it.readText() }
        val criacao = Regex("""create table public\.(\w+) \((.*?)\n\);""", RegexOption.DOT_MATCHES_ALL)
        val naoSaoColunas = setOf("unique", "check", "foreign", "primary", "constraint")
        return criacao.findAll(sql).associate { m ->
            m.groupValues[1] to m.groupValues[2].lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("--") }
                .map { it.substringBefore(' ').lowercase() }
                .filter { it !in naoSaoColunas }
                .toSet()
        }
    }

    @Test
    fun rnf_04_envio_manda_as_colunas_do_supabase_menos_updated_at() {
        val supabase = colunasNoSupabase()
        for (tabela in TABELAS_DE_SINCRONIZACAO) {
            val remotas = supabase[tabela.nome] ?: error("tabela ${tabela.nome} não encontrada nas migrations")
            assertEquals(remotas - "updated_at", tabela.colunas.map { it.first }.toSet(), "colunas de envio diferentes em ${tabela.nome}")
        }
    }

    @Test
    fun rf_02_ordem_respeita_as_referencias() {
        val posicao = TABELAS_DE_SINCRONIZACAO.mapIndexed { i, t -> t.nome to i }.toMap()
        val alvo = mapOf("lote_id" to "lotes", "animal_id" to "animais", "protocolo_id" to "protocolos_sanitarios")
        for (tabela in TABELAS_DE_SINCRONIZACAO) {
            for (ref in tabela.referencias) {
                val pai = alvo[ref] ?: continue // mae_id/pai_id: mesma tabela, ordenado na hora do envio
                assertTrue(posicao.getValue(pai) < posicao.getValue(tabela.nome), "${tabela.nome} antes de $pai")
            }
        }
    }
}
