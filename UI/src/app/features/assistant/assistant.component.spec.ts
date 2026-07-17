import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { of, throwError } from 'rxjs';

import { AssistantComponent } from './assistant.component';
import { AssistantService } from './assistant.service';

describe('AssistantComponent', () => {
  let fixture: ComponentFixture<AssistantComponent>;
  let component: AssistantComponent;
  let assistant: jasmine.SpyObj<AssistantService>;
  let location: Location;

  beforeEach(async () => {
    assistant = jasmine.createSpyObj('AssistantService', ['chat']);
    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [{ provide: AssistantService, useValue: assistant }, provideLocationMocks()],
    }).compileComponents();

    location = TestBed.inject(Location);
    fixture = TestBed.createComponent(AssistantComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('appends the user turn and the reply on success', () => {
    assistant.chat.and.returnValue(of({ reply: 'It stores audit rows.', blocked: false }));

    component.input.setValue('What does the audit service do?');
    component.send();

    const turns = component.turns();
    expect(turns.length).toBe(2);
    expect(turns[0]).toEqual(
      jasmine.objectContaining({ role: 'user', content: 'What does the audit service do?' }),
    );
    expect(turns[1]).toEqual(
      jasmine.objectContaining({ role: 'assistant', content: 'It stores audit rows.' }),
    );
    expect(component.busy()).toBeFalse();
    expect(component.input.value).toBe('');
  });

  // Regression: (ngSubmit) is only a real event when a forms directive is attached to the
  // <form> ([formGroup]) — without it the binding is silently dead and the Send button does
  // a native page-reload GET instead of calling the API. Calling component.send() directly
  // (like the tests above) can never catch that, so this test goes through the DOM.
  it('sends when the composer form itself is submitted', () => {
    assistant.chat.and.returnValue(of({ reply: 'hi', blocked: false }));

    component.input.setValue('hello there');
    fixture.detectChanges();
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form.composer');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(assistant.chat).toHaveBeenCalledWith('hello there', []);
    expect(component.turns().length).toBe(2);
  });

  it('marks guardrail refusals as blocked and excludes them from later history', () => {
    assistant.chat.and.returnValue(of({ reply: "Can't help with that.", blocked: true }));
    component.input.setValue('my password=hunter2');
    component.send();
    expect(component.turns()[1].blocked).toBeTrue();

    assistant.chat.and.returnValue(of({ reply: 'sure', blocked: false }));
    component.input.setValue('ok, a clean question');
    component.send();

    const sentHistory = assistant.chat.calls.mostRecent().args[1];
    expect(sentHistory.some((t) => t.content === "Can't help with that.")).toBeFalse();
    expect(sentHistory.some((t) => t.content === 'my password=hunter2')).toBeTrue();
  });

  it('shows the not-configured message on 503', () => {
    assistant.chat.and.returnValue(throwError(() => ({ status: 503 })));

    component.input.setValue('hello?');
    component.send();

    expect(component.error()).toBe('assistant.error503');
    expect(component.busy()).toBeFalse();
  });

  it('ignores empty input', () => {
    component.input.setValue('   ');
    component.send();
    expect(assistant.chat).not.toHaveBeenCalled();
    expect(component.turns().length).toBe(0);
  });

  it('is not "started" until the first message, then docks and updates the URL with an id', () => {
    assistant.chat.and.returnValue(of({ reply: 'ok', blocked: false }));
    expect(component.started()).toBeFalse();

    component.input.setValue('hello');
    component.send();

    expect(component.started()).toBeTrue();
    expect(location.path()).toMatch(/^\/chat\/.+/);
  });

  it('copies a turn to the clipboard and flags it briefly as copied', fakeAsync(() => {
    const writeText = jasmine.createSpy('writeText').and.returnValue(Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    component.copy('How does the RAG pipeline work?', 1);
    tick();
    expect(writeText).toHaveBeenCalledWith('How does the RAG pipeline work?');
    expect(component.copiedIndex()).toBe(1);

    // The copied flag clears itself after the transient window.
    tick(1500);
    expect(component.copiedIndex()).toBeNull();
  }));

  it('keeps the same URL id across subsequent messages', () => {
    assistant.chat.and.returnValue(of({ reply: 'ok', blocked: false }));
    component.input.setValue('first');
    component.send();
    const firstUrl = location.path();

    component.input.setValue('second');
    component.send();

    expect(location.path()).toBe(firstUrl);
  });
});
