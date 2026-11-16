# Cronograma de melhorias · Catálogo de Livros

**Objetivo.** Corrigir os problemas que afetam segurança e circulação, eliminar duplicações que tornam o sistema difícil de manter e deixar a entrega reproduzível para o grupo. Cada etapa funciona como uma ficha de implementação, com ponto de partida, mudanças propostas e critérios para verificar o resultado.

**Estimativa:** 6 a 8 sessões de 2 a 3 horas. É uma estimativa para planejar o trabalho. O acesso às dependências Java e as decisões do grupo sobre as regras de empréstimo podem afetar o prazo.

| Etapa | Quando | Foco | Entrega |
|---|---|---|---|
| 1 | Sessões 1–2 | Segurança e integridade na API | Inicialização do administrador e concorrência testadas |
| 2 | Próximas 1–2 sessões | Correção dos fluxos Angular | Retirada, busca e detalhe com testes de regressão |
| 3 | Próximas 1–2 sessões | Organização dos módulos | Tipos compartilhados e lógica repetida nos módulos certos |
| 4 | Últimas 2 sessões | Expiração, regras e entrega | API e telas alinhadas, instalação documentada e fluxo completo validado |

**Linha de base em 16/11/2026.** Os 20 testes Angular passaram e o build foi concluído. No Java, o projeto já reconhece o POM pai do Spring Boot. A suíte parou antes de rodar os testes porque o cache local ainda não tem `maven-surefire-plugin:3.5.6` e a tentativa de download falhou ao resolver o endereço do Maven Central neste ambiente.

As etapas 1 e 2 podem avançar em paralelo se duas pessoas estiverem disponíveis. A etapa 3 vem depois das correções do módulo correspondente. A etapa 4 depende de uma API executável. Os caminhos abaixo são relativos a `catalogo-livros`. Quando uma linha é indicada, ela aponta para o trecho da versão revisada.

## Etapa 1 · Proteger administração e reserva

### Problema

Ao subir a API, o e-mail de administrador configurado pode coincidir com o de uma conta comum cadastrada. O inicializador adiciona `ADMIN` à conta existente, que conserva sua senha anterior. Também existe um risco de concorrência: a reserva bloqueia a linha do leitor, mas a inativação consulta o usuário sem o mesmo bloqueio, e a reserva deixa de revalidar o status da conta sob esse bloqueio. A colisão de e-mail está demonstrada na lógica do código. O cenário concorrente permanece uma hipótese a reproduzir com o banco.

### Onde está

- `catalogo-api/src/main/java/org/example/catalogo/config/AdminInitializer.java:27` e `service/impl/UsuarioServiceImpl.java:116` para a inicialização do administrador.
- Na mesma raiz Java, `service/impl/UsuarioServiceImpl.java:94`, `service/impl/LoanServiceImpl.java:68` e `repository/UsuarioRepository.java:16` para inativação e reserva.

### Como resolver

1. Recuperar a linha de base executando a suíte Java. Se a resolução de dependências falhar, registrar o artefato ausente para reproduzir o teste no ambiente que consegue acessar o Maven Central.
2. No bootstrap, criar o administrador quando o e-mail estiver livre e aceitar um administrador já existente. Para um e-mail de conta comum, encerrar a inicialização com uma mensagem que explique o conflito e preservar o perfil e a credencial da conta. Testar os três caminhos.
3. Na inativação e na reserva, coordenar as duas operações pelo mesmo bloqueio de usuário. Validar novamente se o leitor está ativo depois de obter esse bloqueio. Verificar a ordem dos bloqueios de usuário, livro e empréstimo nos fluxos que se cruzam, inclusive expiração e devolução.
4. Criar um teste de integração com duas transações independentes. Cobrir reserva vencedora, que impede inativação enquanto houver empréstimo aberto, e inativação vencedora, que impede a reserva. Reexecutar o teste existente de disputa pelo último exemplar e o de repetição da chave de idempotência.

### Resultado esperado

O startup nunca promove automaticamente uma conta comum. A conta administradora pode ser criada em um banco novo e a reinicialização aceita o administrador existente. Sob concorrência, a inativação e a reserva respeitam a mesma regra de integridade e a disponibilidade continua consistente.

## Etapa 2 · Corrigir os fluxos de balcão e catálogo

### Problema

Três defeitos foram reproduzidos com HTTP simulado. No balcão, o atendente pode visualizar a reserva A e enviar o código B se editar o campo após a consulta. No catálogo, uma resposta antiga pode substituir a busca mais recente. No detalhe, trocar só o ID na rota pode manter o livro anterior na tela.

### Onde está

Em `catalogo-web/src/app`, `features/desk/pages/desk.ts:154` e `:175`, `features/books/pages/catalog.ts:96`, `features/books/pages/book-detail.ts:69` e `:79`. A infraestrutura de testes para esses fluxos está em `app.spec.ts`.

### Como resolver

1. Guardar o código consultado junto com a prévia e invalidar a confirmação quando o campo mudar. Uma resposta de consulta pendente só deve preencher a prévia se ainda corresponder ao código atual. Enviar ao backend o código associado à prévia confirmada. Cobrir tanto a edição após a consulta quanto a edição durante a requisição.
2. Processar a busca com `switchMap` ou mecanismo equivalente que ignore respostas antigas. Capturar o filtro e a página como parte da mesma solicitação. Testar duas buscas com respostas em ordem invertida.
3. Observar `paramMap` no detalhe e recarregar o livro a cada mudança de ID. Limpar o estado da reserva anterior, ignorar respostas atrasadas e criar uma nova chave de idempotência para o novo livro. Reutilizar a chave quando houver uma tentativa repetida da mesma reserva. Testar a navegação direta de um ID para outro.

### Resultado esperado

Entre consultar e confirmar, o balcão opera sobre a reserva que o atendente viu. A tela de catálogo exibe a busca mais recente e o detalhe acompanha o ID da URL. Os testes de regressão da etapa passam junto da suíte existente e do build Angular.

## Etapa 3 · Centralizar duplicações e corrigir a divisão dos módulos

### Problema

A API repete a conversão de paginação em quatro serviços e a definição de empréstimo em aberto em três. No Angular, paginação e validação genéricas moram em funcionalidades específicas, tipos de sessão ficam fora de `core/auth` e o timeout de 15 segundos está repetido nos clientes HTTP. O balcão ainda concentra responsabilidades independentes em um único componente.

### Onde está

- Java, em `catalogo-api/src/main/java/org/example/catalogo/service/impl`. `BookServiceImpl.java:256`, `DeskServiceImpl.java:154`, `LoanServiceImpl.java:260` e `UsuarioServiceImpl.java:149` repetem `toPageable`. A definição de aberto está em `BookServiceImpl.java:39`, `LoanServiceImpl.java:45` e `UsuarioServiceImpl.java:29`.
- Angular, em `catalogo-web/src/app`. `Page<T>` em `features/books/models/book.models.ts:42`, `notBlank` em `features/auth/forms/form-utils.ts:3`, sessão em `core/auth/auth.service.ts:6` e `features/auth/models/auth.models.ts`. O timeout aparece nos clientes `*/data-access` e no serviço de autenticação. O ponto de partida do balcão é `features/desk/pages/desk.ts`.

### Como resolver

1. Criar um adaptador de paginação no backend e reutilizá-lo nos quatro serviços. Manter parâmetros, ordenação e campos JSON existentes nesta etapa. Centralizar o conjunto `RESERVED` e `ACTIVE`, mantendo a separação entre contratos de aplicação e entidades de persistência.
2. Mover `Page<T>` para `core/http`, `notBlank` para `shared/forms` e os tipos transversais `Session` e `Perfil` para `core/auth`. Manter `passwordsMatch` junto da funcionalidade de cadastro. Atualizar todos os imports e verificar que as mudanças resolvem os acoplamentos descritos.
3. Definir o timeout em um ponto HTTP comum para chamadas à própria API, preservando o início da contagem e a mensagem de erro. Priorizar as extrações de responsabilidade comprovada. Com os testes de retirada já escritos, separar o formulário e a prévia de retirada da listagem e do resumo do balcão. Migrar os estilos exclusivos desse bloco junto da extração.

### Resultado esperado

Uma mudança na paginação, no conjunto de status abertos ou no timeout tem um ponto claro de manutenção. Os módulos usam contratos e validadores em locais compatíveis com sua responsabilidade. A resposta da API, a autorização e o comportamento coberto nas etapas anteriores continuam validados pelas respectivas suítes.

## Etapa 4 · Separar a expiração, alinhar as regras e validar a entrega

### Problema

Consultas de empréstimo e de balcão disparam uma varredura que pode expirar reservas e liberar exemplares na mesma transação. O Angular repete o prazo de retirada e a política de renovação que já existem na API. A documentação de instalação também diverge entre o README principal e o do frontend. Por fim, a proposta inicial e a implementação diferem no prazo de retirada, na quantidade de renovações, nas multas e no controle dos exemplares.

### Onde está

- Expiração em `catalogo-api/src/main/java/org/example/catalogo/service/impl/LoanServiceImpl.java:175`, `:193` e `:214`, e em `DeskServiceImpl.java:55` e `:68`.
- Regras de circulação em `LoanServiceImpl.java:41` e `DeskServiceImpl.java:33`. No Angular, `catalogo-web/src/app/features/books/pages/book-detail.ts:57` e `features/loans/pages/my-loans.ts:10` e `:86` repetem partes da política.
- Entrega em `README.md`, `catalogo-api/README.md` e `catalogo-web/README.md`. Proposta inicial em `Minha ideia/Material de Documentação/Regras Funcionais Iniciais.md`, fora de `catalogo-livros`.

### Como resolver

1. Dar à expiração uma operação própria e executá-la em transação dedicada. Tornar as consultas de empréstimo e balcão somente leitura. Definir o que ocorre entre o vencimento e o próximo ciclo do agendador, principalmente ao tentar retirar uma reserva vencida ou pedir o último exemplar ocupado por ela. Testar essas duas situações, a liberação única do exemplar e a repetição do processamento.
2. Registrar com o grupo quais regras entram na entrega. O ponto de partida do cronograma é a versão atual, com reserva imediata por quantidade de cópias. Se forem exigidos exemplares individuais ou reservas futuras por período, estimar a evolução separadamente, pois ela afeta banco, API, telas e testes. Alinhar prazo, renovações e multas com a decisão de escopo. Fazer a interface exibir datas e elegibilidade informadas pela API.
3. Consolidar a instalação nos READMEs. Reproduzir a execução a partir de uma cópia de fonte com manifests, lockfile e migrações. O pacote deve excluir `node_modules`, caches, runtimes locais, compilados e o banco H2 gerado.
4. Rodar as suítes e fazer a demonstração em banco novo. Verificar cadastro → login → reserva → retirada → devolução. Conferir que o estoque diminui na reserva e é recuperado na devolução. Verificar histórico, bloqueio por perfil, cancelamento e expiração. Se houver tempo após a validação, medir as consultas de uma página do balcão antes de decidir se vale criar uma projeção resumida.

### Resultado esperado

A leitura do histórico deixa de alterar reservas. Os prazos e ações oferecidos pela tela seguem as regras da API. Um integrante consegue subir o sistema a partir do código-fonte, e o grupo tem evidência de que cadastro, perfis, estoque e circulação funcionam juntos.

## Como registrar a conclusão de cada etapa

Anotar os arquivos alterados, os casos reproduzidos, os testes executados e seus resultados. No PowerShell, a partir de `catalogo-livros`, os comandos de referência são:

```powershell
.\catalogo-api\maven-local.ps1 test
.\catalogo-web\web-local.ps1 test --watch=false
.\catalogo-web\web-local.ps1 build
```

A conclusão de uma etapa depende dos seus resultados esperados. Se um teste estiver bloqueado pelo ambiente, registrar o impedimento e retomar a validação assim que o ambiente estiver disponível.

[Relatório de revisão técnica que fundamenta o cronograma](https://chatgpt.com/library?file_id=libfile_f700539322f48191a1f112efeb0ac46b&account_id=personal)
