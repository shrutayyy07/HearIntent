"use client";

import { useCallback, useEffect, useState } from "react";
import { DashboardHeader } from "@/components/dashboard/DashboardHeader";
import { AudioCaptureCard } from "@/components/dashboard/AudioCaptureCard";
import { LiveTranscriptionCard } from "@/components/dashboard/LiveTranscriptionCard";
import { IntentAnalysisCard } from "@/components/dashboard/IntentAnalysisCard";
import { SystemStatusCard } from "@/components/dashboard/SystemStatusCard";
import { ActivityLogsCard } from "@/components/dashboard/ActivityLogsCard";
import { FileUploadCard } from "@/components/dashboard/FileUploadCard";
import { UserProfileCard } from "@/components/dashboard/UserProfileCard";
import { useSpeechSocket } from "@/hooks/useSpeechSocket";
import { useRequireAuth } from "@/hooks/useRequireAuth";
import { useAuthStore } from "@/lib/auth-store";
import { api } from "@/lib/api-client";
import type {
  ActivityLogItem,
  IntentResult,
  SystemStatus,
  TranscriptionMessage,
} from "@/types/speech";

export default function DashboardPage() {
  const { isReady } = useRequireAuth();
  const { accessToken } = useAuthStore();

  const [messages, setMessages] = useState<TranscriptionMessage[]>([]);
  const [intents, setIntents] = useState<IntentResult[]>([]);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [logs, setLogs] = useState<ActivityLogItem[]>([]);
  const [systemStatus, setSystemStatus] = useState<SystemStatus>({
    frontend: "online",
    backend: "offline",
    pythonWorker: "offline",
    websocket: "offline",
  });

  const pushLog = useCallback(
    (message: string, level: ActivityLogItem["level"] = "INFO") => {
      setLogs((prev) => [
        ...prev.slice(-99),
        {
          id: crypto.randomUUID(),
          timestamp: new Date().toLocaleTimeString("en-US", { hour12: false }),
          message,
          level,
        },
      ]);
    },
    []
  );

  const handleTranscription = useCallback(
    (msg: TranscriptionMessage) => {
      setMessages((prev) => [...prev, msg]);
      pushLog(`Transcription received: "${msg.text}"`);
    },
    [pushLog]
  );

  const handleIntent = useCallback(
    (intent: IntentResult) => {
      setIntents((prev) => [...prev, intent]);
      // If we are looking at latest, keep it at latest
      if (selectedIndex === null) {
        setSelectedIndex(null);
      }
      pushLog(`Intent extracted: ${intent.intent}`);
    },
    [pushLog, selectedIndex]
  );

  const handleStreamError = useCallback(
    (message: string) => {
      pushLog(message, "ERROR");
    },
    [pushLog]
  );

  const { isRecording, isAnalyzing, isConnected, inputLevel, startRecording, stopRecording } =
    useSpeechSocket({
      accessToken,
      onTranscription: handleTranscription,
      onIntent: handleIntent,
      onError: handleStreamError,
    });

  const loadHistory = useCallback(async () => {
    if (!accessToken) return;
    try {
      const sessions = await api.mySessions(accessToken);
      if (sessions.length > 0) {
        const latestSessionId = sessions[0].id;
        const [transcripts, intents] = await Promise.all([
          api.sessionTranscripts(accessToken, latestSessionId),
          api.sessionIntents(accessToken, latestSessionId)
        ]);
        
        if (transcripts.length > 0) {
          setMessages(transcripts.map(t => ({
            id: crypto.randomUUID(),
            role: "user",
            timestamp: new Date().toLocaleTimeString("en-US", { hour12: false }),
            text: t.text,
            translatedText: (t as any).translatedText,
            confidence: t.confidence
          })));
        }
        
        if (intents.length > 0) {
          const formattedIntents = intents.map(i => ({
            intent: i.intent,
            confidence: i.confidence,
            entities: JSON.parse((i as any).entitiesJson || '{}'),
            ruleAction: undefined
          }));
          setIntents(formattedIntents);
        }
        pushLog("Restored previous session history");
      }
    } catch (err) {
      console.error("Failed to load history", err);
    }
  }, [accessToken, pushLog]);

  useEffect(() => {
    if (isReady) {
      loadHistory();
    }
  }, [isReady, loadHistory]);

  useEffect(() => {
    if (!isReady) return;

    let cancelled = false;

    const poll = async () => {
      try {
        const status = await api.systemStatus();
        if (cancelled) return;
        setSystemStatus({
          frontend: "online",
          backend: status.backend === "online" ? "online" : "offline",
          pythonWorker: status.pythonWorker === "online" ? "online" : "offline",
          websocket: isConnected ? "online" : "offline",
          whisperModelLoaded: status.whisperModelLoaded,
          intentModelLoaded: status.intentModelLoaded,
        });
      } catch {
        if (cancelled) return;
        setSystemStatus((prev) => ({
          ...prev,
          backend: "offline",
          pythonWorker: "offline",
        }));
      }
    };

    poll();
    const interval = setInterval(poll, 10000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [isReady, isConnected]);

  const handleFileResult = useCallback(
    (newMessages: TranscriptionMessage[], newIntents: IntentResult[]) => {
      setMessages((prev) => [...prev, ...newMessages]);
      setIntents((prev) => [...prev, ...newIntents]);
      setSelectedIndex(null); // Snap back to the latest when new file uploaded
      pushLog(`Processed uploaded file: ${newMessages.length} segment(s)`);
      if (newIntents.length > 0) {
        newIntents.forEach((intent) =>
          pushLog(`Intent extracted from upload: ${intent.intent}`)
        );
      }
    },
    [pushLog]
  );

  if (!isReady) {
    return (
      <div className="flex h-screen items-center justify-center text-sm text-muted-foreground">
        Loading...
      </div>
    );
  }

  const displayedIntent = selectedIndex !== null && intents[selectedIndex]
    ? intents[selectedIndex]
    : (intents.length > 0 ? intents[intents.length - 1] : null);

  return (
    <div className="min-h-screen">
      <DashboardHeader wsConnected={isConnected} />

      <main className="grid grid-cols-1 gap-5 p-6 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-1">
          <AudioCaptureCard
            isRecording={isRecording}
            isAnalyzing={isAnalyzing}
            inputLevel={inputLevel}
            onStart={startRecording}
            onStop={stopRecording}
          />
          <FileUploadCard onResult={handleFileResult} />
          <SystemStatusCard status={systemStatus} />
        </div>

        <div className="lg:col-span-1">
          <LiveTranscriptionCard 
            messages={messages} 
            selectedIndex={selectedIndex}
            onSelect={setSelectedIndex}
            onClear={() => {
              setMessages([]);
              setIntents([]);
              setSelectedIndex(null);
              pushLog("Transcript cleared by user");
            }}
          />
        </div>

        <div className="space-y-5 lg:col-span-1">
          <IntentAnalysisCard intent={displayedIntent} />
          <UserProfileCard />
          <ActivityLogsCard logs={logs} />
        </div>
      </main>
    </div>
  );
}
