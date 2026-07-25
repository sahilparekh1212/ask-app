import { VoiceService } from './voice.service';

/** A stand-in for the browser's SpeechRecognition whose events the test drives directly. */
class FakeRecognition {
  lang = '';
  interimResults = false;
  continuous = false;
  maxAlternatives = 0;
  onresult:
    ((event: { results: ArrayLike<Record<number, { transcript: string }>> }) => void) | null = null;
  onerror: (() => void) | null = null;
  onend: (() => void) | null = null;
  start = jasmine.createSpy('start');
  stop = jasmine.createSpy('stop').and.callFake(() => this.onend?.());

  emit(transcript: string): void {
    this.onresult?.({ results: [{ 0: { transcript } }] });
  }
}

describe('VoiceService', () => {
  const win = window as unknown as Record<string, unknown>;
  let originalRecognition: unknown;
  let originalWebkit: unknown;
  let lastRecognition: FakeRecognition;

  beforeEach(() => {
    originalRecognition = win['SpeechRecognition'];
    originalWebkit = win['webkitSpeechRecognition'];
    delete win['SpeechRecognition'];
    win['webkitSpeechRecognition'] = function () {
      lastRecognition = new FakeRecognition();
      return lastRecognition;
    };
  });

  afterEach(() => {
    win['SpeechRecognition'] = originalRecognition;
    win['webkitSpeechRecognition'] = originalWebkit;
  });

  it('detects recognition support and streams the transcript while listening', () => {
    const service = new VoiceService();
    expect(service.canListen).toBeTrue();

    const transcripts: string[] = [];
    const started = service.startDictation((text) => transcripts.push(text));

    expect(started).toBeTrue();
    expect(service.listening()).toBeTrue();
    expect(lastRecognition.lang).toBe('en-US');

    lastRecognition.emit('hello world');
    expect(transcripts).toEqual(['hello world']);

    service.stopDictation();
    expect(lastRecognition.stop).toHaveBeenCalled();
    expect(service.listening()).toBeFalse();
  });

  it('is a no-op for dictation when recognition is unsupported', () => {
    delete win['webkitSpeechRecognition'];
    const service = new VoiceService();

    expect(service.canListen).toBeFalse();
    expect(service.startDictation(() => undefined)).toBeFalse();
    expect(service.listening()).toBeFalse();
  });

  it('speaks via speechSynthesis and clears the speaking flag when it ends', () => {
    const spoken: SpeechSynthesisUtterance[] = [];
    const fakeSynth = {
      cancel: jasmine.createSpy('cancel'),
      speak: jasmine
        .createSpy('speak')
        .and.callFake((utterance: SpeechSynthesisUtterance) => spoken.push(utterance)),
    };
    const original = Object.getOwnPropertyDescriptor(window, 'speechSynthesis');
    Object.defineProperty(window, 'speechSynthesis', { value: fakeSynth, configurable: true });
    try {
      const service = new VoiceService();
      expect(service.canSpeak).toBeTrue();

      let ended = false;
      const started = service.speak('the answer', () => (ended = true));

      expect(started).toBeTrue();
      expect(service.speaking()).toBeTrue();
      expect(fakeSynth.cancel).toHaveBeenCalled(); // cancels any queued utterance first
      expect(spoken[0].lang).toBe('en-US');

      spoken[0].onend?.(new Event('end') as SpeechSynthesisEvent);
      expect(service.speaking()).toBeFalse();
      expect(ended).toBeTrue();

      service.stopSpeaking();
      expect(fakeSynth.cancel).toHaveBeenCalledTimes(2);
    } finally {
      if (original) {
        Object.defineProperty(window, 'speechSynthesis', original);
      }
    }
  });

  it('does not speak blank text', () => {
    const fakeSynth = { cancel: jasmine.createSpy(), speak: jasmine.createSpy() };
    const original = Object.getOwnPropertyDescriptor(window, 'speechSynthesis');
    Object.defineProperty(window, 'speechSynthesis', { value: fakeSynth, configurable: true });
    try {
      const service = new VoiceService();
      expect(service.speak('   ')).toBeFalse();
      expect(fakeSynth.speak).not.toHaveBeenCalled();
    } finally {
      if (original) {
        Object.defineProperty(window, 'speechSynthesis', original);
      }
    }
  });
});
