import email.message
import importlib.util
import json
import smtplib
import socket
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("capture-invitation-smtp.py")
SPEC = importlib.util.spec_from_file_location("capture_invitation_smtp", MODULE_PATH)
capture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(capture)


class CaptureServerTest(unittest.TestCase):
    def setUp(self):
        self.store = capture.MessageStore()
        self.smtp = capture.CaptureSMTPServer(("127.0.0.1", 0), self.store)
        self.api = capture.CaptureAPIServer(
            ("127.0.0.1", 0), self.store, "local-test-key-" + "x" * 32)
        self.smtp_thread = threading.Thread(target=self.smtp.serve_forever, daemon=True)
        self.api_thread = threading.Thread(target=self.api.serve_forever, daemon=True)
        self.smtp_thread.start()
        self.api_thread.start()
        self.smtp_address = ("127.0.0.1", self.smtp.server_address[1])
        self.api_url = f"http://127.0.0.1:{self.api.server_address[1]}/"
        self.api_key = self.api.secret

    def tearDown(self):
        self.smtp.shutdown()
        self.api.shutdown()
        self.smtp.server_close()
        self.api.server_close()

    def send_message(self, recipient="employee@example.test"):
        message = email.message.EmailMessage()
        message["From"] = "ethioloadai-test@example.test"
        message["To"] = recipient
        message["Subject"] = "Demo Fleet Logistics invitation"
        message.set_content(
            "Demo Fleet Logistics invited you as FLEET_MANAGER.\n"
            "Accept invitation: http://localhost:5173/invitations/accept#token="
            + "A" * 43
            + "\nExpires: 2026-11-04T13:00:00 UTC"
        )
        with smtplib.SMTP(*self.smtp_address, timeout=5) as client:
            return client.send_message(message)

    def retrieve(self, cursor=0):
        request = urllib.request.Request(
            f"{self.api_url}?after={cursor}",
            headers={"X-Test-Key": self.api_key},
        )
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.load(response)

    def test_capture_records_envelope_and_invitation_content_without_relay(self):
        original_connect = socket.create_connection
        destinations = []

        def local_only_connect(address, *args, **kwargs):
            destinations.append(address[0])
            if address[0] not in {"127.0.0.1", "::1", "localhost"}:
                raise AssertionError("SMTP test attempted a non-loopback connection")
            return original_connect(address, *args, **kwargs)

        with patch("socket.create_connection", side_effect=local_only_connect):
            refused = self.send_message()
        self.assertEqual(refused, {})

        response = self.retrieve()
        self.assertEqual(response["count"], 1)
        message = response["messages"][0]
        self.assertEqual(message["envelope_recipients"], ["employee@example.test"])
        self.assertEqual(message["recipient"], "employee@example.test")
        self.assertEqual(message["subject"], "Demo Fleet Logistics invitation")
        self.assertIn("FLEET_MANAGER", message["text"])
        self.assertIn("2026-11-04T13:00:00 UTC", message["text"])
        self.assertIn("http://localhost:5173/invitations/accept#token=" + "A" * 43,
                      message["text"])
        self.assertEqual(message["token"], "A" * 43)
        self.assertTrue(message["id"])
        self.assertEqual(message["cursor"], response["nextCursor"])
        self.assertTrue(destinations)
        self.assertEqual(set(destinations), {"127.0.0.1"})

    def test_rejects_non_test_recipient_without_capturing(self):
        with self.assertRaises(smtplib.SMTPRecipientsRefused) as context:
            self.send_message("person@example.com")
        self.assertIn("person@example.com", context.exception.recipients)
        self.assertEqual(self.retrieve()["count"], 0)

    def test_rejects_smtp_auth(self):
        with smtplib.SMTP(*self.smtp_address, timeout=5) as client:
            code, response = client.docmd("AUTH", "PLAIN dGVzdA==")
        self.assertEqual(code, 502)
        self.assertIn(b"not supported", response)

    def test_cursor_excludes_older_captured_messages(self):
        self.send_message("first@example.test")
        cursor = self.retrieve()["nextCursor"]
        self.send_message("second@example.test")

        response = self.retrieve(cursor)
        self.assertEqual(response["count"], 1)
        self.assertEqual(response["messages"][0]["envelope_recipients"],
                         ["second@example.test"])
        self.assertGreater(response["messages"][0]["cursor"], cursor)

    def test_capture_api_requires_test_key(self):
        request = urllib.request.Request(self.api_url, headers={"X-Test-Key": "wrong"})
        try:
            urllib.request.urlopen(request, timeout=5)
        except urllib.error.HTTPError as error:
            self.assertEqual(error.code, 403)
            error.close()
        else:
            self.fail("Capture API accepted a request without the test key")

    def test_api_server_can_bind_only_to_container_loopback(self):
        with patch.dict("os.environ", {"CAPTURE_SMTP_BIND": "0.0.0.0"}):
            smtp, api = capture.create_servers(self.store, self.api_key, 0, 0)
        try:
            self.assertEqual(smtp.server_address[0], "0.0.0.0")
            self.assertEqual(api.server_address[0], "127.0.0.1")
        finally:
            smtp.server_close()
            api.server_close()


if __name__ == "__main__":
    unittest.main()
