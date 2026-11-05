"""Authenticated loopback API client used only through docker exec."""
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path


SECRET_PATH = "/run/secrets/test-mail-capture-key"
API_BASE = "http://127.0.0.1:12526"


def main(arguments=None):
    arguments = sys.argv[1:] if arguments is None else arguments
    if len(arguments) != 1:
        return 2
    path = arguments[0]
    if path != "/cursor" and not re.fullmatch(r"/\?after=[0-9]+", path):
        return 2

    try:
        key = Path(SECRET_PATH).read_text(encoding="utf-8").rstrip("\r\n")
        if len(key) < 32:
            return 2
        request = urllib.request.Request(
            API_BASE + path,
            headers={"X-Test-Key": key},
        )
        with urllib.request.urlopen(request, timeout=5) as response:
            if response.status != 200:
                return 1
            sys.stdout.write(response.read().decode("utf-8"))
    except (OSError, UnicodeError, urllib.error.URLError):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
