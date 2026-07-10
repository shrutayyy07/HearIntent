const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  errorCode?: string;

  constructor(message: string, status: number, errorCode?: string) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  accessToken?: string
): Promise<T> {
  const headers: Record<string, string> = {
    ...(options.body && !(options.body instanceof FormData)
      ? { "Content-Type": "application/json" }
      : {}),
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    ...((options.headers as Record<string, string>) || {}),
  };

  let response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401 && typeof window !== "undefined") {
    // Attempt to refresh token
    const raw = window.localStorage.getItem("hearintent_auth");
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (parsed.refreshToken) {
          const refreshResp = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: parsed.refreshToken }),
          });
          
          if (refreshResp.ok) {
            const newAuth = await refreshResp.json();
            parsed.accessToken = newAuth.accessToken;
            parsed.refreshToken = newAuth.refreshToken;
            window.localStorage.setItem("hearintent_auth", JSON.stringify(parsed));
            
            // Retry the original request with the new token
            headers["Authorization"] = `Bearer ${newAuth.accessToken}`;
            response = await fetch(`${API_BASE_URL}${path}`, {
              ...options,
              headers,
            });
            
            // Force a reload to sync the Zustand store with the new token
            // A more elegant approach would be to update the store directly, but this guarantees consistency
            window.location.reload();
          } else {
            window.localStorage.removeItem("hearintent_auth");
            window.location.href = "/login";
          }
        }
      } catch (e) {
        // Fallback to error handling if refresh logic fails
      }
    }
  }

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    let errorCode: string | undefined;
    try {
      const body = await response.json();
      message = body.message || message;
      errorCode = body.errorCode;
    } catch {
      // response had no JSON body
    }
    throw new ApiError(message, response.status, errorCode);
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  register: (data: Record<string, string>) =>
    request<{ message: string; success: boolean }>("/api/v1/auth/register", {
      method: "POST",
      body: JSON.stringify(data),
    }),

  loginWithEmail: (data: Record<string, string>) =>
    request<{
      accessToken: string;
      refreshToken: string;
      expiresInSeconds: number;
      userId: string;
      email: string;
      displayName: string;
    }>("/api/v1/auth/login/email", {
      method: "POST",
      body: JSON.stringify(data),
    }),

  requestPhoneOtp: (phoneNumber: string) =>
    request<{ message: string; success: boolean }>("/api/v1/auth/login/phone/request", {
      method: "POST",
      body: JSON.stringify({ phoneNumber }),
    }),

  verifyPhoneOtp: (phoneNumber: string, code: string) =>
    request<{
      accessToken: string;
      refreshToken: string;
      expiresInSeconds: number;
      userId: string;
      email: string;
      displayName: string;
    }>("/api/v1/auth/login/phone/verify", {
      method: "POST",
      body: JSON.stringify({ phoneNumber, code }),
    }),

  requestPasswordReset: (email: string) =>
    request<{ message: string; success: boolean }>("/api/v1/auth/password/forgot/request", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  checkPasswordReset: (email: string, code: string) =>
    request<{ success: boolean }>("/api/v1/auth/password/forgot/check", {
      method: "POST",
      body: JSON.stringify({ email, code }),
    }),

  verifyPasswordReset: (email: string, otp: string, newPassword: string) =>
    request<{ message: string; success: boolean }>("/api/v1/auth/password/forgot/verify", {
      method: "POST",
      body: JSON.stringify({ email, otp, newPassword }),
    }),

  refreshToken: (refreshToken: string) =>
    request<{
      accessToken: string;
      refreshToken: string;
      expiresInSeconds: number;
      userId: string;
      email: string;
      displayName: string;
    }>("/api/v1/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }),

  verifyOtp: (email: string, code: string) =>
    request<{
      accessToken: string;
      refreshToken: string;
      expiresInSeconds: number;
      userId: string;
      email: string;
      displayName: string;
    }>("/api/v1/auth/login/email/verify", {
      method: "POST",
      body: JSON.stringify({ email, code }),
    }),

  resendOtp: (email: string) =>
    request<{ message: string; success: boolean }>("/api/v1/auth/login/email/resend", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  logout: (accessToken: string) =>
    request<{ message: string; success: boolean }>(
      "/api/v1/auth/logout",
      { method: "POST" },
      accessToken
    ),

  me: (accessToken: string) =>
    request<{ id: string; email: string; displayName: string }>(
      "/api/v1/users/me",
      {},
      accessToken
    ),

  systemStatus: () =>
    request<{
      frontend: string;
      backend: string;
      pythonWorker: string;
      websocket: string;
      whisperModelLoaded: boolean;
      intentModelLoaded: boolean;
    }>("/api/v1/health/status"),

  recentLogs: (accessToken: string, limit = 50) =>
    request<
      {
        id: string;
        level: string;
        category: string;
        message: string;
        createdAt: string;
      }[]
    >(`/api/v1/logs?limit=${limit}`, {}, accessToken),

  uploadMedia: (accessToken: string, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return request<{
      sessionId: string;
      success: boolean;
      errorMessage?: string;
      segments: { text: string; confidence: number }[];
      intents: { intent: string; confidence: number; entities: Record<string, unknown>; ruleAction?: string }[];
      fullTranscript: string;
      durationSec: number;
    }>(
      "/api/v1/media/upload",
      { method: "POST", body: formData },
      accessToken
    );
  },

  mySessions: (accessToken: string) =>
    request<
      {
        id: string;
        sourceType: string;
        status: string;
        startedAt: string;
        endedAt?: string;
      }[]
    >("/api/v1/sessions", {}, accessToken),

  sessionTranscripts: (accessToken: string, sessionId: string) =>
    request<
      {
        id: string;
        text: string;
        confidence: number;
        startTimeSec: number;
        endTimeSec: number;
      }[]
    >(`/api/v1/sessions/${sessionId}/transcripts`, {}, accessToken),

  sessionIntents: (accessToken: string, sessionId: string) =>
    request<
      {
        id: string;
        intent: string;
        confidence: number;
        entitiesJson: string;
        rawText: string;
      }[]
    >(`/api/v1/sessions/${sessionId}/intents`, {}, accessToken),
};

export { API_BASE_URL };
