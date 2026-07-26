import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatResponse, ChatTurn } from './assistant.models';

/**
 * Calls the Audit service's LLM assistant proxy. The SPA never talks to the LLM provider —
 * only to our own backend, which holds the API key and screens the input server-side.
 */
@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.auditApiUrl}/api/v1/assistant`;

  /** History is capped server-side at 20 turns; send only the most recent ones. */
  static readonly MAX_HISTORY = 20;

  chat(message: string, history: ChatTurn[]): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.base}/chat`, {
      message,
      history: history.slice(-AssistantService.MAX_HISTORY),
    });
  }

  /**
   * Synthesize a reply to natural speech via the server's Google Cloud TTS proxy, returning the
   * MP3 as a Blob. Errors (notably 503 when no TTS key is configured) are surfaced to the caller,
   * which falls back to the browser's built-in voice.
   */
  speak(text: string): Observable<Blob> {
    return this.http.post(`${this.base}/speak`, { text }, { responseType: 'blob' });
  }
}
