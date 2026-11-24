plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
}

// ---------------------------------------------------------------------
// Publicação do core e do data para o app em Compose Multiplatform (outro repositório).
// Coordenadas: com.jakatech.rebanhomais:core e com.jakatech.rebanhomais:data (o data já traz o core).
// A versão vem de -Prebanho.versao (o workflow de release usa a tag, ex.: v1.2.0 → 1.2.0).
// ---------------------------------------------------------------------
val versaoDaBiblioteca: String = providers.gradleProperty("rebanho.versao").getOrElse("0.0.0-SNAPSHOT")

subprojects {
    group = "com.jakatech.rebanhomais"
    version = versaoDaBiblioteca

    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/arcangeloBiel/Rebanho-Business-Logic")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}
