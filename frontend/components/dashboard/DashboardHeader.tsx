"use client";

import { useState, useEffect } from "react";
import { AudioWaveform, Sun, Moon } from "lucide-react";
import { useTheme } from "next-themes";
import { Badge } from "@/components/ui/badge";

interface DashboardHeaderProps {
  wsConnected: boolean;
}

export function DashboardHeader({ wsConnected }: DashboardHeaderProps) {
  const [mounted, setMounted] = useState(false);
  const { theme, setTheme } = useTheme();

  useEffect(() => {
    setMounted(true);
  }, []);

  return (
    <header className="flex items-center justify-between border-b border-border px-6 py-4">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <AudioWaveform className="h-5 w-5" />
        </div>
        <div>
          <h1 className="text-xl font-bold leading-tight tracking-tight">
            HearIntent
          </h1>
          <p className="text-xs font-medium text-muted-foreground/80 hidden sm:block">
            Real-time transcription & intent extraction
          </p>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <Badge variant={wsConnected ? "success" : "outline"} className="gap-1.5">
          <span
            className={`h-1.5 w-1.5 rounded-full ${
              wsConnected ? "bg-success animate-pulse-dot" : "bg-muted-foreground"
            }`}
          />
          WS: {wsConnected ? "connected" : "disconnected"}
        </Badge>
        <button
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border hover:bg-muted"
          aria-label="Toggle theme"
        >
          {mounted && theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>
      </div>
    </header>
  );
}
