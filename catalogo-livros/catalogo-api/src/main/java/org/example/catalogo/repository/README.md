# Repositórios

Camada de persistência baseada em Spring Data JPA para usuários, acervo e empréstimos.

## Dependências e fluxos

- Depende das entidades de `model`, do Spring Data JPA e do datasource configurado em `src/main/resources/application.properties`.
- `UsuarioRepository`: busca por e-mail sem diferenciar maiúsculas, com os perfis carregados; `findWithLockById` serializa as reservas de um mesmo leitor.
- `BookRepository`: `search` filtra por status, gênero, disponibilidade e termo em título, ISBN ou autor; `findWithLockById` protege os exemplares.
- `GenreRepository` e `AuthorRepository`: buscam por nome para impedir duplicidade.
- `LoanRepository`: idempotência por leitor e chave, histórico do dono, busca por código de retirada, reservas vencidas, verificações de limite, atraso e multa, além dos totais do balcão.

Os métodos `findWithLock*` usam `PESSIMISTIC_WRITE` e precisam rodar dentro de uma transação do service.
