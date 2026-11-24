package com.jakatech.rebanhomais.kmp.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

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

/** Grava registros do rebanho no servidor pelo id (inserir ou atualizar), sem duplicar (RN-01). */
internal fun interface ServidorDoRebanho {
    suspend fun gravar(tabela: String, linhas: List<JsonObject>): RespostaDoServidor
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
}

/** Status HTTP + SQLSTATE → o que fazer com o lote. Função pura, testada sem rede. */
internal fun classificar(status: Int, codigo: String?, mensagem: String?): RespostaDoServidor = when {
    status == 401 || codigo == "PGRST301" || codigo == "PGRST303" -> RespostaDoServidor.SemSessao
    status >= 500 || status == 408 || status == 429 -> RespostaDoServidor.Indisponivel("HTTP $status")
    else -> RespostaDoServidor.Recusado(codigo ?: "HTTP$status", mensagem.orEmpty())
}
