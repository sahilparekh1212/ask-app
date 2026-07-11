import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { FlashcardsService } from './flashcards.service';
import { Flashcard } from './flashcards.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-flashcards',
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './flashcards.component.html',
  styleUrl: './flashcards.component.scss',
})
export class FlashcardsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly flashcards = inject(FlashcardsService);

  readonly count = this.fb.nonNullable.control(5);
  // The [formGroup] binding is what attaches Angular's form directive to the <form> element;
  // without it, (ngSubmit) is a dead binding and the submit button falls back to a native
  // page-reload GET submit — no API call ever fires (the prod bug this group fixes).
  readonly form = this.fb.group({ count: this.count });
  readonly deck = signal<Flashcard[]>([]);
  readonly index = signal(0);
  readonly flipped = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly current = computed(() => this.deck()[this.index()] ?? null);
  readonly hasDeck = computed(() => this.deck().length > 0);

  generate(): void {
    const count = this.count.value;
    if (this.loading() || count < 1 || count > 20) {
      this.error.set(count < 1 || count > 20 ? 'flashcards.range' : null);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.flashcards.generate(count).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.deck.set(res.cards);
        this.index.set(0);
        this.flipped.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.status === 503 ? 'flashcards.error503' : 'flashcards.error');
      },
    });
  }

  flip(): void {
    this.flipped.update((f) => !f);
  }

  next(): void {
    if (this.index() < this.deck().length - 1) {
      this.index.update((i) => i + 1);
      this.flipped.set(false);
    }
  }

  prev(): void {
    if (this.index() > 0) {
      this.index.update((i) => i - 1);
      this.flipped.set(false);
    }
  }

  shuffle(): void {
    // Fisher–Yates on a copy so the signal sees a new array reference.
    const cards = [...this.deck()];
    for (let i = cards.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [cards[i], cards[j]] = [cards[j], cards[i]];
    }
    this.deck.set(cards);
    this.index.set(0);
    this.flipped.set(false);
  }
}
