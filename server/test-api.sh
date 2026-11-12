#!/usr/bin/env bash
# Quick cURL tests of the TranslatorResource endpoint.
# Usage: ./test-api.sh [base-url] [user:password]
BASE="${1:-http://localhost:9080/translator/api}"
CRED="${2:-ali:darija2026}"

echo "== 1. Public health check"
curl -s "$BASE/health"; echo; echo

echo "== 2. No credentials -> expect 401"
curl -s -i -X POST "$BASE/translate" -H "Content-Type: application/json" -d '{"text":"Hello"}' | head -1; echo

echo "== 3. Wrong password -> expect 401"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -u "ali:wrong" "$BASE/translate?text=Hello"; echo

echo "== 4. POST JSON (Arabic script)"
curl -s -u "$CRED" -X POST "$BASE/translate" \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello my friend, how are you today?","sourceLang":"en"}'; echo; echo

echo "== 5. POST JSON (Latin script / Arabizi)"
curl -s -u "$CRED" -X POST "$BASE/translate" \
  -H "Content-Type: application/json" \
  -d '{"text":"Je voudrais un thé à la menthe, s il vous plaît","script":"latin"}'; echo; echo

echo "== 6. POST text/plain -> plain text answer"
curl -s -u "$CRED" -X POST "$BASE/translate?script=arabic" -H "Content-Type: text/plain" \
  --data "Where is the train station?"; echo; echo

echo "== 7. GET with query parameter"
curl -s -u "$CRED" -G "$BASE/translate" --data-urlencode "text=See you tomorrow"; echo; echo

echo "== 8. Empty text -> expect 400"
curl -s -u "$CRED" -X POST "$BASE/translate" -H "Content-Type: application/json" -d '{"text":""}'; echo
