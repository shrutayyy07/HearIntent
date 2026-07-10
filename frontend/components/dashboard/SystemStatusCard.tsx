import { Activity, Server, Cpu, Wifi } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { SystemStatus } from "@/types/speech";

interface SystemStatusCardProps {
  status: SystemStatus;
}

const rows = [
  { key: "frontend" as const, label: "Frontend", icon: Activity },
  { key: "backend" as const, label: "Backend Connection", icon: Server },
  { key: "pythonWorker" as const, label: "Python Worker", icon: Cpu },
  { key: "websocket" as const, label: "WebSocket", icon: Wifi },
];

export function SystemStatusCard({ status }: SystemStatusCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>System Status</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1">
        {rows.map(({ key, label, icon: Icon }) => {
          const online = status[key] === "online";
          return (
            <div
              key={key}
              className="flex items-center justify-between rounded-lg px-1 py-2.5"
            >
              <div className="flex items-center gap-2.5 text-sm">
                <Icon className="h-4 w-4 text-muted-foreground" />
                <span>{label}</span>
              </div>
              <div className="flex items-center gap-1.5 text-xs font-medium">
                <span
                  className={`h-2 w-2 rounded-full ${
                    online ? "bg-success" : "bg-red-500"
                  }`}
                />
                <span className={online ? "text-success" : "text-red-400"}>
                  {online ? "Online" : "Offline"}
                </span>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
