package com.jakatech.rebanhomais.kmp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Cliente único do Supabase do app.
 * A URL e a chave publicável vêm de [ConfiguracaoSupabase], gerado no build a partir de gradle.properties.
 */
internal fun criarClienteSupabase(
    url: String = ConfiguracaoSupabase.URL,
    chavePublica: String = ConfiguracaoSupabase.CHAVE_PUBLICA
): SupabaseClient = createSupabaseClient(supabaseUrl = url, supabaseKey = chavePublica) {
    install(Auth) {
        // Guarda a sessão no aparelho e a restaura ao abrir o app (RF-03).
        autoLoadFromStorage = true
        autoSaveToStorage = true
        // Renova o token sozinho quando há internet (RF-04).
        alwaysAutoRefresh = true
    }
    // Tabelas e funções do banco (RLS garante o acesso por fazenda).
    install(Postgrest)
}
