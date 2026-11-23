package com.jakatech.rebanhomais.kmp.domain.casodeuso

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDeData
import com.jakatech.rebanhomais.kmp.domain.regra.RegrasDePeso
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import kotlinx.datetime.LocalDate

/** Busca um animal que precisa existir e estar ativo para receber um manejo. */
internal suspend fun AnimalRepository.animalAtivo(animalId: String): Resultado<Animal> {
    val animal = buscarPorId(animalId) ?: return Resultado.Falha(ErroDominio.AnimalNaoEncontrado(animalId))
    if (!animal.ativo) return Resultado.Falha(ErroDominio.AnimalInativo(animal.status))
    return Resultado.Sucesso(animal)
}

data class NovaPesagem(
    val animalId: String,
    val data: LocalDate,
    val pesoKg: Double,
    val observacao: String? = null
)

class RegistrarPesagem(
    private val animais: AnimalRepository,
    private val pesagens: PesagemRepository,
    private val ids: GeradorDeId,
    private val relogio: Relogio
) {
    suspend operator fun invoke(nova: NovaPesagem): Resultado<Pesagem> {
        val animal = when (val r = animais.animalAtivo(nova.animalId)) {
            is Resultado.Falha -> return r
            is Resultado.Sucesso -> r.valor
        }
        RegrasDePeso.validar(nova.pesoKg)?.let { return Resultado.Falha(it) }
        RegrasDeData.validarManejo(nova.data, relogio.hoje(), animal.dataNascimento)?.let { return Resultado.Falha(it) }

        val pesagem = Pesagem(
            id = ids.novo(),
            fazendaId = animal.fazendaId,
            animalId = animal.id,
            data = nova.data,
            pesoKg = nova.pesoKg,
            observacao = nova.observacao?.trim()?.ifEmpty { null }
        )
        pesagens.salvar(pesagem)
        return Resultado.Sucesso(pesagem)
    }
}

data class ResumoDePeso(
    val ultimaPesagem: Pesagem?,
    /** kg/dia entre a primeira e a última pesagem. */
    val ganhoMedioDiario: Double?,
    /** kg/dia entre as duas últimas pesagens. */
    val ganhoRecente: Double?,
    val totalDePesagens: Int
)

class ObterResumoDePeso(
    private val pesagens: PesagemRepository
) {
    suspend operator fun invoke(animalId: String): ResumoDePeso {
        val lista = pesagens.listarPorAnimal(animalId)
        return ResumoDePeso(
            ultimaPesagem = lista.maxByOrNull { it.data },
            ganhoMedioDiario = RegrasDePeso.ganhoMedioDiario(lista),
            ganhoRecente = RegrasDePeso.ganhoRecente(lista),
            totalDePesagens = lista.size
        )
    }
}
