package com.jakatech.rebanhomais.kmp.domain.repositorio

import com.jakatech.rebanhomais.kmp.domain.comum.Resultado
import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.EstadoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.model.Membro
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
import com.jakatech.rebanhomais.kmp.domain.model.RegistroComErro
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDaSincronizacao
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoEnvio
import com.jakatech.rebanhomais.kmp.domain.model.ResumoDoRecebimento
import kotlinx.coroutines.flow.Flow

/*
 * Contratos que a camada de dados implementa (Fases 2 e 3: Supabase + banco local).
 * O domínio só conhece estas interfaces, nunca a tecnologia por trás.
 * "salvar" cria ou atualiza pelo id.
 *
 * Registros excluídos (RN-03 da Fase 3) continuam guardados, mas nenhuma busca ou lista os devolve,
 * exceto [AnimalRepository.buscarPorBrinco], porque o brinco segue ocupado na fazenda.
 */

interface FazendaRepository {
    suspend fun salvar(fazenda: Fazenda)
    suspend fun buscarPorId(fazendaId: String): Fazenda?
    suspend fun listarDoUsuario(usuarioId: String): List<Fazenda>
    suspend fun salvarMembro(membro: Membro)
    suspend fun listarMembros(fazendaId: String): List<Membro>
}

interface LoteRepository {
    suspend fun salvar(lote: Lote)
    suspend fun buscarPorId(loteId: String): Lote?
    /** Lotes da fazenda em ordem de nome. */
    suspend fun listarPorFazenda(fazendaId: String): List<Lote>

    /** Lotes da fazenda em ordem de nome, atualizados a cada mudança (PRD Fase 3, O4). */
    fun observarPorFazenda(fazendaId: String): Flow<List<Lote>>
}

interface AnimalRepository {
    suspend fun salvar(animal: Animal)
    suspend fun buscarPorId(animalId: String): Animal?
    /** Busca pelo brinco já normalizado, entre todos os animais da fazenda (ativos, vendidos e excluídos). */
    suspend fun buscarPorBrinco(fazendaId: String, brinco: String): Animal?
    suspend fun listarPorFazenda(fazendaId: String): List<Animal>

    /**
     * Animais da fazenda, atualizados a cada mudança (PRD Fase 3, RF-03): ativos primeiro, depois pelo brinco.
     * [busca] filtra por parte do brinco ou do nome, sem diferença de maiúsculas; vazia traz todos.
     */
    fun observarPorFazenda(fazendaId: String, busca: String = ""): Flow<List<Animal>>

    /** Marca o animal como excluído (RN-03). Nada é apagado de fato, para o servidor ficar sabendo. */
    suspend fun excluir(animalId: String)

    /** Quantos animais têm este animal como mãe. Usado para saber se a fêmea já pariu. */
    suspend fun contarFilhos(maeId: String): Int
}

interface PesagemRepository {
    suspend fun salvar(pesagem: Pesagem)
    /** Pesagens do animal em ordem de data. */
    suspend fun listarPorAnimal(animalId: String): List<Pesagem>

    /** Pesagens do animal em ordem de data, atualizadas a cada mudança (PRD Fase 3, O4). */
    fun observarPorAnimal(animalId: String): Flow<List<Pesagem>>
}

interface VacinaRepository {
    suspend fun salvarProtocolo(protocolo: ProtocoloSanitario)
    suspend fun buscarProtocolo(protocoloId: String): ProtocoloSanitario?
    /** Protocolos da fazenda em ordem de nome. */
    suspend fun listarProtocolos(fazendaId: String): List<ProtocoloSanitario>
    suspend fun salvarAplicacao(aplicacao: AplicacaoVacina)
    /** Aplicações do animal em ordem de data e dose. */
    suspend fun listarAplicacoesPorAnimal(animalId: String): List<AplicacaoVacina>
    /** Aplicações da fazenda em ordem de data e dose. */
    suspend fun listarAplicacoesPorFazenda(fazendaId: String): List<AplicacaoVacina>

    // Versões observáveis, atualizadas a cada mudança (PRD Fase 3, O4).
    fun observarProtocolos(fazendaId: String): Flow<List<ProtocoloSanitario>>
    fun observarAplicacoesPorAnimal(animalId: String): Flow<List<AplicacaoVacina>>
    fun observarAplicacoesPorFazenda(fazendaId: String): Flow<List<AplicacaoVacina>>
}

interface MovimentacaoRepository {
    suspend fun salvar(movimentacao: Movimentacao)
    suspend fun listarPorAnimal(animalId: String): List<Movimentacao>

    /** Movimentações do animal em ordem de data, atualizadas a cada mudança. */
    fun observarPorAnimal(animalId: String): Flow<List<Movimentacao>>
}

/** Sincronização do banco do aparelho com o servidor (PRD Fase 4). */
interface SincronizacaoRepository {
    /** Pendentes, com erro, último envio e se está sincronizando, atualizado a cada mudança (RF-07). */
    val estado: Flow<EstadoDaSincronizacao>

    /** Registros recusados pelo servidor, com o motivo (CA-04.3). */
    fun observarRegistrosComErro(): Flow<List<RegistroComErro>>

    /**
     * Envia todos os pendentes (RF-01 a RF-03). Recusas não interrompem o envio: ficam com erro.
     * Falha com [com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio.SemConexao] ou
     * [com.jakatech.rebanhomais.kmp.domain.comum.ErroDominio.SemSessao] quando para no meio;
     * o que já foi aceito fica marcado e o resto segue pendente.
     */
    suspend fun enviarPendentes(): Resultado<ResumoDoEnvio>

    /**
     * Traz do servidor o que mudou nas fazendas do usuário desde o último recebimento, inclusive
     * exclusões (RF-04); num aparelho vazio, a carga inicial completa, retomável (RF-05).
     * Apaga do aparelho as fazendas das quais o usuário saiu (RF-12).
     */
    suspend fun receberNovidades(): Resultado<ResumoDoRecebimento>

    /** Envia os pendentes e, se der certo, recebe as novidades. */
    suspend fun sincronizar(): Resultado<ResumoDaSincronizacao>
}
