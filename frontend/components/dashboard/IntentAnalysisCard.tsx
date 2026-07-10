"use client";

import { Brain } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { IntentResult } from "@/types/speech";

interface IntentAnalysisCardProps {
  intent: IntentResult | null;
}

export function IntentAnalysisCard({ intent }: IntentAnalysisCardProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>
          <Brain className="h-4 w-4 text-muted-foreground" />
          Intent Analysis
        </CardTitle>
        {intent && (
          <Badge variant="accent">{intent.intent.toUpperCase()}</Badge>
        )}
      </CardHeader>
      <CardContent>
        {intent ? (
          <pre className="json-viewer rounded-lg bg-muted px-4 py-3.5">
            {JSON.stringify(
              {
                intent: intent.intent,
                ...intent.entities,
                confidence: intent.confidence,
                ...(intent.ruleAction ? { ruleAction: intent.ruleAction } : {}),
              },
              null,
              2
            )}
          </pre>
        ) : (
          <p className="py-12 text-center text-sm text-muted-foreground">
            No intent detected yet.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
