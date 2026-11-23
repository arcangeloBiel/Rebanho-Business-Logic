package com.jakatech.rebanhomais.kmp.data.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Ponto único de injeção de dependência da camada KMP.
 *
 * A UI (Android e iOS) só precisa carregar este módulo:
 *
 * ```
 * startKoin { modules(rebanhoModule) }
 * ```
 *
 * As fases seguintes registram aqui os repositórios (Fases 2 e 3),
 * o sincronizador (Fase 4) e os casos de uso (Fase 1 em diante).
 */
val rebanhoModule: Module = module {
}
