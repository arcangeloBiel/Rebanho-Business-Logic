package com.jakatech.rebanhomais.kmp.data.sync

import com.jakatech.rebanhomais.kmp.domain.repositorio.Conectividade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** JVM (testes e ferramentas): considera que sempre há rede; uma falha de verdade vira nova tentativa (RN-08). */
internal class ConectividadeSempreOnline : Conectividade {
    override val online: Flow<Boolean> = flowOf(true)
}

internal actual val moduloDeConectividade: Module = module {
    single<Conectividade> { ConectividadeSempreOnline() }
}
