import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Location } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AssistantService } from './assistant.service';
import { ChatTurn } from './assistant.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';
import { VoiceService } from '../../core/voice/voice.service';
import { FeatureFlagService } from '../../core/feature-flags/feature-flag.service';

/** A rendered chat entry; `blocked` marks the server's local guardrail refusals. */
interface DisplayTurn extends ChatTurn {
  blocked?: boolean;
}

@Component({
  selector: 'app-assistant',
  imports: [ReactiveFormsModule, TranslatePipe, MarkdownPipe],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly assistant = inject(AssistantService);
  private readonly location = inject(Location);
  private readonly voice = inject(VoiceService);
  private readonly flags = inject(FeatureFlagService);

  // Feature flags: voice controls and the hints popover are also gated by DB flags (see ADR-0015),
  // AND-ed with the browser-capability checks below. Default (flag on) leaves behavior unchanged.
  readonly voiceEnabled = computed(() => this.flags.isEnabled('voice'));
  readonly hintsEnabled = computed(() => this.flags.isEnabled('hints'));

  // Voice I/O via the browser's Web Speech API (no backend). Controls hide where unsupported.
  readonly canListen = this.voice.canListen;
  readonly canSpeak = this.voice.canSpeak;
  readonly listening = this.voice.listening;
  // Index of the assistant turn currently being read aloud (null = nothing speaking).
  readonly speakingIndex = signal<number | null>(null);
  // In-flight request for server-synthesized speech, tracked so stopping read-aloud (or starting
  // another) can cancel a fetch that hasn't returned audio yet.
  private speakSub: Subscription | null = null;

  // Hands-free "voice only" chat: tap once and it listens → sends → reads the reply aloud →
  // listens again, an eyes-free conversation, until toggled off. Replies are asked to be short and
  // spoken-friendly (the `voice` flag on the chat call). voicePhase drives the composer's status.
  readonly voiceMode = signal(false);
  readonly voicePhase = signal<'listening' | 'thinking' | 'speaking'>('listening');
  readonly voicePhaseLabel = computed(
    () =>
      ({
        listening: 'assistant.voiceListening',
        thinking: 'assistant.voiceThinking',
        speaking: 'assistant.voiceSpeaking',
      })[this.voicePhase()],
  );
  // What the user has said so far in the current listening turn (sent when they pause).
  private voiceTranscript = '';

  // Pre-filled (not placeholder) so a visitor can hit Send immediately — and the default is
  // a conceptual question the RAG grounding answers well, doubling as a feature demo.
  readonly input = this.fb.nonNullable.control('How does the RAG pipeline behind this chat work?');
  // The [formGroup] binding is what attaches Angular's form directive to the <form> element;
  // without it, (ngSubmit) is a dead binding and the Send button falls back to a native
  // page-reload GET submit — no API call ever fires (the prod bug this group fixes).
  readonly form = this.fb.group({ message: this.input });
  readonly turns = signal<DisplayTurn[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  // The conversation's URL id (ChatGPT-style /chat/<id>), null until the first message is sent.
  private readonly conversationId = signal<string | null>(null);
  // Before the first message the composer is centered (welcome screen); once a turn exists it
  // docks to the bottom and the chat log takes over — drives the `.started` layout class.
  readonly started = computed(() => this.turns().length > 0);

  // Index of the turn whose copy control was just clicked (shows a transient "Copied" state).
  readonly copiedIndex = signal<number | null>(null);

  constructor() {
    // Landing directly on /chat/<id> (reload or shared link): adopt that id so the first send
    // doesn't mint a new one and rewrite the URL. No server-side history — the chat starts empty.
    const existing = /^\/chat\/([^/?#]+)/.exec(this.location.path());
    if (existing) {
      this.conversationId.set(existing[1]);
    }
  }

  send(): void {
    const message = this.input.value.trim();
    if (!message || this.busy()) {
      return;
    }
    // A new typed question ends any dictation / read-aloud / hands-free chat in progress.
    this.voice.stopDictation();
    this.stopReadAloud();
    if (this.voiceMode()) {
      this.stopVoiceChat();
    }
    this.input.setValue('');
    this.submitMessage(message, false);
  }

  /**
   * Post a message to the assistant and append the reply. Shared by the typed {@link send} and the
   * hands-free voice loop: {@code voice} asks the server for a short, speakable answer, and
   * {@code onReply} (voice mode) receives the reply text to read aloud and continue the loop.
   */
  private submitMessage(message: string, voice: boolean, onReply?: (reply: string) => void): void {
    this.startConversationIfNeeded();
    // History = everything said so far, minus guardrail refusals (they carry no context).
    const history: ChatTurn[] = this.turns()
      .filter((t) => !t.blocked)
      .map(({ role, content }) => ({ role, content }));

    this.turns.update((t) => [...t, { role: 'user', content: message }]);
    this.busy.set(true);
    this.error.set(null);
    this.scrollToEnd();

    this.assistant.chat(message, history, voice).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.turns.update((t) => [
          ...t,
          { role: 'assistant', content: res.reply, blocked: res.blocked },
        ]);
        this.scrollToEnd();
        onReply?.(res.reply);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.status === 503 ? 'assistant.error503' : 'assistant.errorGeneric');
        // A failed turn ends the hands-free loop rather than silently re-listening on an error.
        if (this.voiceMode()) {
          this.stopVoiceChat();
        }
      },
    });
  }

  /**
   * On the first message of a conversation, mint an id and reflect it in the URL as
   * {@code /chat/<id>} — like ChatGPT. Uses {@link Location#replaceState} rather than a router
   * navigation so the component instance (and the in-progress turns) is preserved; a reload of
   * that URL matches the {@code chat/:id} route and re-opens an empty chat with the same id.
   */
  private startConversationIfNeeded(): void {
    if (this.conversationId()) {
      return;
    }
    const id = this.newConversationId();
    this.conversationId.set(id);
    this.location.replaceState(`/chat/${id}`);
  }

  private newConversationId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}${Math.floor(Math.random() * 1e9).toString(36)}`;
  }

  /** Copy a message (question or answer) to the clipboard, flagging it briefly as copied. */
  copy(text: string, index: number): void {
    const clipboard = navigator.clipboard;
    if (!clipboard) {
      return;
    }
    clipboard
      .writeText(text)
      .then(() => {
        this.copiedIndex.set(index);
        setTimeout(() => {
          if (this.copiedIndex() === index) {
            this.copiedIndex.set(null);
          }
        }, 1500);
      })
      .catch(() => {
        /* clipboard blocked (permissions / insecure context) — nothing to show */
      });
  }

  /**
   * Toggle voice dictation (Web Speech API). While listening, the transcript-so-far streams into
   * the composer input; clicking again (or a natural pause) stops it. A no-op where unsupported.
   */
  dictate(): void {
    // Hands-free chat drives the mic itself; ignore the manual toggle while it's running.
    if (this.voiceMode()) {
      return;
    }
    if (this.listening()) {
      this.voice.stopDictation();
      return;
    }
    // Don't dictate over an in-progress read-aloud.
    this.stopReadAloud();
    this.voice.startDictation((text) => this.input.setValue(text));
  }

  /**
   * Toggle hands-free "voice only" chat: tap once and the assistant listens, answers aloud in a
   * short spoken reply, then listens again — an eyes-free conversation. Tap again to end it.
   * Requires dictation support (Chrome/Edge); a no-op otherwise.
   */
  toggleVoiceChat(): void {
    if (this.voiceMode()) {
      this.stopVoiceChat();
      return;
    }
    if (!this.canListen) {
      return;
    }
    // Starting a conversation supersedes any typing / dictation / read-aloud in progress.
    this.voice.stopDictation();
    this.stopReadAloud();
    this.voiceMode.set(true);
    this.listenForVoiceTurn();
  }

  /** End hands-free chat and quiet everything: the mic, any playback, and a pending synth fetch. */
  private stopVoiceChat(): void {
    this.voiceMode.set(false);
    this.voice.stopDictation();
    this.stopReadAloud();
  }

  /** One listening turn: capture speech; when the user pauses, send it (or re-listen if silent). */
  private listenForVoiceTurn(): void {
    if (!this.voiceMode()) {
      return;
    }
    this.voicePhase.set('listening');
    this.voiceTranscript = '';
    const started = this.voice.startDictation(
      (text) => (this.voiceTranscript = text),
      () => this.onVoiceTurnSpoken(),
    );
    if (!started) {
      this.stopVoiceChat();
    }
  }

  /** The user paused: send what they said (a voice reply), or keep the ear open if nothing landed. */
  private onVoiceTurnSpoken(): void {
    if (!this.voiceMode()) {
      return;
    }
    const spoken = this.voiceTranscript.trim();
    if (!spoken) {
      // Heard only silence/noise — re-listen rather than send an empty turn.
      this.listenForVoiceTurn();
      return;
    }
    this.voicePhase.set('thinking');
    this.submitMessage(spoken, true, (reply) => this.speakReplyThenListen(reply));
  }

  /** Read the reply aloud, then (still hands-free) open the mic for the next question. */
  private speakReplyThenListen(reply: string): void {
    if (!this.voiceMode()) {
      return;
    }
    this.voicePhase.set('speaking');
    this.speakText(this.toPlainText(reply), () => {
      if (this.voiceMode()) {
        this.listenForVoiceTurn();
      }
    });
  }

  /**
   * Read an assistant reply aloud, or stop it if that same reply is already being read. The reply
   * is Markdown, so it's flattened to plain prose first (bullets/backticks/links shouldn't be
   * spoken literally). Prefers the server's natural neural voice (Google Cloud TTS); if that's
   * unavailable — no key configured (503) or any error — it falls back to the browser's own voice.
   */
  speak(text: string, index: number): void {
    if (this.speakingIndex() === index) {
      this.stopReadAloud();
      return;
    }
    this.stopReadAloud();
    // Optimistic: reflect "reading" state now; cleared by onEnd (or if both voices are unavailable).
    this.speakingIndex.set(index);
    this.speakText(this.toPlainText(text), () => {
      if (this.speakingIndex() === index) {
        this.speakingIndex.set(null);
      }
    });
  }

  /**
   * Speak plain prose, preferring the server's natural neural voice (Google Cloud TTS) and falling
   * back to the browser's own voice on 503/error; {@link onEnd} fires when speech finishes (or
   * can't start at all). The in-flight synth fetch is tracked so {@link stopReadAloud} can cancel it.
   */
  private speakText(plain: string, onEnd: () => void): void {
    this.speakSub = this.assistant.speak(plain).subscribe({
      next: (audio) => this.voice.playAudio(audio, onEnd),
      error: () => {
        if (!this.voice.speak(plain, onEnd)) {
          onEnd();
        }
      },
    });
  }

  /** Stop any read-aloud: cancel an in-flight synth request, stop playback, and reset the state. */
  private stopReadAloud(): void {
    this.speakSub?.unsubscribe();
    this.speakSub = null;
    this.voice.stopSpeaking();
    this.speakingIndex.set(null);
  }

  /** Flatten Markdown to speakable prose — strip fences/inline code, turn links into their text. */
  private toPlainText(markdown: string): string {
    return markdown
      .replace(/```[\s\S]*?```/g, ' code block ')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/[#>*_~`-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  ngOnDestroy(): void {
    // Leaving the page must not leave the mic open, a reply talking, a synth fetch pending, or the
    // hands-free loop running.
    this.voiceMode.set(false);
    this.voice.stopDictation();
    this.stopReadAloud();
  }

  private scrollToEnd(): void {
    // The page scrolls (single far-right scrollbar); jump to the newest turn after it renders.
    setTimeout(() => window.scrollTo({ top: document.documentElement.scrollHeight }));
  }
}
