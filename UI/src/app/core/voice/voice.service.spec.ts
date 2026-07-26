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

/** A stand-in for SpeechSynthesisUtterance that just records what the service set on it. */
class FakeUtterance {
  voice: SpeechSynthesisVoice | null = null;
  lang = '';
  rate = 1;
  pitch = 1;
  onend: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(public text: string) {}
}

/** A stand-in for HTMLAudioElement whose lifecycle events the test drives directly. */
class FakeAudio {
  onended: (() => void) | null = null;
  onerror: (() => void) | null = null;
  play = jasmine.createSpy('play').and.returnValue(Promise.resolve());
  pause = jasmine.createSpy('pause');
  constructor(public src: string) {}
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
    const fakeSynth = {
      cancel: jasmine.createSpy(),
      speak: jasmine.createSpy(),
      getVoices: () => [],
    };
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

  it('reads aloud with the most natural available voice, not the robotic default', () => {
    const robotic = {
      name: 'Microsoft David',
      lang: 'en-US',
      localService: true,
    } as SpeechSynthesisVoice;
    const natural = {
      name: 'Google US English',
      lang: 'en-US',
      localService: false,
    } as SpeechSynthesisVoice;
    const spoken: FakeUtterance[] = [];
    const fakeSynth = {
      cancel: jasmine.createSpy('cancel'),
      speak: jasmine.createSpy('speak').and.callFake((u: FakeUtterance) => spoken.push(u)),
      getVoices: () => [robotic, natural],
    };
    const originalSynth = Object.getOwnPropertyDescriptor(window, 'speechSynthesis');
    const originalUtterance = win['SpeechSynthesisUtterance'];
    Object.defineProperty(window, 'speechSynthesis', { value: fakeSynth, configurable: true });
    win['SpeechSynthesisUtterance'] = FakeUtterance;
    try {
      const service = new VoiceService();
      expect(service.speak('hello')).toBeTrue();
      expect(spoken[0].voice).toBe(natural);
      expect(spoken[0].lang).toBe('en-US');
    } finally {
      if (originalSynth) {
        Object.defineProperty(window, 'speechSynthesis', originalSynth);
      }
      win['SpeechSynthesisUtterance'] = originalUtterance;
    }
  });

  /** Stub the global Audio ctor and object-URL helpers; returns cleanup + the created elements. */
  function withFakeAudio(): { created: FakeAudio[]; revoke: jasmine.Spy; restore: () => void } {
    const created: FakeAudio[] = [];
    const originalAudio = win['Audio'];
    win['Audio'] = function (src: string) {
      const audio = new FakeAudio(src);
      created.push(audio);
      return audio;
    } as unknown;
    const fakeSynth = { cancel: jasmine.createSpy('cancel'), getVoices: () => [] };
    const originalSynth = Object.getOwnPropertyDescriptor(window, 'speechSynthesis');
    Object.defineProperty(window, 'speechSynthesis', { value: fakeSynth, configurable: true });
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake-url');
    const revoke = spyOn(URL, 'revokeObjectURL');
    return {
      created,
      revoke,
      restore: () => {
        win['Audio'] = originalAudio;
        if (originalSynth) {
          Object.defineProperty(window, 'speechSynthesis', originalSynth);
        }
      },
    };
  }

  it('plays server-synthesized audio and clears the speaking flag when it ends', () => {
    const { created, revoke, restore } = withFakeAudio();
    try {
      const service = new VoiceService();
      const blob = new Blob([new Uint8Array([1, 2])], { type: 'audio/mpeg' });
      let ended = false;

      service.playAudio(blob, () => (ended = true));

      expect(created.length).toBe(1);
      expect(created[0].src).toBe('blob:fake-url');
      expect(created[0].play).toHaveBeenCalled();
      expect(service.speaking()).toBeTrue();

      created[0].onended?.();
      expect(service.speaking()).toBeFalse();
      expect(ended).toBeTrue();
      expect(revoke).toHaveBeenCalledWith('blob:fake-url'); // no leaked object URL
    } finally {
      restore();
    }
  });

  it('stopSpeaking halts in-progress server audio and revokes its object URL', () => {
    const { created, revoke, restore } = withFakeAudio();
    try {
      const service = new VoiceService();
      let ended = false;
      service.playAudio(new Blob(['x']), () => (ended = true));
      expect(service.speaking()).toBeTrue();

      service.stopSpeaking();

      expect(created[0].pause).toHaveBeenCalled();
      expect(service.speaking()).toBeFalse();
      expect(revoke).toHaveBeenCalledWith('blob:fake-url');
      // Handlers are detached on stop, so a late 'ended' can't re-fire the onEnd callback.
      created[0].onended?.();
      expect(ended).toBeFalse();
    } finally {
      restore();
    }
  });
});
