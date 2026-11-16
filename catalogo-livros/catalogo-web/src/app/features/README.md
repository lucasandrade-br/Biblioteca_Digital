# Features do frontend

As funcionalidades são organizadas por domínio para manter páginas, contratos e acesso a dados próximos de quem os utiliza. As rotas permanecem lazy-loaded em `../app.routes.ts`.

- `auth` reúne login, cadastro e conta. A validação de confirmação de senha fica na funcionalidade e os contratos de autenticação ficam em `core/auth`.
- `books`: catálogo público e detalhe do livro, onde a reserva é iniciada.
- `loans`: histórico do leitor, QR de retirada, cancelamento, renovação e pagamento simulado da multa.
- `desk` reúne o balcão, com listagem, devolução e resumo na página e consulta e confirmação de retirada em `components/desk-pickup.ts`.
- `collection`: gestão do acervo, editor de livros, autores e gêneros.
- `admin`: administração de usuários.

Dentro das features, `pages` contém componentes roteáveis, `data-access` contém clientes HTTP, `models` contém os contratos, `components` contém partes específicas da funcionalidade e `forms` contém validadores. Componentes reutilizáveis sem regra de domínio ficam em `shared`.
