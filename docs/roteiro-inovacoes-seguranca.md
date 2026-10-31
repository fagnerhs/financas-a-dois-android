# Roteiro de inovações e segurança para Finanças a Dois

## O que vale preservar

O app já tem uma boa base para funcionar sem conexão bancária: contas individuais, espaço do casal por convite, lançamentos manuais ou por frase, orçamento por categoria, meta de emergência, sincronização entre aparelhos, cache local e exportação/importação JSON. Essas funções já resolvem parte do que os concorrentes fazem com contas manuais; o próximo passo deve aprimorá-las, não duplicá-las. [1] [2]

Também encontrei boas decisões de segurança: o cliente usa a chave publicável do Supabase, não uma chave administrativa; o banco tem políticas RLS e permissões reduzidas; o tráfego em texto claro está desativado; e o backup automático do Android está desativado. A chave publicável pode ficar no app cliente segundo o próprio Supabase, desde que o acesso real continue protegido por Auth, grants e RLS. [4] [5] [6] [7] [9] [10]

## Primeiro: fechar lacunas de segurança e continuidade

**1. Usar uma chave de assinatura persistente para todas as versões.** O workflow atual cria um keystore aleatório a cada build. Isso serve para produzir um APK instalável, mas a assinatura muda entre builds e pode impedir que uma nova versão atualize o app já instalado. Antes de distribuir versões sucessivas, guardar a chave de release de forma protegida no GitHub Actions, manter uma cópia de recuperação segura e testar a atualização sobre o APK anterior. Não colocar a chave ou senha no código nem no repositório. [8]

**2. Automatizar testes negativos das regras RLS.** As migrações já restringem dados por espaço e limitam edição/exclusão de uma despesa ao autor. Porém, no repositório revisado não encontrei uma pasta `supabase/tests`. Criaria testes SQL com usuários de teste para provar que uma pessoa sem login e uma terceira pessoa fora do casal não leem nem alteram despesas; que os dois membros veem somente o espaço ao qual pertencem; e que somente o autor pode editar ou apagar o próprio lançamento. A documentação Supabase recomenda testes automatizados de políticas, inclusive com pgTAP e `supabase test db`. [5] [6] [7] [11]

**3. Proteger o cache e o backup exportado.** O app guarda dados em SQLite local e desativa o backup automático do sistema, mas o código não aplica criptografia de banco em nível de app. A exportação atual é JSON em texto legível, compartilhável por outros apps. Eu manteria o backup automático desativado e acrescentaria exportação protegida por senha, com aviso antes de compartilhar, além de criptografia do armazenamento local usando uma biblioteca consolidada e chaves geridas pelo Android Keystore. Uma trava biométrica após um período de inatividade pode complementar isso, mas não deve ser apresentada como substituta da autenticação do servidor. [2] [3] [9] [12] [14] [15] [17]

**4. Completar acesso e recuperação da conta.** A tela de entrada atualmente oferece e-mail/senha e criação de conta; não localizei fluxo visível de redefinição de senha ou MFA. Incluir recuperação de senha, gerenciamento/revogação de sessões e MFA TOTP opcional seria uma melhoria relevante para dados financeiros. Se houver MFA, é importante que a proteção seja aplicada pelo servidor às operações sensíveis; apenas mostrar uma etapa extra na interface não basta. A autenticação biométrica pode ser usada para destravar o aparelho, enquanto o Supabase continua responsável pela identidade remota. [2] [13] [15] [16]

**5. Explicar exportação, saída e exclusão de dados.** Tornar claro o que o JSON inclui, como restaurá-lo, o que fica salvo quando uma pessoa sai do espaço e como solicitar exclusão da conta e dos dados compartilhados. Como o backup automático está desligado, exportação segura também é um caminho controlado de recuperação para registros ainda não sincronizados. [2] [3] [9] [12] [17]

## Inovações que funcionam sem conectar bancos

**Compromissos recorrentes previstos, com confirmação manual.** Permitir cadastrar aluguel, internet, escola e assinaturas com valor previsto, categoria, frequência e próxima data. O app pode avisar antes do vencimento e perguntar se o lançamento deve ser confirmado. Deve diferenciar claramente “previsto” de “pago”: sem conexão bancária, o app não tem evidência de que a conta foi realmente quitada. YNAB, Splitwise e Wallet documentam padrões de transações agendadas, próximas ocorrências ou pagamentos planejados; Wallet também documenta confirmação manual. [20] [21]

**Privacidade escolhida pelo casal, por conta ou lançamento.** Oferecer opções explícitas como “compartilhado”, “somente meu” e, se fizer sentido, “mostrar apenas o saldo”. Honeydue documenta controles de visibilidade por conta e por transação; Wallet descreve níveis de permissão por conta, como somente leitura. No app do casal, esconder algo apenas na tela não é suficiente: a regra deve valer no banco, nas notificações, na busca e na exportação, e o produto precisa explicar se um lançamento privado afeta ou não o orçamento compartilhado. [18] [19]

**Previsão de “disponível até o próximo pagamento”, usando dados digitados pelo casal.** Como melhoria diferenciadora, permitir informar manualmente rendas e compromissos fixos, projetar o saldo planejado até a próxima data de recebimento e mostrar quais hipóteses entraram no cálculo. Isso não exige acesso a banco, mas exige que o app passe a registrar renda e vencimentos. A primeira versão deve usar cálculos determinísticos e explicar como chegou ao número, em vez de dar um valor de “saldo disponível” sem contexto.

**Rateio flexível.** Para uma despesa de casal, permitir dividir por valor ou porcentagem entre as duas pessoas, em vez de presumir metade para cada uma. Splitwise documenta divisões iguais, valores, porcentagens, cotas e itens; para começar, valores e porcentagens já cobrem os casos mais comuns. Validar que as partes somam exatamente o total e deixar visível quem pagou e quem ficou responsável por cada parte. [22]

**Metas com prazo e aporte planejado.** A meta de emergência já existe. O avanço natural é permitir outras metas compartilhadas, mostrar valor-alvo, saldo anotado, quanto falta, prazo e contribuição periódica sugerida. Goodbudget e Monarch documentam combinações de alvo, prazo e contribuição; isso é acompanhamento planejado, não transferência automática de dinheiro. [23] [24]

**Revisão do casal e histórico de alterações.** Criar uma lista curta de lançamentos “a conferir”, com responsável ou estado de revisão, e mostrar quem adicionou ou alterou cada despesa. Monarch documenta revisão atribuível; Splitwise documenta atividade e notificações de alterações. No Finanças a Dois, o histórico deve ser desenhado como transparência para o casal, sem prometer ser imutável até que essa propriedade seja tecnicamente garantida. [25] [26]

**OCR opcional de recibos, somente depois das proteções básicas.** Uma captura de foto pode sugerir valor, data e estabelecimento, mas a pessoa precisa revisar antes de salvar. O Google ML Kit oferece reconhecimento de texto no Android com modelo incluído no app ou baixado pelo Google Play Services; escolher o modelo incluído permite disponibilizar o OCR imediatamente, ao custo de um APK maior. Para preservar privacidade, a versão inicial deve processar no aparelho e não enviar a imagem do recibo ao Supabase por padrão. [27]

## Ordem sugerida

Eu priorizaria a chave de assinatura persistente, os testes RLS e o backup protegido antes de ampliar o volume de dados guardado. Em seguida, implementaria a recuperação de conta e MFA opcional. Para ganho de uso diário, começaria pelas recorrências com confirmação manual e pelos controles de visibilidade. Depois viria a previsão baseada em renda digitada, o rateio, as metas adicionais e a revisão/histórico. Deixaria OCR para uma etapa posterior, quando o fluxo de privacidade e armazenamento de imagens estiver decidido.

A escolha de **não integrar bancos** é compatível com esse roteiro. Honeydue, Monarch, YNAB, Splitwise, Goodbudget e Wallet documentam, em diferentes graus, lançamentos ou contas manuais, orçamento, metas ou despesas previstas. As recomendações acima usam esses padrões manuais; não dependem de conexão bancária, agregação de saldos ou importação automática. As descrições de privacidade e segurança dos concorrentes são declarações dos próprios fornecedores, não auditorias independentes.

## Referências

[1]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/README.md "README do app Finanças a Dois"
[2]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/app/src/main/java/br/com/financasdocasal/MainActivity.kt "Tela e fluxos do app"
[3]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/app/src/main/java/br/com/financasdocasal/LocalStore.kt "Armazenamento local SQLite"
[4]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/app/src/main/java/br/com/financasdocasal/SupabaseCloud.kt "Cliente Supabase do app"
[5]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/supabase/migrations/20261030100000_couple_sync.sql "Migração inicial de sincronização e RLS"
[6]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/supabase/migrations/20261030103000_security_hardening.sql "Migração de hardening de segurança"
[7]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/supabase/migrations/20261030110000_security_privileges.sql "Migração de privilégios mínimos"
[8]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/.github/workflows/build.yml "Workflow de build e assinatura APK"
[9]: https://github.com/fagnerhs/financas-a-dois-android/blob/main/app/src/main/AndroidManifest.xml "Manifesto Android"
[10]: https://supabase.com/docs/guides/api/api-keys "Supabase API Keys"
[11]: https://supabase.com/docs/guides/database/testing "Testing Your Database — Supabase"
[12]: https://mas.owasp.org/MASVS/05-MASVS-STORAGE/ "OWASP MASVS Storage"
[13]: https://mas.owasp.org/MASVS/07-MASVS-AUTH/ "OWASP MASVS Authentication and Authorization"
[14]: https://developer.android.com/privacy-and-security/keystore "Android Keystore"
[15]: https://developer.android.com/identity/sign-in/biometric-auth "Biometric authentication on Android"
[16]: https://supabase.com/docs/guides/auth/auth-mfa "Supabase Multi-Factor Authentication"
[17]: https://developer.android.com/identity/data/autobackup "Android Auto Backup"
[18]: https://support.honeydue.com/en/articles/3283211-how-to-change-what-my-partner-can-see "Honeydue: controle de visibilidade para o parceiro"
[19]: https://support.budgetbakers.com/hc/en-us/articles/7149394922002-Everything-about-Group-Sharing "Wallet: Group Sharing e permissões"
[20]: https://support.budgetbakers.com/hc/en-us/articles/7149523920786-Setup-Planned-Payments "Wallet: Planned Payments"
[21]: https://support.ynab.com/en_us/scheduled-transactions-a-guide-BygrAIFA9 "YNAB: Scheduled Transactions"
[22]: https://kb.splitwise.com/balances-and-expenses/what-are-different-ways-i-can-split-an-expense "Splitwise: formas de dividir despesas"
[23]: https://goodbudget.com/help/customize-your-goodbudget/goals-and-annuals/ "Goodbudget: metas e despesas anuais"
[24]: https://help.monarchmoney.com/hc/en-us/articles/15000751305108-Using-Goals "Monarch: Using Goals"
[25]: https://help.monarchmoney.com/hc/en-us/articles/5528707082516-Reviewing-Transactions "Monarch: Reviewing Transactions"
[26]: https://kb.splitwise.com/groups/can-i-prevent-group-members-from-changing-expenses-or-set-permissions "Splitwise: permissões e alterações em despesas de grupo"
[27]: https://developers.google.com/ml-kit/vision/text-recognition/v2/android "ML Kit Text Recognition on Android"
