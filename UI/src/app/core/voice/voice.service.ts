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
