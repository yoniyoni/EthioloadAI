import { API_BASE } from "@/lib/api";

type RealtimeMessage = { type?: string; data?: Record<string, unknown>; source?: string };

export function buildWebSocketUrl(
  endpoint: string,
  apiBase: string = API_BASE,
  origin: string = window.location.origin,
): URL {
  const apiUrl = new URL(apiBase, origin);
  if (apiUrl.protocol !== "http:" && apiUrl.protocol !== "https:") {
    throw new Error("The API base URL must use HTTP or HTTPS.");
  }

  const apiPath = apiUrl.pathname.replace(/\/+$/, "");
  const endpointPath = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const path =
    apiPath && (endpointPath === apiPath || endpointPath.startsWith(`${apiPath}/`))
      ? endpointPath
      : `${apiPath}${endpointPath}`;
  const url = new URL(path, apiUrl.origin);
  url.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  return url;
}

export class SpringWebSocketClient {
  private socket: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private pingTimer: number | null = null;
  private stopped = false;
  private readonly listeners = new Set<(message: RealtimeMessage) => void>();

  constructor(private readonly endpoint: string, private readonly topic: string) {}

  onMessage(listener: (message: RealtimeMessage) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  connect(): void {
    this.stopped = false;
    this.open();
  }

  private open(): void {
    if (this.stopped) return;
    this.closeSocket();
    const stored = localStorage.getItem("freightlink_auth");
    let token: string | null = null;
    try { token = stored ? JSON.parse(stored).token ?? null : null; } catch { token = null; }
    if (!token) return;

    const url = buildWebSocketUrl(this.endpoint);
    url.searchParams.set("token", token);
    const socket = new WebSocket(url);
    this.socket = socket;
    socket.onopen = () => {
      this.send({ type: "SUBSCRIBE", data: { topic: this.topic, filter: null } });
      this.pingTimer = window.setInterval(() => this.send({ type: "PING", data: {} }), 30_000);
    };
    socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data) as RealtimeMessage;
        this.listeners.forEach((listener) => listener(message));
      } catch { /* REST polling remains the fallback for malformed frames. */ }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => this.scheduleReconnect();
  }

  private send(message: Record<string, unknown>): void {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(message));
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.reconnectTimer !== null) return;
    if (this.pingTimer !== null) window.clearInterval(this.pingTimer);
    this.pingTimer = null;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.open();
    }, 3000);
  }

  private closeSocket(): void {
    if (this.pingTimer !== null) window.clearInterval(this.pingTimer);
    this.pingTimer = null;
    this.socket?.close();
    this.socket = null;
  }

  dispose(): void {
    this.stopped = true;
    if (this.reconnectTimer !== null) window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    this.closeSocket();
    this.listeners.clear();
  }
}