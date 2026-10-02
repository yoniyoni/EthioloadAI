"""Loopback-only SMTP capture for Phase 2 HTTP verification; no mail or credentials are logged."""
import email
import email.policy
import hmac
import http.server
import json
import os
import re
import socketserver
import threading

messages = []
guard = threading.Lock()
secret = os.environ["TEST_MAIL_CAPTURE_SECRET"]


class SMTP(socketserver.StreamRequestHandler):
    def line(self, value):
        self.wfile.write((value + "\r\n").encode("ascii"))
        self.wfile.flush()

    def handle(self):
        self.line("220 localhost test SMTP")
        auth_step = 0
        while True:
            raw = self.rfile.readline(8192)
            if not raw:
                return
            command = raw.decode("utf-8", "replace").strip()
            verb = command.split(" ", 1)[0].upper()
            if auth_step:
                if auth_step == 1:
                    self.line("334 UGFzc3dvcmQ6")
                    auth_step = 2
                else:
                    self.line("235 Authentication successful")
                    auth_step = 0
            elif verb in ("EHLO", "HELO"):
                self.line("250-localhost")
                self.line("250-AUTH LOGIN PLAIN")
                self.line("250 SIZE 1048576")
            elif verb == "AUTH":
                if command.upper().startswith("AUTH PLAIN "):
                    self.line("235 Authentication successful")
                else:
                    self.line("334 VXNlcm5hbWU6")
                    auth_step = 1
            elif verb in ("MAIL", "RCPT", "RSET", "NOOP"):
                self.line("250 OK")
            elif verb == "DATA":
                self.line("354 End with dot")
                chunks = []
                while True:
                    part = self.rfile.readline(8192)
                    if not part or part == b".\r\n":
                        break
                    chunks.append(part[1:] if part.startswith(b"..") else part)
                msg = email.message_from_bytes(b"".join(chunks), policy=email.policy.default)
                plain = msg.get_body(preferencelist=("plain",)).get_content()
                match = re.search(r"#token=([A-Za-z0-9_-]{43})", plain)
                with guard:
                    messages.append({"token": match.group(1) if match else None,
                                     "subject": str(msg["Subject"]), "recipient": str(msg["To"]),
                                     "text": plain, "html": msg.get_body(preferencelist=("html",)).get_content()})
                self.line("250 Accepted for local test capture")
            elif verb == "QUIT":
                self.line("221 Bye")
                return
            else:
                self.line("502 Not supported")


class Capture(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        if not hmac.compare_digest(self.headers.get("X-Test-Key", ""), secret):
            self.send_error(403)
            return
        with guard:
            value = {"count": len(messages), "latest": messages[-1] if messages else None}
        payload = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


if __name__ == "__main__":
    smtp = Server(("127.0.0.1", 12525), SMTP)
    threading.Thread(target=smtp.serve_forever, daemon=True).start()
    http.server.ThreadingHTTPServer(("127.0.0.1", 12526), Capture).serve_forever()
