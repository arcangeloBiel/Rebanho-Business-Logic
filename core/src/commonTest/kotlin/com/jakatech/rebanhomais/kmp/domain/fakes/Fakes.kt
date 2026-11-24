package com.jakatech.rebanhomais.kmp.domain.fakes

import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.Membro
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.FazendaRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.assertIs
import kotlin.test.fail

val HOJE = LocalDate(2026, 11, 23)

class RelogioFixo(var data: LocalDate = HOJE) : Relogio {
    override fun hoje() = data
}

class IdsSequenciais : GeradorDeId {
    private var n = 0
    override fun novo() = "id-${++n}"
}

class FazendasEmMemoria : FazendaRepository {
    val fazendas = mutableMapOf<String, Fazenda>()
    val membros = mutableListOf<Membro>()
    override suspend fun salvar(fazenda: Fazenda) { fazendas[fazenda.id] = fazenda }
    override suspend fun buscarPorId(fazendaId: String) = fazendas[fazendaId]
    override suspend fun listarDoUsuario(usuarioId: String) =
        membros.filter { it.usuarioId == usuarioId }.mapNotNull { fazendas[it.fazendaId] }
    override suspend fun salvarMembro(membro: Membro) { membros += membro }
    override suspend fun listarMembros(fazendaId: String) = membros.filter { it.fazendaId == fazendaId }
}

class MinhasFazendasEmMemoria : MinhasFazendasRepository {
    override val fazendas = MutableStateFlow<List<FazendaDoUsuario>>(emptyList())
    var falhaAoCriar: Resultado<FazendaDoUsuario>? = null
    override suspend fun atualizar(): Resultado<List<FazendaDoUsuario>> = Resultado.Sucesso(fazendas.value)
    override suspend fun criar(fazenda: Fazenda): Resultado<FazendaDoUsuario> {
        falhaAoCriar?.let { return it }
        val item = FazendaDoUsuario(fazenda, Papel.DONO)
        fazendas.value = fazendas.value + item
        return Resultado.Sucesso(item)
    }
    override suspend fun limpar() { fazendas.value = emptyList() }
}

class PreferenciasEmMemoria : PreferenciasRepository {
    override val fazendaSelecionadaId = MutableStateFlow<String?>(null)
    override suspend fun selecionarFazenda(fazendaId: String?) { fazendaSelecionadaId.value = fazendaId }
}

class AnimaisEmMemoria : AnimalRepository {
    val animais = mutableMapOf<String, Animal>()
    override suspend fun salvar(animal: Animal) { animais[animal.id] = animal }
    override suspend fun buscarPorId(animalId: String) = animais[animalId]
    override suspend fun buscarPorBrinco(fazendaId: String, brinco: String) =
        animais.values.firstOrNull { it.fazendaId == fazendaId && it.brinco == brinco }
    override suspend fun listarPorFazenda(fazendaId: String) = animais.values.filter { it.fazendaId == fazendaId }
    override suspend fun contarFilhos(maeId: String) = animais.values.count { it.maeId == maeId }
}

class PesagensEmMemoria : PesagemRepository {
    val pesagens = mutableListOf<Pesagem>()
    override suspend fun salvar(pesagem: Pesagem) { pesagens += pesagem }
    override suspend fun listarPorAnimal(animalId: String) = pesagens.filter { it.animalId == animalId }
}

class VacinasEmMemoria : VacinaRepository {
    val protocolos = mutableMapOf<String, ProtocoloSanitario>()
    val aplicacoes = mutableListOf<AplicacaoVacina>()
    override suspend fun salvarProtocolo(protocolo: ProtocoloSanitario) { protocolos[protocolo.id] = protocolo }
    override suspend fun buscarProtocolo(protocoloId: String) = protocolos[protocoloId]
    override suspend fun listarProtocolos(fazendaId: String) = protocolos.values.filter { it.fazendaId == fazendaId }
    override suspend fun salvarAplicacao(aplicacao: AplicacaoVacina) { aplicacoes += aplicacao }
    override suspend fun listarAplicacoesPorAnimal(animalId: String) = aplicacoes.filter { it.animalId == animalId }
    override suspend fun listarAplicacoesPorFazenda(fazendaId: String) = aplicacoes.filter { it.fazendaId == fazendaId }
}

class MovimentacoesEmMemoria : MovimentacaoRepository {
    val movimentacoes = mutableListOf<Movimentacao>()
    override suspend fun salvar(movimentacao: Movimentacao) { movimentacoes += movimentacao }
    override suspend fun listarPorAnimal(animalId: String) = movimentacoes.filter { it.animalId == animalId }
}

fun <T> Resultado<T>.sucesso(): T = when (this) {
    is Resultado.Sucesso -> valor
    is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
}

inline fun <reified E> Resultado<*>.falhaCom(): E {
    val falha = assertIs<Resultado.Falha>(this, "Esperava falha, veio $this")
    return assertIs<E>(falha.erro)
}
