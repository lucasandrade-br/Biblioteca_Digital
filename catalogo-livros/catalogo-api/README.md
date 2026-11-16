# catalogo-api

API REST Spring Boot para contas e perfis, acervo de livros, empréstimos, operação de balcão e pagamento simulado de multas.

## Execução e validação

Siga as instruções de execução e teste no [README principal](../README.md#como-executar). A API usa JDK 25 e a versão indicada pelo Maven Wrapper.

Configure `TOKEN_SECRET` com ao menos 64 bytes UTF-8 antes de iniciar; a API falha no startup se estiver ausente ou curto. O datasource padrão é H2 em arquivo (`./data/catalogo`), e o Flyway aplica as migrations antes de o Hibernate validar o schema. O console H2 em `/h2-console` fica desabilitado por padrão. Swagger fica em `/swagger-ui.html` e `/v3/api-docs`.

Configuração por ambiente: `TOKEN_SECRET`, `CATALOGO_DB_URL`, `CATALOGO_DB_USERNAME`, `CATALOGO_DB_PASSWORD`, `H2_CONSOLE_ENABLED`, `JPA_SHOW_SQL`, `PORT`, `CATALOGO_PAYMENT_MOCK_RESULT` (`APPROVED` ou `DECLINED`), `ADMIN_EMAIL` e `ADMIN_PASSWORD`.

Com `ADMIN_EMAIL` e `ADMIN_PASSWORD` preenchidos, o inicializador cria o administrador quando o e-mail está livre. Se a conta já tem o perfil `ADMIN`, a inicialização usa a conta e sua senha cadastrada. Se o e-mail pertence a uma conta sem esse perfil, a inicialização falha e informa que outro e-mail deve ser configurado.

## Responsabilidades e dependências

- `controller`: contratos HTTP de autenticação, usuários, livros, autores, gêneros, empréstimos e balcão.
- `service`: interfaces dos casos de uso e do gateway de pagamento; `service.impl`: implementações e o mock.
- `service.expiration`: processamento das reservas vencidas. Cada reserva é revalidada e liberada em uma transação própria.
- `service.dto`: comandos, consultas e resultados usados entre controllers e services.
- `service.mapper`: conversão de `Loan` para `LoanView` e adaptação de `PageQuery` para a paginação do Spring usada pelo acervo, balcão, empréstimos e usuários.
- `repository`: consultas JPA de usuários, livros, autores, gêneros e empréstimos.
- `model`: entidades e enums do domínio. `LoanStatus.OPEN_STATUSES` define os estados considerados em aberto pelo acervo, empréstimos e usuários.
- `dto`: contratos de requisição e resposta validados.
- `security`: JWT, filtro stateless e principal do Spring Security.
- `config`: cadeia de segurança, CORS, constantes de perfil, tratamento de exceções, OpenAPI e administrador inicial.

Dependências principais: Spring Boot 4.1.1, Java 25, Spring MVC, Security, Validation, Data JPA, Flyway, H2, Auth0 `java-jwt` 4.4.0 e Springdoc 2.8.13. Escopos completos em `pom.xml`.

Os controllers dependem das interfaces em `service`, não das implementações. `ServiceBoundaryTest` verifica esses limites com ArchUnit.

## Entradas e fluxos

- Entrada de processo: `src/main/java/org/example/catalogo/CatalogoApiApplication.java`.
- `POST /api/usuarios`: público; valida os dados, verifica o e-mail, codifica a senha e cria sempre um leitor (`USER`).
- `POST /api/auth/login`: público; autentica e responde `{ "token": "..." }`.
- `GET /api/livros` e `GET /api/livros/{id}`: catálogo público, somente com livros ativos.
- `POST /api/livros`, `PUT /api/livros/{id}`, `PATCH /api/livros/{id}/inativar` e `/reativar`: gestão do acervo pela equipe.
- `GET /api/livros/acervo` e `GET /api/livros/acervo/{id}`: visão da equipe, incluindo inativos.
- `GET /api/generos` e `GET /api/autores`: públicos; alterações em `/api/acervo/generos` e `/api/acervo/autores` são da equipe.
- `POST /api/emprestimos`: reserva de leitor ativo que exige `Idempotency-Key`. A reserva e a inativação serializam as alterações pelo mesmo bloqueio na conta do leitor. Se a reserva é gravada primeiro, o empréstimo aberto impede inativar a conta. Se a inativação ocorre primeiro, a reserva é recusada.
- `GET /api/emprestimos` e `GET /api/emprestimos/{id}`: histórico e detalhe restritos ao dono, em transações de leitura. As respostas incluem `pickupDeadline`, `dueAt`, `canPickup`, `canCancel`, `canRenew` e `maxRenewals`.
- `PATCH /api/emprestimos/{id}/cancelar` e `/renovar`; `POST /api/emprestimos/{id}/multa/pagamento`: operações do dono.
- `GET /api/balcao/retiradas/{codigo}`, `POST /api/balcao/retiradas`, `POST /api/balcao/emprestimos/{id}/devolucao`, `GET /api/balcao/emprestimos` e `GET /api/balcao/resumo`: operação da equipe.
- `/api/admin/usuarios`: listar, promover ou remover bibliotecário, inativar e reativar; exige `ADMIN`.

O frontend está em `../catalogo-web`. A arquitetura completa está em [Arquitetura do Sistema](../docs/Arquitetura%20do%20Sistema.md).

## Arquivos críticos e observações

- `src/main/resources/application.properties`: configuração por ambiente; segredo sem valor padrão.
- `src/main/resources/db/migration`: `V1` usuários, `V2` acervo, `V3` empréstimos e `V4` dados iniciais.
- `config/SecurityConfig.java`, `security/SecurityFilter.java` e `security/TokenServiceImpl.java`: fronteira de segurança.
- `service/impl/LoanServiceImpl.java`: reserva com locks, limites, cancelamento, renovação e multa.
- `service/expiration/ReservationExpiration.java`: agenda a expiração a cada 60 segundos por padrão e também processa reservas vencidas do leitor ou livro antes de uma nova tentativa de reserva. O intervalo pode ser definido por `CATALOGO_LOANS_EXPIRATION_SCAN_MS`.
- `model/LoanPolicy.java`: concentra os prazos e as condições de retirada e renovação.
- `service/impl/DeskServiceImpl.java`: retirada, devolução e cálculo da multa.
- `service/impl/BookServiceImpl.java`: ISBN e limite de 5.000 páginas.
- Os testes cobrem cadastro, login, perfis, acervo, fluxo de empréstimo, concorrência, idempotência, multa, expiração e limites entre camadas.
- O pagamento é simulado e o mock só existe nos perfis `dev` e `test`. Nenhuma rota permite ao leitor escolher o resultado.
- H2 em arquivo é uma escolha local, não uma configuração de produção.
