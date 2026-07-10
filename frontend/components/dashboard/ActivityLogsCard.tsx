"use client";

import { Terminal } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ActivityLogItem } from "@/types/speech";
import { useEffect, useRef } from "react";

interface ActivityLogsCardProps {
  logs: ActivityLogItem[];
}

const levelColor: Record<ActivityLogItem["level"], string> = {
  INFO: "text-sky-400",
  WARN: "text-amber-400",
  ERROR: "text-red-400",
};

export function ActivityLogsCard({ logs }: ActivityLogsCardProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [logs.length]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <Terminal className="h-4 w-4 text-muted-foreground" />
          Activity Logs
        </CardTitle>
        <span className="text-xs text-muted-foreground">{logs.length}</span>
      </CardHeader>
      <CardContent>
        <div
          ref={scrollRef}
          className="max-h-72 space-y-1.5 overflow-y-auto rounded-lg bg-muted/40 p-3 font-mono text-[12.5px]"
        >
          {logs.length === 0 ? (
            <p className="py-6 text-center text-muted-foreground">
              No activity yet.
            </p>
          ) : (
            logs.map((log) => (
              <div key={log.id} className="flex gap-2">
                <span className="text-muted-foreground">[{log.timestamp}]</span>
                <span className={levelColor[log.level]}>{log.message}</span>
              </div>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
}
