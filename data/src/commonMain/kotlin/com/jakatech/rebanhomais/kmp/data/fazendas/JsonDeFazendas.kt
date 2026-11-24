package com.jakatech.rebanhomais.kmp.data.fazendas

import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Conversões entre o JSON do Supabase (e do cache no aparelho) e o domínio.
 * Funções puras, testadas sem rede.
 */
internal object JsonDeFazendas {

    /** Colunas pedidas na tabela membros, com a fazenda embutida pela chave estrangeira. */
    const val COLUNAS_MEMBROS = "papel, deleted_at, fazendas(id, nome, municipio, uf, codigo_propriedade, deleted_at)"

    /** Uma linha da tabela fazendas (ou o retorno de criar_fazenda). */
    fun fazendaDe(obj: JsonObject): Fazenda = Fazenda(
        id = obj.texto("id") ?: error("fazenda sem id"),
        nome = obj.texto("nome") ?: error("fazenda sem nome"),
        municipio = obj.texto("municipio"),
        uf = obj.texto("uf"),
        codigoPropriedade = obj.texto("codigo_propriedade")
    )

    /**
     * Resposta de `membros?select=COLUNAS_MEMBROS` filtrada pelo usuário.
     * Ignora vínculos e fazendas excluídos e papéis desconhecidos; ordena pelo nome.
     */
    fun minhasFazendasDe(resposta: JsonArray): List<FazendaDoUsuario> = resposta
        .mapNotNull { linha ->
            val membro = linha as? JsonObject ?: return@mapNotNull null
            if (membro.texto("deleted_at") != null) return@mapNotNull null
            val papel = membro.papel() ?: return@mapNotNull null
            val fazenda = membro["fazendas"] as? JsonObject ?: return@mapNotNull null
            if (fazenda.texto("deleted_at") != null) return@mapNotNull null
            FazendaDoUsuario(fazendaDe(fazenda), papel)
        }
        .ordenadas()

    /** Lista guardada no aparelho, marcada com o usuário dono dela. */
    fun paraCache(usuarioId: String, fazendas: List<FazendaDoUsuario>): String = buildJsonObject {
        put("usuario", usuarioId)
        putJsonArray("fazendas") {
            fazendas.forEach { item ->
                addJsonObject {
                    put("id", item.fazenda.id)
                    put("nome", item.fazenda.nome)
                    put("municipio", item.fazenda.municipio)
                    put("uf", item.fazenda.uf)
                    put("codigo_propriedade", item.fazenda.codigoPropriedade)
                    put("papel", item.papel.name)
                }
            }
        }
    }.toString()

    /** Lê o cache; nulo se não houver, se estiver corrompido ou se for de outro usuário. */
    fun deCache(texto: String?, usuarioId: String): List<FazendaDoUsuario>? {
        if (texto == null) return null
        return runCatching {
            val raiz = Json.parseToJsonElement(texto) as JsonObject
            if (raiz.texto("usuario") != usuarioId) return null
            (raiz["fazendas"] as JsonArray).mapNotNull { elemento ->
                val obj = elemento as? JsonObject ?: return@mapNotNull null
                val papel = obj.papel() ?: return@mapNotNull null
                FazendaDoUsuario(fazendaDe(obj), papel)
            }.ordenadas()
        }.getOrNull()
    }

    fun ordenar(fazendas: List<FazendaDoUsuario>): List<FazendaDoUsuario> = fazendas.ordenadas()

    private fun List<FazendaDoUsuario>.ordenadas() = sortedBy { it.fazenda.nome.lowercase() }

    private fun JsonObject.papel(): Papel? =
        texto("papel")?.let { nome -> Papel.entries.firstOrNull { it.name == nome } }

    private fun JsonObject.texto(campo: String): String? = (this[campo] as? JsonPrimitive)?.contentOrNull

    fun objeto(texto: String): JsonObject = Json.parseToJsonElement(texto) as JsonObject

    fun lista(texto: String): JsonArray = Json.parseToJsonElement(texto) as JsonArray
}
