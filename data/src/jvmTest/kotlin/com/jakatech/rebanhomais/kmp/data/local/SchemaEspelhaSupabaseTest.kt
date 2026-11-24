package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.db.QueryResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PRD Fase 3, RNF-04 e R-03: as tabelas do aparelho têm exatamente as colunas do Supabase,
 * mais as colunas sync_* (só do aparelho). Se alguém mudar um lado sem o outro, este teste falha.
 */
class SchemaEspelhaSupabaseTest {

    private val tabelasDoRebanho = listOf(
        "lotes", "animais", "movimentacoes", "pesagens", "protocolos_sanitarios", "aplicacoes_vacina"
    )
    private val colunasSoLocais = setOf("sync_status", "sync_erro_codigo", "sync_erro_mensagem")
    private val palavrasQueNaoSaoColunas = setOf("unique", "check", "foreign", "primary", "constraint")

    /** Colunas de cada "create table public.x (...)" nas migrations do Supabase (lidas em ordem). */
    private fun colunasNoSupabase(): Map<String, Set<String>> {
        val pasta = File("../supabase/migrations")
        assertTrue(pasta.isDirectory, "migrations do Supabase não encontradas em ${pasta.absolutePath}")
        val sql = pasta.listFiles { f -> f.name.endsWith(".sql") }!!.sortedBy { it.name }.joinToString("\n") { it.readText() }
        val criacao = Regex("""create table public\.(\w+) \((.*?)\n\);""", RegexOption.DOT_MATCHES_ALL)
        return criacao.findAll(sql).associate { m ->
            val colunas = m.groupValues[2].lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("--") }
                .map { it.substringBefore(' ').lowercase() }
                .filter { it !in palavrasQueNaoSaoColunas }
                .toSet()
            m.groupValues[1] to colunas
        }
    }

    private fun colunasNoAparelho(tabela: String): Set<String> {
        val driver = driverEmMemoria()
        try {
            return driver.executeQuery(
                identifier = null,
                sql = "PRAGMA table_info($tabela)",
                mapper = { cursor ->
                    val nomes = mutableSetOf<String>()
                    while (cursor.next().value) nomes += cursor.getString(1)!!
                    QueryResult.Value(nomes)
                },
                parameters = 0
            ).value
        } finally {
            driver.close()
        }
    }

    @Test
    fun rnf_04_cada_tabela_local_tem_as_colunas_do_supabase_mais_sync_status() {
        val supabase = colunasNoSupabase()
        for (tabela in tabelasDoRebanho) {
            val remotas = supabase[tabela] ?: error("tabela $tabela não encontrada nas migrations do Supabase")
            val locais = colunasNoAparelho(tabela)
            assertEquals(remotas, locais - colunasSoLocais, "colunas diferentes em $tabela")
            assertTrue(locais.containsAll(colunasSoLocais), "$tabela sem sync_status")
        }
    }
}
