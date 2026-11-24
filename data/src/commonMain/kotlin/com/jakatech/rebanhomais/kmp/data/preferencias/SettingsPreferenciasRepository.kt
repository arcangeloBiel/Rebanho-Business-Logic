package com.jakatech.rebanhomais.kmp.data.preferencias

import com.jakatech.rebanhomais.kmp.domain.repositorio.PreferenciasRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferências guardadas no aparelho (SharedPreferences no Android, NSUserDefaults no iOS). */
class SettingsPreferenciasRepository(private val settings: Settings) : PreferenciasRepository {

    private val selecionada = MutableStateFlow(settings.getStringOrNull(CHAVE_FAZENDA_SELECIONADA))
    override val fazendaSelecionadaId: Flow<String?> = selecionada.asStateFlow()

    override suspend fun selecionarFazenda(fazendaId: String?) {
        if (fazendaId == null) settings.remove(CHAVE_FAZENDA_SELECIONADA) else settings.putString(CHAVE_FAZENDA_SELECIONADA, fazendaId)
        selecionada.value = fazendaId
    }

    private companion object {
        const val CHAVE_FAZENDA_SELECIONADA = "rebanho.fazenda_selecionada"
    }
}
