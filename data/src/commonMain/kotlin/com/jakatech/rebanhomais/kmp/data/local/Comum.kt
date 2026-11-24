package com.jakatech.rebanhomais.kmp.data.local

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Hora do aparelho em ISO 8601 (UTC), o formato de created_at/updated_at no Supabase. */
@OptIn(ExperimentalTime::class)
internal fun instanteAtual(): String = Clock.System.now().toString()

/**
 * Transforma o texto digitado num padrão de LIKE: "contém", com %, _ e ! escapados por '!'
 * para que o usuário não filtre por curinga sem querer.
 */
internal fun padraoDeBusca(busca: String): String {
    val escapado = busca.trim()
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")
    return "%$escapado%"
}
