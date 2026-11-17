import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService, SESSION_KEY } from './core/auth/auth.service';
import { authInterceptor } from './core/auth/auth.interceptor';
import { apiTimeoutInterceptor } from './core/http/api-timeout.interceptor';

const BOOK = {
  id: 41,
  titulo: 'Dom Casmurro',
  isbn: '9788535914849',
  publisher: 'Editora Exemplo',
  publicationYear: 1899,
  numeroPaginas: 256,
  synopsis: 'Bentinho e Capitu.',
  coverUrl: null,
  genre: { id: 1, name: 'Romance' },
  authors: [{ id: 1, name: 'Machado de Assis', nationality: 'Brasileira' }],
  totalCopies: 2,
  availableCopies: 1,
  status: 'ACTIVE',
};

const LOAN = {
  id: 101,
  bookId: 41,
  bookTitle: 'Dom Casmurro',
  readerId: 7,
  readerName: 'Leitor',
  readerEmail: 'leitor@exemplo.com',
  status: 'RESERVED',
  pickupCode: 'LIV-ABCD2345',
  requestedAt: '2026-12-01T10:00:00Z',
  pickupDeadline: '2026-12-03T10:00:00Z',
  pickedUpAt: null,
  dueAt: null,
  returnedAt: null,
  renewalCount: 0,
  maxRenewals: 2,
  canRenew: false,
  canCancel: true,
  canPickup: true,
  overdue: false,
  fineInCents: 0,
  fineStatus: 'NONE',
  finePaidAt: null,
  lastPaymentOutcome: null,
  events: [{ type: 'RESERVED', description: 'Reserva criada', actorName: 'Leitor', createdAt: '2026-12-01T10:00:00Z' }],
};

describe('Fluxos das telas', () => {
  let http: HttpTestingController;

  // jsdom does not implement the native dialog lifecycle.
  beforeAll(() => {
    if (!HTMLDialogElement.prototype.showModal) {
      Object.defineProperties(HTMLDialogElement.prototype, {
        showModal: { configurable: true, value(this: HTMLDialogElement) { this.open = true; } },
        close: { configurable: true, value(this: HTMLDialogElement) { this.open = false; } },
      });
    }
  });

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([apiTimeoutInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    TestBed.inject(AuthService).ngOnDestroy();
    sessionStorage.clear();
  });

  function signIn(perfis: string[], nome?: string): void {
    const claims = btoa(JSON.stringify({
      sub: 'leitor@exemplo.com',
      exp: Date.now() / 1000 + 3600,
      iss: 'catalogo-api',
      perfis,
      nome,
    }));
    sessionStorage.setItem(SESSION_KEY, `header.${claims}.signature`);
  }

  async function render(path: string) {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(path);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      el,
      fill: (id: string, value: string) => {
        const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      },
      submit: (selector = 'form') => {
        el.querySelector(selector)!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        fixture.detectChanges();
      },
      button: (text: string) =>
        [...el.querySelectorAll('button')].find((button) => button.textContent?.includes(text))!,
      settle: async () => {
        await fixture.whenStable();
        fixture.detectChanges();
      },
    };
  }

  it('impede login vazio e exibe validações', async () => {
    const page = await render('/entrar');
    expect(page.el.querySelector('.library-topbar, .library-sidebar, .auth-tabs')).toBeNull();
    expect(page.el.querySelector('.story-panel')).not.toBeNull();
    expect(page.el.querySelector('.form-switch a')?.textContent).toBe('Cadastre-se');
    page.submit();
    expect(page.el.textContent).toContain('Informe um e-mail válido.');
    expect(page.el.textContent).toContain('Informe sua senha.');
    http.expectNone('/api/auth/login');
  });

  it('mostra a mensagem da API quando a senha está errada', async () => {
    const page = await render('/entrar');
    page.fill('email', 'ana@exemplo.com');
    page.fill('password', 'errada123');
    page.submit();
    http.expectOne('/api/auth/login').flush({ message: 'E-mail ou senha incorretos' }, { status: 401, statusText: 'Unauthorized' });
    await page.settle();
    expect(page.el.querySelector('[role=alert]')!.textContent).toContain('E-mail ou senha incorretos');
    expect(page.el.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
  });

  it('impede cadastro com senhas diferentes ou curtas', async () => {
    const page = await render('/cadastro');
    page.fill('nome', 'Ana');
    page.fill('email', 'ana@exemplo.com');
    page.fill('password', '123');
    page.fill('confirmation', '456');
    page.submit();
    expect(page.el.textContent).toContain('As senhas precisam ser iguais.');
    expect(page.el.textContent).toContain('ao menos 6 caracteres');
    http.expectNone('/api/usuarios');
  });

  it('envia cadastro sem perfil e redireciona com confirmação', async () => {
    const page = await render('/cadastro');
    page.fill('nome', ' Ana ');
    page.fill('email', 'ana@exemplo.com');
    page.fill('password', 'senha123');
    page.fill('confirmation', 'senha123');
    page.submit();
    const request = http.expectOne('/api/usuarios');
    expect(request.request.body).toEqual({ nome: 'Ana', email: 'ana@exemplo.com', password: 'senha123' });
    request.flush(null, { status: 201, statusText: 'Created' });
    await page.settle();
    expect(TestBed.inject(Router).url).toBe('/entrar?cadastro=concluido');
    expect(page.el.textContent).toContain('Conta criada!');
  });

  it('carrega o catálogo público sem Bearer e envia os filtros', async () => {
    const page = await render('/livros');
    http.expectOne('/api/generos').flush([{ id: 1, name: 'Romance' }]);
    const first = http.expectOne((candidate) => candidate.url === '/api/livros');
    expect(first.request.headers.has('Authorization')).toBe(false);
    first.flush({
      content: [{
        id: 41, titulo: 'Dom Casmurro', authors: ['Machado de Assis'], genre: 'Romance', coverUrl: null,
        publicationYear: 1899, numeroPaginas: 256, availableCopies: 0, status: 'ACTIVE',
      }],
      number: 0, size: 12, totalPages: 1, totalElements: 1,
    });
    await page.settle();
    expect(page.el.textContent).toContain('Dom Casmurro');
    expect(page.el.textContent).toContain('Todos emprestados');
    expect(page.el.textContent).toContain('256 págs.');

    page.fill('q', 'machado');
    page.submit();
    const search = http.expectOne((candidate) => candidate.url === '/api/livros');
    expect(search.request.params.get('q')).toBe('machado');
    search.flush({ content: [], number: 0, size: 12, totalPages: 0, totalElements: 0 });
    await page.settle();
    expect(page.el.textContent).toContain('Nenhum livro encontrado.');
  });

  it('cancela a busca anterior quando uma busca mais recente é enviada', async () => {
    const page = await render('/livros');
    http.expectOne('/api/generos').flush([]);
    http.expectOne((request) => request.url === '/api/livros').flush({
      content: [], number: 0, size: 12, totalPages: 0, totalElements: 0,
    });
    await page.settle();

    page.fill('q', 'machado');
    page.submit();
    const older = http.expectOne((request) => request.params.get('q') === 'machado');
    page.fill('q', 'iracema');
    page.submit();
    const latest = http.expectOne((request) => request.params.get('q') === 'iracema');

    expect(older.cancelled).toBe(true);
    expect(latest.request.params.get('page')).toBe('0');
    latest.flush({
      content: [{ ...BOOK, id: 42, titulo: 'Iracema', authors: ['José de Alencar'], genre: 'Romance' }],
      number: 0, size: 12, totalPages: 1, totalElements: 1,
    });
    await page.settle();
    expect(page.el.textContent).toContain('Iracema');
    expect(page.el.textContent).not.toContain('Dom Casmurro');
  });

  it('visitante precisa entrar para reservar', async () => {
    const page = await render('/livros/41');
    http.expectOne('/api/livros/41').flush(BOOK);
    await page.settle();
    expect(page.el.textContent).toContain('Entre para reservar');
    expect(page.button('Reservar exemplar')).toBeUndefined();
  });

  it('reserva com Idempotency-Key e mostra o código de retirada', async () => {
    signIn(['USER']);
    const page = await render('/livros/41');
    http.expectOne('/api/livros/41').flush(BOOK);
    await page.settle();
    page.button('Reservar exemplar').click();
    const request = http.expectOne('/api/emprestimos');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(request.request.headers.get('Authorization')).toContain('Bearer');
    expect(request.request.body).toEqual({ bookId: 41 });
    request.flush(LOAN, { status: 201, statusText: 'Created' });
    await page.settle();
    expect(page.el.textContent).toContain('Reserva confirmada!');
    expect(page.el.textContent).toContain('LIV-ABCD2345');
    page.fixture.destroy();
  });

  it('permite verificar uma liberação na API mesmo com estoque zerado no catálogo', async () => {
    signIn(['USER']);
    const page = await render('/livros/41');
    http.expectOne('/api/livros/41').flush({ ...BOOK, totalCopies: 1, availableCopies: 0 });
    await page.settle();

    page.button('Reservar exemplar').click();
    const request = http.expectOne('/api/emprestimos');
    expect(request.request.body).toEqual({ bookId: 41 });
    request.flush(LOAN);
    await page.settle();

    expect(page.el.textContent).toContain('Reserva confirmada!');
    expect(page.el.textContent).toContain('0 de 1 disponíveis');
    page.fixture.destroy();
  });

  it('cancela o carregamento do livro anterior ao mudar o ID na mesma rota', async () => {
    const page = await render('/livros/41');
    const older = http.expectOne('/api/livros/41');
    await TestBed.inject(Router).navigateByUrl('/livros/42');

    expect(older.cancelled).toBe(true);
    http.expectOne('/api/livros/42').flush({ ...BOOK, id: 42, titulo: 'Iracema' });
    await page.settle();
    expect(page.el.querySelector('h1')?.textContent).toBe('Iracema');
  });

  it('reutiliza a chave na mesma reserva e ignora respostas de outra visita ao trocar de livro', async () => {
    signIn(['USER']);
    const page = await render('/livros/41');
    http.expectOne('/api/livros/41').flush(BOOK);
    await page.settle();

    page.button('Reservar exemplar').click();
    const first = http.expectOne('/api/emprestimos');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    first.flush({ message: 'Falha temporária' }, { status: 503, statusText: 'Service Unavailable' });
    await page.settle();
    page.button('Reservar exemplar').click();
    const retry = http.expectOne('/api/emprestimos');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);

    await TestBed.inject(Router).navigateByUrl('/livros/42');
    http.expectOne('/api/livros/42').flush({ ...BOOK, id: 42, titulo: 'Iracema' });
    await page.settle();
    page.button('Reservar exemplar').click();
    const current = http.expectOne('/api/emprestimos');
    expect(current.request.body).toEqual({ bookId: 42 });
    expect(current.request.headers.get('Idempotency-Key')).not.toBe(key);

    retry.flush(LOAN);
    await page.settle();
    expect(page.el.textContent).not.toContain(LOAN.pickupCode);
    expect(page.button('Reservando').disabled).toBe(true);

    current.flush({ ...LOAN, bookId: 42, bookTitle: 'Iracema', pickupCode: 'LIV-EFGH6789' });
    await page.settle();
    expect(page.el.querySelector('h1')?.textContent).toBe('Iracema');
    expect(page.el.textContent).toContain('LIV-EFGH6789');
    page.fixture.destroy();
  });

  it('mostra o bloqueio de regra de negócio vindo da API', async () => {
    signIn(['USER']);
    const page = await render('/livros/41');
    http.expectOne('/api/livros/41').flush(BOOK);
    await page.settle();
    page.button('Reservar exemplar').click();
    http.expectOne('/api/emprestimos').flush(
      { message: 'Limite de 3 livros simultâneos atingido' },
      { status: 409, statusText: 'Conflict' },
    );
    await page.settle();
    expect(page.el.querySelector('[role=alert]')!.textContent).toContain('Limite de 3 livros');
    expect(page.button('Reservar exemplar').disabled).toBe(false);
  });

  it('mostra o comprovante sob demanda e mantém ações de histórico e multas', async () => {
    signIn(['USER']);
    const page = await render('/emprestimos?etapa=reservas');
    const fined = {
      ...LOAN, id: 102, status: 'RETURNED', pickupCode: null, returnedAt: '2026-12-20T10:00:00Z',
      canPickup: false, canCancel: false, fineInCents: 400, fineStatus: 'PENDING',
    };
    const empty = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 };
    const reservations = http.expectOne((request) => request.url === '/api/emprestimos');
    expect(reservations.request.params.get('scope')).toBe('RESERVAS');
    reservations.flush({ ...empty, content: [LOAN], totalPages: 1, totalElements: 1 });
    await page.settle();
    expect(page.el.textContent).not.toContain('LIV-ABCD2345');
    page.button('Ver código').click();
    await page.settle();
    expect(page.el.querySelector('dialog')?.textContent).toContain('LIV-ABCD2345');
    page.el.querySelector<HTMLButtonElement>('dialog [aria-label="Fechar"]')!.click();
    await page.settle();

    page.button('Ver detalhes').click();
    await page.settle();
    page.button('Cancelar reserva').click();
    http.expectOne('/api/emprestimos/101/cancelar').flush({
      ...LOAN, status: 'CANCELLED', pickupCode: null, canPickup: false, canCancel: false,
    });
    http.expectOne((request) => request.url === '/api/emprestimos').flush(empty);
    await page.settle();
    expect(page.el.querySelector('dialog')?.textContent).toContain('Reserva cancelada');
    page.el.querySelector<HTMLButtonElement>('dialog [aria-label="Fechar"]')!.click();
    await page.settle();

    await TestBed.inject(Router).navigateByUrl('/emprestimos?etapa=historico');
    const history = http.expectOne((request) => request.url === '/api/emprestimos');
    expect(history.request.params.get('scope')).toBe('HISTORICO');
    history.flush({ ...empty, content: [fined], totalPages: 1, totalElements: 1 });
    await page.settle();
    page.button('Ver detalhes').click();
    await page.settle();
    page.button('Pagar multa').click();
    http.expectOne('/api/emprestimos/102/multa/pagamento').flush({ ...fined, lastPaymentOutcome: 'DECLINED' });
    await page.settle();
    expect(page.el.textContent).toContain('Pagamento simulado recusado');

    page.button('Pagar multa').click();
    http.expectOne('/api/emprestimos/102/multa/pagamento').flush({ ...fined, fineStatus: 'PAID' });
    http.expectOne((request) => request.url === '/api/emprestimos').flush({
      ...empty, content: [{ ...fined, fineStatus: 'PAID' }], totalPages: 1, totalElements: 1,
    });
    await page.settle();
    expect(page.el.textContent).toContain('Multa quitada');
    page.fixture.destroy();
  });

  it('pagina o histórico de empréstimos', async () => {
    signIn(['USER']);
    const page = await render('/emprestimos?etapa=historico');
    http.expectOne((candidate) => candidate.url === '/api/emprestimos').flush({
      content: [LOAN], number: 0, size: 20, totalPages: 2, totalElements: 21,
    });
    await page.settle();
    expect(page.el.textContent).toContain('Página 1 de 2');

    page.button('Próxima').click();
    const next = http.expectOne((candidate) => candidate.url === '/api/emprestimos');
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ content: [{ ...LOAN, id: 103, bookTitle: 'Iracema', status: 'RETURNED' }], number: 1, size: 20, totalPages: 2, totalElements: 21 });
    await page.settle();
    expect(page.el.textContent).toContain('Iracema');
    expect(page.button('Próxima').disabled).toBe(true);
    page.fixture.destroy();
  });

  it('usa ações e limite de renovação informados pela API', async () => {
    signIn(['USER']);
    const page = await render('/emprestimos');
    const expired = { ...LOAN, canPickup: false, canCancel: false, pickupCode: null };
    const active = {
      ...expired, id: 102, status: 'ACTIVE', pickedUpAt: LOAN.requestedAt,
      dueAt: '2026-12-15T10:00:00Z', renewalCount: 3, maxRenewals: 5, canRenew: true,
    };
    http.expectOne((request) => request.url === '/api/emprestimos').flush({
      content: [active, { ...active, id: 103, canRenew: false }],
      number: 0, size: 20, totalPages: 1, totalElements: 3,
    });
    await page.settle();
    const cards = page.el.querySelectorAll('.loan-preview');
    expect(cards).toHaveLength(2);
    expect(page.el.textContent).not.toContain('Renovar empréstimo');
    cards[0].querySelector<HTMLButtonElement>('button')!.click();
    await page.settle();
    expect(page.el.querySelector('dialog')?.textContent).toContain('3 de 5');
    expect(page.button('Renovar empréstimo')).toBeDefined();
    page.el.querySelector<HTMLButtonElement>('dialog [aria-label="Fechar"]')!.click();
    await page.settle();
    cards[1].querySelector<HTMLButtonElement>('button')!.click();
    await page.settle();
    expect(page.button('Renovar empréstimo')).toBeUndefined();
  });

  it('pagina a lista do balcão', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/balcao');
    http.expectOne('/api/balcao/resumo').flush({
      reservedCount: 0, activeCount: 21, overdueCount: 0, pendingFinesInCents: 0, paidFinesInCents: 0,
    });
    const active = { ...LOAN, status: 'ACTIVE', pickupCode: null, dueAt: '2026-12-15T10:00:00Z' };
    http.expectOne((candidate) => candidate.url === '/api/balcao/emprestimos').flush({
      content: [active], number: 0, size: 20, totalPages: 2, totalElements: 21,
    });
    await page.settle();

    page.button('Próxima').click();
    const next = http.expectOne((candidate) => candidate.url === '/api/balcao/emprestimos');
    expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('pending')).toBe('true');
    next.flush({ content: [active], number: 1, size: 20, totalPages: 2, totalElements: 21 });
    await page.settle();
    expect(page.el.textContent).toContain('Página 2 de 2');
  });

  it('esconde menus de gestão do leitor e redireciona a rota protegida', async () => {
    signIn(['USER']);
    const page = await render('/balcao');
    expect(TestBed.inject(Router).url).toBe('/livros');
    expect(page.el.querySelector('.site-nav')!.textContent).not.toContain('Balcão');
    http.expectOne('/api/generos').flush([]);
    http.expectOne((candidate) => candidate.url === '/api/livros').flush({ content: [], number: 0, size: 12, totalPages: 0, totalElements: 0 });
  });

  it('balcão consulta o código e confirma a retirada', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/balcao');
    const empty = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 };
    http.expectOne('/api/balcao/resumo').flush({
      reservedCount: 1, activeCount: 0, overdueCount: 0, pendingFinesInCents: 0, paidFinesInCents: 0,
    });
    http.expectOne((candidate) => candidate.url === '/api/balcao/emprestimos').flush(empty);
    await page.settle();
    expect(page.el.textContent).toContain('Reservas aguardando');

    page.button('Realizar retirada').click();
    await page.settle();
    page.fill('pickupCode', 'liv-abcd2345');
    page.el.querySelector('#pickupCode')!.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Enter', bubbles: true, cancelable: true,
    }));
    http.expectOne('/api/balcao/retiradas/liv-abcd2345').flush({ ...LOAN, pickupCode: null });
    await page.settle();
    expect(page.el.textContent).toContain('Leitor (leitor@exemplo.com)');

    page.button('Confirmar retirada').click();
    const confirm = http.expectOne('/api/balcao/retiradas');
    expect(confirm.request.body).toEqual({ pickupCode: 'liv-abcd2345' });
    confirm.flush({ ...LOAN, status: 'ACTIVE', pickupCode: null, dueAt: '2026-12-15T10:00:00Z' });
    await page.settle();
    http.expectOne('/api/balcao/resumo').flush({
      reservedCount: 0, activeCount: 1, overdueCount: 0, pendingFinesInCents: 0, paidFinesInCents: 0,
    });
    http.expectOne((candidate) => candidate.url === '/api/balcao/emprestimos').flush(empty);
    await page.settle();
    expect(page.el.textContent).toContain('registrado para Leitor');
  });

  it.each(['após', 'durante'])('balcão invalida a consulta ao editar o código %s a resposta', async (timing) => {
    signIn(['LIBRARIAN']);
    const page = await render('/balcao');
    http.expectOne('/api/balcao/resumo').flush({
      reservedCount: 2, activeCount: 0, overdueCount: 0, pendingFinesInCents: 0, paidFinesInCents: 0,
    });
    http.expectOne((request) => request.url === '/api/balcao/emprestimos').flush({
      content: [], number: 0, size: 20, totalPages: 0, totalElements: 0,
    });

    page.button('Realizar retirada').click();
    await page.settle();
    page.fill('pickupCode', LOAN.pickupCode);
    page.submit('dialog form');
    const lookup = http.expectOne('/api/balcao/retiradas/' + LOAN.pickupCode);
    if (timing === 'após') {
      lookup.flush({ ...LOAN, pickupCode: null });
      await page.settle();
      expect(page.button('Confirmar retirada')).toBeDefined();
    }

    page.fill('pickupCode', 'LIV-EFGH6789');
    await page.settle();
    if (timing === 'durante') expect(lookup.cancelled).toBe(true);
    expect(page.button('Confirmar retirada')).toBeUndefined();
    expect(page.el.textContent).not.toContain(LOAN.bookTitle);
    http.expectNone('/api/balcao/retiradas');

    page.submit('dialog form');
    http.expectOne('/api/balcao/retiradas/LIV-EFGH6789').flush({
      ...LOAN, id: 102, bookTitle: 'Iracema', pickupCode: null,
    });
    await page.settle();
    expect(page.el.textContent).toContain('Iracema');
    expect(page.button('Confirmar retirada')).toBeDefined();
  });

  it('busca leitor e livro pelos formulários do atendimento e registra empréstimo presencial', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/balcao');
    const empty = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 };
    const summary = { reservedCount: 0, activeCount: 0, overdueCount: 0, pendingFinesInCents: 0, paidFinesInCents: 0 };
    http.expectOne('/api/balcao/resumo').flush(summary);
    http.expectOne((request) => request.url === '/api/balcao/emprestimos').flush(empty);
    await page.settle();

    expect(page.el.querySelectorAll('.desk-heading .actions button')).toHaveLength(1);
    page.button('Realizar retirada').click();
    await page.settle();
    expect(page.el.querySelector('dialog h2')?.textContent).toBe('Realizar retirada');
    expect(page.el.querySelector('#pickupCode')).not.toBeNull();
    page.button('Pesquisar leitor').click();
    await page.settle();
    expect(page.el.querySelector('#pickupCode')).toBeNull();
    expect(page.button('Cadastrar leitor')).toBeUndefined();
    page.fill('desk-reader-search', 'Leitor');
    page.submit('dialog form');
    const readers = http.expectOne((request) => request.url === '/api/balcao/leitores');
    expect(readers.request.params.get('q')).toBe('Leitor');
    readers.flush({ ...empty, content: [{
      id: LOAN.readerId, nome: LOAN.readerName, email: LOAN.readerEmail, ativo: true,
    }], totalPages: 1, totalElements: 1 });
    await page.settle();
    page.button('Selecionar').click();
    await page.settle();

    page.fill('desk-book-search', 'Dom Casmurro');
    page.submit('dialog form');
    const books = http.expectOne((request) => request.url === '/api/livros');
    expect(books.request.params.get('q')).toBe('Dom Casmurro');
    books.flush({ ...empty, content: [{ ...BOOK, authors: ['Machado de Assis'], genre: 'Romance' }],
      totalPages: 1, totalElements: 1 });
    await page.settle();
    page.button('Selecionar').click();
    http.expectOne((request) => request.url === '/api/balcao/emprestimos/preparar')
      .flush({ reservation: null, availableCopies: 1 });
    await page.settle();
    expect(page.button('Registrar empréstimo').disabled).toBe(true);
    page.el.querySelector<HTMLInputElement>('dialog input[type="checkbox"]')!.click();
    await page.settle();
    page.button('Registrar empréstimo').click();
    const loan = http.expectOne((request) => request.url === '/api/balcao/emprestimos' && request.method === 'POST');
    expect(loan.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(loan.request.body).toEqual({ readerId: LOAN.readerId, bookId: BOOK.id });
    const active = { ...LOAN, status: 'ACTIVE', canPickup: false, pickupCode: null, dueAt: '2026-12-15T10:00:00Z' };
    loan.flush(active);
    http.expectOne('/api/balcao/resumo').flush({ ...summary, activeCount: 1 });
    http.expectOne((request) => request.url === '/api/balcao/emprestimos' && request.method === 'GET')
      .flush({ ...empty, content: [active], totalPages: 1, totalElements: 1 });
    await page.settle();
    expect(page.el.querySelector('dialog')).toBeNull();
    expect(page.button('Devolver')).toBeDefined();
  });

  it('editor valida o formulário e mostra erro de ISBN duplicado', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo/novo');
    http.expectOne((request) => request.url === '/api/livros/acervo').flush({
      content: [], number: 0, totalPages: 0,
    });
    const genreLists = http.match('/api/generos');
    expect(genreLists).toHaveLength(2);
    genreLists.forEach((request) => request.flush([BOOK.genre]));
    http.expectOne('/api/autores').flush([{ id: 2, name: 'Machado de Assis', nationality: null }]);
    await page.settle();

    page.submit('dialog form');
    expect(page.el.textContent).toContain('Informe o título.');
    expect(page.el.textContent).toContain('Informe de 1 a 5.000 páginas.');
    expect(page.el.textContent).toContain('Selecione ao menos um autor.');
    http.expectNone('/api/livros');

    page.fill('titulo', 'Dom Casmurro');
    page.fill('isbn', '978-85-359-1484-9');
    page.fill('publisher', 'Editora');
    page.fill('numeroPaginas', '256');
    page.fill('synopsis', 'Sinopse.');
    const select = page.el.querySelector<HTMLSelectElement>('#genreId')!;
    select.selectedIndex = 1;
    select.dispatchEvent(new Event('change'));
    page.el.querySelector<HTMLInputElement>('.check-list input')!.click();
    await page.settle();
    page.submit('dialog form');

    const request = http.expectOne('/api/livros');
    expect(request.request.body).toMatchObject({ titulo: 'Dom Casmurro', numeroPaginas: 256, genreId: 1, authorIds: [2], totalCopies: 1 });
    request.flush({ message: 'Já existe um livro cadastrado com este ISBN' }, { status: 409, statusText: 'Conflict' });
    await page.settle();
    expect(page.el.querySelector('[role=alert]')!.textContent).toContain('Já existe um livro');
  });
  it('exibe o menu de administrador e abre Minha conta com o nome e os perfis da sessão', async () => {
    signIn(['USER', 'LIBRARIAN', 'ADMIN'], 'Ana Lima');
    const page = await render('/conta');
    const profile = page.el.querySelector<HTMLButtonElement>('.profile-toggle')!;
    expect(profile.textContent).toContain('Ana Lima');
    expect(profile.textContent).toContain('Administrador');
    expect(profile.textContent).not.toContain('Bibliotecário');
    expect(page.el.querySelector('.facts')?.textContent).toContain('Bibliotecário');
    expect(page.el.querySelectorAll('.site-nav a')).toHaveLength(7);
    profile.click();
    await page.settle();
    expect(profile.getAttribute('aria-expanded')).toBe('true');
    expect(page.el.querySelector('.profile-actions a')?.getAttribute('href')).toBe('/conta');
    page.button('Sair').click();
    await page.settle();
    expect(TestBed.inject(Router).url).toBe('/entrar');
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });

  it('mostra apenas o catálogo na navegação de visitantes', async () => {
    const guest = await render('/livros');
    http.expectOne('/api/generos').flush([]);
    http.expectOne((request) => request.url === '/api/livros').flush({
      content: [], number: 0, totalPages: 0,
    });
    await guest.settle();
    expect([...guest.el.querySelectorAll('.site-nav a')].map((a) => a.textContent?.trim()))
      .toEqual(['Catálogo']);
  });

  it('redireciona a rota antiga para Autores e abre cadastro e edição em modal', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo/autores-generos');
    const original = { id: 1, name: 'Machado de Assis', nationality: 'Brasileira' };
    http.expectOne((request) => request.url === '/api/acervo/autores' && request.method === 'GET')
      .flush({ content: [original], number: 0, size: 20, totalPages: 1, totalElements: 1 });
    http.expectNone('/api/generos');
    await page.settle();

    expect(TestBed.inject(Router).url).toBe('/acervo/autores');
    expect(page.el.querySelector('.site-nav [aria-current="page"]')?.textContent?.trim()).toBe('Autores');
    expect(page.el.querySelector('.site-nav a[href="/admin/usuarios"]')).toBeNull();
    expect(page.el.querySelector('dialog')).toBeNull();

    page.button('Novo autor').click();
    await page.settle();
    expect(page.el.querySelector('dialog[open] h2')?.textContent).toBe('Novo autor');
    page.submit('dialog form');
    expect(page.el.querySelector('dialog .field-error')?.textContent).toContain('Informe o nome');
    page.fill('record-name', '  Clarice Lispector ');
    page.submit('dialog form');
    page.submit('dialog form');
    const create = http.expectOne('/api/acervo/autores');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Clarice Lispector', nationality: '' });
    expect(page.button('Cancelar').disabled).toBe(true);
    create.flush({ id: 2, name: 'Clarice Lispector', nationality: null });
    http.expectOne((request) => request.url === '/api/acervo/autores' && request.method === 'GET')
      .flush({
        content: [original, { id: 2, name: 'Clarice Lispector' }], number: 0, size: 20,
        totalPages: 1, totalElements: 2,
      });
    await page.settle();
    expect(page.el.querySelector('dialog')).toBeNull();
    expect(page.el.textContent).toContain('Autor salvo.');

    page.button('Editar').click();
    await page.settle();
    expect(page.el.querySelector<HTMLInputElement>('#nationality')?.value).toBe('Brasileira');
    page.button('Cancelar').click();
    await page.settle();
    expect(page.el.querySelector('dialog')).toBeNull();
    http.expectNone('/api/acervo/autores/1');
  });

  it('edita um gênero e preserva o formulário quando a API rejeita o nome', async () => {
    signIn(['ADMIN']);
    const page = await render('/acervo/generos');
    http.expectOne((request) => request.url === '/api/acervo/generos' && request.method === 'GET')
      .flush({ content: [BOOK.genre], number: 0, size: 20, totalPages: 1, totalElements: 1 });
    http.expectNone('/api/autores');
    await page.settle();
    page.button('Editar').click();
    await page.settle();
    expect(page.el.querySelector('dialog[open] h2')?.textContent).toBe('Editar gênero');
    expect(page.el.querySelector<HTMLInputElement>('#record-name')?.value).toBe('Romance');
    expect(page.el.querySelector('#nationality')).toBeNull();

    page.fill('record-name', 'Fantasia');
    page.submit('dialog form');
    const update = http.expectOne('/api/acervo/generos/1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ name: 'Fantasia' });
    update.flush({ message: 'Nome já cadastrado' }, { status: 409, statusText: 'Conflict' });
    await page.settle();
    expect(page.el.querySelector('dialog[open] [role=alert]')?.textContent).toContain('Nome já cadastrado');
    expect(page.el.querySelector<HTMLInputElement>('#record-name')?.value).toBe('Fantasia');
    page.el.querySelector('dialog')!.dispatchEvent(new Event('cancel', { cancelable: true }));
    await page.settle();
    expect(page.el.querySelector('dialog')).toBeNull();
  });

  it('atualiza a página e a busca do acervo depois de editar um livro', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo');
    http.expectOne('/api/generos').flush([BOOK.genre]);
    const summary = { ...BOOK, genre: 'Romance', authors: ['Machado de Assis'] };
    const pageResult = { content: [summary], number: 0, totalPages: 2 };
    http.expectOne((request) => request.url === '/api/livros/acervo').flush(pageResult);
    await page.settle();
    page.fill('q', 'Machado');
    page.submit();
    http.expectOne((request) => request.url === '/api/livros/acervo' && request.params.get('q') === 'Machado')
      .flush(pageResult);
    await page.settle();
    page.button('Próxima').click();
    http.expectOne((request) => request.url === '/api/livros/acervo' && request.params.get('page') === '1')
      .flush({ ...pageResult, number: 1 });
    await page.settle();

    await TestBed.inject(Router).navigateByUrl('/acervo/41/editar');
    await page.settle();
    http.expectOne('/api/generos').flush([BOOK.genre]);
    http.expectOne('/api/autores').flush(BOOK.authors);
    http.expectOne('/api/livros/acervo/41').flush(BOOK);
    await page.settle();
    expect(page.el.querySelector('dialog[open] h2')?.textContent).toBe('Editar livro');
    expect(page.el.querySelector<HTMLInputElement>('#titulo')?.value).toBe(BOOK.titulo);
    page.fill('titulo', 'Dom Casmurro — edição revisada');
    page.submit('dialog form');
    const update = http.expectOne('/api/livros/41');
    expect(update.request.method).toBe('PUT');
    update.flush(BOOK);
    http.expectOne((request) => request.url === '/api/livros/acervo'
      && request.params.get('q') === 'Machado' && request.params.get('page') === '1')
      .flush({ ...pageResult, number: 1 });
    await page.settle();
    expect(TestBed.inject(Router).url).toBe('/acervo');
    expect(page.el.querySelector('dialog')).toBeNull();
    expect(page.el.textContent).toContain('Página 2 de 2');
    expect(page.el.textContent).toContain('Livro salvo.');
  });

  it('mantém os filtros aplicados ao paginar e trocar o tamanho, e limpa a busca', async () => {
    signIn(['ADMIN']);
    const page = await render('/admin/usuarios');
    const empty = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0 };
    const listRequest = () => http.expectOne((request) => request.url === '/api/admin/usuarios');
    const initial = listRequest();
    expect(initial.request.params.get('size')).toBe('20');
    initial.flush(empty);
    await page.settle();

    expect(page.el.querySelector<HTMLSelectElement>('.pagination-size select')?.value).toBe('20');
    page.fill('q', 'Ana');
    const active = page.el.querySelector<HTMLSelectElement>('#ativo')!;
    active.selectedIndex = 2;
    active.dispatchEvent(new Event('change'));
    page.submit();
    const filtered = listRequest();
    expect(filtered.request.params.get('q')).toBe('Ana');
    expect(filtered.request.params.get('ativo')).toBe('false');
    const results = { ...empty, totalPages: 2, totalElements: 21 };
    filtered.flush(results);
    await page.settle();
    expect(page.el.querySelector('.pagination-total')?.textContent).toContain('21 resultados');

    page.fill('q', 'rascunho');
    page.button('Próxima').click();
    const next = listRequest();
    expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('q')).toBe('Ana');
    next.flush({ ...results, number: 1 });
    await page.settle();

    const size = page.el.querySelector<HTMLSelectElement>('.pagination-size select')!;
    size.value = '50';
    size.dispatchEvent(new Event('change'));
    const resized = listRequest();
    expect(resized.request.params.get('page')).toBe('0');
    expect(resized.request.params.get('size')).toBe('50');
    expect(resized.request.params.get('q')).toBe('Ana');
    resized.flush({ ...empty, size: 50 });
    await page.settle();
    expect(page.el.querySelector('.pagination-total')?.textContent).toContain('0 resultados');

    page.button('Limpar filtros').click();
    const cleared = listRequest();
    expect(cleared.request.params.has('q')).toBe(false);
    expect(cleared.request.params.has('ativo')).toBe(false);
    expect(cleared.request.params.get('size')).toBe('50');
    cleared.flush({ ...empty, size: 50 });
    await page.settle();
    expect(page.el.querySelector<HTMLInputElement>('#q')?.value).toBe('');
  });

  it('volta à última página disponível quando a exclusão esvazia a página atual', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo/generos');
    const listRequest = () => http.expectOne((request) =>
      request.url === '/api/acervo/generos' && request.method === 'GET');
    const first = { content: [BOOK.genre], size: 20, number: 0, totalPages: 2, totalElements: 21 };
    listRequest().flush(first);
    await page.settle();

    page.button('Próxima').click();
    const last = listRequest();
    expect(last.request.params.get('page')).toBe('1');
    last.flush({ ...first, number: 1, content: [{ id: 2, name: 'Fantasia' }] });
    await page.settle();

    page.button('Excluir').click();
    http.expectOne('/api/acervo/generos/2').flush(null);
    const empty = listRequest();
    expect(empty.request.params.get('page')).toBe('1');
    empty.flush({ ...first, number: 1, content: [], totalPages: 1, totalElements: 20 });
    const fallback = listRequest();
    expect(fallback.request.params.get('page')).toBe('0');
    fallback.flush({ ...first, totalPages: 1, totalElements: 20 });
    await page.settle();
    expect(page.el.textContent).toContain('Página 1 de 1');
    expect(page.el.textContent).toContain('Gênero excluído.');
    expect(page.el.textContent).toContain('Romance');
  });

  it.each([
    { resource: 'autores', title: 'Autores', record: { id: 1, name: 'Machado de Assis', nationality: 'Brasileira' } },
    { resource: 'generos', title: 'Gêneros', record: BOOK.genre },
  ])('abre $title pela sidebar quando a API anterior responde 401', async ({ resource, title, record }) => {
    signIn(['LIBRARIAN']);
    const page = await render('/conta');
    const destination = '/acervo/' + resource;
    page.el.querySelector<HTMLAnchorElement>('.site-nav a[href="' + destination + '"]')!.click();
    await page.settle();

    const listing = http.expectOne((request) => request.url === '/api' + destination);
    expect(listing.request.headers.get('Authorization')).toMatch(/^Bearer /);
    listing.flush({ message: 'Autenticação necessária' }, { status: 401, statusText: 'Unauthorized' });
    const verification = http.expectOne((request) => request.url === '/api/livros/acervo');
    expect(verification.request.params.get('size')).toBe('1');
    expect(verification.request.headers.get('Authorization')).toBe(listing.request.headers.get('Authorization'));
    verification.flush({ content: [], number: 0, totalPages: 0, size: 1, totalElements: 0 });
    const legacy = http.expectOne('/api/' + resource);
    expect(legacy.request.headers.has('Authorization')).toBe(false);
    legacy.flush([record]);
    await page.settle();

    expect(TestBed.inject(Router).url).toBe(destination);
    expect(page.el.querySelector('h1')?.textContent?.trim()).toBe(title);
    expect(page.el.querySelector('tbody')?.textContent).toContain(record.name);
    expect(page.el.querySelector('.pagination-total')?.textContent).toContain('1 resultado');
    expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull();

    page.fill('q', resource === 'autores' ? '  BRASILEIRA  ' : '  ROM  ');
    page.submit();
    http.expectOne((request) => request.url === '/api' + destination)
      .flush(null, { status: 405, statusText: 'Method Not Allowed' });
    http.expectOne((request) => request.url === '/api/livros/acervo')
      .flush({ content: [], number: 0, totalPages: 0, size: 1, totalElements: 0 });
    http.expectOne('/api/' + resource).flush([record, { id: 2, name: 'Outro registro' }]);
    await page.settle();
    expect(page.el.querySelector('tbody')?.textContent).toContain(record.name);
    expect(page.el.querySelector('tbody')?.textContent).not.toContain('Outro registro');
  });

  it('encerra a sessão se a verificação do acervo também rejeitar o token', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo/autores');
    http.expectOne((request) => request.url === '/api/acervo/autores')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne((request) => request.url === '/api/livros/acervo')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectNone('/api/autores');

    await page.settle();
    expect(TestBed.inject(Router).url).toBe('/entrar?sessao=expirada');
    expect(page.el.textContent).toContain('Sua sessão expirou.');
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });

  it('recolhe a navegação em categorias e mantém os atalhos acessíveis', async () => {
    signIn(['ADMIN']);
    const page = await render('/conta');
    const toggle = page.el.querySelector<HTMLButtonElement>('.navigation-toggle')!;
    try {
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      toggle.click();
      await page.settle();
      expect(toggle.getAttribute('aria-label')).toBe('Expandir navegação');
      const categories = [...page.el.querySelectorAll<HTMLButtonElement>('.category-toggle')];
      expect(categories.map((button) => button.getAttribute('aria-label')))
        .toEqual(['Livros', 'Operacional', 'Sistema']);
      expect(page.el.querySelectorAll('.navigation-links:not([hidden])')).toHaveLength(0);
      categories[2].click();
      await page.settle();
      expect(page.el.querySelector('.navigation-links:not([hidden])')?.textContent).toContain('Usuários');
      expect(categories[2].getAttribute('aria-expanded')).toBe('true');
      toggle.click();
      await page.settle();
      expect(page.el.querySelector('.category-toggle')).toBeNull();
      expect(page.el.querySelectorAll('.navigation-links:not([hidden])')).toHaveLength(3);
    } finally {
      localStorage.removeItem('catalogo.sidebar.compact');
    }
  });

  it('mostra o rótulo da ação da tabela no foco sem perder seu nome acessível', async () => {
    signIn(['LIBRARIAN']);
    const page = await render('/acervo/generos');
    http.expectOne((request) => request.url === '/api/acervo/generos').flush({
      content: [BOOK.genre], number: 0, size: 20, totalPages: 1, totalElements: 1,
    });
    await page.settle();
    const edit = page.el.querySelector<HTMLButtonElement>('td.action-column button[aria-label="Editar"]')!;
    const tooltip = edit.querySelector<HTMLElement>('.row-action-tooltip')!;
    expect(edit.querySelector('svg')).not.toBeNull();
    expect(tooltip.hidden).toBe(true);
    edit.dispatchEvent(new FocusEvent('focus'));
    await page.settle();
    expect(tooltip.hidden).toBe(false);
    expect(tooltip.textContent).toBe('Editar');
    edit.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await page.settle();
    expect(tooltip.hidden).toBe(true);
  });

});
