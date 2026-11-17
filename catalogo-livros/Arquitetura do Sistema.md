# Arquitetura do Sistema

## Visão geral

Dois projetos independentes que se comunicam por HTTP sob `/api`:

| Módulo | Tecnologia | Responsabilidade |
| --- | --- | --- |
| `catalogo-api` | Java 25, Spring Boot 4.1, JPA, Flyway, Spring Security, H2 | Regras de negócio, persistência e autorização |
| `catalogo-web` | Angular 22 standalone, TypeScript, RxJS | Telas e integração com a API |

Em desenvolvimento, `proxy.conf.json` faz o servidor Angular (4200) encaminhar `/api/**` para a API (8081).

## Camadas da API

```
HTTP -> SecurityFilter -> Controller -> Service (interface) -> ServiceImpl -> Repository -> Banco
                             │                                     │
                        dto (HTTP)                     service.dto (contratos) + model (JPA)
```

| Pacote | Responsabilidade |
| --- | --- |
| `controller` | Adapta HTTP: recebe DTO, chama o service e devolve o status. Sem regra de negócio. |
| `dto` | Formato do JSON de entrada e saída, com Bean Validation. |
| `service` | Interfaces dos serviços. |
| `service.impl` | Regras de negócio e transações. |
| `service.expiration` | Expira reservas vencidas e libera estoque em transações dedicadas. |
| `service.mapper` | Mapeia empréstimos e adapta a paginação do Spring. |
| `service.dto` | Contratos internos (`Command` e `View`), sem entidades JPA nem tipos HTTP. |
| `repository` | Acesso ao banco com Spring Data JPA. |
| `model` | Entidades JPA e enums. Guardam regras do próprio estado, como `Book.reserveCopy()`. |
| `security` | Geração e validação do JWT, filtro de requisição e usuário autenticado. |
| `config` | Segurança, tratamento central de erros, OpenAPI e criação do administrador. |

`ServiceBoundaryTest` (ArchUnit) reprova o build se um controller usar um repository ou `service.impl`, ou se um contrato depender de entidade JPA.

## Modelo de dados

```
usuarios 1──N usuario_perfis
usuarios 1──N loans N──1 books N──1 genres
                │          └──N:N── authors (book_authors)
                ├──1:N loan_events
                └──1:N fine_payments
```

- `books`: `titulo`, `numero_paginas`, `isbn`, `publisher`, `publication_year`, `synopsis`, `cover_url`, `status`, `total_copies`, `loaned_copies`.
- Exemplares disponíveis = `total_copies - loaned_copies`.
- `loans`: reserva e empréstimo são a mesma linha, mudando de `status`.
- `loan_events`: linha do tempo exibida ao leitor.
- `src/main/resources/db/migration` é a autoridade do schema; o Hibernate apenas valida (`ddl-auto=validate`).

## Principais endpoints

| Método e rota | Acesso | Função |
| --- | --- | --- |
| `POST /api/usuarios` | Público | Cadastro de leitor |
| `POST /api/auth/login` | Público | Devolve o JWT |
| `GET /api/livros`, `GET /api/livros/{id}` | Público | Catálogo (só livros ativos) |
| `GET /api/generos`, `GET /api/autores` | Público | Dados dos filtros |
| `POST /api/emprestimos` | Autenticado | Reservar (exige `Idempotency-Key`) |
| `GET /api/emprestimos`, `GET /api/emprestimos/{id}` | Dono | Histórico do leitor |
| `PATCH /api/emprestimos/{id}/cancelar` | Dono | Cancelar reserva |
| `PATCH /api/emprestimos/{id}/renovar` | Dono | Renovar |
| `POST /api/emprestimos/{id}/multa/pagamento` | Dono | Pagamento simulado |
| `POST /api/livros`, `PUT /api/livros/{id}` | Equipe | Cadastrar e editar livro |
| `PATCH /api/livros/{id}/inativar`, `/reativar` | Equipe | Inativar e reativar livro |
| `GET /api/livros/acervo`, `GET /api/livros/acervo/{id}` | Equipe | Acervo, incluindo inativos |
| `/api/acervo/autores`, `/api/acervo/generos` | Equipe | CRUD de autores e gêneros |
| `GET /api/balcao/retiradas/{codigo}` | Equipe | Consultar código |
| `POST /api/balcao/retiradas` | Equipe | Confirmar retirada |
| `POST /api/balcao/emprestimos/{id}/devolucao` | Equipe | Registrar devolução |
| `GET /api/balcao/emprestimos`, `GET /api/balcao/resumo` | Equipe | Operação do balcão |
| `/api/admin/usuarios` | `ADMIN` | Perfis e inativação |

Erros seguem o formato `{ "message": "..." }` (validação inclui `errors` por campo): 400 dados inválidos, 401 sem autenticação, 403 sem permissão, 404 não encontrado (inclusive empréstimo de outro leitor), 409 regra de negócio bloqueada.

## Segurança

1. O login valida a senha (BCrypt) e devolve um JWT com e-mail (`sub`), perfis e validade de 2 horas.
2. O interceptor Angular envia `Authorization: Bearer` apenas nas chamadas privadas da API.
3. `SecurityFilter` valida a assinatura e **recarrega o usuário do banco a cada requisição**. Assim, promoção e inativação valem na hora, mesmo com token antigo.
4. `SecurityConfig` protege as rotas, e `@PreAuthorize` restringe os perfis nos controllers.
5. A posse do empréstimo é verificada na consulta (`findByIdAndReader_Id`).
6. A inicialização cria a conta administradora para um e-mail livre. Um administrador existente conserva sua senha. Um e-mail pertencente a uma conta comum bloqueia o startup.

Os perfis lidos do token no navegador servem somente para esconder menus. O `roleGuard` melhora a navegação, mas quem bloqueia é a API.

## Concorrência e consistência

- `reserve` trava o usuário e depois o livro (`PESSIMISTIC_WRITE`). Isso impede que dois leitores peguem o último exemplar e que o mesmo leitor burle o limite com requisições simultâneas.
- `@Version` em `Book` e `Loan` detecta alterações concorrentes, respondidas com 409.
- `Idempotency-Key` tem restrição única por leitor: repetir a chamada devolve a mesma reserva.
- Reserva e inativação da conta usam o mesmo bloqueio de usuário. A reserva verifica se a conta continua ativa depois de obter o bloqueio.
- O agendador seleciona até 100 reservas vencidas por ciclo, por padrão a cada minuto. Para cada item, a expiração bloqueia e revalida a reserva, depois bloqueia o livro e libera uma cópia na mesma transação. Uma execução repetida ignora a reserva já processada.
- Uma nova tentativa de reserva trata antes as pendências vencidas do livro ou do leitor, inclusive quando a última cópia está retida por uma reserva vencida. Esse processamento tem transação própria e termina antes dos bloqueios da nova reserva.
- Consultas do histórico e do balcão são somente leitura. Entre o vencimento e o processamento, a linha ainda pode aparecer como `RESERVED`, mas o código já está oculto no histórico e a API devolve `canPickup=false` e `canCancel=false`. A confirmação no balcão verifica a data sob bloqueio e rejeita o código vencido.

## Frontend

```
src/app
├── core       sessão, guards, interceptor, API_URL e tratamento de erro HTTP
├── shared     capa do livro, layout de autenticação e formatação
└── features   auth, books, loans, desk (balcão), collection (acervo), admin
    └── <feature>/{pages, data-access, models, components}
```

- Componentes standalone, rotas lazy-loaded, formulários reativos e estado com signals.
- Clientes de domínio ficam em `data-access`, e o cliente de autenticação fica em `core/auth`. Um interceptor central aplica 15 segundos às chamadas da API.
- `apiErrorMessage` transforma o erro da API na mensagem exibida na tela.
- O catálogo cancela a busca anterior ao enviar novos filtros. A rota de detalhe recarrega o livro quando o ID muda e controla a chave de idempotência por livro visitado.
- O componente de retirada guarda o código da prévia e exige nova consulta após a edição. As ações disponíveis e o limite de renovação vêm do contrato da API.

## Limitações conhecidas

- H2 em arquivo atende à demonstração, não a produção com várias instâncias.
- O pagamento é simulado por `MockPaymentGateway`, ativo nos perfis `dev` e `test`.
- O código de retirada é guardado em texto no banco para poder ser reexibido ao leitor. Ele vale só para aquela reserva, é de uso único e só funciona nas mãos de um atendente autenticado.
- Perfis alterados só mudam os menus depois de um novo login, embora a API já aplique a mudança na hora.
- Não há fila de espera para livros esgotados.
