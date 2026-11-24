package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.repositorio.LoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Lotes no banco do aparelho (PRD Fase 3, RF-01, fatia 4). */
class LotesLocais(
    banco: RebanhoDatabase,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual
) : LoteRepository {
    private val q = banco.loteQueries

    override suspend fun salvar(lote: Lote) = withContext(contexto) {
        val instante = agora()
        q.transaction {
            if (q.existe(lote.id).executeAsOne() > 0) {
                q.atualizar(fazenda_id = lote.fazendaId, nome = lote.nome, agora = instante, id = lote.id)
            } else {
                q.inserir(id = lote.id, fazenda_id = lote.fazendaId, nome = lote.nome, agora = instante)
            }
        }
    }

    override suspend fun buscarPorId(loteId: String): Lote? = withContext(contexto) {
        q.buscarPorId(loteId, ::lote).executeAsOneOrNull()
    }

    override suspend fun listarPorFazenda(fazendaId: String): List<Lote> = withContext(contexto) {
        q.listarPorFazenda(fazendaId, ::lote).executeAsList()
    }

    override fun observarPorFazenda(fazendaId: String): Flow<List<Lote>> =
        q.listarPorFazenda(fazendaId, ::lote).asFlow().mapToList(contexto)
}

/** Linha de lotes → domínio, na ordem das colunas da tabela (Lote.sq). */
@Suppress("UNUSED_PARAMETER")
private fun lote(
    id: String,
    fazendaId: String,
    nome: String,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    syncStatus: String
) = Lote(id = id, fazendaId = fazendaId, nome = nome)
