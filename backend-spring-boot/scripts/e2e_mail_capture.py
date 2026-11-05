"""Host-side retrieval for the isolated E2E mail capture container."""
import json
import subprocess


CAPTURE_CONTAINER = "ethioloadai-e2e-mail-capture"
_IDENTITY_FORMAT = (
    '{{.Name}}|{{.State.Running}}|'
    '{{index .Config.Labels "com.docker.compose.service"}}'
)
class CaptureRetrievalError(RuntimeError):
    """A sanitized capture retrieval failure."""


class DockerCaptureClient:
    def __init__(self, run=subprocess.run):
        self._run = run

    def _docker(self, arguments):
        try:
            result = self._run(
                ["docker", *arguments],
                capture_output=True,
                text=True,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            raise CaptureRetrievalError(
                "Could not access the expected E2E mail capture container"
            ) from error
        if result.returncode != 0:
            raise CaptureRetrievalError(
                "Could not access the expected E2E mail capture container"
            )
        return result.stdout.strip()

    def _require_container(self):
        identity = self._docker([
            "inspect",
            f"--format={_IDENTITY_FORMAT}",
            CAPTURE_CONTAINER,
        ])
        expected = f"/{CAPTURE_CONTAINER}|true|mail-capture"
        if identity != expected:
            raise CaptureRetrievalError(
                "The E2E mail capture container identity is not the expected one"
            )

    def _request(self, path):
        self._require_container()
        return self._docker([
            "exec",
            CAPTURE_CONTAINER,
            "python",
            "-B",
            "/app/capture-invitation-smtp-client.py",
            path,
        ])

    def cursor(self):
        try:
            result = json.loads(self._request("/cursor"))
            cursor = result.get("cursor")
            if type(cursor) is not int or cursor < 0:
                raise ValueError
            return cursor
        except (json.JSONDecodeError, ValueError, AttributeError) as error:
            raise CaptureRetrievalError(
                "The E2E mail capture cursor response was invalid"
            ) from error

    def message(self, after, recipient, expected_cursor=None):
        if type(after) is not int or after < 0 or not recipient:
            raise CaptureRetrievalError("The requested capture cursor was invalid")
        if expected_cursor is not None and (
            type(expected_cursor) is not int or expected_cursor <= after
        ):
            raise CaptureRetrievalError("The requested capture cursor was invalid")

        try:
            result = json.loads(self._request(f"/?after={after}"))
        except json.JSONDecodeError as error:
            raise CaptureRetrievalError(
                "The E2E mail capture message response was invalid"
            ) from error

        if not isinstance(result, dict):
            raise CaptureRetrievalError(
                "The E2E mail capture message response was invalid"
            )
        messages = result.get("messages")
        if (
            not isinstance(messages, list)
            or type(result.get("count")) is not int
            or result["count"] != len(messages)
        ):
            raise CaptureRetrievalError(
                "The E2E mail capture message response was invalid"
            )
        matches = [
            message for message in messages
            if isinstance(message, dict)
            and message.get("envelope_recipients") == [recipient]
            and type(message.get("cursor")) is int
            and message["cursor"] > after
            and (
                expected_cursor is None
                or message["cursor"] == expected_cursor
            )
        ]
        if len(matches) != 1:
            raise CaptureRetrievalError(
                "Expected exactly one matching new invitation message"
            )
        return matches[0]


_default_client = DockerCaptureClient()


def capture_cursor():
    return _default_client.cursor()


def captured_message(after, recipient, expected_cursor=None):
    return _default_client.message(after, recipient, expected_cursor)
