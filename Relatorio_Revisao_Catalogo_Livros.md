# Relatório de revisão técnica
## Catálogo de livros da biblioteca · 15 de novembro de 2026

### Parecer

O projeto tem uma **boa base para o trabalho acadêmico**. A API Java segue as camadas pedidas pelo professor e o Angular está organizado por funcionalidade. O domínio foi adaptado para biblioteca com disponibilidade, reserva, retirada, devolução e histórico. Os pontos mais urgentes estão em **como o administrador inicial é criado**, **qual reserva o balcão confirma** e **como inativação e reserva se comportam quando executadas ao mesmo tempo**.

A prontidão para uso integrado continua pendente. O frontend compilou, seus 20 testes passaram e três testes de diagnóstico reproduziram problemas específicos com HTTP simulado. A suíte Java parou antes da compilação porque o Spring Boot 4.1.1 exigido no projeto está ausente do cache local, e a tentativa de download neste ambiente falhou na resolução do endereço do Maven Central. A análise da API e da integração com o banco é, portanto, uma revisão do código e dos testes escritos.

### Escopo e como ler as referências

Revisei a cópia `catalogo-livros`, incluindo os dois módulos, configuração, migrações, testes, documentação da arquitetura e o material inicial em `Minha ideia`. Usei os requisitos do professor registrados em `Minha ideia/Contexto Geral.md` como referência acadêmica e as regras em `Minha ideia/Material de Documentação/Regras Funcionais Iniciais.md` para identificar diferenças de escopo. Os caminhos Java abreviados partem de `catalogo-api/src/main/java/org/example/catalogo/` e os caminhos Angular partem de `catalogo-web/src/app/`. O número após o nome indica a linha de início do trecho relevante.

## 1. Backend · Java e Spring Boot

| Módulo ou camada | O que está bem desenvolvido | O que vale melhorar |
|---|---|---|
| Controllers, DTOs e contratos | Os controllers adaptam HTTP, recebem DTOs validados e delegam aos services. Mapeadores separam respostas públicas das entidades JPA. ArchUnit verifica dependências entre pacotes. | As páginas da API são serializadas a partir de `PageImpl`, o que acopla o contrato HTTP à representação do Spring. Um DTO JSON explícito para paginação dá estabilidade ao Angular. |
| Acervo, autores e gêneros | Há distinção entre catálogo público e gestão interna. O serviço valida ISBN, existência de autor e gênero e impede reduzir o total de cópias abaixo das que estão em circulação. O banco reforça restrições de ISBN e quantidade. | O acervo conhece o repositório de empréstimos para impedir inativação de livros em circulação. É uma dependência legítima, mas deve permanecer centralizada no caso de uso. O resumo de catálogo acessa relações JPA durante o mapeamento, com possibilidade de consultas adicionais por livro. |
| Circulação, balcão e multas | A reserva usa transação, bloqueia leitor e livro e reusa a reserva quando a mesma chave de idempotência é repetida. A restrição única no banco reforça essa proteção. Retirada, devolução e pagamento registram eventos. O gateway simulado tem interface própria. | `LoanServiceImpl` reúne reserva, cancelamento, renovação, cobrança, consulta e expiração. A devolução está no balcão, que por sua vez chama o serviço de empréstimo para expirar reservas. A extração de uma responsabilidade de expiração é o recorte com benefício mais direto. |
| Usuários, perfis e autenticação | O cadastro público atribui perfil de leitor no servidor. Há BCrypt, verificação de JWT, recarga da conta para aplicar mudanças de perfil e verificação de posse nas consultas de empréstimo. | A criação do administrador inicial pode promover uma conta existente. A operação de inativação também precisa usar a mesma disciplina de bloqueio da reserva. |
| Persistência | Flyway controla as alterações do esquema, com chaves estrangeiras, restrições de unicidade, checagem de estoque e índices para consultas de empréstimo. O Hibernate valida o esquema. H2 é uma escolha prática para a demonstração. | Separar dados de demonstração do fluxo de migração caso exista uma instalação com acervo real. Medir consultas de catálogo e histórico antes de trocar a estratégia de carregamento. |

### Achados do backend

**B1 · Alta · Uma conta existente pode virar administradora no startup.**  
Quando `ADMIN_EMAIL` e `ADMIN_PASSWORD` estão preenchidos, `AdminInitializer` chama `garantirAdministrador`. Se o e-mail já existe no banco, o serviço acrescenta `ADMIN` à conta existente. A senha configurada é usada apenas na criação. Como o cadastro de leitor é público, um cadastro anterior com o mesmo e-mail ou um reaproveitamento de banco pode elevar a conta com a senha que ela já tinha. A condição exige que o e-mail coincida com o configurado no startup. **Correção:** criar a conta inicial quando o e-mail estiver livre, aceitar a existência de um administrador já configurado e falhar se o e-mail pertencer a uma conta comum. Testar os três cenários. Evidência estática em `config/AdminInitializer.java:27` e `service/impl/UsuarioServiceImpl.java:116`.

**B2 · Alta · Reserva e inativação do leitor podem entrar em conflito.**  
A reserva bloqueia a linha do leitor, mas valida seu perfil e segue sem revalidar se continua ativo. A inativação lê o usuário por `findById`, verifica empréstimos abertos e altera o status. Entre a autenticação inicial, a consulta de empréstimos e a gravação, duas requisições podem observar estados diferentes. O resultado a investigar é uma reserva aceita para um leitor que terminou a operação inativo. **Correção:** obter o mesmo bloqueio de usuário nos dois casos de uso, validar `ativo` sob o bloqueio e adicionar um teste concorrente das duas operações. Risco identificado no código, sem reprodução em banco. Evidências em `service/impl/LoanServiceImpl.java:68`, `service/impl/UsuarioServiceImpl.java:94` e `repository/UsuarioRepository.java:16`.

**B3 · Média · Consultas de empréstimo também escrevem no banco.**  
Listar ou detalhar empréstimos e consultar um código no balcão pode varrer e expirar até 100 reservas vencidas no sistema inteiro. Cada atualização pode bloquear um empréstimo e o livro associado. Isso aumenta o trabalho das consultas e junta a expiração à transação da operação. Se uma consulta lançar uma exceção e a transação reverter, as expirações feitas durante ela também podem reverter. **Correção:** dar à rotina agendada uma transação própria e tornar as consultas somente leitura. Antes de remover a expiração durante uma tentativa de reserva, definir e testar como a disponibilidade será atualizada enquanto o agendamento ainda não rodou. Evidências em `service/impl/LoanServiceImpl.java:175`, `:185`, `:193`, `:214` e `service/impl/DeskServiceImpl.java:55`.

**B4 · Média · Listagens devolvem detalhes e carregam associações que nem toda tela precisa.**  
O histórico e a lista do balcão usam o mesmo mapeador de detalhe. Ele acessa livro, leitor, pagamentos e todos os eventos de cada item paginado. O histórico usa parte desses dados, mas o balcão não precisa da linha do tempo completa para listar itens. O catálogo também transforma autores e gênero a partir das entidades retornadas. **Correção:** medir o SQL com uma página de 20 itens, criar uma projeção resumida para o balcão e carregar detalhes individualmente; no catálogo, otimizar somente as relações que a medição apontar. Possibilidade de consultas N+1 identificada por leitura, sem medição. Evidências em `service/impl/DeskServiceImpl.java:55`, `service/mapper/LoanViewMapper.java:17` e `service/impl/BookServiceImpl.java:144` e `:222`.

**B5 · Média · O contrato de erro e paginação expõe decisões internas.**  
O tratamento central de erros já ajuda a interface. Porém, qualquer `IllegalArgumentException` vira HTTP 409, incluindo status de empréstimo inválido e chave de idempotência em branco. Já um cabeçalho ausente vira 400. A ordenação também passa por nomes de propriedades JPA informados na URL. **Correção:** tipar erros esperados, usar 400 para entrada inválida e 409 para conflito de estado, declarar campos ordenáveis por rota e retornar um formato explícito de página. Manter os campos que o Angular já usa durante a transição. Evidências em `config/ApiExceptionHandler.java:37` e `:51`, `service/impl/DeskServiceImpl.java:131`, `service/impl/LoanServiceImpl.java:240` e `controller/PageHttpMapper.java:20`.

**B6 · Baixa · Há oportunidade de melhorar coesão sem aumentar o número de camadas.**  
A conversão de `PageQuery` para `Pageable` se repete em acervo, usuários, balcão e empréstimos. `LoanServiceImpl` e `DeskServiceImpl` obtêm a hora diretamente, o que dificulta testar o limite exato da retirada e do atraso. **Correção:** reutilizar uma conversão de paginação interna e adotar `Clock` nos fluxos temporais. Se as regras de circulação crescerem, aproximar serviços, mapeadores e políticas desse domínio mantendo a separação entre controller, service e repository pedida pelo professor. Evidências em `service/impl/BookServiceImpl.java:256`, `service/impl/LoanServiceImpl.java:260`, `service/impl/DeskServiceImpl.java:154` e `service/impl/UsuarioServiceImpl.java:149`.

## 2. Frontend · Angular e TypeScript

| Módulo | O que está bem desenvolvido | O que vale melhorar |
|---|---|---|
| `core` | Sessão, interceptor, guards, URL base e mensagens de erro estão centralizados. O Bearer fica nas chamadas privadas da própria API. | O `core` importa tipos de `features/auth`, e `shared` e as funcionalidades também têm dependências cruzadas que merecem critério. Mover os tipos consumidos pelo núcleo para junto da sessão e reduzir a lista manual de rotas públicas que duplica regras do backend. |
| `books` e `loans` | Catálogo e histórico têm clientes HTTP próprios, tipos de resposta, paginação, mensagens de retorno e telas carregadas sob demanda. | Tornar a busca resistente a respostas fora de ordem, recarregar o detalhe ao trocar de livro e remover as constantes de duração e renovação duplicadas da API. |
| `desk` | O balcão tem uma prévia de livro, leitor e situação antes de confirmar. Há consulta, devolução, resumo e filtro de estado no mesmo fluxo operacional. | Vincular a confirmação à prévia consultada. Separar a retirada da listagem quando a página crescer ou as duas partes passarem a mudar de forma independente. |
| `collection` e `admin` | A edição usa formulários reativos, valida campos e apresenta erros da API. A gestão do acervo e de contas está em funcionalidades distintas e tem rotas protegidas. | Permitir tratar erros de validação no próprio campo. O helper atual agrega erros de múltiplos campos numa mensagem geral. Separar o template do editor quando mudanças em validação e layout começarem a se misturar. |
| `shared` e navegação | Componentes de capa e formatação são reaproveitados. O projeto usa rotas lazy-loaded, sinais, rótulos de campo e regiões de aviso acessíveis. | Migrar estilos específicos das páginas para junto dos componentes ao modificá-las. `styles.css` reúne 728 linhas de estilos globais; convém manter nele apenas regras realmente compartilhadas ao longo da evolução. |

### Achados do frontend

**F1 · Alta · A confirmação no balcão pode enviar um código diferente do exibido.**  
Após consultar A, a prévia continua visível se o atendente editar o campo para B. O botão fica disponível e o POST usa o valor atual do formulário. Se B for válido, o backend pode registrar a retirada de B enquanto a tela apresenta os dados de A. **Correção:** guardar o código normalizado junto ao resultado da consulta e invalidar a prévia assim que o campo mudar. Manter o botão desabilitado até consultar o novo valor. Reproduzido com HTTP simulado. Evidência em `features/desk/pages/desk.ts:33`, `:154` e `:175`.

**F2 · Média · Uma busca antiga pode sobrescrever a mais recente.**  
Cada submissão cria uma requisição independente. No diagnóstico, a segunda busca respondeu primeiro e exibiu a lista vazia; a resposta antiga chegou depois e recolocou um livro na tela com o filtro novo ainda preenchido. **Correção:** cancelar a requisição anterior com `switchMap` ou descartar respostas que já não correspondem à última solicitação. Reproduzido com HTTP simulado. Evidência em `features/books/pages/catalog.ts:96`.

**F3 · Média · A rota pode mudar sem atualizar o detalhe do livro.**  
O componente lê `route.snapshot` apenas no `ngOnInit`. Ao navegar de `/livros/41` para `/livros/42` na mesma configuração de rota, a tela permaneceu com o livro 41 e não solicitou o 42. **Correção:** observar `paramMap`, carregar o novo ID e limpar a prévia de reserva e a chave de idempotência quando a intenção mudar de livro. Reproduzido com HTTP simulado. Evidência em `features/books/pages/book-detail.ts:69` e `:79`.

**F4 · Média · Políticas e estado de operações estão espalhados entre telas e API.**  
A tela de detalhe fixa 48 horas e 14 dias, e o histórico limita o botão de renovação com uma constante do frontend. Uma mudança no servidor pode deixar um texto ou botão incorreto. O histórico usa um único `busyId` para todos os empréstimos; operações iniciadas em cartões diferentes podem sobrescrever esse estado e reabilitar o primeiro cartão antes do fim. **Correção:** devolver datas e elegibilidade no contrato da API e rastrear a operação em andamento por ID, ou bloquear todas as ações enquanto houver uma operação, conforme o fluxo desejado. Identificado em leitura. Evidências em `features/books/pages/book-detail.ts:57`, `features/loans/pages/my-loans.ts:83`, `:124` e `:172`, e `service/impl/LoanServiceImpl.java:41`.

## 3. Aderência ao que foi solicitado

| Requisito ou decisão | Implementação encontrada | Avaliação |
|---|---|---|
| Java, Spring Boot, Angular, REST, JPA e camadas | Tecnologias e divisão presentes. Rota de catálogo `/api/livros`. | Alinhamento estrutural com o pedido acadêmico. Confirmar com o professor se o prefixo `/api` precisa seguir alguma convenção específica para a entrega. |
| Cadastro, JWT e perfis; gestão dos registros principais | Leitor cria a própria conta, equipe administra acervo e circulação e administrador gerencia usuários. Livros podem ser inativados; autores e gêneros podem ser excluídos quando desvinculados. | Implementado no código e coberto por testes escritos. B1 e B2 merecem correção. |
| Fluxo principal, disponibilidade e histórico | Reserva → retirada → empréstimo → devolução, com renovação, status, histórico e QR/código de retirada. | Adaptação concreta ao domínio da biblioteca. O roteiro de ponta a ponta ainda precisa ser executado com API e banco reais. |
| Exemplar individual, localização e período futuro da proposta inicial | Livro mantém contadores de cópias e a reserva imediata referencia o livro. | Modelo diferente. Sem entidade individual e intervalo, o sistema não consegue garantir reserva futura por exemplar nem rastrear a unidade física retirada. |
| Prazo, renovação e atraso da proposta inicial | A ideia previa retirada em 24 horas, três renovações configuráveis, bloqueio por reserva futura e fluxo inicial sem multa financeira. A versão entregue usa 48 horas, duas renovações fixas e multa simulada de R$ 2 por dia de atraso, arredondado para cima. | Divergências de produto. O professor permite simulação quando há pagamento. O grupo deve decidir qual regra vai demonstrar e atualizar a documentação de acordo com essa decisão. |

## 4. Testes, entrega e prioridades

O Angular tem testes de componente e navegação com `HttpTestingController`, incluindo login, filtros, reserva, proteção de rotas, balcão e erros do formulário. Os três testes de diagnóstico usaram a mesma infraestrutura e foram removidos após a reprodução. O Java tem casos escritos de arquitetura, autenticação, acervo e circulação. Entre os bons cenários estão a disputa pelo último exemplar, a repetição da chave de idempotência, a posse do empréstimo e o pagamento simulado. Esses resultados Java continuam pendentes de execução.

| Ordem | Ação | Como verificar |
|---|---|---|
| 1 | Corrigir a promoção de administrador no startup e a confirmação de retirada. | Uma conta comum preexistente conserva seu perfil. Após editar o código consultado, o balcão exige nova consulta antes do POST. |
| 2 | Coordenar bloqueios de reserva e inativação. Validar a execução integrada em ambiente limpo. | Java compila e executa a suíte. Em duas transações concorrentes, o leitor nunca termina inativo com uma reserva que a própria regra de inativação impediria. No navegador, executar cadastro → login → reserva → retirada → devolução e conferir histórico e estoque. |
| 3 | Corrigir busca fora de ordem e mudança de ID. Formalizar prazos, exemplar individual e renovação com o grupo. | Testes permanentes reproduzem os casos de rede e rota. README e telas descrevem as regras que o grupo realmente escolheu. |
| 4 | Melhorar consultas e divisão das responsabilidades com base nos fluxos corrigidos. | Comparar a quantidade de consultas antes e depois; manter a suíte e os contratos HTTP passando. |
| 5 | Padronizar a entrega do fonte. | Uma cópia limpa com JDK e Node compatíveis usa Maven Wrapper e `npm ci`. O pacote contém código, manifests, lockfile e migrações, com caches, dependências instaladas, compilados e banco local fora do ZIP. |

**Recomendação final:** manter os dois projetos e a arquitetura em camadas. Para o tamanho atual, as melhores melhorias são correções localizadas nos casos de uso de administração e circulação, testes permanentes para os três defeitos do Angular e uma execução ponta a ponta em banco novo. A decisão sobre exemplares individuais só exige remodelagem se o grupo confirmar que esse controle faz parte da entrega.
