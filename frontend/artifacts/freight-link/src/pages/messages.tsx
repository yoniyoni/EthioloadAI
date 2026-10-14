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
  warning?: string;
}

interface MessageHistoryResponse {
  messages: ChatMessage[];
  page?: {
    mode: "LATEST" | "OLDER" | "AFTER";
    olderCursor: string | null;
    afterCursor: string | null;
    hasOlder: boolean | null;
    hasMoreAfter: boolean | null;
  };
}

function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map<number, ChatMessage>();
  for (const message of [...current, ...incoming]) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt) || b.id - a.id,
  );
}

let nextConversationInstanceId = 0;

export default function Messages() {
  const { id } = useParams<{ id: string }>();
  return <MessagesConversation key={id} id={id} />;
}

function MessagesConversation({ id }: { id: string }) {
  const [conversationInstanceId] = useState(() => ++nextConversationInstanceId);
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const freightId = Number(id);
  const scrollRef = useRef<HTMLDivElement>(null);
  const afterCursorRef = useRef<string | null>(null);
  const lastResponseModeRef = useRef<"LATEST" | "OLDER" | "AFTER" | null>(null);
  const stickToBottomRef = useRef(true);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [olderCursor, setOlderCursor] = useState<string | null>(null);
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const { data: freightRaw, isLoading: freightLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  // Normalise Spring Boot FreightResponse field names for the existing UI
  const freight = freightRaw ? {
    ...freightRaw,
    originCity: freightRaw.originCity ?? freightRaw.city ?? freightRaw.pickupLocation ?? "Origin",
    destinationCity: freightRaw.destinationCity ?? freightRaw.destination ?? "Destination",
    cargoType: freightRaw.cargoType ?? freightRaw.materialType ?? "Cargo",
    shipperName: freightRaw.shipperName ?? freightRaw.shipper?.name ?? "Shipper",
    matchedDriverName: freightRaw.matchedDriverName ?? freightRaw.driver?.user?.name ?? "Driver",
  } : freightRaw;

  const isOwner = user?.id === freight?.shipperId;
  const isDriver = user?.id === freight?.matchedDriverId;
  const targetReceiverId = isOwner ? freight?.matchedDriverId : freight?.shipperId;
  const canSendMessage = Boolean(targetReceiverId);

  const { data: messagesData, isLoading: messagesLoading } = useQuery<MessageHistoryResponse>({
    queryKey: ["messages", freightId, conversationInstanceId],
    queryFn: () => {
      const cursor = afterCursorRef.current;
      const params = new URLSearchParams({ limit: "50" });
      if (cursor) params.set("after", cursor);
      return api.get<MessageHistoryResponse>(`/messages/${freightId}?${params.toString()}`);
    },
    enabled: !isNaN(freightId),
    refetchInterval: 10000,
  });

  useEffect(() => {
    if (!messagesData) return;
    lastResponseModeRef.current = messagesData.page?.mode ?? "LATEST";
    setMessages((current) => mergeMessages(current, messagesData.messages ?? []));

    if (messagesData.page?.mode === "LATEST") {
      afterCursorRef.current = messagesData.page.afterCursor;
      setOlderCursor(messagesData.page.olderCursor);
      setHasOlder(messagesData.page.hasOlder ?? false);
    } else if (messagesData.page?.mode === "AFTER") {
      afterCursorRef.current = messagesData.page.afterCursor;
    }
  }, [messagesData]);

  const loadOlderMessages = async () => {
    if (!olderCursor || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const params = new URLSearchParams({ limit: "50", before: olderCursor });
      const response = await api.get<MessageHistoryResponse>(`/messages/${freightId}?${params.toString()}`);
      lastResponseModeRef.current = "OLDER";
      setMessages((current) => mergeMessages(current, response.messages ?? []));
      setOlderCursor(response.page?.olderCursor ?? null);
      setHasOlder(response.page?.hasOlder ?? false);
    } catch (error: any) {
      toast({ title: "Failed to load older messages", description: error.message, variant: "destructive" });
    } finally {
      setLoadingOlder(false);
    }
  };

  const sendMessage = useMutation({
    mutationFn: (content: string) => {
      if (!targetReceiverId) {
        throw new Error("Cannot send message: no recipient has been assigned to this freight yet.");
      }
      return api.post<ChatMessage>("/messages", {
        freightId,
        receiverId: targetReceiverId,
        content,
        type: "text",
      });
    },
    onSuccess: (data: any) => {
      setInput("");
      stickToBottomRef.current = true;
      qc.invalidateQueries({ queryKey: ["messages", freightId] });
      if (data?.warning) {
        toast({ title: "Warning", description: data.warning, variant: "destructive" });
      }
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  useEffect(() => {
    if (
      scrollRef.current &&
      (lastResponseModeRef.current === "LATEST" ||
        (lastResponseModeRef.current === "AFTER" && stickToBottomRef.current))
    ) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

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

  const otherParty = isOwner
    ? freight.matchedDriverName ?? "Driver"
    : freight.shipperName ?? "Shipper";

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
          <div
            ref={scrollRef}
            onScroll={(event) => {
              const element = event.currentTarget;
              stickToBottomRef.current =
                element.scrollHeight - element.scrollTop - element.clientHeight <= 100;
            }}
            className="flex-1 overflow-y-auto p-4 space-y-3"
          >
            {hasOlder && (
              <div className="flex justify-center pb-2">
                <Button variant="outline" size="sm" onClick={loadOlderMessages} disabled={loadingOlder}>
                  {loadingOlder ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                  Load older messages
                </Button>
              </div>
            )}
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
              placeholder={canSendMessage ? "Type your message..." : "Waiting for driver assignment before messaging can begin..."}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && input.trim() && canSendMessage && !sendMessage.isPending && sendMessage.mutate(input)}
              className="text-sm rounded-lg"
              disabled={!canSendMessage || sendMessage.isPending}
            />
            <Button
              size="icon"
              onClick={() => sendMessage.mutate(input)}
              disabled={!canSendMessage || !input.trim() || sendMessage.isPending}
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
