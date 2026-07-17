import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TitleStrategy, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AppTitleStrategy } from './title.strategy';

@Component({ template: '' })
class BlankComponent {}

describe('AppTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'chat', component: BlankComponent, title: 'Chat' },
          { path: 'untitled', component: BlankComponent },
        ]),
        { provide: TitleStrategy, useClass: AppTitleStrategy },
      ],
    });
  });

  it('prefixes the route title with the app name', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/chat');
    expect(document.title).toBe('Ask App - Chat');
  });

  it('falls back to the bare app name when a route has no title', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/untitled');
    expect(document.title).toBe('Ask App');
  });
});
