import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { FlashcardsService } from './flashcards.service';
import { environment } from '../../../environments/environment';

describe('FlashcardsService', () => {
  let service: FlashcardsService;
  let httpMock: HttpTestingController;
  const url = `${environment.auditApiUrl}/api/v1/assistant/flashcards`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FlashcardsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the requested count to the flashcards endpoint', () => {
    service.generate(8).subscribe();
    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ count: 8 });
    req.flush({ cards: [] });
  });
});
