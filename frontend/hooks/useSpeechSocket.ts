"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { IntentResult, TranscriptionMessage } from "@/types/speech";

const WS_BASE_URL =
  process.env.NEXT_PUBLIC_WS_BASE_URL || "ws://localhost:8080";
const TARGET_SAMPLE_RATE = 16000;

interface UseSpeechSocketOptions {
  accessToken: string | null;
  onTranscription: (msg: TranscriptionMessage) => void;
  onIntent: (intent: IntentResult) => void;
  onError?: (message: string) => void;
}

export function useSpeechSocket({
  accessToken,
  onTranscription,
  onIntent,
  onError,
}: UseSpeechSocketOptions) {
  const [isRecording, setIsRecording] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [inputLevel, setInputLevel] = useState(0);

  const wsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const connectWebSocket = useCallback((): Promise<WebSocket> => {
    return new Promise((resolve, reject) => {
      const url = `${WS_BASE_URL}/ws/speech${
        accessToken ? `?token=${encodeURIComponent(accessToken)}` : ""
      }`;
      const ws = new WebSocket(url);
      ws.binaryType = "arraybuffer";

      ws.onopen = () => {
        setIsConnected(true);
        ws.send(
          JSON.stringify({ type: "START_SESSION", sampleRate: TARGET_SAMPLE_RATE })
        );
        resolve(ws);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === "TRANSCRIPTION") {
            onTranscription({
              id: crypto.randomUUID(),
              role: "user",
              text: data.payload.text,
              translatedText: data.payload.translatedText,
              confidence: data.payload.confidence,
              timestamp: new Date().toLocaleTimeString("en-US", {
                hour12: false,
              }),
            });
          } else if (data.type === "INTENT") {
            onIntent(data.payload as IntentResult);
          } else if (data.type === "SESSION_CLOSED") {
            if (wsRef.current) wsRef.current.close();
            setIsConnected(false);
            setIsAnalyzing(false);
          } else if (data.type === "ERROR") {
            onError?.(data.payload?.message || "Unknown stream error");
            setIsAnalyzing(false);
          }
        } catch {
          // ignore malformed frames
        }
      };

      ws.onerror = () => {
        setIsConnected(false);
        reject(new Error("WebSocket connection failed"));
      };

      ws.onclose = () => {
        setIsConnected(false);
      };

      wsRef.current = ws;
    });
  }, [accessToken, onTranscription, onIntent, onError]);

  const downsampleAndConvert = useCallback(
    (input: Float32Array, inputSampleRate: number): ArrayBuffer => {
      const ratio = inputSampleRate / TARGET_SAMPLE_RATE;
      const outputLength = Math.floor(input.length / ratio);
      const result = new Int16Array(outputLength);

      for (let i = 0; i < outputLength; i++) {
        const srcIndex = Math.floor(i * ratio);
        const sample = Math.max(-1, Math.min(1, input[srcIndex]));
        result[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
      }
      return result.buffer;
    },
    []
  );

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
        },
      });
      streamRef.current = stream;

      const audioContext = new AudioContext();
      audioContextRef.current = audioContext;

      const source = audioContext.createMediaStreamSource(stream);
      const processor = audioContext.createScriptProcessor(4096, 1, 1);
      processorRef.current = processor;

      const ws = await connectWebSocket();

      processor.onaudioprocess = (e) => {
        const channelData = e.inputBuffer.getChannelData(0);

        let sum = 0;
        for (let i = 0; i < channelData.length; i++) {
          sum += channelData[i] * channelData[i];
        }
        const rms = Math.sqrt(sum / channelData.length);
        setInputLevel(Math.min(100, Math.round(rms * 400)));

        if (ws.readyState === WebSocket.OPEN) {
          const pcmBuffer = downsampleAndConvert(
            channelData,
            audioContext.sampleRate
          );
          ws.send(pcmBuffer);
        }
      };

      source.connect(processor);
      processor.connect(audioContext.destination);

      setIsRecording(true);
    } catch (err) {
      onError?.(
        err instanceof Error ? err.message : "Failed to access microphone"
      );
    }
  }, [connectWebSocket, downsampleAndConvert, onError]);

  const stopRecording = useCallback(() => {
    processorRef.current?.disconnect();
    processorRef.current = null;

    audioContextRef.current?.close();
    audioContextRef.current = null;

    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "END_SESSION" }));
      setIsAnalyzing(true);
    } else {
      setIsConnected(false);
      setIsAnalyzing(false);
    }

    setIsRecording(false);
    setInputLevel(0);
  }, []);

  useEffect(() => {
    return () => {
      stopRecording();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
    isRecording,
    isAnalyzing,
    isConnected,
    inputLevel,
    startRecording,
    stopRecording,
  };
}
