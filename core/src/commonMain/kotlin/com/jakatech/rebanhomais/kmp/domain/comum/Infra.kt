package com.jakatech.rebanhomais.kmp.domain.comum

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Fonte da data de hoje. Nos testes, usa-se uma data fixa. */
fun interface Relogio {
    fun hoje(): LocalDate
}

object RelogioDoSistema : Relogio {
    @OptIn(ExperimentalTime::class)
    override fun hoje(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}

/**
 * Gera IDs no próprio aparelho. Isso permite cadastrar sem internet e
 * manter o mesmo ID quando o registro chegar ao servidor.
 */
fun interface GeradorDeId {
    fun novo(): String
}

object GeradorDeUuid : GeradorDeId {
    @OptIn(ExperimentalUuidApi::class)
    override fun novo(): String = Uuid.random().toString()
}
