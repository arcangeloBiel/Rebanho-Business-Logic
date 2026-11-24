import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// ---------------------------------------------------------------------
// Configuração do Supabase gerada no build.
// Valores padrão em gradle.properties (projeto de desenvolvimento);
// variáveis de ambiente sobrescrevem (ex.: build de produção no CI).
// ---------------------------------------------------------------------
val supabaseUrl: Provider<String> = providers.environmentVariable("REBANHO_SUPABASE_URL")
    .orElse(providers.gradleProperty("rebanho.supabase.url"))
val supabaseChave: Provider<String> = providers.environmentVariable("REBANHO_SUPABASE_CHAVE")
    .orElse(providers.gradleProperty("rebanho.supabase.chave"))

val gerarConfiguracaoSupabase = tasks.register("gerarConfiguracaoSupabase") {
    val url = supabaseUrl
    val chave = supabaseChave
    val saida = layout.buildDirectory.dir("generated/configuracao/kotlin")
    inputs.property("url", url)
    inputs.property("chave", chave)
    outputs.dir(saida)
    doLast {
        val arquivo = saida.get().file("com/jakatech/rebanhomais/kmp/data/ConfiguracaoSupabase.kt").asFile
        arquivo.parentFile.mkdirs()
        arquivo.writeText(
            """
            |package com.jakatech.rebanhomais.kmp.data
            |
            |/** Gerado pelo Gradle (tarefa gerarConfiguracaoSupabase). Não edite. */
            |internal object ConfiguracaoSupabase {
            |    const val URL: String = "${url.get()}"
            |    const val CHAVE_PUBLICA: String = "${chave.get()}"
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    android {
        namespace = "com.jakatech.rebanhomais.kmp.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(gerarConfiguracaoSupabase)
            dependencies {
                // Implementações dos repositórios (Supabase + banco local) e injeção de dependência.
                api(project(":core"))
                api(libs.koin.core)
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.multiplatform.settings.noArg)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.clientDarwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
