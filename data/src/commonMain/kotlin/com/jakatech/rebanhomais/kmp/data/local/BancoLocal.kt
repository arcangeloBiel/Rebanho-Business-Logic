package com.jakatech.rebanhomais.kmp.data.local

import org.koin.core.module.Module

/** Nome do arquivo do banco local no aparelho. */
internal const val NOME_DO_BANCO = "rebanho.db"

/**
 * Registra no Koin o driver SQLite da plataforma (Android, iOS ou JVM).
 * O resto do código usa só [RebanhoDatabase], gerado pelo SQLDelight a partir dos arquivos .sq.
 */
internal expect val moduloDoBancoLocal: Module
