#!/usr/bin/env python3
"""
Python client for the Darija Translator RESTful web service.

Standard library only (urllib), so it runs anywhere Python 3.8+ is installed.

Library use:
    from darija_client import DarijaTranslatorClient
    client = DarijaTranslatorClient("http://localhost:9080/translator/api", "ali", "darija2026")
    print(client.translate("Where is the train station?")["translation"])

Command line:
    python darija_client.py "Where is the train station?"
    python darija_client.py --latin --from en "See you tomorrow"
    echo "Bonjour tout le monde" | python darija_client.py
    python darija_client.py -i            # interactive mode
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any, Dict, Optional

DEFAULT_URL = os.environ.get("DARIJA_API_URL", "http://localhost:9080/translator/api")
DEFAULT_USER = os.environ.get("DARIJA_USER", "ali")
DEFAULT_PASSWORD = os.environ.get("DARIJA_PASSWORD", "darija2026")


class DarijaTranslatorError(Exception):
    """Raised when the service cannot translate (network, auth, LLM error...)."""

    def __init__(self, message: str, status: int = 0):
        super().__init__(message)
        self.status = status


class DarijaTranslatorClient:
    def __init__(self, base_url: str = DEFAULT_URL, username: str = DEFAULT_USER,
                 password: str = DEFAULT_PASSWORD, timeout: float = 90.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
        self._auth_header = f"Basic {token}"

    def health(self) -> Dict[str, Any]:
        """GET /health - public, no credentials needed."""
        return self._request("GET", "/health", authenticated=False)

    def translate(self, text: str, source_lang: Optional[str] = None,
                  script: str = "arabic") -> Dict[str, Any]:
        """POST /translate. Returns the JSON answer (``translation``, ``model``, ``elapsedMs``...)."""
        payload: Dict[str, Any] = {"text": text, "script": script}
        if source_lang and source_lang != "auto":
            payload["sourceLang"] = source_lang
        return self._request("POST", "/translate", payload)

    def _request(self, method: str, path: str, payload: Optional[dict] = None,
                 authenticated: bool = True) -> Dict[str, Any]:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json; charset=UTF-8")
        if authenticated:
            request.add_header("Authorization", self._auth_header)

        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as err:
            message = f"HTTP {err.code}"
            try:
                message = json.loads(err.read().decode("utf-8")).get("message", message)
            except (ValueError, AttributeError):
                pass
            if err.code == 401:
                message = "Wrong username or password."
            raise DarijaTranslatorError(message, err.code) from None
        except urllib.error.URLError as err:
            raise DarijaTranslatorError(f"Cannot reach {self.base_url}: {err.reason}") from None
        except TimeoutError:
            raise DarijaTranslatorError("The server took too long to answer.") from None


# ---------------------------------------------------------------------- CLI

def _configure_stdout() -> None:
    # Windows consoles default to a legacy code page that cannot print Arabic.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
        except (AttributeError, ValueError):
            pass


def _interactive(client: DarijaTranslatorClient, source_lang: Optional[str], script: str) -> None:
    print("Darija Translator - type text and press Enter.")
    print("Commands: :latin  :arabic  :from <code|auto>  :quit\n")
    while True:
        try:
            line = input(f"[{source_lang or 'auto'} -> darija/{script}] > ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not line:
            continue
        if line in (":q", ":quit", ":exit"):
            return
        if line in (":latin", ":arabic"):
            script = line[1:]
            continue
        if line.startswith(":from"):
            parts = line.split()
            source_lang = parts[1] if len(parts) > 1 and parts[1] != "auto" else None
            continue
        try:
            result = client.translate(line, source_lang, script)
            cached = " (cached)" if result.get("cached") else ""
            print(f"  {result['translation']}\n  - {result['model']}, {result['elapsedMs']} ms{cached}\n")
        except DarijaTranslatorError as err:
            print(f"  ! {err}\n")


def main(argv: Optional[list] = None) -> int:
    _configure_stdout()
    parser = argparse.ArgumentParser(description="Translate text into Moroccan Darija.")
    parser.add_argument("text", nargs="*", help="text to translate (read from stdin if omitted)")
    parser.add_argument("--from", dest="source_lang", default=None, help="source language, e.g. en, fr (default: auto)")
    parser.add_argument("--latin", action="store_true", help="write Darija in Latin letters (Arabizi)")
    parser.add_argument("-i", "--interactive", action="store_true", help="interactive mode")
    parser.add_argument("--json", action="store_true", help="print the full JSON response")
    parser.add_argument("--url", default=DEFAULT_URL, help=f"service base URL (default {DEFAULT_URL})")
    parser.add_argument("--user", default=DEFAULT_USER)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    args = parser.parse_args(argv)

    client = DarijaTranslatorClient(args.url, args.user, args.password)
    script = "latin" if args.latin else "arabic"

    if args.interactive:
        _interactive(client, args.source_lang, script)
        return 0

    text = " ".join(args.text) if args.text else ("" if sys.stdin.isatty() else sys.stdin.read())
    if not text.strip():
        parser.print_usage(sys.stderr)
        print("error: nothing to translate", file=sys.stderr)
        return 2

    try:
        result = client.translate(text.strip(), args.source_lang, script)
    except DarijaTranslatorError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2) if args.json else result["translation"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
