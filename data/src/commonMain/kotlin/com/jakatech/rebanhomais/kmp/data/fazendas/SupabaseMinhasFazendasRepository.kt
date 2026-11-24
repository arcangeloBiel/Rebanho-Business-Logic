package com.jakatech.rebanhomais.kmp.data.fazendas

import com.jakatech.rebanhomais.kmp.data.comum.executar
import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fazendas do usuário no Supabase, com cache no aparelho.
 *
 * - Ao entrar (ou abrir o app logado), carrega o cache e busca a lista atualizada no servidor.
 * - Sem internet, fica com o cache (CA-02.3).
 * - O cache é marcado com o id do usuário: outro usuário no mesmo celular nunca vê a lista anterior.
 */
class SupabaseMinhasFazendasRepository(
    private val supabase: SupabaseClient,
    private val settings: Settings,
    escopo: CoroutineScope
) : MinhasFazendasRepository {

    private val estado = MutableStateFlow<List<FazendaDoUsuario>>(emptyList())
    override val fazendas: Flow<List<FazendaDoUsuario>> = estado.asStateFlow()

    init {
        escopo.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> usuarioId()?.let { id ->
                        carregarCache(id)
                        atualizar()
                    }
                    is SessionStatus.RefreshFailure -> usuarioId()?.let { id -> carregarCache(id) }
                    is SessionStatus.NotAuthenticated -> estado.value = emptyList()
                    is SessionStatus.Initializing -> Unit
                }
            }
        }
    }

    override suspend fun atualizar(): Resultado<List<FazendaDoUsuario>> = executar {
        val id = usuarioId() ?: return Resultado.Falha(ErroDominio.SemPermissao)
        val resposta = supabase.from("membros").select(Columns.raw(JsonDeFazendas.COLUNAS_MEMBROS)) {
            filter { eq("usuario_id", id) }
        }
        val lista = JsonDeFazendas.minhasFazendasDe(JsonDeFazendas.lista(resposta.data))
        estado.value = lista
        settings.putString(CHAVE_CACHE, JsonDeFazendas.paraCache(id, lista))
        lista
    }

    override suspend fun criar(fazenda: Fazenda): Resultado<FazendaDoUsuario> = executar {
        val resposta = supabase.postgrest.rpc(
            "criar_fazenda",
            buildJsonObject {
                put("p_id", fazenda.id)
                put("p_nome", fazenda.nome)
                put("p_municipio", fazenda.municipio)
                put("p_uf", fazenda.uf)
                put("p_codigo_propriedade", fazenda.codigoPropriedade)
            }
        )
        val criada = FazendaDoUsuario(JsonDeFazendas.fazendaDe(JsonDeFazendas.objeto(resposta.data)), Papel.DONO)
        estado.update { atual -> JsonDeFazendas.ordenar(atual.filterNot { it.fazenda.id == criada.fazenda.id } + criada) }
        usuarioId()?.let { id -> settings.putString(CHAVE_CACHE, JsonDeFazendas.paraCache(id, estado.value)) }
        criada
    }

    override suspend fun limpar() {
        estado.value = emptyList()
        settings.remove(CHAVE_CACHE)
    }

    private fun carregarCache(usuarioId: String) {
        JsonDeFazendas.deCache(settings.getStringOrNull(CHAVE_CACHE), usuarioId)?.let { estado.value = it }
    }

    private fun usuarioId(): String? =
        supabase.auth.currentSessionOrNull()?.user?.id ?: supabase.auth.currentUserOrNull()?.id

    private companion object {
        const val CHAVE_CACHE = "rebanho.minhas_fazendas"
    }
}
