# Finanças a Dois

App Android nativo em Kotlin e Jetpack Compose para acompanhar as finanças do casal. A versão 1.1 acrescenta contas individuais, espaço compartilhado por convite, sincronização entre celulares, painel mensal, limites por categoria, insights, meta de emergência e backup/importação JSON.

## Primeiro uso nos dois celulares

1. Instale o mesmo `app-release-signed.apk` nos dois aparelhos.
2. No primeiro aparelho, crie uma conta com e-mail e senha. Se a confirmação de e-mail estiver habilitada no projeto, abra o link enviado antes de entrar.
3. Crie o espaço compartilhado e use **Compartilhar convite** para enviar o código à sua esposa. O código é de uso único e expira após 48 horas.
4. No segundo aparelho, crie uma conta separada, toque em **Tenho um código de convite** e informe o código.
5. Com a conexão ativa, despesas novas aparecem por Realtime; limites, metas e participação do segundo membro são atualizados na verificação periódica (aproximadamente a cada minuto). Se a rede cair, novos lançamentos ficam no SQLite local e são enviados quando a conexão voltar; ajustes salvos offline também ficam pendentes.

A chave publishable do Supabase está embutida como configuração pública de cliente. O app não contém `service_role` nem chave `secret`; acesso depende de autenticação, grants mínimos e Row Level Security. O projeto Supabase foi provisionado na cota de US$ 0/mês; limites e cotas do plano gratuito continuam sujeitos aos termos do provedor.

## Recursos

O Painel mostra gastos do mês, orçamento total, movimentações e andamento dos limites. A aba Registrar aceita preenchimento manual ou frases simples como “gastei 50 no mercado”; a categoria é inferida por regras locais, não por IA, então revise antes de confiar no resultado. Insights agrupam gastos por categoria e mostram uma estimativa simples para a meta do fundo de emergência. Ajustes compartilham limites e metas no espaço do casal.

Os dois membros podem consultar todas as despesas, mas somente quem registrou uma despesa pode editá-la ou excluí-la. Cada pessoa autentica com a própria conta; o código de convite não substitui a senha. O app não conecta a bancos e não faz divisão/fechamento automático de contas.

## Atualização a partir do APK 1.0

O workflow anterior gerava uma chave temporária, portanto o Android pode recusar instalar a versão 1.1 por cima da antiga. Antes de remover o APK 1.0, abra **Ajustes → Exportar backup JSON** e guarde o conteúdo em local seguro. Se necessário, desinstale a versão antiga, instale a nova, entre/crie a conta e importe o backup em **Ajustes → Backup e transferência**.

## Build

O workflow em `.github/workflows/build.yml` compila e testa o projeto, assina o APK para instalação manual e publica o artefato `financas-casal-apk` na execução do GitHub Actions. Para iniciar um build manual, abra **Actions → Build APK assinado → Run workflow**. Depois de concluído, baixe `app-release-signed.apk` em **Artifacts**. Cada execução cria uma assinatura temporária diferente; use o mesmo APK daquela execução nos dois celulares. O arquivo não é publicado na Play Store.

As principais fontes técnicas e decisões de produto estão registradas em [`docs/fontes-tecnicas.md`](docs/fontes-tecnicas.md).

## Testes

`./gradlew test` roda os testes unitários do parser e da conversão de valores. O workflow também compila o release e verifica a assinatura. Testes reais de login, convite, Realtime e instalação precisam de dois aparelhos ou emuladores Android com acesso à internet.
