"use client";

import { useRef, useState } from "react";
import { UploadCloud, FileVideo, Loader2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import type { IntentResult, TranscriptionMessage } from "@/types/speech";

interface FileUploadCardProps {
  onResult: (messages: TranscriptionMessage[], intents: IntentResult[]) => void;
}

export function FileUploadCard({ onResult }: FileUploadCardProps) {
  const { accessToken } = useAuthStore();
  const inputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFile = async (file: File) => {
    if (!accessToken) {
      setError("Please sign in before uploading a file.");
      return;
    }
    setFileName(file.name);
    setIsUploading(true);
    setError(null);

    try {
      const result = await api.uploadMedia(accessToken, file);
      if (!result.success) {
        setError(result.errorMessage || "Processing failed.");
        return;
      }
      const messages: TranscriptionMessage[] = result.segments.map((seg) => ({
        id: crypto.randomUUID(),
        role: "user",
        text: seg.text,
        confidence: seg.confidence,
        timestamp: new Date().toLocaleTimeString("en-US", { hour12: false }),
      }));
      const intents: IntentResult[] = result.intents.map((i) => ({
        intent: i.intent,
        confidence: i.confidence,
        entities: i.entities,
        ruleAction: i.ruleAction,
      }));
      onResult(messages, intents);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed.");
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <FileVideo className="h-4 w-4 text-muted-foreground" />
          Upload Video / Audio
        </CardTitle>
      </CardHeader>
      <CardContent>
        <input
          ref={inputRef}
          type="file"
          accept="audio/*,video/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) handleFile(file);
          }}
        />
        <button
          onClick={() => inputRef.current?.click()}
          disabled={isUploading}
          className="flex w-full flex-col items-center gap-2 rounded-lg border border-dashed border-border px-4 py-8 text-sm text-muted-foreground transition-colors hover:bg-muted/40 disabled:opacity-60"
        >
          {isUploading ? (
            <Loader2 className="h-6 w-6 animate-spin" />
          ) : (
            <UploadCloud className="h-6 w-6" />
          )}
          <span>
            {isUploading
              ? `Analyzing ${fileName}...`
              : "Click to upload a video or audio file"}
          </span>
          <span className="text-xs">MP4, MOV, WEBM, MP3, WAV up to 200MB</span>
        </button>

        {error && (
          <p className="mt-3 text-sm text-red-400">{error}</p>
        )}

        <div className="mt-3">
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => inputRef.current?.click()}
            disabled={isUploading}
          >
            Select File
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
