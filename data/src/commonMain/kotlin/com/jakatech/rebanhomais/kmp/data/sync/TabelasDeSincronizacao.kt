package com.jakatech.rebanhomais.kmp.data.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.jakatech.rebanhomais.kmp.domain.model.TipoDeRegistro
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * As 6 tabelas do rebanho como o envio as enxerga (PRD Fase 4, fatia 1).
 * As colunas são as do Supabase, menos updated_at: quem grava a hora é o servidor (RN-03).
 * SchemaDeEnvioTest confere esta lista com as migrations do Supabase.
 */

internal enum class TipoDaColuna { TEXTO, INTEIRO, REAL, BOOLEANO }

internal class TabelaDeSincronizacao(
    val nome: String,
    val tipo: TipoDeRegistro,
    val colunas: List<Pair<String, TipoDaColuna>>,
    /** Colunas que apontam para outro registro do rebanho: um pai com erro segura o filho (RN-06). */
    val referencias: List<String>
)

private val T = TipoDaColuna.TEXTO
private val I = TipoDaColuna.INTEIRO
private val R = TipoDaColuna.REAL
private val B = TipoDaColuna.BOOLEANO

/** Na ordem do envio: quem é referenciado vai antes de quem referencia (RF-02). */
internal val TABELAS_DE_SINCRONIZACAO: List<TabelaDeSincronizacao> = listOf(
    TabelaDeSincronizacao(
        "lotes", TipoDeRegistro.LOTE,
        listOf("id" to T, "fazenda_id" to T, "nome" to T, "created_at" to T, "deleted_at" to T),
        referencias = emptyList()
    ),
    TabelaDeSincronizacao(
        "protocolos_sanitarios", TipoDeRegistro.PROTOCOLO,
        listOf(
            "id" to T, "fazenda_id" to T, "nome" to T, "doses" to I, "intervalo_entre_doses_dias" to I,
            "reforco_dias" to I, "sexo_alvo" to T, "idade_minima_meses" to I, "idade_maxima_meses" to I,
            "created_at" to T, "deleted_at" to T
        ),
        referencias = emptyList()
    ),
    TabelaDeSincronizacao(
        "animais", TipoDeRegistro.ANIMAL,
        listOf(
            "id" to T, "fazenda_id" to T, "brinco" to T, "sexo" to T, "origem" to T, "data_nascimento" to T,
            "nome" to T, "raca" to T, "aptidao" to T, "mae_id" to T, "pai_id" to T, "lote_id" to T,
            "reprodutor" to B, "status" to T, "observacao" to T, "created_at" to T, "deleted_at" to T
        ),
        referencias = listOf("lote_id", "mae_id", "pai_id")
    ),
    TabelaDeSincronizacao(
        "movimentacoes", TipoDeRegistro.MOVIMENTACAO,
        listOf(
            "id" to T, "fazenda_id" to T, "animal_id" to T, "tipo" to T, "data" to T, "contraparte" to T,
            "valor" to R, "observacao" to T, "created_at" to T, "deleted_at" to T
        ),
        referencias = listOf("animal_id")
    ),
    TabelaDeSincronizacao(
        "pesagens", TipoDeRegistro.PESAGEM,
        listOf(
            "id" to T, "fazenda_id" to T, "animal_id" to T, "data" to T, "peso_kg" to R, "observacao" to T,
            "created_at" to T, "deleted_at" to T
        ),
        referencias = listOf("animal_id")
    ),
    TabelaDeSincronizacao(
        "aplicacoes_vacina", TipoDeRegistro.VACINACAO,
        listOf(
            "id" to T, "fazenda_id" to T, "animal_id" to T, "protocolo_id" to T, "nome_vacina" to T, "data" to T,
            "dose" to I, "lote_produto" to T, "observacao" to T, "created_at" to T, "deleted_at" to T
        ),
        referencias = listOf("animal_id", "protocolo_id")
    )
)

/** Um registro pendente pronto para ir ao servidor. [updatedAt] é a versão local enviada (RN-02). */
internal class LinhaPendente(val id: String, val updatedAt: String, val json: JsonObject) {
    fun referencia(coluna: String): String? = (json[coluna] as? JsonPrimitive)?.contentOrNull
}

/** Pendentes sem erro da tabela, do mais antigo ao mais novo. */
internal fun SqlDriver.pendentes(tabela: TabelaDeSincronizacao): List<LinhaPendente> {
    val colunas = tabela.colunas.joinToString(", ") { it.first }
    val sql = "SELECT $colunas, updated_at FROM ${tabela.nome} " +
        "WHERE sync_status = 'PENDENTE' AND sync_erro_codigo IS NULL ORDER BY created_at, id"
    return executeQuery(null, sql, { cursor ->
        val linhas = mutableListOf<LinhaPendente>()
        while (cursor.next().value) linhas += cursor.linha(tabela)
        QueryResult.Value(linhas)
    }, 0).value
}

private fun SqlCursor.linha(tabela: TabelaDeSincronizacao): LinhaPendente {
    val json = buildJsonObject {
        tabela.colunas.forEachIndexed { i, (nome, tipo) ->
            when (tipo) {
                TipoDaColuna.TEXTO -> put(nome, getString(i))
                TipoDaColuna.INTEIRO -> put(nome, getLong(i))
                TipoDaColuna.REAL -> put(nome, getDouble(i))
                TipoDaColuna.BOOLEANO -> put(nome, getLong(i)?.let { it != 0L })
            }
        }
    }
    val id = getString(0)!!
    val updatedAt = getString(tabela.colunas.size)!!
    return LinhaPendente(id, updatedAt, json)
}

/** Ids da tabela recusados pelo servidor (RN-06). */
internal fun SqlDriver.idsComErro(tabela: TabelaDeSincronizacao): Set<String> =
    executeQuery(null, "SELECT id FROM ${tabela.nome} WHERE sync_erro_codigo IS NOT NULL", { cursor ->
        val ids = mutableSetOf<String>()
        while (cursor.next().value) ids += cursor.getString(0)!!
        QueryResult.Value(ids)
    }, 0).value

/**
 * Aceito pelo servidor. Só vale se o registro não mudou desde que foi lido (RN-02):
 * alterado durante o envio, continua pendente e vai no próximo.
 */
internal fun SqlDriver.marcarSincronizado(tabela: TabelaDeSincronizacao, linha: LinhaPendente) {
    execute(
        null,
        "UPDATE ${tabela.nome} SET sync_status = 'SINCRONIZADO', sync_erro_codigo = NULL, sync_erro_mensagem = NULL" +
            tabela.marcaJaSincronizado() + " WHERE id = ? AND updated_at = ?",
        2
    ) {
        bindString(0, linha.id)
        bindString(1, linha.updatedAt)
    }
}

/** Recusado pelo servidor: fica com o motivo e sai do envio até ser corrigido (RN-06). */
internal fun SqlDriver.marcarErro(tabela: TabelaDeSincronizacao, linha: LinhaPendente, codigo: String, mensagem: String) {
    execute(
        null,
        "UPDATE ${tabela.nome} SET sync_erro_codigo = ?, sync_erro_mensagem = ? WHERE id = ? AND updated_at = ?",
        4
    ) {
        bindString(0, codigo)
        bindString(1, mensagem)
        bindString(2, linha.id)
        bindString(3, linha.updatedAt)
    }
}

/** O que aconteceu com uma linha recebida do servidor. */
internal enum class Aplicacao {
    /** Gravada no aparelho como sincronizada. */
    APLICADA,

    /** O aparelho tem uma alteração ainda não enviada: ela vence até ser enviada (RN-04). */
    MANTIDA_A_LOCAL,

    /**
     * Brinco já usado por outro animal só deste aparelho. Esse animal fica com erro de duplicidade
     * e o recebimento dessa tabela para aqui até ele ser corrigido.
     */
    CONFLITO_DE_BRINCO
}

/**
 * Grava no aparelho uma linha que veio do servidor, com o updated_at do servidor e como SINCRONIZADO.
 * Chame dentro de uma transação.
 */
internal fun SqlDriver.aplicarDoServidor(tabela: TabelaDeSincronizacao, json: JsonObject): Aplicacao {
    val id = (json["id"] as? JsonPrimitive)?.contentOrNull ?: return Aplicacao.APLICADA
    val statusLocal = executeQuery(null, "SELECT sync_status FROM ${tabela.nome} WHERE id = ?", { c ->
        QueryResult.Value(if (c.next().value) c.getString(0) else null)
    }, 1) { bindString(0, id) }.value
    if (statusLocal == "PENDENTE") return Aplicacao.MANTIDA_A_LOCAL

    if (tabela.nome == "animais") {
        val fazenda = (json["fazenda_id"] as? JsonPrimitive)?.contentOrNull
        val brinco = (json["brinco"] as? JsonPrimitive)?.contentOrNull
        val outro = executeQuery(
            null,
            "SELECT id FROM animais WHERE fazenda_id = ? AND brinco = ? AND id <> ?",
            { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) },
            3
        ) {
            bindString(0, fazenda)
            bindString(1, brinco)
            bindString(2, id)
        }.value
        if (outro != null) {
            execute(
                null,
                "UPDATE animais SET sync_erro_codigo = '23505', " +
                    "sync_erro_mensagem = 'Brinco já usado por outro animal desta fazenda no servidor' " +
                    "WHERE id = ? AND sync_status = 'PENDENTE'",
                1
            ) { bindString(0, outro) }
            return Aplicacao.CONFLITO_DE_BRINCO
        }
    }

    val colunas = tabela.colunas.map { it.first } + "updated_at"
    val tipos = tabela.colunas.map { it.second } + TipoDaColuna.TEXTO
    val vincular: SqlPreparedStatement.(Int) -> Unit = { inicio ->
        colunas.forEachIndexed { i, nome ->
            val valor = json[nome] as? JsonPrimitive
            val texto = valor?.contentOrNull
            when (tipos[i]) {
                TipoDaColuna.TEXTO -> bindString(inicio + i, texto)
                TipoDaColuna.INTEIRO -> bindLong(inicio + i, texto?.toDoubleOrNull()?.toLong())
                TipoDaColuna.REAL -> bindDouble(inicio + i, texto?.toDoubleOrNull())
                TipoDaColuna.BOOLEANO -> bindLong(inicio + i, texto?.toBooleanStrictOrNull()?.let { if (it) 1L else 0L })
            }
        }
    }
    if (statusLocal == null) {
        execute(
            null,
            "INSERT INTO ${tabela.nome} (${colunas.joinToString(", ")}, sync_status" +
                (if (tabela.nome == "animais") ", ja_sincronizado" else "") + ") " +
                "VALUES (${colunas.joinToString(", ") { "?" }}, 'SINCRONIZADO'" +
                (if (tabela.nome == "animais") ", 1" else "") + ")",
            colunas.size
        ) { vincular(0) }
    } else {
        execute(
            null,
            "UPDATE ${tabela.nome} SET ${colunas.joinToString(", ") { "$it = ?" }}, sync_status = 'SINCRONIZADO', " +
                "sync_erro_codigo = NULL, sync_erro_mensagem = NULL" + tabela.marcaJaSincronizado() + " WHERE id = ?",
            colunas.size + 1
        ) {
            vincular(0)
            bindString(colunas.size, id)
        }
    }
    return Aplicacao.APLICADA
}

/** Animais guardam que já estiveram no servidor: o brinco deixa de poder ser corrigido (RN-12). */
private fun TabelaDeSincronizacao.marcaJaSincronizado(): String = if (nome == "animais") ", ja_sincronizado = 1" else ""

/** Apaga do aparelho todo o rebanho e os marcadores (ao sair, RF-10). */
internal fun SqlDriver.apagarTudo() {
    for (tabela in TABELAS_DE_SINCRONIZACAO) execute(null, "DELETE FROM ${tabela.nome}", 0)
    execute(null, "DELETE FROM marcadores_sync", 0)
}

/** Apaga do aparelho tudo das fazendas que não estão em [fazendas] (RN-11, RF-12). */
internal fun SqlDriver.apagarForaDasFazendas(fazendas: Collection<String>) {
    val marcas = fazendas.joinToString(", ") { "?" }
    for (tabela in TABELAS_DE_SINCRONIZACAO) {
        execute(null, "DELETE FROM ${tabela.nome} WHERE fazenda_id NOT IN ($marcas)", fazendas.size) {
            fazendas.forEachIndexed { i, f -> bindString(i, f) }
        }
    }
    execute(null, "DELETE FROM marcadores_sync WHERE fazenda_id NOT IN ($marcas)", fazendas.size) {
        fazendas.forEachIndexed { i, f -> bindString(i, f) }
    }
}
