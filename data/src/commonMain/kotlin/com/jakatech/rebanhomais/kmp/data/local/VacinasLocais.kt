package com.jakatech.rebanhomais.kmp.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.CoroutineContext

/** Protocolos sanitários e aplicações de vacina no banco do aparelho (PRD Fase 3, RF-01, RF-08, HU-04, HU-06). */
class VacinasLocais(
    banco: RebanhoDatabase,
    private val contexto: CoroutineContext = Dispatchers.Default,
    private val agora: () -> String = ::instanteAtual
) : VacinaRepository {
    private val protocolos = banco.protocoloSanitarioQueries
    private val aplicacoes = banco.aplicacaoVacinaQueries

    override suspend fun salvarProtocolo(protocolo: ProtocoloSanitario) = withContext(contexto) {
        val instante = agora()
        val q = protocolos
        q.transaction {
            with(protocolo) {
                val dosesL = doses.toLong()
                val intervalo = intervaloEntreDosesDias?.toLong()
                val reforco = reforcoDias?.toLong()
                val sexo = sexoAlvo?.name
                val minima = idadeMinimaMeses?.toLong()
                val maxima = idadeMaximaMeses?.toLong()
                if (q.existe(id).executeAsOne() > 0) {
                    q.atualizar(
                        fazenda_id = fazendaId, nome = nome, doses = dosesL, intervalo_entre_doses_dias = intervalo,
                        reforco_dias = reforco, sexo_alvo = sexo, idade_minima_meses = minima,
                        idade_maxima_meses = maxima, agora = instante, id = id
                    )
                } else {
                    q.inserir(
                        id = id, fazenda_id = fazendaId, nome = nome, doses = dosesL, intervalo_entre_doses_dias = intervalo,
                        reforco_dias = reforco, sexo_alvo = sexo, idade_minima_meses = minima,
                        idade_maxima_meses = maxima, agora = instante
                    )
                }
            }
        }
    }

    override suspend fun buscarProtocolo(protocoloId: String): ProtocoloSanitario? = withContext(contexto) {
        protocolos.buscarPorId(protocoloId, ::protocolo).executeAsOneOrNull()
    }

    override suspend fun listarProtocolos(fazendaId: String): List<ProtocoloSanitario> = withContext(contexto) {
        protocolos.listarPorFazenda(fazendaId, ::protocolo).executeAsList()
    }

    override fun observarProtocolos(fazendaId: String): Flow<List<ProtocoloSanitario>> =
        protocolos.listarPorFazenda(fazendaId, ::protocolo).asFlow().mapToList(contexto)

    override suspend fun salvarAplicacao(aplicacao: AplicacaoVacina) = withContext(contexto) {
        val instante = agora()
        val q = aplicacoes
        q.transaction {
            with(aplicacao) {
                if (q.existe(id).executeAsOne() > 0) {
                    q.atualizar(
                        fazenda_id = fazendaId, animal_id = animalId, protocolo_id = protocoloId, nome_vacina = nomeVacina,
                        data = data.toString(), dose = dose.toLong(), lote_produto = loteProduto,
                        observacao = observacao, agora = instante, id = id
                    )
                } else {
                    q.inserir(
                        id = id, fazenda_id = fazendaId, animal_id = animalId, protocolo_id = protocoloId,
                        nome_vacina = nomeVacina, data = data.toString(), dose = dose.toLong(),
                        lote_produto = loteProduto, observacao = observacao, agora = instante
                    )
                }
            }
        }
    }

    override suspend fun listarAplicacoesPorAnimal(animalId: String): List<AplicacaoVacina> = withContext(contexto) {
        aplicacoes.listarPorAnimal(animalId, ::aplicacao).executeAsList()
    }

    override suspend fun listarAplicacoesPorFazenda(fazendaId: String): List<AplicacaoVacina> = withContext(contexto) {
        aplicacoes.listarPorFazenda(fazendaId, ::aplicacao).executeAsList()
    }

    override fun observarAplicacoesPorAnimal(animalId: String): Flow<List<AplicacaoVacina>> =
        aplicacoes.listarPorAnimal(animalId, ::aplicacao).asFlow().mapToList(contexto)

    override fun observarAplicacoesPorFazenda(fazendaId: String): Flow<List<AplicacaoVacina>> =
        aplicacoes.listarPorFazenda(fazendaId, ::aplicacao).asFlow().mapToList(contexto)
}

/** Linha de protocolos_sanitarios → domínio, na ordem das colunas da tabela (ProtocoloSanitario.sq). */
@Suppress("UNUSED_PARAMETER")
private fun protocolo(
    id: String,
    fazendaId: String,
    nome: String,
    doses: Long,
    intervaloEntreDosesDias: Long?,
    reforcoDias: Long?,
    sexoAlvo: String?,
    idadeMinimaMeses: Long?,
    idadeMaximaMeses: Long?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    syncStatus: String
) = ProtocoloSanitario(
    id = id,
    fazendaId = fazendaId,
    nome = nome,
    doses = doses.toInt(),
    intervaloEntreDosesDias = intervaloEntreDosesDias?.toInt(),
    reforcoDias = reforcoDias?.toInt(),
    sexoAlvo = sexoAlvo?.let(Sexo::valueOf),
    idadeMinimaMeses = idadeMinimaMeses?.toInt(),
    idadeMaximaMeses = idadeMaximaMeses?.toInt()
)

/** Linha de aplicacoes_vacina → domínio, na ordem das colunas da tabela (AplicacaoVacina.sq). */
@Suppress("UNUSED_PARAMETER")
private fun aplicacao(
    id: String,
    fazendaId: String,
    animalId: String,
    protocoloId: String?,
    nomeVacina: String,
    data: String,
    dose: Long,
    loteProduto: String?,
    observacao: String?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    syncStatus: String
) = AplicacaoVacina(
    id = id,
    fazendaId = fazendaId,
    animalId = animalId,
    protocoloId = protocoloId,
    nomeVacina = nomeVacina,
    data = LocalDate.parse(data),
    dose = dose.toInt(),
    loteProduto = loteProduto,
    observacao = observacao
)
