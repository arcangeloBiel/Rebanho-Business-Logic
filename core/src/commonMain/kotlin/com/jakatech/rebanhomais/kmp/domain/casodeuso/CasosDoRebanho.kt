package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Aptidao
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeFazenda
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/*
 * Casos de uso do rebanho que dependem do banco local (PRD Fase 3, fatia 2).
 */

/**
 * HU-02: os animais da fazenda selecionada, atualizados sozinhos a cada cadastro (RF-03, O4).
 *
 * Usa a mesma regra de seleção da sessão (RN-07 da Fase 2). Sem fazenda selecionada,
 * ou depois de sair, a lista fica vazia (RN-01).
 */
class ObservarRebanho(
    private val animais: AnimalRepository,
    private val minhasFazendas: MinhasFazendasRepository,
    private val preferencias: PreferenciasRepository
) {
    /** Para uma busca que muda enquanto a tela está aberta (o texto digitado). */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(busca: Flow<String>): Flow<List<Animal>> {
        val fazendaId = combine(minhasFazendas.fazendas, preferencias.fazendaSelecionadaId) { fazendas, idGuardado ->
            RegrasDeFazenda.escolherSelecionada(fazendas, idGuardado)?.fazenda?.id
        }
        return combine(fazendaId, busca.map { it.trim() }) { id, texto -> id to texto }
            .distinctUntilChanged()
            .flatMapLatest { (id, texto) ->
                if (id == null) flowOf(emptyList()) else animais.observarPorFazenda(id, texto)
            }
    }

    operator fun invoke(busca: String = ""): Flow<List<Animal>> = invoke(flowOf(busca))
}

/**
 * O que pode ser corrigido num animal (RF-05). Brinco, sexo, origem, nascimento e genitores
 * ficam de fora: o brinco é a identificação do PNIB (RN-05, P-02).
 */
data class EdicaoDeAnimal(
    val animalId: String,
    val nome: String?,
    val raca: String?,
    val aptidao: Aptidao,
    val loteId: String?,
    val observacao: String?
)

/** HU-03: corrige os dados descritivos de um animal ativo (CA-03.1). Qualquer papel pode. */
class EditarAnimal(private val animais: AnimalRepository) {
    suspend operator fun invoke(edicao: EdicaoDeAnimal): Resultado<Animal> {
        val animal = when (val r = animais.animalAtivo(edicao.animalId)) {
            is Resultado.Falha -> return r
            is Resultado.Sucesso -> r.valor
        }
        val editado = animal.copy(
            nome = edicao.nome?.trim()?.ifEmpty { null },
            raca = edicao.raca?.trim()?.ifEmpty { null },
            aptidao = edicao.aptidao,
            loteId = edicao.loteId,
            observacao = edicao.observacao?.trim()?.ifEmpty { null }
        )
        // Sem mudança, nada é gravado: o registro não volta a ficar pendente à toa.
        if (editado != animal) animais.salvar(editado)
        return Resultado.Sucesso(editado)
    }
}

/**
 * HU-03: exclui um animal lançado por engano. Só o DONO da fazenda pode (RF-06, CA-03.2, RN-04).
 * O animal some das listas, mas continua no aparelho marcado como excluído (CA-03.3).
 */
class ExcluirAnimal(
    private val animais: AnimalRepository,
    private val minhasFazendas: MinhasFazendasRepository
) {
    suspend operator fun invoke(animalId: String): Resultado<Unit> {
        val animal = animais.buscarPorId(animalId) ?: return Resultado.Falha(ErroDominio.AnimalNaoEncontrado(animalId))
        val papel = minhasFazendas.fazendas.first().firstOrNull { it.fazenda.id == animal.fazendaId }?.papel
            ?: return Resultado.Falha(ErroDominio.FazendaNaoEncontrada(animal.fazendaId))
        if (!papel.podeExcluirRegistros) return Resultado.Falha(ErroDominio.SemPermissao)
        animais.excluir(animal.id)
        return Resultado.Sucesso(Unit)
    }
}

/** Um acontecimento na vida do animal, para a tela de histórico (RF-07). */
sealed interface EventoDoHistorico {
    val data: LocalDate

    /** Entrada (nascimento, compra) ou saída (venda, morte, transferência). */
    data class Movimentou(val movimentacao: Movimentacao) : EventoDoHistorico {
        override val data: LocalDate get() = movimentacao.data
    }
    // Pesagens e vacinas entram na fatia 3.
}

/** HU-01 e HU-05: o histórico do animal em ordem de data, do mais antigo ao mais recente (RF-07). */
class ObservarHistoricoDoAnimal(private val movimentacoes: MovimentacaoRepository) {
    operator fun invoke(animalId: String): Flow<List<EventoDoHistorico>> =
        movimentacoes.observarPorAnimal(animalId).map { lista ->
            lista.map { EventoDoHistorico.Movimentou(it) }.sortedBy { it.data }
        }
}
