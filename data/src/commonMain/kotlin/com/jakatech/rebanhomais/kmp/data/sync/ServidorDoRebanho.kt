package com.jakatech.rebanhomais.kmp.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** O que o servidor respondeu a um lote de registros. */
internal sealed interface RespostaDoServidor {
    /** Todos gravados. */
    data object Aceito : RespostaDoServidor

    /** O servidor recusou (regra, permissão, duplicidade). [codigo] é o SQLSTATE quando houver. */
    data class Recusado(val codigo: String, val mensagem: String) : RespostaDoServidor

    /** Sem internet, tempo esgotado ou servidor fora do ar: tentar de novo depois (RN-08). */
    data class Indisponivel(val detalhe: String) : RespostaDoServidor

    /** Sem sessão válida: parar e esperar o usuário entrar de novo (RN-09). */
    data object SemSessao : RespostaDoServidor
}

/** Que fatia da tabela buscar, sempre em ordem de (updated_at, id) (PRD Fase 4, RF-04, RF-05). */
internal sealed interface FiltroDeBusca {
    /** Desde o começo: carga inicial (RF-05). */
    data object Tudo : FiltroDeBusca

    /** Linhas com o mesmo updated_at do marcador e id maior: termina um instante com mais de um lote. */
    data class MesmoInstante(val atualizadoEm: String, val depoisDoId: String) : FiltroDeBusca

    /** Linhas com updated_at maior que o marcador. */
    data class DepoisDe(val atualizadoEm: String) : FiltroDeBusca
}

/** O que o servidor devolveu numa busca. */
internal sealed interface RespostaDaBusca {
    data class Linhas(val linhas: List<JsonObject>) : RespostaDaBusca
    data class Falhou(val resposta: RespostaDoServidor) : RespostaDaBusca
}

internal interface ServidorDoRebanho {
    /** Grava registros do rebanho pelo id (inserir ou atualizar), sem duplicar (RN-01). */
    suspend fun gravar(tabela: String, linhas: List<JsonObject>): RespostaDoServidor

    /** Até [limite] linhas da fazenda, inclusive excluídas, em ordem de (updated_at, id). */
    suspend fun buscar(tabela: String, fazendaId: String, filtro: FiltroDeBusca, limite: Int): RespostaDaBusca
}

/** Supabase (PostgREST) com a sessão do usuário: RLS e gatilhos do banco valem para toda escrita (RNF-04). */
internal class ServidorSupabase(private val supabase: SupabaseClient) : ServidorDoRebanho {

    override suspend fun gravar(tabela: String, linhas: List<JsonObject>): RespostaDoServidor {
        if (supabase.auth.currentSessionOrNull() == null) return RespostaDoServidor.SemSessao
        return try {
            supabase.from(tabela).upsert(linhas)
            RespostaDoServidor.Aceito
        } catch (e: CancellationException) {
            throw e
        } catch (e: PostgrestRestException) {
            classificar(e.statusCode, e.code, e.message)
        } catch (e: RestException) {
            classificar(e.statusCode, null, e.message)
        } catch (e: HttpRequestException) {
            RespostaDoServidor.Indisponivel(e.message ?: "sem conexão")
        } catch (e: Exception) {
            // Desconhecido: não marca erro no registro; fica pendente e tenta de novo.
            RespostaDoServidor.Indisponivel(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    override suspend fun buscar(tabela: String, fazendaId: String, filtro: FiltroDeBusca, limite: Int): RespostaDaBusca {
        if (supabase.auth.currentSessionOrNull() == null) return RespostaDaBusca.Falhou(RespostaDoServidor.SemSessao)
        return try {
            val resposta = supabase.from(tabela).select {
                filter {
                    eq("fazenda_id", fazendaId)
                    when (filtro) {
                        FiltroDeBusca.Tudo -> Unit
                        is FiltroDeBusca.MesmoInstante -> {
                            eq("updated_at", filtro.atualizadoEm)
                            gt("id", filtro.depoisDoId)
                        }
                        is FiltroDeBusca.DepoisDe -> gt("updated_at", filtro.atualizadoEm)
                    }
                }
                order("updated_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                limit(limite.toLong())
            }
            RespostaDaBusca.Linhas(Json.parseToJsonElement(resposta.data).jsonArray.map { it.jsonObject })
        } catch (e: CancellationException) {
            throw e
        } catch (e: PostgrestRestException) {
            RespostaDaBusca.Falhou(classificar(e.statusCode, e.code, e.message))
        } catch (e: RestException) {
            RespostaDaBusca.Falhou(classificar(e.statusCode, null, e.message))
        } catch (e: Exception) {
            RespostaDaBusca.Falhou(RespostaDoServidor.Indisponivel(e.message ?: e::class.simpleName.orEmpty()))
        }
    }
}

/** Status HTTP + SQLSTATE → o que fazer com o lote. Função pura, testada sem rede. */
internal fun classificar(status: Int, codigo: String?, mensagem: String?): RespostaDoServidor = when {
    status == 401 || codigo == "PGRST301" || codigo == "PGRST303" -> RespostaDoServidor.SemSessao
    status >= 500 || status == 408 || status == 429 -> RespostaDoServidor.Indisponivel("HTTP $status")
    else -> RespostaDoServidor.Recusado(codigo ?: "HTTP$status", mensagem.orEmpty())
}
