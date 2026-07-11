import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { FlashcardsComponent } from './flashcards.component';
import { FlashcardsService } from './flashcards.service';
import { Flashcard } from './flashcards.models';

describe('FlashcardsComponent', () => {
  let fixture: ComponentFixture<FlashcardsComponent>;
  let component: FlashcardsComponent;
  let service: jasmine.SpyObj<FlashcardsService>;

  const deck: Flashcard[] = [
    { question: 'Q1', answer: 'A1' },
    { question: 'Q2', answer: 'A2' },
    { question: 'Q3', answer: 'A3' },
  ];

  beforeEach(async () => {
    service = jasmine.createSpyObj('FlashcardsService', ['generate']);
    await TestBed.configureTestingModule({
      imports: [FlashcardsComponent],
      providers: [{ provide: FlashcardsService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(FlashcardsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads a deck and shows the first card', () => {
    service.generate.and.returnValue(of({ cards: deck }));

    component.generate();

    expect(component.deck().length).toBe(3);
    expect(component.current()?.question).toBe('Q1');
    expect(component.index()).toBe(0);
    expect(component.flipped()).toBeFalse();
  });

  // Regression: (ngSubmit) is only a real event when a forms directive is attached to the
  // <form> ([formGroup]) — without it the binding is silently dead and the submit button
  // does a native page-reload GET instead of calling the API. Calling component.generate()
  // directly (like the tests above) can never catch that, so this test goes through the DOM.
  it('generates when the form itself is submitted', () => {
    service.generate.and.returnValue(of({ cards: deck }));

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form.controls');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(service.generate).toHaveBeenCalledWith(5);
    expect(component.deck().length).toBe(3);
  });

  it('flips the current card and resets flip on navigation', () => {
    service.generate.and.returnValue(of({ cards: deck }));
    component.generate();

    component.flip();
    expect(component.flipped()).toBeTrue();

    component.next();
    expect(component.index()).toBe(1);
    expect(component.flipped()).toBeFalse();

    component.prev();
    expect(component.index()).toBe(0);
  });

  it('does not navigate past the deck bounds', () => {
    service.generate.and.returnValue(of({ cards: deck }));
    component.generate();

    component.prev();
    expect(component.index()).toBe(0);

    component.next();
    component.next();
    component.next();
    expect(component.index()).toBe(2);
  });

  it('shuffle keeps the same cards and resets position', () => {
    service.generate.and.returnValue(of({ cards: deck }));
    component.generate();
    component.next();

    component.shuffle();

    expect(component.deck().length).toBe(3);
    expect([...component.deck()].map((c) => c.question).sort()).toEqual(['Q1', 'Q2', 'Q3']);
    expect(component.index()).toBe(0);
  });

  it('shows the not-configured message on 503', () => {
    service.generate.and.returnValue(throwError(() => ({ status: 503 })));

    component.generate();

    expect(component.error()).toContain('not available');
    expect(component.hasDeck()).toBeFalse();
  });

  it('rejects an out-of-range count without calling the service', () => {
    component.count.setValue(50);
    component.generate();
    expect(service.generate).not.toHaveBeenCalled();
    expect(component.error()).toContain('between 1 and 20');
  });
});
