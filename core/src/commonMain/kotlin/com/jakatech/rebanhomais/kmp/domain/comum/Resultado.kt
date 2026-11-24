package com.jakatech.rebanhomais.kmp.domain.comum

import com.jakatech.rebanhomais.kmp.domain.model.StatusAnimal

/** Resultado de um caso de uso: ou deu certo com um valor, ou falhou com um erro de negócio conhecido. */
sealed interface Resultado<out T> {
    data class Sucesso<out T>(val valor: T) : Resultado<T>
    data class Falha(val erro: ErroDominio) : Resultado<Nothing>
}

/** Erros de regra de negócio. A UI decide a mensagem; o domínio só diz o que aconteceu. */
sealed interface ErroDominio {
    data class NomeObrigatorio(val campo: String) : ErroDominio
    data class BrincoInvalido(val motivo: MotivoBrincoInvalido) : ErroDominio
    data class BrincoDuplicado(val brinco: String) : ErroDominio
    data object DataNoFuturo : ErroDominio
    data object DataAntesDoNascimento : ErroDominio
    data object DataNascimentoObrigatoria : ErroDominio
    data object DataEntradaObrigatoria : ErroDominio
    data class PesoInvalido(val pesoKg: Double) : ErroDominio
    data class AnimalNaoEncontrado(val animalId: String) : ErroDominio
    data class AnimalInativo(val status: StatusAnimal) : ErroDominio
    data class AnimalDeOutraFazenda(val animalId: String) : ErroDominio
    data class GenitorInvalido(val motivo: MotivoGenitorInvalido) : ErroDominio
    data class ProtocoloInvalido(val motivo: MotivoProtocoloInvalido) : ErroDominio
    data class ProtocoloNaoEncontrado(val protocoloId: String) : ErroDominio
    data class DoseInvalida(val dose: Int, val dosesDoProtocolo: Int) : ErroDominio
    data object TipoDeSaidaInvalido : ErroDominio
    /** O lote não existe, foi excluído ou é de outra fazenda (o servidor recusaria: FK (fazenda_id, lote_id)). */
    data class LoteNaoEncontrado(val loteId: String) : ErroDominio

    // Fazendas (PRD Fase 2, fatia 3)
    /** UF precisa ter 2 letras, como "MG". */
    data object UfInvalida : ErroDominio
    /** A fazenda não está entre as fazendas do usuário. */
    data class FazendaNaoEncontrada(val fazendaId: String) : ErroDominio
    /** O servidor recusou por falta de permissão (RLS). */
    data object SemPermissao : ErroDominio

    // Conta e sessão (PRD Fase 2, RF-12)
    data object EmailInvalido : ErroDominio
    data class SenhaFraca(val minimoDeCaracteres: Int) : ErroDominio
    data object EmailJaCadastrado : ErroDominio
    /** E-mail ou senha errados. Nunca diz qual dos dois (RN-03). */
    data object CredenciaisInvalidas : ErroDominio
    data object EmailNaoConfirmado : ErroDominio
    /** Código de confirmação errado, vencido ou mal digitado. */
    data object CodigoInvalido : ErroDominio
    /** A senha nova é igual à atual. */
    data object SenhaIgualAnterior : ErroDominio
    /** Muitos pedidos seguidos (ex.: reenviar código); tente de novo em alguns minutos. */
    data object MuitasTentativas : ErroDominio
    data object SemConexao : ErroDominio
    /** Falha do servidor que o app não sabe tratar. [detalhe] é para log, não para o usuário. */
    data class ErroInesperado(val detalhe: String) : ErroDominio
}

enum class MotivoBrincoInvalido { VAZIO, MUITO_LONGO, CARACTERE_INVALIDO }

enum class MotivoGenitorInvalido {
    NAO_ENCONTRADO,
    OUTRA_FAZENDA,
    MAE_NAO_E_FEMEA,
    PAI_NAO_E_MACHO,
    NASCEU_DEPOIS_DO_FILHO,
    O_PROPRIO_ANIMAL
}

enum class MotivoProtocoloInvalido {
    DOSES_MENOR_QUE_UM,
    INTERVALO_OBRIGATORIO,
    INTERVALO_NAO_POSITIVO,
    REFORCO_NAO_POSITIVO,
    IDADE_NEGATIVA,
    IDADE_MINIMA_MAIOR_QUE_MAXIMA
}
