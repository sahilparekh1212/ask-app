import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AssistantService } from './assistant.service';
import { ChatTurn } from './assistant.models';

/** A rendered chat entry; `blocked` marks the server's local guardrail refusals. */
interface DisplayTurn extends ChatTurn {
  blocked?: boolean;
}

@Component({
  selector: 'app-assistant',
  imports: [ReactiveFormsModule],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
})
export class AssistantComponent {
  private readonly fb = inject(FormBuilder);
  private readonly assistant = inject(AssistantService);

  readonly input = this.fb.nonNullable.control('');
  // The [formGroup] binding is what attaches Angular's form directive to the <form> element;
  // without it, (ngSubmit) is a dead binding and the Send button falls back to a native
  // page-reload GET submit — no API call ever fires (the prod bug this group fixes).
  readonly form = this.fb.group({ message: this.input });
  readonly turns = signal<DisplayTurn[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  send(): void {
    const message = this.input.value.trim();
    if (!message || this.busy()) {
      return;
    }
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
        this.error.set(
          err?.status === 503
            ? 'The assistant is not available right now (it may not be configured on this server).'
            : 'Something went wrong — please try again.',
        );
      },
    });
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const el = this.log()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }
}
