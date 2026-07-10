"use client";

import { Mic, MicOff } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface AudioCaptureCardProps {
  isRecording: boolean;
  isAnalyzing: boolean;
  inputLevel: number;
  onStart: () => void;
  onStop: () => void;
}

export function AudioCaptureCard({
  isRecording,
  isAnalyzing,
  inputLevel,
  onStart,
  onStop,
}: AudioCaptureCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Audio Capture</CardTitle>
        <Badge variant={isAnalyzing ? "default" : isRecording ? "accent" : "outline"}>
          {isAnalyzing ? "Analyzing..." : isRecording ? "Recording" : "Idle"}
        </Badge>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center gap-5 py-2">
          <button
            onClick={isRecording ? onStop : onStart}
            disabled={isAnalyzing}
            className={`flex h-32 w-32 items-center justify-center rounded-full transition-all ${
              isAnalyzing
                ? "bg-muted opacity-70 cursor-wait"
                : isRecording
                ? "bg-accent/20 ring-4 ring-accent/40"
                : "bg-muted hover:bg-muted/80"
            }`}
            aria-label={isRecording ? "Stop recording" : "Start recording"}
          >
            {isRecording || isAnalyzing ? (
              <Mic className={`h-10 w-10 ${isAnalyzing ? "text-muted-foreground animate-pulse" : "text-accent"}`} />
            ) : (
              <Mic className="h-10 w-10 text-muted-foreground" />
            )}
          </button>

          <div className="flex gap-2">
            <Button
              variant={isRecording ? "outline" : "default"}
              size="sm"
              onClick={onStart}
              disabled={isRecording || isAnalyzing}
            >
              <Mic className="h-3.5 w-3.5" />
              Start Recording
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={onStop}
              disabled={!isRecording || isAnalyzing}
            >
              <MicOff className="h-3.5 w-3.5" />
              Stop Recording
            </Button>
          </div>

          <Waveform active={isRecording} />

          <div className="w-full">
            <div className="mb-1.5 flex items-center justify-between text-xs text-muted-foreground">
              <span>Input level</span>
              <span>{inputLevel}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-accent transition-all duration-100"
                style={{ width: `${Math.min(100, inputLevel)}%` }}
              />
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function Waveform({ active }: { active: boolean }) {
  const bars = Array.from({ length: 32 });
  return (
    <div className="flex h-10 w-full items-center justify-center gap-0.5 rounded-lg border border-dashed border-border px-3">
      {bars.map((_, i) => (
        <span
          key={i}
          className={`w-0.5 rounded-full bg-muted-foreground/40 ${
            active ? "animate-waveform bg-accent/70" : "h-1"
          }`}
          style={
            active
              ? {
                  height: "60%",
                  animationDelay: `${(i % 8) * 0.07}s`,
                }
              : undefined
          }
        />
      ))}
    </div>
  );
}
