# Controllers da API

Expõem os contratos HTTP e delegam as regras de autenticação, acervo, empréstimos e usuários aos services.

## Funcionalidades e dependências

- `AuthController`: `POST /api/auth/login`; valida `CredentialRequest`, delega ao `AuthenticationManager` e emite o token via `TokenService`.
- `UsuarioController`: `POST /api/usuarios` cria a conta de leitor.
- `AdminUsuarioController`: `ADMIN` lista usuários, promove ou remove bibliotecário, inativa e reativa contas.
- `PublicBookController`: `GET /api/livros` e `GET /api/livros/{id}` expõem o catálogo público.
- `CollectionBookController`: a equipe cadastra, edita, inativa e reativa livros em `/api/livros` e consulta o acervo em `/api/livros/acervo`.
- `GenreController` e `AuthorController`: listagem pública; criação, edição e exclusão pela equipe em `/api/acervo`.
- `LoanController`: o leitor reserva com `Idempotency-Key`, consulta o próprio histórico, cancela, renova e paga multa.
- `DeskController`: a equipe consulta código, confirma retirada, registra devolução e vê o resumo.
- `PageHttpMapper`, `BookHttpMapper` e `LoanHttpMapper`: convertem entre DTOs HTTP e contratos de `service.dto`.
- Dependem de DTOs em `dto`, interfaces em `service` e autorização em `config`. Não dependem de `service.impl` nem de `repository`.

## Pontos críticos e limites

O cadastro público não recebe perfil. A posse do empréstimo é verificada pelo service com o ID do usuário autenticado (`UserSS`), nunca com um ID enviado pelo cliente. Os erros são tratados em `config.ApiExceptionHandler`.
