export interface TranscriptionMessage {
  id: string;
  role: "user" | "system";
  text: string;
  translatedText?: string;
  confidence: number;
  timestamp: string;
}

export interface IntentResult {
  intent: string;
  confidence: number;
  entities: Record<string, unknown>;
  ruleAction?: string;
  date?: string;
  time?: string;
}

export interface SystemStatus {
  frontend: "online" | "offline";
  backend: "online" | "offline";
  pythonWorker: "online" | "offline";
  websocket: "online" | "offline";
  whisperModelLoaded?: boolean;
  intentModelLoaded?: boolean;
}

export interface ActivityLogItem {
  id: string;
  timestamp: string;
  message: string;
  level: "INFO" | "WARN" | "ERROR";
}

export interface AuthUser {
  userId: string;
  email: string;
  displayName: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

export type WsEventType =
  | "TRANSCRIPTION"
  | "INTENT"
  | "ERROR"
  | "SESSION_CLOSED";

export interface WsEvent<T = unknown> {
  type: WsEventType;
  payload: T;
  timestampMs: number;
}

export interface FileProcessingResult {
  sessionId: string;
  success: boolean;
  errorMessage?: string;
  segments: { text: string; translatedText?: string; confidence: number; startTimeSec?: number; endTimeSec?: number }[];
  intents: IntentResult[];
  fullTranscript: string;
  durationSec: number;
}
