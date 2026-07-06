/** One flashcard, mirroring the backend's Flashcard DTO. */
export interface Flashcard {
  question: string;
  answer: string;
}

/** Response of POST /api/v1/assistant/flashcards. */
export interface FlashcardDeck {
  cards: Flashcard[];
}
