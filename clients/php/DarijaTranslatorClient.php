<?php
declare(strict_types=1);

/**
 * PHP client for the Darija Translator RESTful web service.
 * Uses the cURL extension and HTTP Basic authentication.
 *
 *   $client = new DarijaTranslatorClient('http://localhost:9080/translator/api', 'ali', 'darija2026');
 *   $result = $client->translate('Where is the train station?');
 *   echo $result['translation'];
 */
final class DarijaTranslatorClient
{
    public function __construct(
        private string $baseUrl,
        private string $username,
        private string $password,
        private int $timeoutSeconds = 90,
    ) {
        $this->baseUrl = rtrim($baseUrl, '/');
    }

    /** GET /health (public). */
    public function health(): array
    {
        return $this->request('GET', '/health', null, false);
    }

    /**
     * POST /translate.
     *
     * @param string      $text       text to translate
     * @param string|null $sourceLang ISO code ("en", "fr"...) or null for auto-detection
     * @param string      $script     "arabic" or "latin"
     * @return array{translation:string, original:string, sourceLang:string, model:string, cached:bool, elapsedMs:int}
     */
    public function translate(string $text, ?string $sourceLang = null, string $script = 'arabic'): array
    {
        $payload = ['text' => $text, 'script' => $script];
        if ($sourceLang !== null && $sourceLang !== '' && $sourceLang !== 'auto') {
            $payload['sourceLang'] = $sourceLang;
        }
        return $this->request('POST', '/translate', $payload, true);
    }

    private function request(string $method, string $path, ?array $payload, bool $authenticated): array
    {
        if (!function_exists('curl_init')) {
            throw new DarijaTranslatorException('The PHP cURL extension is not enabled (php.ini: extension=curl).', 0);
        }

        $ch = curl_init($this->baseUrl . $path);
        $headers = ['Accept: application/json'];
        $options = [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CUSTOMREQUEST  => $method,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT        => $this->timeoutSeconds,
        ];
        if ($authenticated) {
            $options[CURLOPT_HTTPAUTH] = CURLAUTH_BASIC;
            $options[CURLOPT_USERPWD] = $this->username . ':' . $this->password;
        }
        if ($payload !== null) {
            $headers[] = 'Content-Type: application/json; charset=UTF-8';
            $options[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        }
        $options[CURLOPT_HTTPHEADER] = $headers;
        curl_setopt_array($ch, $options);

        $body = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $curlError = curl_error($ch);
        curl_close($ch);

        if ($body === false) {
            throw new DarijaTranslatorException("Cannot reach the server at {$this->baseUrl}: {$curlError}", 0);
        }

        $data = json_decode((string) $body, true);
        if ($status < 200 || $status >= 300) {
            $message = is_array($data) && isset($data['message']) ? $data['message'] : "HTTP $status";
            if ($status === 401) {
                $message = 'Wrong username or password.';
            }
            throw new DarijaTranslatorException($message, $status);
        }
        if (!is_array($data)) {
            throw new DarijaTranslatorException('The server returned an invalid JSON response.', $status);
        }
        return $data;
    }
}

final class DarijaTranslatorException extends RuntimeException
{
}
