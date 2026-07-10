"use client";

import { LogOut, User } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/lib/auth-store";
import { api } from "@/lib/api-client";
import { useRouter } from "next/navigation";

export function UserProfileCard() {
  const { email, displayName, accessToken, clearAuth } = useAuthStore();
  const router = useRouter();

  const handleLogout = async () => {
    try {
      if (accessToken) {
        await api.logout(accessToken);
      }
    } catch {
      // proceed with local logout regardless of API outcome
    } finally {
      clearAuth();
      router.push("/login");
    }
  };

  return (
    <Card>
      <CardContent className="flex items-center justify-between py-4">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-muted">
            <User className="h-4 w-4 text-muted-foreground" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">
              {displayName || "User"}
            </p>
            <p className="truncate text-xs text-muted-foreground">{email}</p>
          </div>
        </div>
        <Button variant="ghost" size="icon" onClick={handleLogout} aria-label="Logout">
          <LogOut className="h-4 w-4" />
        </Button>
      </CardContent>
    </Card>
  );
}
