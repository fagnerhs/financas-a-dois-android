package br.com.financasdocasal

internal fun authErrorNotice(error: Throwable, creatingAccount: Boolean): String {
    val details = generateSequence(error) { it.cause }
        .take(8)
        .joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()} $it" }
        .lowercase()

    return when {
        "over_email_send_rate_limit" in details ->
            "O envio de confirmação por e-mail atingiu um limite temporário do Supabase. Aguarde o limite liberar e tente criar a conta apenas uma vez."
        "over_request_rate_limit" in details || "too many requests" in details ->
            "Muitas tentativas em pouco tempo. Aguarde o limite temporário liberar antes de tentar novamente."
        "email_not_confirmed" in details || "email not confirmed" in details ->
            "A conta já foi criada, mas o e-mail ainda não foi confirmado. Confira a caixa de entrada e o spam."
        "user_already_exists" in details || "already registered" in details || "user already exists" in details ->
            "Este e-mail já tem uma conta. Toque em “Já tenho uma conta · entrar”; se necessário, use “Esqueci minha senha”."
        "invalid_credentials" in details || "invalid login credentials" in details ->
            "E-mail ou senha incorretos. Confira se está no modo certo: criar uma conta nova ou entrar em uma existente."
        "weak_password" in details || "password should" in details ->
            "A senha não atende aos requisitos. Escolha uma senha mais forte e tente novamente."
        "invalid_email" in details || "email address is invalid" in details ->
            "Confira o endereço de e-mail informado."
        "unknownhostexception" in details || "unable to resolve host" in details ||
            "connectexception" in details || "connection timed out" in details || "timeout" in details ->
            "Sem conexão com o serviço agora. Verifique a internet e tente novamente."
        creatingAccount ->
            "Não foi possível criar a conta. Confira o e-mail e a senha e tente novamente."
        else ->
            "Não foi possível entrar. Confira o e-mail e a senha."
    }
}
