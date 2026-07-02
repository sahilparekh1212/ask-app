/** Response shape of POST /auth/login and POST /auth/refresh. */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number; // access-token lifetime in seconds
}

/** Response shape of GET /auth/me (v1 — the default when no X-API-Version header is sent). */
export interface UserProfile {
  userId: string;
  email?: string;
  name?: string;
}

/** Credentials for the demo login (no Google setup required). */
export interface DemoLoginRequest {
  username: string;
  password: string;
  role?: string;
}
