package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Aptidao
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeFazenda
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
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
 * Casos de uso do rebanho que dependem do banco local (PRD Fase 3, fatias 2 e 3).
 */

/**
 * Id da fazenda selecionada, pela mesma regra da sessão (RN-07 da Fase 2).
 * Nulo sem fazendas ou depois de sair: as consultas do rebanho ficam vazias (RN-01).
 */
internal fun fazendaSelecionadaId(
    minhasFazendas: MinhasFazendasRepository,
    preferencias: PreferenciasRepository
): Flow<String?> =
    combine(minhasFazendas.fazendas, preferencias.fazendaSelecionadaId) { fazendas, idGuardado ->
        RegrasDeFazenda.escolherSelecionada(fazendas, idGuardado)?.fazenda?.id
    }.distinctUntilChanged()

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
        val fazendaId = fazendaSelecionadaId(minhasFazendas, preferencias)
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

    data class Pesou(val pesagem: Pesagem) : EventoDoHistorico {
        override val data: LocalDate get() = pesagem.data
    }

    data class Vacinou(val aplicacao: AplicacaoVacina) : EventoDoHistorico {
        override val data: LocalDate get() = aplicacao.data
    }
}

/**
 * HU-01, HU-04 e HU-05: o histórico do animal (movimentações, pesagens e vacinas) em ordem de data,
 * do mais antigo ao mais recente (RF-07). No mesmo dia, a entrada vem antes dos manejos.
 */
class ObservarHistoricoDoAnimal(
    private val movimentacoes: MovimentacaoRepository,
    private val pesagens: PesagemRepository,
    private val vacinas: VacinaRepository
) {
    operator fun invoke(animalId: String): Flow<List<EventoDoHistorico>> =
        combine(
            movimentacoes.observarPorAnimal(animalId),
            pesagens.observarPorAnimal(animalId),
            vacinas.observarAplicacoesPorAnimal(animalId)
        ) { movs, pesos, aplicacoes ->
            // sortedBy é estável: no mesmo dia, mantém movimentação → pesagem → vacina.
            (movs.map { EventoDoHistorico.Movimentou(it) } +
                pesos.map { EventoDoHistorico.Pesou(it) } +
                aplicacoes.map { EventoDoHistorico.Vacinou(it) })
                .sortedBy { it.data }
        }
}
