import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService, readSession, SESSION_KEY } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { apiErrorMessage } from '../http/api-error';
import { apiTimeoutInterceptor } from '../http/api-timeout.interceptor';

function token(perfis = ['USER'], exp = Date.now() / 1000 + 7200, sub = 'pessoa@exemplo.com'): string {
  return `eyJhbGciOiJIUzUxMiJ9.${btoa(JSON.stringify({ sub, exp, perfis, iss: 'catalogo-api' }))}.signature`;
}

describe('Autenticação e integração HTTP', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiTimeoutInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => {
    http.verify();
    auth.ngOnDestroy();
    sessionStorage.clear();
  });

  async function login(perfis = ['USER']): Promise<string> {
    const jwt = token(perfis);
    const result = firstValueFrom(auth.login({ email: 'pessoa@exemplo.com', password: 'senha123' }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ token: jwt });
    await result;
    return jwt;
  }

  it('guarda o token e lê e-mail e perfis', async () => {
    const jwt = await login(['USER', 'LIBRARIAN']);
    expect(auth.session()?.email).toBe('pessoa@exemplo.com');
    expect(auth.hasRole('LIBRARIAN')).toBe(true);
    expect(auth.isStaff()).toBe(true);
    expect(auth.hasRole('ADMIN')).toBe(false);
    expect(sessionStorage.getItem(SESSION_KEY)).toBe(jwt);
  });

  it('não cria sessão quando as credenciais são rejeitadas', async () => {
    const result = firstValueFrom(auth.login({ email: 'ana@exemplo.com', password: 'errada' }));
    const assertion = expect(result).rejects.toMatchObject({ status: 401 });
    http.expectOne('/api/auth/login').flush({ message: 'E-mail ou senha incorretos' }, { status: 401, statusText: 'Unauthorized' });
    await assertion;
    expect(auth.session()).toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('aplica o prazo de 15 segundos somente nas chamadas à própria API', () => {
    vi.useFakeTimers();
    try {
      const client = TestBed.inject(HttpClient);
      const onError = vi.fn();
      auth.login({ email: 'ana@exemplo.com', password: 'senha123' }).subscribe({ error: onError });
      client.get('https://externo.example/api').subscribe();
      const api = http.expectOne('/api/auth/login');
      const external = http.expectOne('https://externo.example/api');

      vi.advanceTimersByTime(14_999);
      expect(onError).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      expect(api.cancelled).toBe(true);
      expect(onError).toHaveBeenCalledTimes(1);
      expect(apiErrorMessage(onError.mock.calls[0][0], 'Erro'))
        .toBe('A conexão demorou mais que o esperado. Tente novamente.');
      expect(external.cancelled).toBe(false);
      external.flush({});
    } finally {
      vi.useRealTimers();
    }
  });

  it('envia Bearer só em chamadas privadas da própria API', async () => {
    const jwt = await login();
    const client = TestBed.inject(HttpClient);
    client.get('/api/emprestimos').subscribe();
    const request = http.expectOne('/api/emprestimos');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${jwt}`);
    request.flush({});
    client.get('/api/livros/acervo').subscribe();
    const collection = http.expectOne('/api/livros/acervo');
    expect(collection.request.headers.get('Authorization')).toBe(`Bearer ${jwt}`);
    collection.flush({});
    for (const url of ['https://externo.example/api', '/api/livros', '/api/livros/41', '/api/generos', '/api/autores']) {
      client.get(url).subscribe();
      const external = http.expectOne(url);
      expect(external.request.headers.has('Authorization')).toBe(false);
      external.flush({});
    }
  });

  it('encerra a sessão ao receber 401 em chamada privada', async () => {
    await login();
    TestBed.inject(HttpClient).get('/api/emprestimos').subscribe({ error: () => {} });
    http.expectOne('/api/emprestimos').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.session()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/entrar'], { queryParams: { sessao: 'expirada' } });
  });

  it('protege rotas por autenticação e perfil', async () => {
    const run = (guard: typeof authGuard) =>
      TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    const staffGuard = roleGuard('LIBRARIAN', 'ADMIN');

    expect(run(authGuard)?.toString()).toBe('/entrar');
    expect(run(staffGuard)?.toString()).toBe('/entrar');
    expect(run(guestGuard)).toBe(true);

    await login(['USER']);
    expect(run(authGuard)).toBe(true);
    expect(run(guestGuard)?.toString()).toBe('/conta');
    expect(run(staffGuard)?.toString()).toBe('/livros');

    auth.clearSession();
    await login(['USER', 'LIBRARIAN']);
    expect(run(staffGuard)).toBe(true);
    expect(run(roleGuard('ADMIN'))?.toString()).toBe('/livros');
  });
});

describe('Leitura do token', () => {
  it('rejeita tokens malformados, expirados ou de outro emissor', () => {
    expect(readSession('abc')).toBeNull();
    expect(readSession('a.%%%%.c')).toBeNull();
    expect(readSession(token(['USER'], 0))).toBeNull();
    expect(readSession(`x.${btoa(JSON.stringify({ sub: 'a', exp: Date.now() / 1000 + 60, iss: 'outro' }))}.y`)).toBeNull();
  });
});
