import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AssistantComponent } from './assistant.component';
import { AssistantService } from './assistant.service';
import { VoiceService } from '../../core/voice/voice.service';

describe('AssistantComponent', () => {
  let fixture: ComponentFixture<AssistantComponent>;
  let component: AssistantComponent;
  let assistant: jasmine.SpyObj<AssistantService>;
  let location: Location;
  let voice: {
    canListen: boolean;
    canSpeak: boolean;
    listening: ReturnType<typeof signal<boolean>>;
    speaking: ReturnType<typeof signal<boolean>>;
    startDictation: jasmine.Spy;
    stopDictation: jasmine.Spy;
    speak: jasmine.Spy;
    playAudio: jasmine.Spy;
    stopSpeaking: jasmine.Spy;
  };

  beforeEach(async () => {
    assistant = jasmine.createSpyObj('AssistantService', ['chat', 'speak']);
    // By default the server voice is available — read-aloud plays the returned audio.
    assistant.speak.and.returnValue(of(new Blob([new Uint8Array([1])], { type: 'audio/mpeg' })));
    voice = {
      canListen: true,
      canSpeak: true,
      listening: signal(false),
      speaking: signal(false),
      startDictation: jasmine.createSpy('startDictation').and.returnValue(true),
      stopDictation: jasmine.createSpy('stopDictation'),
      speak: jasmine.createSpy('speak').and.returnValue(true),
      playAudio: jasmine.createSpy('playAudio'),
      stopSpeaking: jasmine.createSpy('stopSpeaking'),
    };
    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [
        { provide: AssistantService, useValue: assistant },
        { provide: VoiceService, useValue: voice },
        provideLocationMocks(),
      ],
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

  it('dictate() starts recognition and streams the transcript into the composer input', () => {
    component.dictate();

    expect(voice.startDictation).toHaveBeenCalled();
    const onTranscript = voice.startDictation.calls.mostRecent().args[0] as (t: string) => void;
    onTranscript('what does this app do');

    expect(component.input.value).toBe('what does this app do');
  });

  it('dictate() while already listening stops dictation instead of starting a new one', () => {
    voice.listening.set(true);

    component.dictate();

    expect(voice.stopDictation).toHaveBeenCalled();
    expect(voice.startDictation).not.toHaveBeenCalled();
  });

  it('speak() prefers the server voice, playing the synthesized audio (markdown flattened)', () => {
    component.speak('**Bold** with `code` and a [link](https://x)', 2);

    expect(assistant.speak).toHaveBeenCalled();
    const sentText = assistant.speak.calls.mostRecent().args[0] as string;
    expect(sentText).not.toContain('**');
    expect(sentText).not.toContain('`');
    expect(sentText).toContain('link');
    expect(voice.playAudio).toHaveBeenCalled();
    // The browser voice isn't used when the server voice succeeds.
    expect(voice.speak).not.toHaveBeenCalled();
    expect(component.speakingIndex()).toBe(2);

    // Clicking the same reply again stops it.
    component.speak('anything', 2);
    expect(voice.stopSpeaking).toHaveBeenCalled();
    expect(component.speakingIndex()).toBeNull();
  });

  it('speak() falls back to the browser voice when the server voice is unavailable', () => {
    assistant.speak.and.returnValue(throwError(() => ({ status: 503 })));

    component.speak('read this aloud', 1);

    expect(assistant.speak).toHaveBeenCalled();
    expect(voice.playAudio).not.toHaveBeenCalled();
    expect(voice.speak).toHaveBeenCalledWith('read this aloud', jasmine.any(Function));
    expect(component.speakingIndex()).toBe(1);
  });

  it('speak() clears the reading state when neither the server nor the browser can speak', () => {
    assistant.speak.and.returnValue(throwError(() => ({ status: 503 })));
    voice.speak.and.returnValue(false); // browser synthesis unsupported too

    component.speak('nothing can read this', 3);

    expect(component.speakingIndex()).toBeNull();
  });
});
