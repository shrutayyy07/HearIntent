"use client";

import { MessageSquare, User, Trash2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { TranscriptionMessage } from "@/types/speech";
import { useEffect, useRef } from "react";

interface LiveTranscriptionCardProps {
  messages: TranscriptionMessage[];
  selectedIndex?: number | null;
  onSelect?: (index: number) => void;
  onClear?: () => void;
}

export function LiveTranscriptionCard({ messages, selectedIndex, onSelect, onClear }: LiveTranscriptionCardProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Only auto-scroll if the user hasn't explicitly selected an older message
    if (selectedIndex === null) {
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
    }
  }, [messages.length, selectedIndex]);

  return (
    <Card className="flex h-full flex-col">
      <CardHeader className="flex flex-row items-center justify-between py-4">
        <CardTitle className="flex items-center gap-2">
          <MessageSquare className="h-4 w-4 text-muted-foreground" />
          Live Transcription
        </CardTitle>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground mr-2">
            {messages.length} message{messages.length === 1 ? "" : "s"}
          </span>
          {messages.length > 0 && onClear && (
            <Button variant="ghost" size="icon" onClick={onClear} className="h-8 w-8" title="Clear Transcript">
              <Trash2 className="h-4 w-4 text-muted-foreground" />
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex-1 overflow-hidden">
        <div ref={scrollRef} className="h-full space-y-4 overflow-y-auto pr-1">
          {messages.length === 0 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">
              Start recording or upload a file to see transcriptions appear here.
            </p>
          ) : (
            messages.map((msg, index) => {
              const isSelected = selectedIndex === index;
              return (
                <div 
                  key={msg.id || index} 
                  className={`flex gap-3 cursor-pointer rounded-lg p-2 transition-colors ${
                    isSelected ? "bg-primary/5 ring-1 ring-primary/20" : "hover:bg-muted/50"
                  }`}
                  onClick={() => onSelect?.(index)}
                >
                  {/* Dark Blue PFP */}
                  <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
                    <User className="h-4 w-4" />
                  </div>
                  <div className="flex-1">
                    <div className="mb-1 flex items-baseline gap-2">
                      <span className="text-sm font-semibold capitalize">
                        {msg.role}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {msg.timestamp}
                      </span>
                    </div>
                    {/* Light Blue Chat Bubble */}
                    <div 
                      className={`inline-block rounded-2xl rounded-tl-sm px-4 py-2.5 text-sm shadow-sm transition-colors ${
                        isSelected 
                          ? "bg-blue-200 text-blue-950 dark:bg-blue-600 dark:text-white" 
                          : "bg-blue-100 text-blue-900 dark:bg-blue-900/50 dark:text-blue-100"
                      }`}
                    >
                      <div>{msg.text}</div>
                      {msg.translatedText && msg.translatedText !== msg.text && (
                        <div className="mt-1 pt-1 border-t border-current/20 text-xs opacity-90 italic">
                          {msg.translatedText}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </CardContent>
    </Card>
  );
}
