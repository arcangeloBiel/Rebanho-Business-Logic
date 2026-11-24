package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.MotivoGenitorInvalido
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Aptidao
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Origem
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.model.TipoMovimentacao
import com.jakatech.rebanhomais.kmp.domain.regra.CategoriaAnimal
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeBrinco
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeCategoria
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeData
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.LoteRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import kotlinx.datetime.LocalDate

data class NovoAnimal(
    val fazendaId: String,
    val brinco: String,
    val sexo: Sexo,
    val origem: Origem,
    /** Obrigatória para animais nascidos na fazenda. */
    val dataNascimento: LocalDate? = null,
    /** Data em que o animal comprado chegou. Obrigatória para [Origem.COMPRADO]. */
    val dataEntrada: LocalDate? = null,
    /** Vendedor, para animais comprados. */
    val procedencia: String? = null,
    val nome: String? = null,
    val raca: String? = null,
    val aptidao: Aptidao = Aptidao.MISTA,
    val maeId: String? = null,
    val paiId: String? = null,
    val loteId: String? = null,
    val reprodutor: Boolean = false,
    val observacao: String? = null
)

/**
 * Cadastra um animal e registra a entrada dele (nascimento ou compra),
 * que é o primeiro evento do histórico de rastreabilidade.
 */
class CadastrarAnimal(
    private val animais: AnimalRepository,
    private val movimentacoes: MovimentacaoRepository,
    private val lotes: LoteRepository,
    private val ids: GeradorDeId,
    private val relogio: Relogio
) {
    suspend operator fun invoke(novo: NovoAnimal): Resultado<Animal> {
        val hoje = relogio.hoje()
        val brinco = RegrasDeBrinco.normalizar(novo.brinco)
        RegrasDeBrinco.validar(brinco)?.let { return Resultado.Falha(it) }

        val nascimento = novo.dataNascimento
        if (nascimento != null && nascimento > hoje) return Resultado.Falha(ErroDominio.DataNoFuturo)

        val dataEntrada: LocalDate = when (novo.origem) {
            Origem.NASCIDO_NA_FAZENDA -> nascimento ?: return Resultado.Falha(ErroDominio.DataNascimentoObrigatoria)
            Origem.COMPRADO -> {
                val entrada = novo.dataEntrada ?: return Resultado.Falha(ErroDominio.DataEntradaObrigatoria)
                RegrasDeData.validarManejo(entrada, hoje, nascimento)?.let { return Resultado.Falha(it) }
                entrada
            }
        }

        if (animais.buscarPorBrinco(novo.fazendaId, brinco) != null) {
            return Resultado.Falha(ErroDominio.BrincoDuplicado(brinco))
        }

        validarGenitor(novo.maeId, Sexo.FEMEA, novo)?.let { return Resultado.Falha(ErroDominio.GenitorInvalido(it)) }
        validarGenitor(novo.paiId, Sexo.MACHO, novo)?.let { return Resultado.Falha(ErroDominio.GenitorInvalido(it)) }
        lotes.validarDaFazenda(novo.loteId, novo.fazendaId)?.let { return Resultado.Falha(it) }

        val animal = Animal(
            id = ids.novo(),
            fazendaId = novo.fazendaId,
            brinco = brinco,
            sexo = novo.sexo,
            origem = novo.origem,
            dataNascimento = nascimento,
            nome = novo.nome?.trim()?.ifEmpty { null },
            raca = novo.raca?.trim()?.ifEmpty { null },
            aptidao = novo.aptidao,
            maeId = novo.maeId,
            paiId = novo.paiId,
            loteId = novo.loteId,
            reprodutor = novo.reprodutor && novo.sexo == Sexo.MACHO,
            observacao = novo.observacao?.trim()?.ifEmpty { null }
        )
        animais.salvar(animal)
        movimentacoes.salvar(
            Movimentacao(
                id = ids.novo(),
                fazendaId = animal.fazendaId,
                animalId = animal.id,
                tipo = if (novo.origem == Origem.COMPRADO) TipoMovimentacao.COMPRA else TipoMovimentacao.NASCIMENTO,
                data = dataEntrada,
                contraparte = novo.procedencia?.trim()?.ifEmpty { null }
            )
        )
        return Resultado.Sucesso(animal)
    }

    private suspend fun validarGenitor(genitorId: String?, sexoEsperado: Sexo, filho: NovoAnimal): MotivoGenitorInvalido? {
        if (genitorId == null) return null
        val genitor = animais.buscarPorId(genitorId) ?: return MotivoGenitorInvalido.NAO_ENCONTRADO
        if (genitor.fazendaId != filho.fazendaId) return MotivoGenitorInvalido.OUTRA_FAZENDA
        if (genitor.sexo != sexoEsperado) {
            return if (sexoEsperado == Sexo.FEMEA) MotivoGenitorInvalido.MAE_NAO_E_FEMEA else MotivoGenitorInvalido.PAI_NAO_E_MACHO
        }
        val nascGenitor = genitor.dataNascimento
        val nascFilho = filho.dataNascimento
        if (nascGenitor != null && nascFilho != null && nascGenitor >= nascFilho) {
            return MotivoGenitorInvalido.NASCEU_DEPOIS_DO_FILHO
        }
        return null
    }
}

/** Categoria atual do animal (bezerro, novilha, vaca…), calculada na hora. */
class ObterCategoria(
    private val animais: AnimalRepository,
    private val relogio: Relogio
) {
    suspend operator fun invoke(animal: Animal): CategoriaAnimal {
        val jaPariu = animal.sexo == Sexo.FEMEA && animais.contarFilhos(animal.id) > 0
        return RegrasDeCategoria.calcular(
            sexo = animal.sexo,
            dataNascimento = animal.dataNascimento,
            hoje = relogio.hoje(),
            jaPariu = jaPariu,
            reprodutor = animal.reprodutor
        )
    }
}

data class NovaSaida(
    val animalId: String,
    /** VENDA, MORTE ou TRANSFERENCIA. */
    val tipo: TipoMovimentacao,
    val data: LocalDate,
    val contraparte: String? = null,
    val valor: Double? = null,
    val observacao: String? = null
)

/** Registra a saída do animal (venda, morte ou transferência) e atualiza o status dele. */
class RegistrarSaida(
    private val animais: AnimalRepository,
    private val movimentacoes: MovimentacaoRepository,
    private val ids: GeradorDeId,
    private val relogio: Relogio
) {
    suspend operator fun invoke(saida: NovaSaida): Resultado<Movimentacao> {
        val novoStatus = saida.tipo.statusResultante
        if (saida.tipo.entrada || novoStatus == null) return Resultado.Falha(ErroDominio.TipoDeSaidaInvalido)

        val animal = animais.buscarPorId(saida.animalId)
            ?: return Resultado.Falha(ErroDominio.AnimalNaoEncontrado(saida.animalId))
        if (!animal.ativo) return Resultado.Falha(ErroDominio.AnimalInativo(animal.status))
        RegrasDeData.validarManejo(saida.data, relogio.hoje(), animal.dataNascimento)?.let { return Resultado.Falha(it) }

        val movimentacao = Movimentacao(
            id = ids.novo(),
            fazendaId = animal.fazendaId,
            animalId = animal.id,
            tipo = saida.tipo,
            data = saida.data,
            contraparte = saida.contraparte?.trim()?.ifEmpty { null },
            valor = saida.valor,
            observacao = saida.observacao?.trim()?.ifEmpty { null }
        )
        movimentacoes.salvar(movimentacao)
        animais.salvar(animal.copy(status = novoStatus))
        return Resultado.Sucesso(movimentacao)
    }
}
