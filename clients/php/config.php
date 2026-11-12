<?php
declare(strict_types=1);

// Connection settings. Environment variables win so nothing secret has to be committed:
//   DARIJA_API_URL=http://localhost:9080/translator/api DARIJA_USER=ali DARIJA_PASSWORD=darija2026 php -S localhost:8000
return [
    'api_url'  => getenv('DARIJA_API_URL') ?: 'http://localhost:9080/translator/api',
    'username' => getenv('DARIJA_USER') ?: 'ali',
    'password' => getenv('DARIJA_PASSWORD') ?: 'darija2026',
];
