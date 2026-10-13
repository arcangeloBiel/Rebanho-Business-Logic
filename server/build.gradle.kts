plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

group = "com.jakatech.rebanhomais.kmp"
version = "1.0.0"
application {
    mainClass = "com.jakatech.rebanhomais.kmp.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.firebase.admin)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}