# DTOs da API

Define os contratos de entrada e saída usados pelos controllers. Os records transportam os dados sem expor as entidades JPA.

## Contratos existentes

- `CredentialRequest`: e-mail e senha.
- `CreateUsuarioRequest`: nome, e-mail e senha de 6 a 72 caracteres; não há campo de perfil.
- `AuthResponse`: token JWT.
- `UsuarioResponse`: id, nome, e-mail, situação e perfis.
- `BookRequest`: `titulo`, ISBN, editora, ano, `numeroPaginas` (mínimo 1), sinopse, capa, gênero, autores e exemplares.
- `BookResponse` e `BookSummaryResponse`: detalhe e resumo paginado do livro.
- `AuthorRequest`, `AuthorResponse`, `GenreRequest` e `GenreResponse`: autores e gêneros.
- `CreateLoanRequest`: livro a reservar.
- `PickupRequest`: código de retirada.
- `LoanResponse`: status, prazos, renovações, multa, último resultado de pagamento e eventos.
- `LoanSummaryResponse`: totais exibidos no balcão.

## Dependências e cuidados

Depende de Jakarta Validation e dos enums de `model.enums`. O máximo de 5.000 páginas é regra de negócio e fica em `BookServiceImpl`. Alterar campos muda o contrato com `../catalogo-web`; atualize os testes e a documentação na mesma alteração.
