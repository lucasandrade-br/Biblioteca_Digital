# Core do frontend

Contém a infraestrutura transversal do aplicativo, sem regras de uma feature específica.

- `http/api-url.ts` define o token `API_URL`, com valor padrão `/api`.
- `http/api-error.ts` transforma erros HTTP na mensagem exibida ao usuário.
- `http/api-timeout.interceptor.ts` aplica o prazo de 15 segundos às chamadas para `API_URL`, e `http/page.ts` define o contrato de paginação compartilhado.
- `auth/` contém `AuthService`, seus tipos de sessão e credenciais, guards, interceptor e testes.
- O interceptor de autenticação envia Bearer apenas para chamadas privadas à API e encerra uma sessão inválida em `401`.
- `authGuard` e `guestGuard` protegem a navegação por sessão; `roleGuard` protege por perfil, mas quem bloqueia de fato é a API.

Clientes HTTP e contratos de livros, empréstimos, balcão, acervo e administração pertencem a `features/*/data-access` e `features/*/models`.
