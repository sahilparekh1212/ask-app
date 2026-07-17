import { Component, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AssistantService } from './assistant.service';
import { ChatTurn } from './assistant.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';

/** A rendered chat entry; `blocked` marks the server's local guardrail refusals. */
interface DisplayTurn extends ChatTurn {
  blocked?: boolean;
}

@Component({
  selector: 'app-assistant',
  imports: [ReactiveFormsModule, TranslatePipe, MarkdownPipe],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent {
  private readonly fb = inject(FormBuilder);
  private readonly assistant = inject(AssistantService);
  private readonly location = inject(Location);

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
    this.startConversationIfNeeded();
    // History = everything said so far, minus guardrail refusals (they carry no context).
    const history: ChatTurn[] = this.turns()
      .filter((t) => !t.blocked)
      .map(({ role, content }) => ({ role, content }));

    this.turns.update((t) => [...t, { role: 'user', content: message }]);
    this.input.setValue('');
    this.busy.set(true);
    this.error.set(null);
    this.scrollToEnd();

    this.assistant.chat(message, history).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.turns.update((t) => [
          ...t,
          { role: 'assistant', content: res.reply, blocked: res.blocked },
        ]);
        this.scrollToEnd();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.status === 503 ? 'assistant.error503' : 'assistant.errorGeneric');
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

  private scrollToEnd(): void {
    // The page scrolls (single far-right scrollbar); jump to the newest turn after it renders.
    setTimeout(() => window.scrollTo({ top: document.documentElement.scrollHeight }));
  }
}
