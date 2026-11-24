package com.jakatech.rebanhomais.kmp.data.sync

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Servidor de mentira com o comportamento que importa do Supabase:
 * grava pelo id (upsert), carimba updated_at com a hora do servidor (o mesmo instante para o lote
 * inteiro, como o now() de uma transação) e devolve em ordem de (updated_at, id).
 */
internal class ServidorFalso : ServidorDoRebanho {
    val tabelas = mutableMapOf<String, MutableMap<String, JsonObject>>()
    /** Exatamente o que o aparelho mandou, sem o updated_at que o servidor carimba. */
    val enviados = mutableMapOf<String, MutableMap<String, JsonObject>>()
    val chamadas = mutableListOf<Pair<String, List<String>>>()
    val buscas = mutableListOf<Pair<String, FiltroDeBusca>>()
    var recusarIds = setOf<String>()
    var indisponivelNaTabela: String? = null
    var semSessao = false
    /** Falha as buscas a partir da n-ésima (1 = a primeira). */
    var falharBuscaApartirDe: Int? = null
    var aoGravar: suspend () -> Unit = {}
    private var relogio = 0L

    /** Próxima hora do servidor: microssegundos, como o Postgres, com "+00:00". */
    @OptIn(ExperimentalTime::class)
    fun proximoInstante(): String {
        relogio++
        return (Instant.parse("2026-11-24T12:00:00Z") + relogio.microseconds).toString().replace("Z", "+00:00")
    }

    /** Faz o relógio do servidor andar, para separar envios no tempo. */
    fun pularSegundos(segundos: Int) {
        relogio += segundos * 1_000_000L
    }

    /** Coloca linhas como se outro aparelho tivesse enviado, todas no mesmo instante. */
    fun colocar(tabela: String, vararg linhas: JsonObject) {
        val instante = proximoInstante()
        val destino = tabelas.getOrPut(tabela) { mutableMapOf() }
        for (linha in linhas) {
            destino[linha.getValue("id").jsonPrimitive.content] = JsonObject(linha + ("updated_at" to JsonPrimitive(instante)))
        }
    }

    override suspend fun gravar(tabela: String, linhas: List<JsonObject>): RespostaDoServidor {
        val ids = linhas.map { it.getValue("id").jsonPrimitive.content }
        chamadas += tabela to ids
        aoGravar()
        if (semSessao) return RespostaDoServidor.SemSessao
        if (tabela == indisponivelNaTabela) return RespostaDoServidor.Indisponivel("sem sinal")
        if (ids.any { it in recusarIds }) return RespostaDoServidor.Recusado("23505", "duplicate key value violates unique constraint")
        linhas.forEachIndexed { i, json -> enviados.getOrPut(tabela) { mutableMapOf() }[ids[i]] = json }
        colocar(tabela, *linhas.toTypedArray())
        return RespostaDoServidor.Aceito
    }

    override suspend fun buscar(tabela: String, fazendaId: String, filtro: FiltroDeBusca, limite: Int): RespostaDaBusca {
        buscas += tabela to filtro
        if (semSessao) return RespostaDaBusca.Falhou(RespostaDoServidor.SemSessao)
        falharBuscaApartirDe?.let { if (buscas.size >= it) return RespostaDaBusca.Falhou(RespostaDoServidor.Indisponivel("caiu")) }
        val linhas = tabelas[tabela].orEmpty().values
            .filter { it.texto("fazenda_id") == fazendaId }
            .map { Triple(normalizar(it.texto("updated_at")), it.texto("id"), it) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .filter { (instante, id, _) ->
                when (filtro) {
                    FiltroDeBusca.Tudo -> true
                    is FiltroDeBusca.MesmoInstante -> instante == normalizar(filtro.atualizadoEm) && id > filtro.depoisDoId
                    is FiltroDeBusca.DepoisDe -> instante > normalizar(filtro.atualizadoEm)
                }
            }
            .take(limite)
            .map { it.third }
        return RespostaDaBusca.Linhas(linhas)
    }

    private fun JsonObject.texto(coluna: String) = (this[coluna] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

/** Fazendas do usuário de mentira: [atualizar] devolve a lista atual ou o erro configurado. */
internal class FazendasFalsas(vararg ids: String) : MinhasFazendasRepository {
    override val fazendas = MutableStateFlow(ids.map { FazendaDoUsuario(Fazenda(it, "Fazenda $it"), Papel.DONO) })
    var erro: ErroDominio? = null

    override suspend fun atualizar(): Resultado<List<FazendaDoUsuario>> =
        erro?.let { Resultado.Falha(it) } ?: Resultado.Sucesso(fazendas.value)

    override suspend fun criar(fazenda: Fazenda): Resultado<FazendaDoUsuario> =
        Resultado.Sucesso(FazendaDoUsuario(fazenda, Papel.DONO).also { fazendas.value += it })

    override suspend fun limpar() {
        fazendas.value = emptyList()
    }
}
