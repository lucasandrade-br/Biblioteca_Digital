# catalogo-web

Frontend Angular de integração com `../catalogo-api`. Inclui catálogo, reserva com QR de retirada, histórico de empréstimos, balcão, gestão do acervo e administração de usuários.

## Executar localmente

Use o [guia de execução do projeto](../README.md#como-executar) para instalar as versões do lockfile e iniciar o Angular. O proxy de desenvolvimento encaminha `/api/**` ao backend na porta 8081. A interface fica em `http://localhost:4200`.

## Funcionalidades e contratos

| Tela | Operação | Contrato da API |
| --- | --- | --- |
| `/cadastro` | Criar conta de leitor | `POST /api/usuarios` |
| `/entrar` | Entrar | `POST /api/auth/login` |
| `/conta` | Consultar sessão e sair | Usa os claims do JWT recebido |
| `/livros` | Catálogo público com filtros | `GET /api/livros`, `GET /api/generos` |
| `/livros/:id` | Detalhe e reserva | `GET /api/livros/{id}`, `POST /api/emprestimos` com `Idempotency-Key` |
| `/emprestimos` | Histórico, QR, cancelar, renovar, pagar multa | `/api/emprestimos` |
| `/balcao` | Retirada, devolução e resumo | `/api/balcao` |
| `/acervo` | Listar, inativar e reativar livros | `GET /api/livros/acervo`, `PATCH /api/livros/{id}/inativar` |
| `/acervo/novo`, `/acervo/:id/editar` | Cadastrar e editar livro | `POST /api/livros`, `PUT /api/livros/{id}` |
| `/acervo/autores-generos` | Gerenciar autores e gêneros | `/api/acervo/autores`, `/api/acervo/generos` |
| `/admin/usuarios` | Perfis e inativação | `/api/admin/usuarios` |

As telas tratam campos obrigatórios, mensagens de regra de negócio vindas da API, indisponibilidade e timeout de 15 segundos.

- No balcão, a confirmação usa o código associado à prévia exibida. Editar o campo invalida a prévia e encerra qualquer consulta pendente.
- Ao enviar uma busca no catálogo, o frontend cancela a consulta anterior e exibe o resultado dos filtros e da página solicitados.
- Ao mudar de livro na rota de detalhe, o frontend recarrega os dados e prepara uma chave própria para uma eventual reserva. Uma nova tentativa da mesma reserva reutiliza a chave original.
- O histórico apresenta as datas e as ações informadas pela API. Após pagar uma multa simulada, recarrega a página atual para atualizar a elegibilidade das demais renovações.
- O catálogo pode mostrar zero exemplares até a próxima atualização de estoque. Ao tentar reservar no detalhe, a API verifica se uma reserva vencida já permite liberar uma cópia.

## Sessão

O token fica em `sessionStorage`, com alternativa em memória. Senhas não são armazenadas. O interceptor envia `Authorization: Bearer` apenas em requisições privadas da própria API, e um 401 em rota privada encerra a sessão. Os perfis lidos do token só controlam menus e navegação: a autorização efetiva é do servidor. Depois de mudar o perfil de alguém, os menus só mudam no próximo login dessa pessoa.

## Organização

- `src/app/core`: sessão, guards, interceptor, `API_URL` e tratamento de erro HTTP.
- `src/app/features`: funcionalidades por domínio.
- `src/app/shared`: capa do livro, layout de autenticação e formatação.
- `src/styles.css`: identidade visual e adaptação para celular.

A entrada é `src/main.ts`, que inicializa `App` com `appConfig`; `app.routes.ts` registra as rotas com carregamento lazy.

## Validar e compilar

```sh
npm test -- --watch=false
npm run build
```

Os testes cobrem sessão, guards, formulários, catálogo, reserva, bloqueios da API, histórico, multa, balcão e editor de livros. A saída de produção fica em `dist/catalogo-web/browser`.
