import { Component } from '@angular/core';

@Component({
  selector: 'app-auth-layout',
  template: `
    <main class="auth-layout">
      <section class="story-panel" aria-labelledby="story-title">
        <div class="story-copy">
          <span class="eyebrow">BIBLIOTECA</span>
          <h1 id="story-title">Reserve online.<br />Retire no balcão.</h1>
          <p>Consulte o acervo, guarde seu exemplar por 48 horas e acompanhe prazos e devoluções em um só lugar.</p>
        </div>
        <div class="shelf" aria-hidden="true">
          <span style="height: 118px; background: #b4532a"></span>
          <span style="height: 148px; background: #f6efe2"></span>
          <span style="height: 104px; background: #6f8f8a"></span>
          <span style="height: 136px; background: #d9a441"></span>
          <span style="height: 122px; background: #8c5a4a"></span>
          <span style="height: 152px; background: #e7dcc7"></span>
          <span style="height: 110px; background: #b4532a"></span>
        </div>
      </section>
      <section class="form-panel"><ng-content /></section>
    </main>
  `,
})
export class AuthLayout {}
