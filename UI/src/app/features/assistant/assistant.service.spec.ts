import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AssistantService } from './assistant.service';
import { ChatTurn } from './assistant.models';
import { environment } from '../../../environments/environment';

describe('AssistantService', () => {
  let service: AssistantService;
  let httpMock: HttpTestingController;
  const url = `${environment.auditApiUrl}/api/v1/assistant/chat`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AssistantService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the message and history to the chat endpoint', () => {
    const history: ChatTurn[] = [
      { role: 'user', content: 'hi' },
      { role: 'assistant', content: 'hello' },
    ];

    service.chat('what now?', history).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: 'what now?', history });
    req.flush({ reply: 'ok', blocked: false });
  });

  it('caps replayed history at the server limit, keeping the most recent turns', () => {
    const history: ChatTurn[] = Array.from({ length: 30 }, (_, i) => ({
      role: 'user' as const,
      content: `turn ${i}`,
    }));

    service.chat('latest', history).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.body.history.length).toBe(AssistantService.MAX_HISTORY);
    expect(req.request.body.history[0].content).toBe('turn 10');
    expect(req.request.body.history[19].content).toBe('turn 29');
    req.flush({ reply: 'ok', blocked: false });
  });

  it('posts text to the speak endpoint and returns the audio as a blob', () => {
    const speakUrl = `${environment.auditApiUrl}/api/v1/assistant/speak`;
    let received: Blob | undefined;

    service.speak('read this aloud').subscribe((blob) => (received = blob));

    const req = httpMock.expectOne(speakUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: 'read this aloud' });
    expect(req.request.responseType).toBe('blob');

    const audio = new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/mpeg' });
    req.flush(audio);
    expect(received).toBe(audio);
  });
});
