"""Internal-only SMTP sink for synthetic invitation E2E tests."""
import email
import email.policy
import hmac
import http.server
import json
import os
import re
import socketserver
import threading
import uuid
from urllib.parse import parse_qs, urlparse

MAX_MESSAGE_BYTES = 1_048_576
APPROVED_RECIPIENT_DOMAIN = "example.test"


class MessageStore:
    def __init__(self):
        self._messages = []
        self._lock = threading.Lock()

    def append(self, envelope_recipients, raw_message):
        message = email.message_from_bytes(raw_message, policy=email.policy.default)
        plain_part = message.get_body(preferencelist=("plain",))
        html_part = message.get_body(preferencelist=("html",))
        plain = plain_part.get_content() if plain_part else ""
        token = re.search(r"#token=([A-Za-z0-9_-]{43})", plain)
        with self._lock:
            cursor = self._messages[-1]["cursor"] + 1 if self._messages else 1
            captured = {
                "id": str(uuid.uuid4()),
                "cursor": cursor,
                "envelope_recipients": list(envelope_recipients),
                "recipient": str(message.get("To", "")),
                "subject": str(message.get("Subject", "")),
                "text": plain,
                "html": html_part.get_content() if html_part else "",
                "token": token.group(1) if token else None,
            }
            self._messages.append(captured)
            return captured

    def after(self, cursor):
        with self._lock:
            messages = [message.copy() for message in self._messages
                        if message["cursor"] > cursor]
            next_cursor = self._messages[-1]["cursor"] if self._messages else cursor
            return {"count": len(messages), "messages": messages, "nextCursor": next_cursor}

    def cursor(self):
        with self._lock:
            return self._messages[-1]["cursor"] if self._messages else 0


class SMTPHandler(socketserver.StreamRequestHandler):
    def line(self, value):
        self.wfile.write((value + "\r\n").encode("ascii"))
        self.wfile.flush()

    def handle(self):
        self.request.settimeout(30)
        self.line("220 ethioloadai-e2e-mail-capture ESMTP")
        sender = None
        recipients = []
        while True:
            raw = self.rfile.readline(8192)
            if not raw:
                return
            command = raw.decode("utf-8", "replace").strip()
            verb, _, argument = command.partition(" ")
            verb = verb.upper()

            if verb in ("EHLO", "HELO"):
                self.line("250-ethioloadai-e2e-mail-capture")
                self.line(f"250-SIZE {MAX_MESSAGE_BYTES}")
                self.line("250 OK")
            elif verb == "AUTH":
                self.line("502 5.5.1 SMTP authentication is not supported")
            elif verb == "MAIL":
                sender = argument.partition(":")[2].strip().strip("<>")
                recipients = []
                self.line("250 2.1.0 Sender accepted")
            elif verb == "RCPT":
                recipient = argument.partition(":")[2].strip().strip("<>")
                if sender is None or not is_approved_recipient(recipient):
                    self.line("550 5.7.1 Only @example.test recipients are accepted")
                else:
                    recipients.append(recipient)
                    self.line("250 2.1.5 Recipient accepted")
            elif verb == "DATA":
                if sender is None or not recipients:
                    self.line("503 5.5.1 MAIL and an approved RCPT are required")
                    continue
                self.line("354 End data with <CR><LF>.<CR><LF>")
                chunks = []
                total = 0
                while True:
                    part = self.rfile.readline(8192)
                    if not part or part == b".\r\n":
                        break
                    total += len(part)
                    if total > MAX_MESSAGE_BYTES:
                        self.line("552 5.3.4 Message exceeds fixed test limit")
                        return
                    chunks.append(part[1:] if part.startswith(b"..") else part)
                self.server.store.append(recipients, b"".join(chunks))
                self.line("250 2.0.0 Captured locally")
                sender = None
                recipients = []
            elif verb == "RSET":
                sender = None
                recipients = []
                self.line("250 2.0.0 Reset")
            elif verb == "NOOP":
                self.line("250 2.0.0 OK")
            elif verb == "QUIT":
                self.line("221 2.0.0 Bye")
                return
            else:
                self.line("502 5.5.1 Command not supported")


def is_approved_recipient(address):
    if not address or any(character in address for character in "\r\n<>"):
        return False
    local, separator, domain = address.rpartition("@")
    return bool(separator and local and domain.lower() == APPROVED_RECIPIENT_DOMAIN)


class CaptureSMTPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, store):
        super().__init__(address, SMTPHandler)
        self.store = store


class CaptureAPIHandler(http.server.BaseHTTPRequestHandler):
    server_version = "E2ECapture/1"

    def log_message(self, *_):
        pass

    def do_GET(self):
        if not hmac.compare_digest(self.headers.get("X-Test-Key", ""), self.server.secret):
            self.send_error(403)
            return
        parsed = urlparse(self.path)
        if parsed.path == "/cursor":
            payload = json.dumps({"cursor": self.server.store.cursor()}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        query = parse_qs(parsed.query, strict_parsing=False)
        try:
            cursor = int(query.get("after", ["0"])[0])
            if cursor < 0:
                raise ValueError
        except ValueError:
            self.send_error(400, "Invalid message cursor")
            return
        payload = json.dumps(self.server.store.after(cursor)).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


class CaptureAPIServer(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, store, secret):
        super().__init__(address, CaptureAPIHandler)
        self.store = store
        self.secret = secret


def read_secret():
    path = os.environ.get("TEST_MAIL_CAPTURE_SECRET_FILE", "/run/secrets/test-mail-capture-key")
    with open(path, encoding="utf-8") as secret_file:
        secret = secret_file.read().rstrip("\r\n")
    if len(secret) < 32:
        raise RuntimeError("The test mail capture key must contain at least 32 characters")
    return secret


def create_servers(store, secret, smtp_port=12525, api_port=12526):
    smtp_host = os.environ.get("CAPTURE_SMTP_BIND", "0.0.0.0")
    smtp = CaptureSMTPServer((smtp_host, smtp_port), store)
    api = CaptureAPIServer(("127.0.0.1", api_port), store, secret)
    return smtp, api


def serve():
    store = MessageStore()
    secret = read_secret()
    smtp, api = create_servers(store, secret)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    try:
        api.serve_forever()
    finally:
        smtp.shutdown()
        smtp.server_close()
        api.server_close()


if __name__ == "__main__":
    serve()
