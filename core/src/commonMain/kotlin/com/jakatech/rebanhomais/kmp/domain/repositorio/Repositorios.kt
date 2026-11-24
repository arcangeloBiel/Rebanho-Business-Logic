package com.jakatech.rebanhomais.kmp.domain.repositorio

import com.jakatech.rebanhomais.kmp.domain.model.Animal
import com.jakatech.rebanhomais.kmp.domain.model.AplicacaoVacina
import com.jakatech.rebanhomais.kmp.domain.model.Fazenda
import com.jakatech.rebanhomais.kmp.domain.model.Lote
import com.jakatech.rebanhomais.kmp.domain.model.Membro
import com.jakatech.rebanhomais.kmp.domain.model.Movimentacao
import com.jakatech.rebanhomais.kmp.domain.model.Pesagem
import com.jakatech.rebanhomais.kmp.domain.model.ProtocoloSanitario
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
    suspend fun listarPorFazenda(fazendaId: String): List<Lote>
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
    suspend fun listarPorAnimal(animalId: String): List<Pesagem>
}

interface VacinaRepository {
    suspend fun salvarProtocolo(protocolo: ProtocoloSanitario)
    suspend fun buscarProtocolo(protocoloId: String): ProtocoloSanitario?
    suspend fun listarProtocolos(fazendaId: String): List<ProtocoloSanitario>
    suspend fun salvarAplicacao(aplicacao: AplicacaoVacina)
    suspend fun listarAplicacoesPorAnimal(animalId: String): List<AplicacaoVacina>
    suspend fun listarAplicacoesPorFazenda(fazendaId: String): List<AplicacaoVacina>
}

interface MovimentacaoRepository {
    suspend fun salvar(movimentacao: Movimentacao)
    suspend fun listarPorAnimal(animalId: String): List<Movimentacao>

    /** Movimentações do animal em ordem de data, atualizadas a cada mudança. */
    fun observarPorAnimal(animalId: String): Flow<List<Movimentacao>>
}
