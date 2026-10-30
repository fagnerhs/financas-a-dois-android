# Fontes consultadas e decisões do produto

## Padrões de apps para casais

A pesquisa de apps comparáveis encontrou três padrões úteis: orçamento e lembretes compartilhados no Honeydue [1]; despesas e acertos em grupos no Splitwise e no Settle Up [2] [3]; e visão financeira conjunta no Monarch para casais e famílias [4]. O app implementa o núcleo que cabe nesta versão: livro-caixa compartilhado, orçamento por categoria, despesas recentes, análise de gastos e meta de emergência. Conexão a contas bancárias, divisão automática de contas, lembretes de vencimento e comentários não fazem parte do APK desta entrega.

O Zeta foi incluído na comparação original, mas sua aquisição e situação de produto mudaram; por isso, não foi usado como base para prometer recursos atuais [5]. A escolha de sincronizar pelo Supabase, em vez de Firebase, veio do pedido do usuário e da cotação prévia de US$ 0/mês para o projeto isolado; cota e disponibilidade continuam sujeitas aos termos do provedor.

## Arquitetura e segurança

O app usa Auth, PostgREST e Realtime do SDK `supabase-kt` 3.8.0, com Kotlin 2.4.0, Ktor 3.5.1 e kotlinx.serialization 1.11.0, conforme o catálogo de versões do tag consultado [6]. A configuração Android usa API mínima 26 e um engine Ktor com suporte a WebSockets para Realtime [6] [7]. A sessão persistida é restaurada antes de decidir se o usuário deve autenticar de novo, seguindo o fluxo `sessionStatus` do SDK [17]. O cliente observa mudanças de despesas com `selectAsFlow` e mantém cache SQLite para os lançamentos pendentes offline [8].

A chave publishable incluída no APK é uma configuração pública de cliente; nenhuma chave `service_role` ou `secret` está no código. A autorização dos dados depende de autenticação, Row Level Security e privilégios SQL mínimos [9] [10]. Helpers de membership ficam em schema privado, com `search_path` vazio e referências qualificadas. Cada integrante lê o livro compartilhado; só quem criou uma despesa pode editá-la ou removê-la. Convites são UUIDs de uso único com validade de 48 horas.

## Referências

[1]: https://www.honeydue.com/ "Honeydue — finanças para casais"
[2]: https://www.splitwise.com/ "Splitwise — compartilhamento de despesas"
[3]: https://settleup.io/ "Settle Up — despesas em grupo"
[4]: https://help.monarch.com/hc/en-us/articles/20926382202004-Monarch-for-Couples-and-Households "Monarch para casais e famílias"
[5]: https://www.acorns.com/learn/acorns-zeta-acquisition/ "Acorns — aquisição da Zeta"
[6]: https://raw.githubusercontent.com/supabase/supabase-kt/3.8.0/gradle/libs.versions.toml "Catálogo de versões supabase-kt 3.8.0"
[7]: https://ktor.io/docs/client-engines.html "Ktor — client engines e suporte a WebSockets"
[8]: https://supabase.com/docs/reference/kotlin/subscribe "Supabase Kotlin — subscriptions e selectAsFlow"
[9]: https://supabase.com/docs/guides/database/secure-data "Supabase — secure data e chave publishable"
[10]: https://supabase.com/docs/guides/database/postgres/row-level-security "Supabase — Row Level Security"
[11]: https://supabase.com/docs/reference/kotlin/installing "Supabase Kotlin — instalação"
[12]: https://supabase.com/docs/reference/kotlin/initializing "Supabase Kotlin — inicialização"
[13]: https://supabase.com/docs/reference/kotlin/auth-signup "Supabase Kotlin — cadastro por e-mail"
[14]: https://supabase.com/docs/reference/kotlin/rpc "Supabase Kotlin — RPC"
[15]: https://supabase.com/docs/reference/kotlin/upsert "Supabase Kotlin — upsert"
[16]: https://github.com/supabase/supabase-kt/releases "Releases supabase-kt"
[17]: https://supabase.com/docs/reference/kotlin/auth-getsession "Supabase Kotlin — fluxo de status e restauração da sessão"


## Build Android

O alvo JVM do plugin Kotlin usa `kotlin.compilerOptions` com `JvmTarget.fromTarget("17")`, a migração oficial recomendada para projetos Android atuais [18]. O workflow prepara `sdkmanager` e aceita as licenças com `android-actions/setup-android`, depois instala explicitamente a plataforma API 36 e build-tools 36.0.0 exigidos pela dependência AndroidX usada no SDK [19].

[18]: https://kotlinlang.org/docs/gradle-compiler-options.html "Kotlin Gradle Plugin — compilerOptions e migração de android.kotlinOptions"
[19]: https://raw.githubusercontent.com/android-actions/setup-android/v3/README.md "android-actions/setup-android — sdkmanager e licenças"
