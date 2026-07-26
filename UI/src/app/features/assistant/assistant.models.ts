/** One turn of the chat, mirrored to the backend's ChatTurn DTO. */
export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

/** Body of POST /api/v1/assistant/chat. */
export interface ChatRequest {
  message: string;
  history: ChatTurn[];
  /** True for hands-free voice chat — the server answers in a few short, speakable sentences. */
  voice?: boolean;
}

/** Response of POST /api/v1/assistant/chat. */
export interface ChatResponse {
  reply: string;
  /** True when the server-side sensitive-data screen answered locally. */
  blocked: boolean;
}
