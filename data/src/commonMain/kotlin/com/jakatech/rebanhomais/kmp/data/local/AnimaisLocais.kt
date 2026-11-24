package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Aptidao
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

/** Animais no banco do aparelho (PRD Fase 3, RF-01 a RF-06). */
class AnimaisLocais(
    banco: RebanhoDatabase,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual
) : AnimalRepository {
    private val q = banco.animalQueries

    override suspend fun salvar(animal: Animal) = withContext(contexto) {
        val instante = agora()
        q.transaction {
            with(animal) {
                val nascimento = dataNascimento?.toString()
                val reprodutorInt = if (reprodutor) 1L else 0L
                if (q.existe(id).executeAsOne() > 0) {
                    q.atualizar(
                        fazenda_id = fazendaId, brinco = brinco, sexo = sexo.name, origem = origem.name,
                        data_nascimento = nascimento, nome = nome, raca = raca, aptidao = aptidao.name,
                        mae_id = maeId, pai_id = paiId, lote_id = loteId, reprodutor = reprodutorInt,
                        status = status.name, observacao = observacao, agora = instante, id = id
                    )
                } else {
                    q.inserir(
                        id = id, fazenda_id = fazendaId, brinco = brinco, sexo = sexo.name, origem = origem.name,
                        data_nascimento = nascimento, nome = nome, raca = raca, aptidao = aptidao.name,
                        mae_id = maeId, pai_id = paiId, lote_id = loteId, reprodutor = reprodutorInt,
                        status = status.name, observacao = observacao, agora = instante
                    )
                }
            }
        }
    }

    override suspend fun buscarPorId(animalId: String): Animal? = withContext(contexto) {
        q.buscarPorId(animalId).executeAsOneOrNull()?.paraDominio()
    }

    override suspend fun buscarPorBrinco(fazendaId: String, brinco: String): Animal? = withContext(contexto) {
        q.buscarPorBrinco(fazendaId, brinco).executeAsOneOrNull()?.paraDominio()
    }

    override suspend fun listarPorFazenda(fazendaId: String): List<Animal> = withContext(contexto) {
        q.listarPorFazenda(fazendaId).executeAsList().map { it.paraDominio() }
    }

    override suspend fun contarFilhos(maeId: String): Int = withContext(contexto) {
        q.contarFilhos(maeId).executeAsOne().toInt()
    }

    override fun observarPorFazenda(fazendaId: String, busca: String): Flow<List<Animal>> =
        q.observarPorFazenda(fazenda_id = fazendaId, padrao = padraoDeBusca(busca))
            .asFlow()
            .mapToList(contexto)
            .map { linhas -> linhas.map { it.paraDominio() } }

    override suspend fun jaEstaNoServidor(animalId: String): Boolean = withContext(contexto) {
        (q.jaSincronizado(animalId).executeAsOneOrNull() ?: 0L) != 0L
    }

    override suspend fun excluir(animalId: String) = withContext(contexto) {
        q.excluir(agora = agora(), id = animalId)
        Unit
    }
}

private fun Animais.paraDominio() = Animal(
    id = id,
    fazendaId = fazenda_id,
    brinco = brinco,
    sexo = Sexo.valueOf(sexo),
    origem = Origem.valueOf(origem),
    dataNascimento = data_nascimento?.let(LocalDate::parse),
    nome = nome,
    raca = raca,
    aptidao = Aptidao.valueOf(aptidao),
    maeId = mae_id,
    paiId = pai_id,
    loteId = lote_id,
    reprodutor = reprodutor != 0L,
    status = StatusAnimal.valueOf(status),
    observacao = observacao
)
