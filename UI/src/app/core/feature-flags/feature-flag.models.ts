/**
 * One UI feature flag, as served by `GET /audit-api/api/v1/meta/flags`. The backend returns a list
 * of these; the client folds them into a key→enabled map. `description` is an operator note.
 */
export interface FeatureFlag {
  key: string;
  enabled: boolean;
  description: string;
}
