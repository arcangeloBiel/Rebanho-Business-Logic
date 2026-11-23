package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.Sexo
import com.jakatech.rebanhomais.kmp.domain.regra.CalendarioSanitario
import com.jakatech.rebanhomais.kmp.domain.regra.PendenciaVacinal
import com.jakatech.rebanhomais.kmp.domain.regra.ProximaAplicacao
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeData
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeProtocolo
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
import kotlinx.datetime.LocalDate

data class NovoProtocolo(
    val fazendaId: String,
    val nome: String,
    val doses: Int = 1,
    val intervaloEntreDosesDias: Int? = null,
    val reforcoDias: Int? = null,
    val sexoAlvo: Sexo? = null,
    val idadeMinimaMeses: Int? = null,
    val idadeMaximaMeses: Int? = null
)

class CadastrarProtocolo(
    private val vacinas: VacinaRepository,
    private val ids: GeradorDeId
) {
    suspend operator fun invoke(novo: NovoProtocolo): Resultado<ProtocoloSanitario> {
        val protocolo = ProtocoloSanitario(
            id = ids.novo(),
            fazendaId = novo.fazendaId,
            nome = novo.nome.trim(),
            doses = novo.doses,
            intervaloEntreDosesDias = novo.intervaloEntreDosesDias,
            reforcoDias = novo.reforcoDias,
            sexoAlvo = novo.sexoAlvo,
            idadeMinimaMeses = novo.idadeMinimaMeses,
            idadeMaximaMeses = novo.idadeMaximaMeses
        )
        RegrasDeProtocolo.validar(protocolo)?.let { return Resultado.Falha(it) }
        vacinas.salvarProtocolo(protocolo)
        return Resultado.Sucesso(protocolo)
    }
}

data class NovaVacinacao(
    val animalId: String,
    val data: LocalDate,
    /** Protocolo da fazenda. Se nulo, informe [nomeVacina] (aplicação avulsa). */
    val protocoloId: String? = null,
    val nomeVacina: String? = null,
    /** Se nulo, o app deduz a dose pelo histórico do animal. */
    val dose: Int? = null,
    val loteProduto: String? = null,
    val observacao: String? = null
)

data class VacinacaoRegistrada(
    val aplicacao: AplicacaoVacina,
    /** Quando aplicar de novo. Nulo para avulsas ou esquema encerrado sem reforço. */
    val proxima: ProximaAplicacao?
)

class RegistrarVacinacao(
    private val animais: AnimalRepository,
    private val vacinas: VacinaRepository,
    private val ids: GeradorDeId,
    private val relogio: Relogio
) {
    suspend operator fun invoke(nova: NovaVacinacao): Resultado<VacinacaoRegistrada> {
        val animal = when (val r = animais.animalAtivo(nova.animalId)) {
            is Resultado.Falha -> return r
            is Resultado.Sucesso -> r.valor
        }
        RegrasDeData.validarManejo(nova.data, relogio.hoje(), animal.dataNascimento)?.let { return Resultado.Falha(it) }

        val protocolo = nova.protocoloId?.let {
            vacinas.buscarProtocolo(it) ?: return Resultado.Falha(ErroDominio.ProtocoloNaoEncontrado(it))
        }

        val nome = protocolo?.nome ?: nova.nomeVacina?.trim()?.ifEmpty { null }
            ?: return Resultado.Falha(ErroDominio.NomeObrigatorio("nomeVacina"))

        val dose = if (protocolo == null) {
            nova.dose ?: 1
        } else {
            val dosesAnteriores = vacinas.listarAplicacoesPorAnimal(animal.id)
                .filter { it.protocoloId == protocolo.id && it.data <= nova.data }
            val ultimaDose = dosesAnteriores.maxOfOrNull { it.dose } ?: 0
            nova.dose ?: (ultimaDose + 1).coerceAtMost(protocolo.doses)
        }
        val maximo = protocolo?.doses ?: Int.MAX_VALUE
        if (dose !in 1..maximo) return Resultado.Falha(ErroDominio.DoseInvalida(dose, protocolo?.doses ?: 0))

        val aplicacao = AplicacaoVacina(
            id = ids.novo(),
            fazendaId = animal.fazendaId,
            animalId = animal.id,
            protocoloId = protocolo?.id,
            nomeVacina = nome,
            data = nova.data,
            dose = dose,
            loteProduto = nova.loteProduto?.trim()?.ifEmpty { null },
            observacao = nova.observacao?.trim()?.ifEmpty { null }
        )
        vacinas.salvarAplicacao(aplicacao)
        val proxima = protocolo?.let { CalendarioSanitario.proximaAplicacao(it, aplicacao) }
        return Resultado.Sucesso(VacinacaoRegistrada(aplicacao, proxima))
    }
}

/** O que vence nos próximos [horizonteDias] dias na fazenda, incluindo o que já está atrasado. */
class ListarPendenciasVacinais(
    private val animais: AnimalRepository,
    private val vacinas: VacinaRepository,
    private val relogio: Relogio
) {
    suspend operator fun invoke(fazendaId: String, horizonteDias: Int = 7): List<PendenciaVacinal> =
        CalendarioSanitario.pendencias(
            animais = animais.listarPorFazenda(fazendaId),
            protocolos = vacinas.listarProtocolos(fazendaId),
            aplicacoes = vacinas.listarAplicacoesPorFazenda(fazendaId),
            hoje = relogio.hoje(),
            horizonteDias = horizonteDias
        )
}
