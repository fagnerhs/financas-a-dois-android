# Finanças a Dois — Android 1.3.1

Aplicativo Android nativo para organizar entradas de renda e despesas do casal com lançamentos manuais, contas individuais e espaço compartilhado. Não conecta a bancos e não importa movimentações bancárias. A sincronização entre aparelhos usa o projeto Supabase já configurado no aplicativo; sem internet, os dados ficam na fila local e são enviados quando a conexão voltar.

## Instalar o APK no Android

1. Baixe `app-release-signed.apk` no link fornecido e abra o arquivo pela notificação de download ou pela pasta **Downloads**.
2. Se o Android bloquear a instalação, toque em **Configurações** na mensagem e permita temporariamente que o aplicativo usado para abrir o arquivo (por exemplo, Chrome ou Arquivos) instale apps desconhecidos. Volte e toque em **Instalar**. Os nomes podem variar entre fabricantes; depois da instalação, desative essa permissão.
3. Use exatamente o mesmo APK nos dois celulares. No primeiro, crie uma conta com seu e-mail e senha e crie o espaço do casal. Toque em **Compartilhar convite** e envie o código à sua esposa. No segundo, crie outra conta e escolha **Tenho um código de convite**. Não compartilhem senhas. O convite é de uso único e expira em 48 horas.
4. Se o projeto exigir confirmação de e-mail, abra primeiro a mensagem de confirmação recebida. Depois entre no aplicativo nos dois aparelhos. Opcionalmente, ative o desbloqueio biométrico em **Ajustes** em cada celular.

Se a versão 1.2.0 mostrou erro ao criar o espaço, não repita a ação no APK antigo: o servidor pode ter aceitado a solicitação apesar do aviso. As versões 1.2.1 e 1.2.3 corrigiram problemas de criação do espaço e da tela **Planejar**; a versão 1.3.0 acrescentou rendas e biometria; a 1.3.1 facilita o registro de aportes e mostra sugestões locais de economia.

Se o cadastro mostrar um limite temporário de envio de e-mail, aguarde o limite do provedor liberar e tente criar a conta uma única vez; tentativas repetidas podem gerar novas recusas temporárias.

## Atualizar uma instalação anterior

O workflow atual assina cada APK com uma chave criada somente para aquela execução. Por isso, o Android não permite instalar este APK por cima de um APK assinado por outra execução. Antes de remover a versão anterior, conecte os aparelhos à internet e confirme que os dados sincronizaram. Em **Ajustes → Backup e transferência**, exporte um backup cifrado e guarde a senha (mínimo de 12 caracteres) e o arquivo em local seguro. Em seguida, desinstale a versão anterior, instale o APK novo e entre na mesma conta. Os dados já sincronizados continuam na nuvem; o backup protege lançamentos que ainda não foram sincronizados e dados locais.

A assinatura temporária também significa que futuras versões precisarão ser instaladas novamente, em vez de atualizadas por cima. O APK é assinado e instalável, mas não é publicado na Play Store.

## Recursos e privacidade

A aba **Registrar** alterna entre **Renda** e **Despesa**. Para renda, informe uma fonte personalizada (por exemplo, salário ou trabalho extra); para despesa, escolha uma categoria, divida o valor se quiser ou use frase/OCR para agilizar. O painel e os Insights mostram entradas, despesas e saldo compartilhados separadamente. O OCR apenas sugere valor, data e estabelecimento: confira e corrija os campos antes de salvar. A imagem e o texto reconhecido não são enviados pelo fluxo de OCR.

**Planejamento** inclui contas recorrentes e metas. Ao tocar em **Confirmar que foi paga**, o aplicativo cria um lançamento manual e avança o vencimento seguinte; não realiza pagamentos nem consulta bancos. Em **Insights → Reserva de emergência**, informe cada novo aporte e toque em **Adicionar à reserva**: o valor é somado ao total compartilhado, sem criar uma renda ou despesa fictícia. Insights também mostra totais, categorias, limites e previsões informadas manualmente.

**Sugestões para economizar** ficam em Insights. Na versão 1.3.1, são regras explicáveis calculadas no aparelho a partir das despesas compartilhadas com categoria visível e dos limites definidos; itens privados e somente-saldo são ignorados. Não existe IA generativa conectada e nenhum lançamento é enviado a um modelo externo.

Cada casal usa contas separadas. Lançamentos podem ser compartilhados, visíveis somente para o saldo ou privados. Lançamentos privados não são exibidos ao parceiro; a opção de saldo compartilhado mostra o valor e a data, sem categoria, descrição, pagador ou divisão. Cada membro só pode alterar ou excluir lançamentos que criou. O acesso à nuvem é protegido por autenticação e políticas de acesso do banco de dados.

O OCR funciona localmente. O aplicativo usa armazenamento local protegido por chave do Android e pode criar backups cifrados com senha. A senha do backup não fica salva no aplicativo; mantenha-a separada do arquivo. O **desbloqueio biométrico** é opcional e pode ser ativado em Ajustes; ele bloqueia o app depois de 30 segundos fora de primeiro plano e aceita a biometria ou o PIN, padrão ou senha de tela do próprio aparelho. É uma proteção local, separada do login da conta. A autenticação por código TOTP também é opcional; ao habilitá-la, será necessário usar um autenticador para cumprir a verificação adicional da sessão.

## Recuperação de senha

O callback Android configurado no app é `financas-a-dois://auth`. Para que os links de recuperação de senha abram diretamente o app, esse URI precisa constar na lista **Redirect URLs** da configuração de autenticação do projeto Supabase. A instalação e o uso diário não exigem inserir chaves ou endereços no app.

## Projeto Supabase e limites

O app já vem apontado para o projeto `financas-casal`; não é necessário criar um projeto ou preencher configuração técnica no celular. A chave incluída no APK é a chave publishable, destinada a clientes móveis. Não há chave `service_role` no aplicativo. O uso e as cotas do Supabase dependem do plano e dos limites vigentes do provedor.

## Build e testes

O workflow `.github/workflows/build.yml` executa os testes unitários, compila o release, assina e verifica o APK e publica o artefato `financas-casal-apk` por 30 dias. O build usa Android SDK 36, Java 17 e Gradle 8.14.4. Para iniciar manualmente, abra **Actions → Build APK assinado → Run workflow**; ao terminar, baixe `app-release-signed.apk` em **Artifacts**.

`./gradlew test` cobre parser, valores, previsão, OCR, backup, soma dos aportes e filtros da análise local de economia. A compilação e a assinatura são verificadas pelo workflow. Login, convite, sincronização Realtime, autenticação biométrica em aparelho real, OCR real e instalação precisam ser verificados em aparelhos ou emuladores Android; os testes unitários, sozinhos, não comprovam esses fluxos ponta a ponta.

As migrations SQL estão em `supabase/migrations/`. As fontes técnicas e decisões de segurança estão em `docs/roteiro-inovacoes-seguranca.md` e `docs/fontes-tecnicas.md`.
