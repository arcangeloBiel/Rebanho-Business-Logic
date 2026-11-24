package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

/** Pesagens no banco do aparelho (PRD Fase 3, RF-01, HU-04). */
class PesagensLocais(
    banco: RebanhoDatabase,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual
) : PesagemRepository {
    private val q = banco.pesagemQueries

    override suspend fun salvar(pesagem: Pesagem) = withContext(contexto) {
        val instante = agora()
        q.transaction {
            with(pesagem) {
                if (q.existe(id).executeAsOne() > 0) {
                    q.atualizar(
                        fazenda_id = fazendaId, animal_id = animalId, data = data.toString(), peso_kg = pesoKg,
                        observacao = observacao, agora = instante, id = id
                    )
                } else {
                    q.inserir(
                        id = id, fazenda_id = fazendaId, animal_id = animalId, data = data.toString(), peso_kg = pesoKg,
                        observacao = observacao, agora = instante
                    )
                }
            }
        }
    }

    override suspend fun listarPorAnimal(animalId: String): List<Pesagem> = withContext(contexto) {
        q.listarPorAnimal(animalId, ::pesagem).executeAsList()
    }

    override fun observarPorAnimal(animalId: String): Flow<List<Pesagem>> =
        q.listarPorAnimal(animalId, ::pesagem).asFlow().mapToList(contexto)
}

/** Linha de pesagens → domínio, na ordem das colunas da tabela (Pesagem.sq). */
@Suppress("UNUSED_PARAMETER")
private fun pesagem(
    id: String,
    fazendaId: String,
    animalId: String,
    data: String,
    pesoKg: Double,
    observacao: String?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    syncStatus: String,
    syncErroCodigo: String?,
    syncErroMensagem: String?
) = Pesagem(
    id = id,
    fazendaId = fazendaId,
    animalId = animalId,
    data = LocalDate.parse(data),
    pesoKg = pesoKg,
    observacao = observacao
)
