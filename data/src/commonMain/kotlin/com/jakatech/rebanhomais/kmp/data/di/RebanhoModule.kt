package com.jakatech.rebanhomais.kmp.data.di

import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarAnimal
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarFazenda
import com.jakatech.rebanhomais.kmp.domain.casodeuso.CadastrarProtocolo
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ListarPendenciasVacinais
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterCategoria
import com.jakatech.rebanhomais.kmp.domain.casodeuso.ObterResumoDePeso
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarPesagem
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarSaida
import com.jakatech.rebanhomais.kmp.domain.casodeuso.RegistrarVacinacao
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeId
import com.jakatech.rebanhomais.kmp.domain.comum.GeradorDeUuid
import com.jakatech.rebanhomais.kmp.domain.comum.Relogio
import com.jakatech.rebanhomais.kmp.domain.comum.RelogioDoSistema
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
 * Os repositórios (Supabase + banco local) são registrados aqui nas Fases 2 e 3.
 * Até lá, os casos de uso estão declarados mas não podem ser criados.
 */
val rebanhoModule: Module = module {
    single<Relogio> { RelogioDoSistema }
    single<GeradorDeId> { GeradorDeUuid }

    factory { CadastrarFazenda(get(), get()) }
    factory { CadastrarAnimal(get(), get(), get(), get()) }
    factory { ObterCategoria(get(), get()) }
    factory { RegistrarSaida(get(), get(), get(), get()) }
    factory { RegistrarPesagem(get(), get(), get(), get()) }
    factory { ObterResumoDePeso(get()) }
    factory { CadastrarProtocolo(get(), get()) }
    factory { RegistrarVacinacao(get(), get(), get(), get()) }
    factory { ListarPendenciasVacinais(get(), get(), get()) }
}
