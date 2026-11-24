package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

/** Entradas e saídas dos animais no banco do aparelho (PRD Fase 3, RF-01 e RF-07). */
class MovimentacoesLocais(
    banco: RebanhoDatabase,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual
) : MovimentacaoRepository {
    private val q = banco.movimentacaoQueries

    override suspend fun salvar(movimentacao: Movimentacao) = withContext(contexto) {
        val instante = agora()
        q.transaction {
            with(movimentacao) {
                if (q.existe(id).executeAsOne() > 0) {
                    q.atualizar(
                        fazenda_id = fazendaId, animal_id = animalId, tipo = tipo.name, data = data.toString(),
                        contraparte = contraparte, valor = valor, observacao = observacao, agora = instante, id = id
                    )
                } else {
                    q.inserir(
                        id = id, fazenda_id = fazendaId, animal_id = animalId, tipo = tipo.name, data = data.toString(),
                        contraparte = contraparte, valor = valor, observacao = observacao, agora = instante
                    )
                }
            }
        }
    }

    override suspend fun listarPorAnimal(animalId: String): List<Movimentacao> = withContext(contexto) {
        q.listarPorAnimal(animalId, ::movimentacao).executeAsList()
    }

    override fun observarPorAnimal(animalId: String): Flow<List<Movimentacao>> =
        q.listarPorAnimal(animalId, ::movimentacao).asFlow().mapToList(contexto)
}

/** Linha de movimentacoes → domínio, na ordem das colunas da tabela (Movimentacao.sq). */
@Suppress("UNUSED_PARAMETER")
private fun movimentacao(
    id: String,
    fazendaId: String,
    animalId: String,
    tipo: String,
    data: String,
    contraparte: String?,
    valor: Double?,
    observacao: String?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    syncStatus: String
) = Movimentacao(
    id = id,
    fazendaId = fazendaId,
    animalId = animalId,
    tipo = TipoMovimentacao.valueOf(tipo),
    data = LocalDate.parse(data),
    contraparte = contraparte,
    valor = valor,
    observacao = observacao
)
