import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guard';

const staffGuard = roleGuard('LIBRARIAN', 'ADMIN');

const libraryRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'livros' },
  {
    path: 'livros',
    title: 'Catálogo | Biblioteca',
    loadComponent: () => import('./features/books/pages/catalog').then((m) => m.Catalog),
  },
  {
    path: 'livros/:id',
    title: 'Livro | Biblioteca',
    loadComponent: () => import('./features/books/pages/book-detail').then((m) => m.BookDetail),
  },
  {
    path: 'emprestimos',
    title: 'Meus empréstimos | Biblioteca',
    canActivate: [authGuard],
    loadComponent: () => import('./features/loans/pages/my-loans').then((m) => m.MyLoans),
  },
  {
    path: 'balcao',
    title: 'Balcão | Biblioteca',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/desk/pages/desk').then((m) => m.Desk),
  },
  {
    path: 'acervo/autores',
    title: 'Autores | Biblioteca',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/collection/pages/authors').then((m) => m.Authors),
  },
  {
    path: 'acervo/generos',
    title: 'Gêneros | Biblioteca',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/collection/pages/genres').then((m) => m.Genres),
  },
  { path: 'acervo/autores-generos', pathMatch: 'full', redirectTo: 'acervo/autores' },
  {
    path: 'acervo',
    title: 'Acervo | Biblioteca',
    canActivate: [staffGuard],
    canActivateChild: [staffGuard],
    loadComponent: () =>
      import('./features/collection/pages/collection-books').then((m) => m.CollectionBooks),
    children: [
      {
        path: 'novo',
        title: 'Novo livro | Biblioteca',
        loadComponent: () =>
          import('./features/collection/pages/book-editor').then((m) => m.BookEditor),
      },
      {
        path: ':id/editar',
        title: 'Editar livro | Biblioteca',
        loadComponent: () =>
          import('./features/collection/pages/book-editor').then((m) => m.BookEditor),
      },
    ],
  },
  {
    path: 'admin/usuarios',
    title: 'Usuários | Biblioteca',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/pages/admin-users').then((m) => m.AdminUsers),
  },
  {
    path: 'conta',
    title: 'Minha conta | Biblioteca',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/pages/account').then((m) => m.Account),
  },
  { path: '**', redirectTo: 'livros' },
];

export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Entrar | Biblioteca',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/login').then((m) => m.Login),
  },
  {
    path: 'ativar-conta',
    title: 'Ativar conta | Biblioteca',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/activate').then((m) => m.Activate),
  },
  {
    path: 'cadastro',
    title: 'Criar conta | Biblioteca',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/register').then((m) => m.Register),
  },
  {
    path: '',
    loadComponent: () => import('./shared/layout/library-layout').then((m) => m.LibraryLayout),
    children: libraryRoutes,
  },
];
