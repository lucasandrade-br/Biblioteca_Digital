# Componentes compartilhados

Contém código reutilizável entre funcionalidades, sem acesso HTTP ou regras de domínio. `layout/AuthLayout` compõe as páginas de login e cadastro por meio de `ng-content`. `components/BookCover` mostra a capa informada ou desenha uma em CSS, e `format.ts` formata datas e valores em reais. `forms/not-blank.ts` valida campos de texto obrigatórios, incluindo valores preenchidos apenas com espaços.

Os estilos globais ficam em `src/styles.css`.
