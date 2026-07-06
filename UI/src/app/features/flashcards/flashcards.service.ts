import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FlashcardDeck } from './flashcards.models';

/**
 * Requests an LLM-generated study deck from the Audit service's assistant proxy. As with
 * chat, the SPA never talks to the provider directly — only to our own guarded backend.
 */
@Injectable({ providedIn: 'root' })
export class FlashcardsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.auditApiUrl}/api/v1/assistant`;

  generate(count: number): Observable<FlashcardDeck> {
    return this.http.post<FlashcardDeck>(`${this.base}/flashcards`, { count });
  }
}
