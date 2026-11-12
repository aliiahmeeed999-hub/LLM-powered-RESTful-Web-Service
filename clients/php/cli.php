<?php
declare(strict_types=1);

// Command-line variant:  php cli.php "Where is the train station?" [--latin] [--from=en]
require __DIR__ . '/DarijaTranslatorClient.php';
$config = require __DIR__ . '/config.php';

$args = array_slice($argv, 1);
$script = in_array('--latin', $args, true) ? 'latin' : 'arabic';
$from = null;
$words = [];
foreach ($args as $arg) {
    if (str_starts_with($arg, '--from=')) {
        $from = substr($arg, 7);
    } elseif ($arg !== '--latin') {
        $words[] = $arg;
    }
}
$text = $words ? implode(' ', $words) : trim((string) stream_get_contents(STDIN));

if ($text === '') {
    fwrite(STDERR, "Usage: php cli.php \"text to translate\" [--latin] [--from=en]\n");
    exit(2);
}

try {
    $client = new DarijaTranslatorClient($config['api_url'], $config['username'], $config['password']);
    $result = $client->translate($text, $from, $script);
    echo $result['translation'], PHP_EOL;
} catch (DarijaTranslatorException $e) {
    fwrite(STDERR, 'Error: ' . $e->getMessage() . PHP_EOL);
    exit(1);
}
