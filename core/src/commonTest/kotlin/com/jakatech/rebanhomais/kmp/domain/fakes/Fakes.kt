package com.jakatech.rebanhomais.kmp.domain.fakes

import com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.EstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.FazendaDoUsuario
import com.jakatech.rebanhomais.kmp.domain.model.Papel
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.model.Membro
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.RegistroComErro
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoRecebimento
import com.jakatech.rebanhomais.kmp.domain.repositorio.AnimalRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.FazendaRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.LoteRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MinhasFazendasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.MovimentacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PesagemRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.SincronizacaoRepository
import com.jakatech.rebanhomais.kmp.domain.repositorio.VacinaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    val excluidos = mutableSetOf<String>()
    private val versao = MutableStateFlow(0)
    var gravacoes = 0
        private set

    private fun visiveis() = animais.values.filter { it.id !in excluidos }

    override suspend fun salvar(animal: Animal) { animais[animal.id] = animal; gravacoes++; versao.value++ }
    override suspend fun buscarPorId(animalId: String) = animais[animalId]?.takeIf { animalId !in excluidos }
    override suspend fun buscarPorBrinco(fazendaId: String, brinco: String) =
        animais.values.firstOrNull { it.fazendaId == fazendaId && it.brinco == brinco }
    override suspend fun listarPorFazenda(fazendaId: String) = visiveis().filter { it.fazendaId == fazendaId }
    override suspend fun contarFilhos(maeId: String) = visiveis().count { it.maeId == maeId }
    override fun observarPorFazenda(fazendaId: String, busca: String): Flow<List<Animal>> = versao.map {
        visiveis()
            .filter { it.fazendaId == fazendaId }
            .filter { busca.isEmpty() || it.brinco.contains(busca, true) || it.nome?.contains(busca, true) == true }
            .sortedWith(compareBy({ !it.ativo }, { it.brinco }))
    }
    override suspend fun excluir(animalId: String) { excluidos += animalId; versao.value++ }
    /** Ids que o servidor já aceitou (RN-12). */
    val noServidor = mutableSetOf<String>()
    override suspend fun jaEstaNoServidor(animalId: String) = animalId in noServidor
}

/** Sincronização de mentira: [online] decide se o envio esvazia os pendentes. */
class SincronizacaoEmMemoria(pendentes: Int = 0, comErro: Int = 0) : SincronizacaoRepository {
    val estadoAtual = MutableStateFlow(EstadoDaSincronizacao(pendentes, comErro, null, null, false))
    override val estado: Flow<EstadoDaSincronizacao> = estadoAtual
    var online = true
    var envios = 0
    var apagou = false

    override fun observarRegistrosComErro(): Flow<List<RegistroComErro>> = MutableStateFlow(emptyList())
    override suspend fun enviarPendentes(): Resultado<ResumoDoEnvio> {
        envios++
        if (!online) return Resultado.Falha(ErroDominio.SemConexao)
        val enviados = estadoAtual.value.pendentes
        estadoAtual.value = estadoAtual.value.copy(pendentes = 0)
        return Resultado.Sucesso(ResumoDoEnvio(enviados, 0, 0))
    }
    override suspend fun receberNovidades(): Resultado<ResumoDoRecebimento> = Resultado.Sucesso(ResumoDoRecebimento(0, 0, 0))
    override suspend fun sincronizar(): Resultado<ResumoDaSincronizacao> = when (val e = enviarPendentes()) {
        is Resultado.Falha -> e
        is Resultado.Sucesso -> Resultado.Sucesso(ResumoDaSincronizacao(e.valor, ResumoDoRecebimento(0, 0, 0)))
    }
    override suspend fun apagarDadosLocais() {
        apagou = true
        estadoAtual.value = EstadoDaSincronizacao(0, 0, null, null, false)
    }
}

class LotesEmMemoria : LoteRepository {
    val lotes = mutableMapOf<String, Lote>()
    private val versao = MutableStateFlow(0)
    var gravacoes = 0
        private set
    override suspend fun salvar(lote: Lote) { lotes[lote.id] = lote; gravacoes++; versao.value++ }
    override suspend fun buscarPorId(loteId: String) = lotes[loteId]
    override suspend fun listarPorFazenda(fazendaId: String) =
        lotes.values.filter { it.fazendaId == fazendaId }.sortedBy { it.nome.lowercase() }
    override fun observarPorFazenda(fazendaId: String): Flow<List<Lote>> = versao.map { listarPorFazenda(fazendaId) }
}

class PesagensEmMemoria : PesagemRepository {
    val pesagens = mutableListOf<Pesagem>()
    private val versao = MutableStateFlow(0)
    override suspend fun salvar(pesagem: Pesagem) {
        pesagens.removeAll { it.id == pesagem.id }
        pesagens += pesagem
        versao.value++
    }
    override suspend fun listarPorAnimal(animalId: String) =
        pesagens.filter { it.animalId == animalId }.sortedBy { it.data }
    override fun observarPorAnimal(animalId: String): Flow<List<Pesagem>> = versao.map { listarPorAnimal(animalId) }
}

class VacinasEmMemoria : VacinaRepository {
    val protocolos = mutableMapOf<String, ProtocoloSanitario>()
    val aplicacoes = mutableListOf<AplicacaoVacina>()
    private val versao = MutableStateFlow(0)
    private val ordem = compareBy<AplicacaoVacina>({ it.data }, { it.dose })

    override suspend fun salvarProtocolo(protocolo: ProtocoloSanitario) { protocolos[protocolo.id] = protocolo; versao.value++ }
    override suspend fun buscarProtocolo(protocoloId: String) = protocolos[protocoloId]
    override suspend fun listarProtocolos(fazendaId: String) =
        protocolos.values.filter { it.fazendaId == fazendaId }.sortedBy { it.nome.lowercase() }
    override suspend fun salvarAplicacao(aplicacao: AplicacaoVacina) {
        aplicacoes.removeAll { it.id == aplicacao.id }
        aplicacoes += aplicacao
        versao.value++
    }
    override suspend fun listarAplicacoesPorAnimal(animalId: String) =
        aplicacoes.filter { it.animalId == animalId }.sortedWith(ordem)
    override suspend fun listarAplicacoesPorFazenda(fazendaId: String) =
        aplicacoes.filter { it.fazendaId == fazendaId }.sortedWith(ordem)
    override fun observarProtocolos(fazendaId: String): Flow<List<ProtocoloSanitario>> =
        versao.map { listarProtocolos(fazendaId) }
    override fun observarAplicacoesPorAnimal(animalId: String): Flow<List<AplicacaoVacina>> =
        versao.map { listarAplicacoesPorAnimal(animalId) }
    override fun observarAplicacoesPorFazenda(fazendaId: String): Flow<List<AplicacaoVacina>> =
        versao.map { listarAplicacoesPorFazenda(fazendaId) }
}

class MovimentacoesEmMemoria : MovimentacaoRepository {
    val movimentacoes = mutableListOf<Movimentacao>()
    private val versao = MutableStateFlow(0)
    override suspend fun salvar(movimentacao: Movimentacao) {
        movimentacoes.removeAll { it.id == movimentacao.id }
        movimentacoes += movimentacao
        versao.value++
    }
    override suspend fun listarPorAnimal(animalId: String) = movimentacoes.filter { it.animalId == animalId }
    override fun observarPorAnimal(animalId: String): Flow<List<Movimentacao>> =
        versao.map { movimentacoes.filter { it.animalId == animalId }.sortedBy { it.data } }
}

fun <T> Resultado<T>.sucesso(): T = when (this) {
    is Resultado.Sucesso -> valor
    is Resultado.Falha -> fail("Esperava sucesso, veio $erro")
}

inline fun <reified E> Resultado<*>.falhaCom(): E {
    val falha = assertIs<Resultado.Falha>(this, "Esperava falha, veio $this")
    return assertIs<E>(falha.erro)
}
