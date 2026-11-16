# Modelo de domínio persistido

Contém usuários e perfis, livros, autores, gêneros, empréstimos, eventos do empréstimo e tentativas de pagamento de multa: `Usuario`, `Book`, `Author`, `Genre`, `Loan`, `LoanEvent` e `FinePayment`. Os enums ficam em `enums`.

## Dependências e relações

`Book` pertence a um `Genre` e tem vários `Author`. Guarda `titulo`, `numeroPaginas`, `totalCopies` e `loanedCopies`; `reserveCopy()` e `releaseCopy()` protegem a disponibilidade. `Loan` liga um `Usuario` a um `Book` e percorre `RESERVED`, `ACTIVE`, `RETURNED`, `CANCELLED` ou `EXPIRED`. Cada mudança acrescenta um `LoanEvent`, que forma o histórico do leitor. `FinePayment` registra cada tentativa de pagamento simulado.

## Arquivos críticos e observações

`V1__usuarios.sql`, `V2__acervo.sql` e `V3__emprestimos.sql` criam o schema, e o Hibernate apenas o valida. `@Version` em `Book` e `Loan` detecta alterações concorrentes. O status do empréstimo muda somente pelos métodos da própria entidade.
