import { useState, useEffect, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Link } from "wouter";
import { ArrowLeft, Send, Loader2, MessageSquare, Shield, AlertTriangle, Phone, User, ChevronRight } from "lucide-react";

interface ChatMessage {
  id: number;
  senderId: number;
  receiverId: number;
  freightId: number | null;
  type: string;
  content: string;
  maskedContent: string | null;
  hasPhoneNumber: boolean;
  hasPaymentRequest: boolean;
  isRead: boolean;
  createdAt: string;
  sender: { name: string; role: string } | null;
}

export default function Messages() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const freightId = Number(id);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [input, setInput] = useState("");

  const { data: freight, isLoading: freightLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  const { data: messagesData, isLoading: messagesLoading } = useQuery({
    queryKey: ["messages", freightId],
    queryFn: () => api.get<{ messages: ChatMessage[] }>(`/messages/${freightId}`),
    enabled: !isNaN(freightId),
    refetchInterval: 10000,
  });

  const sendMessage = useMutation({
    mutationFn: (content: string) =>
      api.post("/messages", {
        freightId,
        receiverId: user?.id === freight?.shipperId ? freight?.matchedDriverId : freight?.shipperId,
        content,
        type: "text",
      }),
    onSuccess: (data: any) => {
      setInput("");
      qc.invalidateQueries({ queryKey: ["messages", freightId] });
      if (data?.warning) {
        toast({ title: "Warning", description: data.warning, variant: "destructive" });
      }
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messagesData]);

  const isLoading = freightLoading || messagesLoading;

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-8 w-48 mb-6" />
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  if (!freight) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-20 text-center">
        <p className="text-lg font-medium">Freight not found</p>
        <Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back</Button></Link>
      </div>
    );
  }

  const messages = messagesData?.messages ?? [];
  const isOwner = user?.id === freight.shipperId;
  const isDriver = user?.id === freight.matchedDriverId;
  const otherParty = isOwner ? freight.driver?.user?.name ?? "Driver" : freight.shipper?.name ?? "Shipper";

  const STATUS_COLORS: Record<string, string> = {
    posted: "bg-blue-50 text-blue-700 border-blue-200",
    matched: "bg-sky-50 text-sky-700 border-sky-200",
    in_transit: "bg-purple-50 text-purple-700 border-purple-200",
    delivered: "bg-cyan-50 text-cyan-700 border-cyan-200",
    completed: "bg-emerald-50 text-emerald-700 border-emerald-200",
    cancelled: "bg-red-50 text-red-700 border-red-200",
  };

  return (
    <div className="container mx-auto max-w-3xl px-4 py-8">
      <Link href={`/freight/${id}`}>
        <Button variant="ghost" className="gap-2 mb-4 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> Back to Freight
        </Button>
      </Link>

      <Card className="flex flex-col h-[calc(100vh-12rem)] border-border/60 rounded-xl overflow-hidden">
        <CardHeader className="pb-3 border-b">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center">
                <MessageSquare className="h-5 w-5 text-primary" />
              </div>
              <div>
                <CardTitle className="text-base">Chat with {otherParty}</CardTitle>
                <p className="text-xs text-muted-foreground">
                  {freight.originCity} → {freight.destinationCity} · {freight.cargoType}
                </p>
              </div>
            </div>
            <Badge className={`border ${STATUS_COLORS[freight.status] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}>{freight.status}</Badge>
          </div>

          {/* Security notice */}
          <div className="mt-3 p-2 rounded-xl bg-amber-50 border border-amber-200 flex items-start gap-2">
            <Shield className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
            <p className="text-xs text-amber-700">
              For your safety, phone numbers and payment requests are masked during active transactions. All payments must go through escrow.
            </p>
          </div>
        </CardHeader>

        <CardContent className="flex-1 flex flex-col p-0 overflow-hidden">
          {/* Messages area */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
            {messages.length === 0 && (
              <div className="text-center py-12">
                <MessageSquare className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                <p className="text-sm text-muted-foreground">No messages yet. Start the conversation!</p>
              </div>
            )}
            {messages.slice().reverse().map((msg: ChatMessage) => {
              const isMe = msg.senderId === user?.id;
              const isSystem = msg.type === "system";
              const displayContent = (msg.maskedContent && msg.hasPhoneNumber) ? msg.maskedContent : msg.content;

              if (isSystem) {
                return (
                  <div key={msg.id} className="flex justify-center">
                    <div className="bg-amber-50 border border-amber-200 rounded-xl px-3 py-2 max-w-[80%] text-center">
                      <div className="flex items-center gap-1.5 justify-center mb-1">
                        <AlertTriangle className="h-3 w-3 text-amber-600" />
                        <span className="text-xs font-medium text-amber-700">System Notice</span>
                      </div>
                      <p className="text-xs text-amber-700">{msg.content}</p>
                    </div>
                  </div>
                );
              }

              return (
                <div key={msg.id} className={`flex gap-2 ${isMe ? "flex-row-reverse" : ""}`}>
                  <div className={`h-8 w-8 rounded-full flex items-center justify-center shrink-0 ${isMe ? "bg-primary" : "bg-muted"}`}>
                    <User className={`h-4 w-4 ${isMe ? "text-primary-foreground" : "text-muted-foreground"}`} />
                  </div>
                  <div className={`max-w-[75%] space-y-1`}>
                    <div className={`rounded-2xl px-3 py-2 text-sm ${isMe ? "bg-primary text-primary-foreground rounded-tr-sm" : "bg-muted text-foreground rounded-tl-sm"}`}>
                      <p className="text-xs font-medium opacity-70 mb-0.5">{msg.sender?.name ?? "Unknown"}</p>
                      <p className="whitespace-pre-wrap">{displayContent}</p>
                      {msg.hasPhoneNumber && (
                        <div className="flex items-center gap-1 mt-1 text-xs opacity-70">
                          <Phone className="h-3 w-3" />
                          <span>Phone number masked</span>
                        </div>
                      )}
                    </div>
                    <p className="text-[10px] text-muted-foreground px-1">
                      {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Input area */}
          <div className="p-3 border-t flex gap-2">
            <Input
              placeholder="Type your message..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && input.trim() && !sendMessage.isPending && sendMessage.mutate(input)}
              className="text-sm rounded-lg"
              disabled={sendMessage.isPending}
            />
            <Button
              size="icon"
              onClick={() => sendMessage.mutate(input)}
              disabled={!input.trim() || sendMessage.isPending}
              className="rounded-lg"
            >
              {sendMessage.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
