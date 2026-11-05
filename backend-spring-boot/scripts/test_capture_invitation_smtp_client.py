import importlib.util
import io
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


MODULE_PATH = Path(__file__).with_name("capture-invitation-smtp-client.py")
SPEC = importlib.util.spec_from_file_location("capture_invitation_smtp_client", MODULE_PATH)
client = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(client)


class CaptureClientTest(unittest.TestCase):
    def test_reads_key_in_container_and_calls_only_loopback(self):
        response = MagicMock(status=200)
        response.read.return_value = b'{"cursor":4}'
        response.__enter__.return_value = response
        output = io.StringIO()
        with patch.object(client, "Path") as secret_path, \
             patch.object(client.urllib.request, "urlopen", return_value=response) as urlopen, \
             patch.object(client.sys, "stdout", output):
            secret_path.return_value.read_text.return_value = "k" * 40 + "\n"
            self.assertEqual(client.main(["/cursor"]), 0)

        secret_path.assert_called_once_with(client.SECRET_PATH)
        secret_path.return_value.read_text.assert_called_once_with(encoding="utf-8")
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "http://127.0.0.1:12526/cursor")
        self.assertEqual(request.get_header("X-test-key"), "k" * 40)
        self.assertEqual(output.getvalue(), '{"cursor":4}')

    def test_rejects_non_cursor_paths_before_reading_secret(self):
        with patch.object(client.Path, "read_text") as read_key:
            self.assertEqual(client.main(["http://example.test"]), 2)
        read_key.assert_not_called()

    def test_accepts_only_numeric_cursor_queries(self):
        response = MagicMock(status=200)
        response.read.return_value = b'{"count":0,"messages":[]}'
        response.__enter__.return_value = response
        with patch.object(client, "Path") as secret_path, \
             patch.object(client.urllib.request, "urlopen", return_value=response) as urlopen, \
             patch.object(client.sys, "stdout", io.StringIO()):
            secret_path.return_value.read_text.return_value = "k" * 40
            self.assertEqual(client.main(["/?after=17"]), 0)
        secret_path.assert_called_once_with(client.SECRET_PATH)
        self.assertEqual(
            urlopen.call_args.args[0].full_url,
            "http://127.0.0.1:12526/?after=17",
        )


if __name__ == "__main__":
    unittest.main()
