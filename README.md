# Rebanho-Business-Logic

Regras de negócio e dados do Rebanho+, em Kotlin Multiplatform (Android, iOS e JVM).
A interface fica em outro repositório, em Compose Multiplatform, e usa esta biblioteca.

| Módulo | O que tem |
|---|---|
| `core` | Domínio puro: modelos, regras, casos de uso (`CadastrarAnimal`, `ObservarRebanho`, `RegistrarVacinacao`…) e as interfaces dos repositórios |
| `data` | Implementações: Supabase (conta, fazendas), banco local SQLDelight (rebanho offline) e o módulo Koin `rebanhoModule` |
| `supabase` | Migrations, RLS e testes pgTAP do banco remoto |

```
Tela (app CMP) → ViewModel (app CMP) → caso de uso (core) → repositório (interface no core, implementação no data)
```

## Publicar uma versão

Crie uma tag; o workflow **Publicar biblioteca** roda os testes num macOS e publica `core` e `data` no GitHub Packages:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Para apontar a versão para o Supabase de produção, cadastre os segredos `REBANHO_SUPABASE_URL` e
`REBANHO_SUPABASE_CHAVE` no Environment `producao` do repositório. Sem eles, a versão usa o projeto de
desenvolvimento do `gradle.properties` (o log do workflow avisa).

## Usar no app Compose Multiplatform

1. Crie um token do GitHub (classic) com o escopo `read:packages` e guarde em `~/.gradle/gradle.properties`
   (fora do repositório do app):

   ```properties
   gpr.user=seu-usuario-do-github
   gpr.token=ghp_...
   ```

2. No `settings.gradle.kts` do app, adicione o repositório. O GitHub Packages exige login até para pacotes públicos:

   ```kotlin
   dependencyResolutionManagement {
       repositories {
           google()
           mavenCentral()
           maven {
               url = uri("https://maven.pkg.github.com/arcangeloBiel/Rebanho-Business-Logic")
               credentials {
                   username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                   password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
               }
           }
       }
   }
   ```

3. No módulo compartilhado do app (`composeApp`), dependa do `data` (ele já traz o `core`):

   ```kotlin
   commonMain.dependencies {
       implementation("com.jakatech.rebanhomais:data:1.0.0")
   }
   androidMain.dependencies {
       implementation("io.insert-koin:koin-android:4.2.2") // para o androidContext
   }
   ```

4. Inicie o Koin uma vez:

   ```kotlin
   // Android (Application)
   startKoin { androidContext(this@App); modules(rebanhoModule) }

   // iOS
   startKoin { modules(rebanhoModule) }
   ```

5. Nos ViewModels, peça os casos de uso ao Koin e observe os `Flow` (ex.: `ObservarRebanho`, `ObservarPendenciasVacinais`).
   Os erros de negócio chegam como `Resultado.Falha(ErroDominio)`; a mensagem para o usuário é decisão da tela.

## Desenvolvimento

```bash
./gradlew allTests               # core e data (JVM e Android; iOS só em macOS)
./gradlew publishToMavenLocal    # testa a publicação sem enviar nada
```
