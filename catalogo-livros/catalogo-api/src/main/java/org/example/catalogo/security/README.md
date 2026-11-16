# Segurança e tokens

Implementa o principal do Spring Security, a assinatura e verificação do JWT e o filtro HTTP de Bearer.

## Fluxo e dependências

`TokenService` define a interface; `TokenServiceImpl` a implementa com Auth0 `java-jwt`, HMAC512 e a propriedade `token.secret`. O JWT usa issuer `catalogo-api`, subject com o e-mail, claim `perfis` e validade de duas horas. `SecurityFilter` extrai `Authorization: Bearer`, valida o token, carrega o usuário via `UserDetailsServiceImpl` e instala a autenticação no contexto, somente se a conta estiver ativa. `UserSS` expõe as autoridades derivadas de `UsuarioPerfil`.

Integra-se a `config.SecurityConfig` e ao `AuthController` no login. O claim `perfis` serve apenas para o frontend montar os menus: a autorização usa os perfis carregados do banco em cada requisição.

## Arquivos críticos e riscos

- `TokenService.java`, `TokenServiceImpl.java`, `SecurityFilter.java` e `UserSS.java`.
- `TOKEN_SECRET` não tem valor padrão e o startup rejeita menos de 64 bytes UTF-8; não versione esse valor.
- O subject é o e-mail; mudar o identificador exige compatibilidade com `UserDetailsServiceImpl`.
- `TokenServiceTest` cobre geração, adulteração e segredo inválido; `AuthIntegrationTest` cobre a autorização ponta a ponta.
