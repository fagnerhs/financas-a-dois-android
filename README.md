# Finanças a Dois (Android nativo)

Projeto Kotlin + Jetpack Compose, minSdk 26. Registro e leitura funcionam offline. A categorização é feita por regras locais (não usa IA/nuvem); revise o resultado antes de registrar. Dados ficam no aparelho, não sincronizam sozinhos. Para usar em dois aparelhos, exporte o JSON num e importe no outro; configurações de nomes e limites precisam ser preenchidas em cada telefone.

## Gerar APK sem Android Studio
1. Crie um repositório **público** no GitHub.
2. Extraia este ZIP e envie todo o conteúdo para o repositório (incluindo `.github` e `gradle`).
3. Abra **Actions**, habilite workflows se solicitado e execute **Build APK assinado** (`Run workflow`).
4. Ao terminar, baixe o artefato `financas-casal-apk` na página da execução; ele contém `app-release.apk`.

O workflow gera uma chave de assinatura de instalação para essa execução. Guarde o APK e instale exatamente o mesmo arquivo nos dois aparelhos. Uma nova execução cria outra chave e o Android não instalará por cima da versão anterior sem desinstalá-la (o que apaga os dados locais). O build é assinado para instalação, não para publicação em loja.

## Testes
`./gradlew test` roda os testes unitários do parser. Testes de interface e instalação exigem emulador/aparelho Android e não são executados neste ambiente.
