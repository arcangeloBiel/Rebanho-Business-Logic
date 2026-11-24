package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        q.listarPorAnimal(animalId).executeAsList().map { it.paraDominio() }
    }

    override fun observarPorAnimal(animalId: String): Flow<List<Movimentacao>> =
        q.listarPorAnimal(animalId).asFlow().mapToList(contexto).map { linhas -> linhas.map { it.paraDominio() } }
}

private fun Movimentacoes.paraDominio() = Movimentacao(
    id = id,
    fazendaId = fazenda_id,
    animalId = animal_id,
    tipo = TipoMovimentacao.valueOf(tipo),
    data = LocalDate.parse(data),
    contraparte = contraparte,
    valor = valor,
    observacao = observacao
)
