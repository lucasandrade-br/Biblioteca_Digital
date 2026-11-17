import { DOCUMENT, Component, DestroyRef, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, tap } from 'rxjs';
import { PERFIL_LABELS, Perfil } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { InterfaceIcon } from '../components/interface-icon';

const PROFILE_ORDER: Perfil[] = ['ADMIN', 'LIBRARIAN', 'USER'];
const SIDEBAR_PREFERENCE = 'catalogo.sidebar.compact';
type Category = 'books' | 'operations' | 'system';
interface CategoryGroup {
  icon: Category;
  label: string;
  links: { href: string; label: string }[];
}

@Component({
  selector: 'app-library-layout',
  imports: [RouterOutlet, RouterLink, InterfaceIcon],
  templateUrl: './library-layout.html',
  styleUrl: './library-layout.css',
  host: {
    '(document:click)': 'closeOnOutsideClick($event)',
    '(document:keydown.escape)': 'closeDisclosures($event)',
    '(document:focusin)': 'closeOnOutsideFocus($event)',
  },
})
export class LibraryLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly navigation = viewChild.required<ElementRef<HTMLElement>>('navigation');
  private readonly profile = viewChild<ElementRef<HTMLElement>>('profile');
  private readonly profileButton = viewChild<ElementRef<HTMLButtonElement>>('profileButton');
  private readonly navigationButton = viewChild.required<ElementRef<HTMLButtonElement>>('navigationButton');
  private readonly compactPreference = signal(this.readPreference());
  protected readonly mobile = signal(false);
  protected readonly compact = computed(() => !this.mobile() && this.compactPreference());
  protected readonly navigationOpen = signal(false);
  protected readonly openCategory = signal<Category | null>(null);
  protected readonly profileOpen = signal(false);
  protected readonly primaryProfile = computed(() => {
    const role = PROFILE_ORDER.find((perfil) => this.auth.hasRole(perfil));
    return role ? PERFIL_LABELS[role] : '';
  });

  protected readonly categories = computed<CategoryGroup[]>(() => {
    const books: CategoryGroup = {
      icon: 'books',
      label: 'Livros',
      links: [{ href: '/livros', label: 'Catálogo' }],
    };
    if (this.auth.session()) books.links.push({ href: '/emprestimos', label: 'Meus empréstimos' });
    if (!this.auth.isStaff()) return [books];

    const system: CategoryGroup = {
      icon: 'system',
      label: 'Sistema',
      links: [
        { href: '/acervo/autores', label: 'Autores' },
        { href: '/acervo/generos', label: 'Gêneros' },
      ],
    };
    if (this.auth.hasRole('ADMIN')) system.links.push({ href: '/admin/usuarios', label: 'Usuários' });

    return [books, {
      icon: 'operations',
      label: 'Operacional',
      links: [{ href: '/balcao', label: 'Balcão' }, { href: '/acervo', label: 'Acervo' }],
    }, system];
  });

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      tap(() => {
        this.navigationOpen.set(false);
        this.openCategory.set(null);
        this.profileOpen.set(false);
      }),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  constructor() {
    const media = this.window?.matchMedia?.('(max-width: 800px)');
    if (!media) return;
    this.mobile.set(media.matches);
    const update = (event: MediaQueryListEvent) => {
      this.mobile.set(event.matches);
      this.navigationOpen.set(false);
      this.openCategory.set(null);
    };
    media.addEventListener('change', update);
    inject(DestroyRef).onDestroy(() => media.removeEventListener('change', update));
  }

  protected isCurrent(href: string): boolean {
    const path = this.currentUrl().split(/[?#]/)[0];
    if (href === '/acervo') return /^\/acervo(?:\/novo|\/[^/]+\/editar)?$/.test(path);
    return path === href || path.startsWith(href + '/');
  }

  protected categoryIsCurrent(group: CategoryGroup): boolean {
    return group.links.some(({ href }) => this.isCurrent(href));
  }

  protected toggleNavigation(): void {
    this.profileOpen.set(false);
    this.openCategory.set(null);
    if (!this.mobile()) {
      this.compactPreference.update((value) => !value);
      try {
        this.window?.localStorage.setItem(SIDEBAR_PREFERENCE, String(this.compactPreference()));
      } catch {
        // A preferência continua valendo enquanto a página estiver aberta.
      }
      return;
    }

    this.navigationOpen.update((open) => !open);
    if (this.navigationOpen()) {
      afterNextRender(() => {
        if (!this.navigationOpen()) return;
        const nav = this.navigation().nativeElement;
        (nav.querySelector<HTMLElement>('a[aria-current="page"]') ??
          nav.querySelector<HTMLElement>('a'))?.focus();
      }, { injector: this.injector });
    }
  }

  protected toggleCategory(category: Category): void {
    this.profileOpen.set(false);
    this.openCategory.update((current) => current === category ? null : category);
  }

  protected followLink(main: HTMLElement): void {
    if (this.navigationOpen() || this.openCategory()) main.focus();
  }

  protected toggleProfile(): void {
    this.navigationOpen.set(false);
    this.openCategory.set(null);
    this.profileOpen.update((open) => !open);
  }

  protected closeOnOutsideClick(event: MouseEvent): void {
    this.closeOutside(event.target as Node);
  }

  protected closeOnOutsideFocus(event: FocusEvent): void {
    this.closeOutside(event.target as Node);
  }

  protected closeDisclosures(event: Event): void {
    if (event.defaultPrevented) return;
    if (this.profileOpen()) {
      this.profileOpen.set(false);
      this.profileButton()?.nativeElement.focus();
    } else if (this.navigationOpen()) {
      this.navigationOpen.set(false);
      this.navigationButton().nativeElement.focus();
    } else if (this.openCategory()) {
      const trigger = this.navigation().nativeElement.querySelector<HTMLElement>('.category-toggle[aria-expanded="true"]');
      this.openCategory.set(null);
      trigger?.focus();
    }
  }

  private closeOutside(target: Node): void {
    if (this.profileOpen() && !this.profile()?.nativeElement.contains(target)) this.profileOpen.set(false);
    if (target === this.navigationButton().nativeElement || this.navigation().nativeElement.contains(target)) return;
    this.navigationOpen.set(false);
    this.openCategory.set(null);
  }

  private readPreference(): boolean {
    try {
      return this.window?.localStorage.getItem(SIDEBAR_PREFERENCE) === 'true';
    } catch {
      return false;
    }
  }
}
