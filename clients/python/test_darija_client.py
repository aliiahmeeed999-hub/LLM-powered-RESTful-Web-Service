"""Unit tests for darija_client (no server needed: urlopen is mocked).

    python -m unittest -v test_darija_client.py
"""
import io
import json
import unittest
import urllib.error
from unittest import mock

from darija_client import DarijaTranslatorClient, DarijaTranslatorError, main


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class ClientTest(unittest.TestCase):
    def setUp(self):
        self.client = DarijaTranslatorClient("http://server/api/", "ali", "darija2026")

    @mock.patch("urllib.request.urlopen")
    def test_translate_sends_json_and_basic_auth(self, urlopen):
        urlopen.return_value = FakeResponse(json.dumps({"translation": "سلام"}).encode())
        result = self.client.translate("Hello", "en", "arabic")

        self.assertEqual(result["translation"], "سلام")
        request = urlopen.call_args[0][0]
        self.assertEqual(request.full_url, "http://server/api/translate")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Authorization"), "Basic YWxpOmRhcmlqYTIwMjY=")
        self.assertEqual(json.loads(request.data), {"text": "Hello", "script": "arabic", "sourceLang": "en"})

    @mock.patch("urllib.request.urlopen")
    def test_auto_language_is_not_sent(self, urlopen):
        urlopen.return_value = FakeResponse(b'{"translation": "x"}')
        self.client.translate("Hi", "auto", "latin")
        self.assertEqual(json.loads(urlopen.call_args[0][0].data), {"text": "Hi", "script": "latin"})

    @mock.patch("urllib.request.urlopen")
    def test_401_becomes_friendly_error(self, urlopen):
        urlopen.side_effect = urllib.error.HTTPError("u", 401, "Unauthorized", {}, io.BytesIO(b"{}"))
        with self.assertRaises(DarijaTranslatorError) as ctx:
            self.client.translate("Hi")
        self.assertEqual(ctx.exception.status, 401)
        self.assertIn("password", str(ctx.exception))

    @mock.patch("urllib.request.urlopen")
    def test_server_message_is_propagated(self, urlopen):
        body = io.BytesIO(json.dumps({"message": "Text too long"}).encode())
        urlopen.side_effect = urllib.error.HTTPError("u", 400, "Bad Request", {}, body)
        with self.assertRaises(DarijaTranslatorError) as ctx:
            self.client.translate("Hi")
        self.assertEqual(str(ctx.exception), "Text too long")

    @mock.patch("urllib.request.urlopen")
    def test_unreachable_server(self, urlopen):
        urlopen.side_effect = urllib.error.URLError("Connection refused")
        with self.assertRaises(DarijaTranslatorError) as ctx:
            self.client.health()
        self.assertIn("Cannot reach", str(ctx.exception))

    @mock.patch("urllib.request.urlopen")
    def test_cli_prints_translation(self, urlopen):
        urlopen.return_value = FakeResponse(b'{"translation": "bslama"}')
        with mock.patch("sys.stdout", new_callable=io.StringIO) as out:
            code = main(["--latin", "Goodbye"])
        self.assertEqual(code, 0)
        self.assertEqual(out.getvalue().strip(), "bslama")


if __name__ == "__main__":
    unittest.main()
