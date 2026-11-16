# Configuração da API

Este pacote centraliza a configuração transversal do Spring: autorização, CORS, CSRF, sessão, BCrypt, manager de autenticação, tratamento de erros, OpenAPI, constantes de perfil e criação do administrador inicial.

## Dependências e relações

Depende de Spring Security, Spring Web e Springdoc. `SecurityConfig` instala o `SecurityFilter` do pacote `security`, e os controllers usam as constantes para autorização. `application.properties`, fora deste pacote, fornece porta, datasource e segredo.

## Entradas e arquivos críticos

- `SecurityConfig.java`: política HTTP; login, cadastro e consultas do catálogo são públicos, e as demais rotas exigem autenticação. `GET /api/livros/acervo` é tratado antes da regra pública. Respostas 401 e 403 saem em JSON.
- `ApiExceptionHandler.java`: `ApplicationServiceException` vira 404, 401 ou 403; `IllegalArgumentException` (regra de negócio) vira 409; validação e requisição malformada viram 400; credencial inválida vira 401 e conta inativa, 403; integridade e concorrência viram 409.
- `OpenApiConfig.java`: configura o bearer JWT no OpenAPI.
- `Constants.java`: expressões `SECURITY_ROLE_ADMIN` e `SECURITY_ROLE_STAFF`.
- `AdminInitializer.java`: garante o administrador quando `ADMIN_EMAIL` e `ADMIN_PASSWORD` são informados.

## Observações

CORS usa os valores padrão permitidos, sem lista dedicada de origens, e deve ser revisado antes de uma publicação. Consulte o README na raiz de `catalogo-api` para o módulo completo.
