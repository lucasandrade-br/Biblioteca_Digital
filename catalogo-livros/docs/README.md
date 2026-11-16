# Documentação do projeto

A documentação técnica do sistema fica nesta pasta.

- [Arquitetura do Sistema](Arquitetura%20do%20Sistema.md): camadas, modelo de dados, endpoints, segurança, concorrência e limitações.

Os READMEs operacionais permanecem junto aos projetos executáveis: [API](../catalogo-api/README.md) e [web](../catalogo-web/README.md). Integrantes, regras de negócio e adaptações estão no [README principal](../README.md).

## Escopo da entrega

O catálogo controla a disponibilidade por quantidade de cópias de cada livro. A reserva é imediata e destina uma cópia ao leitor até a retirada, o cancelamento ou a expiração. A proposta de identificar cada exemplar físico e reservar por períodos futuros é uma evolução de escopo. A versão entregue usa 48 horas para retirada, 14 dias para devolução, até duas renovações de 14 dias e multa simulada de R$ 2 por dia de atraso iniciado.

## Melhorias implementadas

| Etapa | Implementação | Cobertura de regressão |
| --- | --- | --- |
| 1. Segurança e integridade | Bootstrap administrativo com verificação de perfil e bloqueio de usuário compartilhado entre reserva e inativação. | E-mail livre, administrador existente, colisão com conta comum e as duas ordens de commit na concorrência. |
| 2. Fluxos Angular | Confirmação vinculada ao código consultado, cancelamento de buscas antigas e recarga do detalhe ao mudar de livro. | Edição durante e após consulta, buscas consecutivas, navegação e chave de idempotência. |
| 3. Organização | Paginação e estados abertos compartilhados no Java, tipos e validadores comuns no Angular, timeout central e retirada em um componente próprio. | Fluxos existentes, limites entre camadas e timeout aplicado às chamadas da própria API. |
| 4. Expiração e entrega | Processamento em transações próprias, consultas de leitura, elegibilidade devolvida pela API e distribuição do código-fonte. | Prazo vencido sem alteração nas consultas, rejeição da retirada, recuperação da última cópia, expiração repetida e ações de acordo com o contrato HTTP. |

## Roteiro de validação

1. Extraia a distribuição de código-fonte e siga o [README principal](../README.md#como-executar) em dois terminais. Para isolar a demonstração de dados locais anteriores, inicie a API com `CATALOGO_DB_URL=jdbc:h2:file:./data/demo-entrega` e use um nome de arquivo ainda inexistente.
2. Cadastre as contas de leitor e de bibliotecário pela interface. Entre como administrador, atribua o perfil de bibliotecário à segunda conta e faça novo login com ela.
3. Como leitor, procure um livro disponível e anote a quantidade de exemplares. Reserve e confirme que a quantidade cai em um. Verifique o prazo e o código no histórico. O perfil de leitor recebe 403 ao tentar uma ação de balcão diretamente pela API.
4. Como bibliotecário, consulte o código, confira o leitor e confirme a retirada. Verifique no histórico que o empréstimo está ativo e que a data de devolução aparece. Registre a devolução e confira o estoque restaurado e o evento no histórico.
5. Faça uma segunda reserva e cancele pelo histórico. Confira que o estoque é restaurado uma vez. Para validar o efeito do vencimento sem aguardar 48 horas, execute os testes de integração de expiração em H2. Eles antecipam o prazo em banco temporário e verificam leitura, bloqueio da retirada, liberação e reaproveitamento da última cópia.

## Conteúdo da distribuição

O ZIP inclui os fontes Java e Angular, testes, configurações, Maven Wrapper, manifests, lockfile, migrações e documentação. Os ambientes locais, caches, dependências instaladas, builds e bancos gerados são recriados durante a instalação e a execução.
