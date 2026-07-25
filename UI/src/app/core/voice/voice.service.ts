import { Injectable, signal } from '@angular/core';

/**
 * Thin wrapper over the browser's built-in **Web Speech API** for the chat assistant's voice I/O:
 * `SpeechRecognition` (dictation, speech → text) and `SpeechSynthesis` (reading a reply aloud,
 * text → speech). No backend and no API key — it all runs client-side. Both capabilities are
 * feature-detected ({@link canListen} / {@link canSpeak}) so callers can hide the controls where the
 * browser doesn't support them (recognition is Chrome/Edge-only; synthesis is broadly supported).
 *
 * English-only, matching the app (the i18n language switcher was removed by decision).
 */
@Injectable({ providedIn: 'root' })
export class VoiceService {
  private readonly recognitionCtor = resolveRecognitionCtor();
  private recognition: SpeechRecognitionLike | null = null;

  /** True when the browser can dictate speech (Chrome/Edge; not Firefox). */
  readonly canListen = this.recognitionCtor !== null;

  /** True when the browser can synthesize speech (broad support). */
  readonly canSpeak =
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    'SpeechSynthesisUtterance' in window;

  /** Whether dictation is currently active. */
  readonly listening = signal(false);

  /** Whether a reply is currently being read aloud. */
  readonly speaking = signal(false);

  // The most natural installed voice for read-aloud (null → let the browser choose its default).
  private preferredVoice: SpeechSynthesisVoice | null = null;

  constructor() {
    if (this.canSpeak) {
      this.loadVoices();
      // The voice list often loads asynchronously (Chrome fetches its network voices), so refresh
      // the pick when it changes. Assigning the handler is safe — this is a root singleton.
      window.speechSynthesis.onvoiceschanged = () => this.loadVoices();
    }
  }

  /**
   * Start dictation, streaming the transcript-so-far to {@link onTranscript} as the user speaks;
   * {@link onEnd} fires when recognition stops (a pause, an error, or {@link stopDictation}).
   * Returns false (a no-op) when unsupported or already listening.
   */
  startDictation(onTranscript: (text: string) => void, onEnd?: () => void): boolean {
    if (!this.recognitionCtor || this.listening()) {
      return false;
    }
    const recognition = new this.recognitionCtor();
    recognition.lang = 'en-US';
    recognition.interimResults = true;
    recognition.continuous = false;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      const transcript = Array.from(event.results)
        .map((result) => result[0].transcript)
        .join('');
      onTranscript(transcript.trim());
    };
    const finish = () => {
      this.listening.set(false);
      this.recognition = null;
      onEnd?.();
    };
    recognition.onerror = finish;
    recognition.onend = finish;
    this.recognition = recognition;
    this.listening.set(true);
    recognition.start();
    return true;
  }

  stopDictation(): void {
    // stop() lets the final result flush and fires onend (which clears the listening state).
    this.recognition?.stop();
  }

  /**
   * Read {@link text} aloud; {@link onEnd} fires when it finishes (or is stopped). Cancels any
   * in-progress utterance first so it never queues. Returns false (a no-op) when unsupported or
   * the text is blank.
   */
  speak(text: string, onEnd?: () => void): boolean {
    if (!this.canSpeak || !text.trim()) {
      return false;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    // Use the most natural installed voice instead of the browser's (often robotic) default.
    if (this.preferredVoice) {
      utterance.voice = this.preferredVoice;
    }
    utterance.rate = 1;
    utterance.pitch = 1;
    const finish = () => {
      this.speaking.set(false);
      onEnd?.();
    };
    utterance.onend = finish;
    utterance.onerror = finish;
    this.speaking.set(true);
    window.speechSynthesis.speak(utterance);
    return true;
  }

  stopSpeaking(): void {
    if (this.canSpeak) {
      window.speechSynthesis.cancel();
    }
    this.speaking.set(false);
  }

  private loadVoices(): void {
    const voices = window.speechSynthesis.getVoices?.() ?? [];
    this.preferredVoice = pickNaturalVoice(voices);
  }
}

/**
 * Choose the most natural-sounding English voice from those installed. The default `en-US` voice is
 * frequently the robotic one even when better voices exist, so this scores by the markers of a
 * neural / network voice — "natural"/"neural" in the name, Google voices, "online" (server-side)
 * voices, and non-local-service voices — preferring en-US. Returns null when there's no English
 * voice (the browser then uses its own default).
 */
function pickNaturalVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  const english = voices.filter((voice) => voice.lang.toLowerCase().startsWith('en'));
  if (english.length === 0) {
    return null;
  }
  const score = (voice: SpeechSynthesisVoice): number => {
    const name = voice.name.toLowerCase();
    let value = 0;
    if (/natural|neural/.test(name)) value += 100;
    if (name.includes('google')) value += 60;
    if (name.includes('online')) value += 40;
    if (!voice.localService) value += 20; // network voices are usually the good ones
    if (voice.lang.toLowerCase() === 'en-us') value += 10;
    return value;
  };
  return english.reduce((best, voice) => (score(voice) > score(best) ? voice : best));
}

// ── Minimal typing for the non-standard SpeechRecognition (absent from lib.dom.d.ts) ──────────

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  readonly [index: number]: SpeechRecognitionAlternativeLike;
  isFinal: boolean;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function resolveRecognitionCtor(): SpeechRecognitionCtor | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const w = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}
