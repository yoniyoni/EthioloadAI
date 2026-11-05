import json
import unittest
from unittest.mock import Mock

from e2e_mail_capture import (
    CAPTURE_CONTAINER,
    CaptureRetrievalError,
    DockerCaptureClient,
)


def completed(stdout, returncode=0):
    return Mock(stdout=stdout, returncode=returncode)


class DockerCaptureClientTest(unittest.TestCase):
    def client(self, message_response):
        runner = Mock(side_effect=[
            completed(f"/{CAPTURE_CONTAINER}|true|mail-capture\n"),
            completed(message_response),
        ])
        return DockerCaptureClient(runner), runner

    def test_cursor_uses_fixed_container_and_reads_key_inside_container(self):
        runner = Mock(side_effect=[
            completed(f"/{CAPTURE_CONTAINER}|true|mail-capture\n"),
            completed('{"cursor":7}'),
        ])
        self.assertEqual(DockerCaptureClient(runner).cursor(), 7)
        inspect_args = runner.call_args_list[0].args[0]
        exec_args = runner.call_args_list[1].args[0]
        self.assertEqual(inspect_args[-1], CAPTURE_CONTAINER)
        self.assertEqual(exec_args[:3], ["docker", "exec", CAPTURE_CONTAINER])
        self.assertEqual(exec_args[-1], "/cursor")
        self.assertIn("/app/capture-invitation-smtp-client.py", exec_args)
        self.assertNotIn("-c", exec_args)
        self.assertNotIn("test-mail-capture-key-value", exec_args)
        self.assertNotIn("TEST_MAIL_CAPTURE_SECRET", runner.call_args_list[1].kwargs)

    def test_message_filters_to_one_new_matching_recipient_and_cursor(self):
        payload = {
            "count": 3,
            "messages": [
                {"cursor": 2, "envelope_recipients": ["target@example.test"], "token": "stale"},
                {"cursor": 8, "envelope_recipients": ["other@example.test"], "token": "other"},
                {"cursor": 9, "envelope_recipients": ["target@example.test"], "token": "new"},
            ],
        }
        client, _ = self.client(json.dumps(payload))
        message = client.message(4, "target@example.test", expected_cursor=9)
        self.assertEqual(message["token"], "new")

    def test_ambiguous_matching_messages_fail_without_echoing_tokens(self):
        payload = {
            "count": 2,
            "messages": [
                {"cursor": 5, "envelope_recipients": ["target@example.test"], "token": "secret-token-one"},
                {"cursor": 6, "envelope_recipients": ["target@example.test"], "token": "secret-token-two"},
            ],
        }
        client, _ = self.client(json.dumps(payload))
        with self.assertRaises(CaptureRetrievalError) as error:
            client.message(4, "target@example.test")
        self.assertNotIn("secret-token", str(error.exception))

    def test_wrong_container_identity_is_rejected_before_exec(self):
        runner = Mock(return_value=completed("/wrong-container|true|mail-capture\n"))
        with self.assertRaises(CaptureRetrievalError):
            DockerCaptureClient(runner).cursor()
        runner.assert_called_once()

    def test_docker_failure_is_sanitized(self):
        runner = Mock(return_value=completed("secret-token", returncode=1))
        with self.assertRaises(CaptureRetrievalError) as error:
            DockerCaptureClient(runner).cursor()
        self.assertNotIn("secret-token", str(error.exception))


if __name__ == "__main__":
    unittest.main()
