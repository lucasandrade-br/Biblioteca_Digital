# Catálogo de Livros

Sistema de biblioteca: o leitor consulta o acervo, reserva um exemplar pela internet, retira no balcão com um código/QR e acompanha prazos, renovações e multas. Adaptado do projeto-base `ingresso`, mantendo a arquitetura (Spring Boot + Angular) e trocando o domínio de venda de ingressos pelo de empréstimo de livros.

## Integrantes

- [Nome do integrante 1]
- [Nome do integrante 2]
- [Nome do integrante 3]

## Tema

| Nº | Tema | O que cadastrar | Campo textual | Campo numérico | Regra de negócio | Rota base |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Catálogo de livros | Livros de uma biblioteca | `titulo` | `numeroPaginas` | Máximo de 5.000 páginas | `/livros` |

Os campos existem com esses nomes na entidade `Book`, na tabela `books` (`titulo`, `numero_paginas`), nos DTOs e nos formulários. O ID é gerado automaticamente. A regra das 5.000 páginas fica em `BookServiceImpl`, com reforço no formulário e no banco. A rota base na API é `/api/livros` (o prefixo `/api` segue o projeto-base) e na tela é `/livros`.

## Perfis de acesso

| Perfil | Como é criado | O que pode fazer |
| --- | --- | --- |
| Visitante | Sem login | Consultar o catálogo |
| `USER` (leitor) | Cadastro público (sempre este perfil) | Reservar, cancelar, renovar, pagar multa e ver **apenas os próprios** empréstimos |
| `LIBRARIAN` (bibliotecário) | Promovido pelo administrador | Gerenciar acervo, autores e gêneros; registrar retirada e devolução |
| `ADMIN` | Variáveis `ADMIN_EMAIL` e `ADMIN_PASSWORD` | Tudo do bibliotecário, mais gerenciar usuários |

A autorização é sempre feita no servidor. Os perfis lidos do JWT no navegador só escondem menus.

## Funcionalidades

- Cadastro, login com JWT e controle de acesso por perfil.
- Catálogo público com busca por título, autor ou ISBN, filtro por gênero e disponibilidade, e paginação.
- CRUD de livros (com inativação), autores e gêneros.
- Fluxo principal de empréstimo com disponibilidade, status e histórico por usuário.
- Comprovante de retirada com QR Code.
- Balcão: consulta de código, retirada, devolução e resumo da operação.
- Multa por atraso com pagamento simulado.
- Administração de usuários (promover bibliotecário, inativar e reativar conta).
- Documentação da API em `http://localhost:8081/swagger-ui.html`.

## Fluxo principal

```
RESERVED ──retirada no balcão──> ACTIVE ──devolução no balcão──> RETURNED
   │                               └─ renovar (até 2x)                └─ multa, se atrasou
   ├─ leitor cancela ──> CANCELLED
   └─ passou de 48h  ──> EXPIRED
```

## Regras de negócio

**Reserva**

- O leitor precisa ter o perfil `USER`.
- O livro precisa estar ativo e ter exemplar disponível.
- Máximo de 3 livros em aberto (reservados + emprestados) por leitor.
- O leitor não pode ter dois empréstimos em aberto do mesmo livro.
- Leitor com empréstimo em atraso ou multa pendente não reserva.
- Dois leitores não conseguem pegar o último exemplar ao mesmo tempo (lock pessimista).
- A mesma `Idempotency-Key` não cria uma segunda reserva, evitando duplicidade por clique repetido.

**Retirada, cancelamento e expiração**

- A retirada fica disponível por 48 horas. Ao vencer o prazo, o código deixa de valer. O estoque é liberado pela rotina agendada ou pela próxima tentativa de reserva relacionada ao livro ou ao leitor.
- O código de retirada é de uso único.
- O cancelamento só é permitido antes da retirada e só pelo dono da reserva.

**Empréstimo**

- O prazo é de 14 dias a partir da retirada.
- São até 2 renovações de 14 dias. Não é possível renovar com atraso ou com multa pendente.
- A API informa datas, limite de renovações e elegibilidade. As telas usam esses dados para mostrar prazos e oferecer ações.

**Multa**

- R$ 2,00 por dia de atraso (dia iniciado conta como dia inteiro), gerada na devolução.
- O pagamento é **simulado**: nenhum dinheiro é movimentado. Um pagamento recusado pode ser tentado de novo.

**Acervo**

- `titulo` é obrigatório. `numeroPaginas` deve estar entre 1 e 5.000.
- O ISBN é único e validado pelo dígito verificador do ISBN-13.
- O ano de publicação não pode ser futuro.
- O total de exemplares não pode ficar abaixo dos que estão em circulação.
- Livro com reserva ou empréstimo em aberto não pode ser inativado.
- Autor ou gênero com livros vinculados não pode ser excluído. Nomes não se repetem.

**Usuários**

- O e-mail é único, sem diferenciar maiúsculas.
- O administrador não pode inativar a própria conta.
- Usuário com empréstimo em aberto não pode ser inativado.
- Conta inativa perde o acesso imediatamente, mesmo com token válido.

## Adaptações em relação ao projeto-base

| Projeto-base (ingressos) | Catálogo de Livros | Por quê |
| --- | --- | --- |
| Evento | `Book` | É o item oferecido no catálogo |
| Categoria e estoque de ingressos | `totalCopies` / `loanedCopies` | Controle de disponibilidade por exemplares |
| Produtor | Bibliotecário (`LIBRARIAN`) | Quem mantém o acervo |
| Pedido (`PurchaseOrder`) | Empréstimo (`Loan`) | O fluxo principal deixa de ser compra |
| Reserva de 15 minutos até pagar | Reserva de 48 horas até retirar | Tempo realista para ir à biblioteca |
| Pagamento do pedido (mock) | Pagamento da multa (mock) | Pegar um livro emprestado é gratuito; só o atraso gera cobrança |
| QR do ingresso | QR do comprovante de retirada | Equivalente direto: apresentado uma única vez ao atendente |
| Validação de entrada | Confirmação de retirada no balcão | Mesmo papel: a equipe valida o código e consome o uso |
| Tentativa de pagamento | `FinePayment` e linha do tempo `LoanEvent` | Histórico de status exigido pelo fluxo |

### Recursos do projeto-base que não foram utilizados

- **Leitura do QR pela câmera:** o QR contém o próprio código de retirada, que o atendente digita no balcão (um leitor USB de QR também funciona, pois digita como um teclado). A biblioteca atende um leitor por vez, sem a fila de um evento que justificaria o scanner por câmera.
- **Validação offline e manifestos:** a entrada de um evento pode ocorrer em local sem internet. O balcão é um ponto fixo, e a retirada precisa da disponibilidade atualizada no servidor.
- **Assentos e setores:** exemplares de um mesmo livro são equivalentes; não há "lugar marcado".
- **Reembolso:** não há compra a reembolsar. A multa só é paga depois de gerada.
- **Convites de produtor e equipe por token:** a promoção é feita direto pelo administrador, pois a equipe da biblioteca é interna.
- **Relatório financeiro por evento:** substituído pelo resumo do balcão.
- **E-mail mock e PWA:** não são necessários ao fluxo; o leitor acompanha tudo em "Meus empréstimos".

## Pré-requisitos

- JDK 25
- Acesso ao Maven Central e ao registro npm na primeira instalação em máquina sem cache.
- Node.js 22.22.3+ na série 22, 24.15.0+ na série 24, ou 26+, e npm

## Como executar

**1. API** (primeiro terminal, PowerShell):

```powershell
cd catalogo-api
$secret = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secret)
$rng.Dispose()
$env:TOKEN_SECRET = [BitConverter]::ToString($secret).Replace('-', '').ToLowerInvariant()
$env:ADMIN_EMAIL = "admin@biblioteca.com"
$env:ADMIN_PASSWORD = "escolha-uma-senha"
.\maven-local.ps1 spring-boot:run
```

No PowerShell, o script usa o JDK 25 indicado em `JAVA_HOME` ou o JDK 25 da extensão Java da Red Hat no VS Code. O Maven e as bibliotecas baixadas ficam em `catalogo-api/.maven-cache/`. O `pom.xml` declara as dependências e o Maven Wrapper seleciona a versão do Maven.

No Linux ou macOS:

```sh
cd catalogo-api
export TOKEN_SECRET="$(openssl rand -hex 32)"
export ADMIN_EMAIL="admin@biblioteca.com"
export ADMIN_PASSWORD="escolha-uma-senha"
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8081`. O banco H2 fica em `catalogo-api/data/` e o Flyway cria as tabelas e 14 livros de exemplo. `TOKEN_SECRET` precisa ter ao menos 64 bytes em UTF-8. Use um e-mail de administrador livre no primeiro boot. O e-mail de uma conta comum causa erro na inicialização, e o de um administrador existente mantém a senha cadastrada. Os ISBNs e números de páginas dos livros de exemplo são ilustrativos.

**2. Frontend** (segundo terminal, PowerShell):

```powershell
cd catalogo-web
.\web-local.ps1 install
.\web-local.ps1 start
```

O script procura um Node.js compatível em `catalogo-web/.runtime/` e depois no PATH da sessão. No Linux, macOS ou Git Bash:

```sh
cd catalogo-web
npm ci --cache .npm-cache
npm start
```

O `npm ci` instala as versões do `package-lock.json` em `catalogo-web/node_modules/`. O cache fica em `catalogo-web/.npm-cache/`. Abra `http://localhost:4200`.

**3. Preparar os usuários**

1. Crie uma conta em "Criar conta": ela será de leitor.
2. Crie uma segunda conta para ser a bibliotecária.
3. Entre como administrador, abra "Usuários" e clique em "Tornar bibliotecário" na segunda conta.
4. A segunda conta precisa entrar novamente para ver os menus "Balcão" e "Acervo".

## Como testar

```sh
cd catalogo-api
./mvnw test        # no PowerShell: .\maven-local.ps1 test
```

```sh
cd catalogo-web
npm test -- --watch=false  # no PowerShell: .\web-local.ps1 test --watch=false
npm run build             # no PowerShell: .\web-local.ps1 build
```

Os testes cobrem cadastro, autenticação, reserva, retirada, devolução e histórico, além de bloqueios por perfil, concorrência, idempotência e expiração. O [roteiro de validação](docs/README.md#roteiro-de-validação) descreve a demonstração em banco novo.

### Simular atraso e multa

O prazo real é de 14 dias, então para demonstrar a multa altere a data no banco:

1. Antes de subir a API, rode `$env:H2_CONSOLE_ENABLED = "true"` (ou `export H2_CONSOLE_ENABLED=true`).
2. Abra `http://localhost:8081/h2-console`, com JDBC URL `jdbc:h2:file:./data/catalogo`, usuário `sa` e senha vazia.
3. Com um empréstimo já retirado, execute (troque o `1` pelo número do empréstimo):

```sql
UPDATE loans SET due_at = DATEADD('DAY', -3, CURRENT_TIMESTAMP) WHERE id = 1;
```

Para testar um pagamento recusado, suba a API com `$env:CATALOGO_PAYMENT_MOCK_RESULT = "DECLINED"`.

## Documentação

- [API](catalogo-api/README.md)
- [Frontend](catalogo-web/README.md)
- [Documentação do projeto](docs/README.md)
- [Arquitetura do sistema](docs/Arquitetura%20do%20Sistema.md)
