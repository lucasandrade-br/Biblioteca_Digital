import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, tap } from 'rxjs';
import { PERFIL_LABELS, Perfil } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';

const PROFILE_ORDER: Perfil[] = ['ADMIN', 'LIBRARIAN', 'USER'];

@Component({
  selector: 'app-library-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './library-layout.html',
  styleUrl: './library-layout.css',
  host: {
    '(document:click)': 'closeProfileOutside($event)',
    '(document:keydown.escape)': 'closeDisclosures($event)',
    '(document:focusin)': 'closeOnOutsideFocus($event)',
  },
})
export class LibraryLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly navigation = viewChild.required<ElementRef<HTMLElement>>('navigation');
  private readonly profile = viewChild<ElementRef<HTMLElement>>('profile');
  private readonly profileButton = viewChild<ElementRef<HTMLButtonElement>>('profileButton');
  private readonly navigationButton = viewChild.required<ElementRef<HTMLButtonElement>>('navigationButton');

  protected readonly navigationOpen = signal(false);
  protected readonly profileOpen = signal(false);
  protected readonly primaryProfile = computed(() => {
    const role = PROFILE_ORDER.find((perfil) => this.auth.hasRole(perfil));
    return role ? PERFIL_LABELS[role] : '';
  });

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      tap(() => {
        this.navigationOpen.set(false);
        this.profileOpen.set(false);
      }),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly collectionActive = computed(() =>
    /^\/acervo(?:\/novo|\/[^/]+\/editar)?(?:[?#]|$)/.test(this.currentUrl()),
  );

  protected toggleNavigation(): void {
    this.profileOpen.set(false);
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

  protected toggleProfile(): void {
    this.navigationOpen.set(false);
    this.profileOpen.update((open) => !open);
  }

  protected closeProfileOutside(event: MouseEvent): void {
    if (this.profileOpen() && !this.profile()?.nativeElement.contains(event.target as Node)) {
      this.profileOpen.set(false);
    }
  }

  protected closeOnOutsideFocus(event: FocusEvent): void {
    const target = event.target as Node;
    if (this.profileOpen() && !this.profile()?.nativeElement.contains(target)) {
      this.profileOpen.set(false);
    }
    if (this.navigationOpen() && target !== this.navigationButton().nativeElement &&
        !this.navigation().nativeElement.contains(target)) {
      this.navigationOpen.set(false);
    }
  }

  protected closeDisclosures(event: Event): void {
    if (event.defaultPrevented) return;
    if (this.profileOpen()) {
      this.profileOpen.set(false);
      this.profileButton()?.nativeElement.focus();
    } else if (this.navigationOpen()) {
      this.navigationOpen.set(false);
      this.navigationButton().nativeElement.focus();
    }
  }
}
