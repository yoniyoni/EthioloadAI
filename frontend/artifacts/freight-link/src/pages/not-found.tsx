import { Card, CardContent } from "@/components/ui/card";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Link } from "wouter";

export default function NotFound() {
  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-slate-50">
      <Card className="w-full max-w-md mx-4 border-border/60 rounded-xl">
        <CardContent className="pt-6">
          <div className="flex mb-4 gap-2">
            <AlertCircle className="h-8 w-8 text-primary" />
            <h1 className="text-2xl font-bold text-foreground">404 Page Not Found</h1>
          </div>

          <p className="mt-4 text-sm text-muted-foreground">
            The page you're looking for doesn't exist. It might have been moved or deleted.
          </p>
          <div className="mt-4 flex gap-2">
            <Link href="/">
              <Button className="rounded-lg">Go Home</Button>
            </Link>
            <Link href="/freight">
              <Button variant="outline" className="rounded-lg">Browse Freight</Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
