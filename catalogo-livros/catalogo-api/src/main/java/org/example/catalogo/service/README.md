# Serviços da API

Camada de aplicação entre controllers, persistência, codificação de senha e gateway de pagamento. As regras de negócio ficam aqui.

## Contratos e implementações

- As interfaces públicas ficam neste pacote; as implementações ficam em `service.impl`.
- Controllers dependem apenas das interfaces, e chamadas entre serviços também usam interfaces.
- Os contratos ficam em `service.dto` e não expõem entidades JPA, tipos HTTP, `UserSS`, `Pageable` nem repositories.
- `service.mapper.LoanViewMapper` converte `Loan` em `LoanView` para os dois services que precisam disso, sem que uma implementação dependa da outra.
- `ServiceBoundaryTest` usa ArchUnit para garantir esses limites.

## Implementações e dependências

- `UsuarioService`: cria leitor com e-mail normalizado e BCrypt, lista usuários, promove ou remove bibliotecário, inativa, reativa e garante o administrador.
- `impl.UserDetailsServiceImpl`: localiza o usuário por e-mail e constrói `UserSS`.
- `BookService`: catálogo público e acervo; valida ISBN-13, duplicidade, ano e o máximo de 5.000 páginas; bloqueia a inativação com empréstimo em aberto.
- `AuthorService` e `GenreService`: impedem nomes repetidos e exclusão com livros vinculados.
- `LoanService`: reserva idempotente com locks, limite de 3 livros, bloqueio por duplicidade, atraso ou multa; cancelamento, até 2 renovações, pagamento simulado e expiração das reservas após 48 horas.
- `DeskService`: consulta por código, retirada de uso único (prazo de 14 dias), devolução com multa de R$ 2,00 por dia e resumo.
- `PaymentGateway`: fronteira substituível; `impl.MockPaymentGateway` só existe em `dev` e `test`, e o resultado vem da configuração, nunca do leitor.
- Dependências internas: `repository`, `model`; externas: Spring Data, Spring Security e `PasswordEncoder`.

## Observações técnicas

O cadastro público sempre atribui `USER`. Empréstimo de outro leitor responde "não encontrado", para não revelar que ele existe. A ordem dos locks na reserva é usuário e depois livro. As expirações rodam a cada minuto e também antes de reservar ou listar.
