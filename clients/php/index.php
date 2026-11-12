<?php
declare(strict_types=1);

require __DIR__ . '/DarijaTranslatorClient.php';
$config = require __DIR__ . '/config.php';

session_start();
$_SESSION['history'] ??= [];

$client = new DarijaTranslatorClient($config['api_url'], $config['username'], $config['password']);

$text = '';
$sourceLang = 'auto';
$script = 'arabic';
$result = null;
$error = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['clear_history'])) {
        $_SESSION['history'] = [];
    } else {
        $text = trim((string) ($_POST['text'] ?? ''));
        $sourceLang = (string) ($_POST['sourceLang'] ?? 'auto');
        $script = ($_POST['script'] ?? 'arabic') === 'latin' ? 'latin' : 'arabic';

        if ($text === '') {
            $error = 'Please type something to translate.';
        } else {
            try {
                $result = $client->translate($text, $sourceLang, $script);
                array_unshift($_SESSION['history'], [
                    'src' => $result['original'],
                    'dst' => $result['translation'],
                    'script' => $script,
                ]);
                $_SESSION['history'] = array_slice($_SESSION['history'], 0, 10);
            } catch (DarijaTranslatorException $e) {
                $error = $e->getMessage() . ($e->getCode() ? " (HTTP {$e->getCode()})" : '');
            }
        }
    }
}

try {
    $health = $client->health();
    $online = true;
} catch (DarijaTranslatorException) {
    $health = null;
    $online = false;
}

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

$languages = ['auto' => 'Detect language', 'en' => 'English', 'fr' => 'French', 'es' => 'Spanish',
              'ar' => 'Arabic (MSA)', 'de' => 'German', 'it' => 'Italian'];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Darija Translator · PHP client</title>
    <style>
        :root { --red:#c1272d; --green:#006233; --bg:#f7f5f2; --card:#fff; --border:#e4e0da; --muted:#6b6b70; }
        * { box-sizing:border-box; }
        body { margin:0; font:15px/1.5 system-ui, sans-serif; background:var(--bg); color:#1d1d1f; }
        header { background:var(--card); border-bottom:3px solid var(--red); padding:16px 24px; display:flex; justify-content:space-between; align-items:center; }
        h1 { margin:0; font-size:20px; }
        .badge { font-size:13px; color:var(--muted); }
        .badge::before { content:"●"; margin-right:6px; color:<?= $online ? 'var(--green)' : 'var(--red)' ?>; }
        main { max-width:760px; margin:24px auto; padding:0 16px; display:grid; gap:16px; }
        .card { background:var(--card); border:1px solid var(--border); border-radius:12px; padding:18px; }
        textarea { width:100%; min-height:120px; font:inherit; padding:10px; border:1px solid var(--border); border-radius:8px; resize:vertical; }
        .row { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-top:12px; }
        select, button { font:inherit; padding:8px 12px; border-radius:8px; border:1px solid var(--border); background:#fff; }
        button.primary { background:var(--red); color:#fff; border-color:var(--red); font-weight:600; cursor:pointer; }
        .output { font-size:22px; line-height:1.7; white-space:pre-wrap; }
        .meta { color:var(--muted); font-size:12px; }
        .error { background:#fbe9ea; border:1px solid #f0b4b7; border-radius:12px; padding:12px 16px; }
        ul { list-style:none; padding:0; margin:0; display:grid; gap:8px; }
        li { border:1px solid var(--border); border-radius:8px; padding:8px 12px; }
        li small { color:var(--muted); display:block; }
        .link { background:none; border:none; color:var(--red); padding:0; cursor:pointer; }
    </style>
</head>
<body>
<header>
    <h1>🇲🇦 Darija Translator <small style="font-weight:400;color:var(--muted)">PHP client</small></h1>
    <span class="badge"><?= $online ? e("Server online · {$health['provider']} / {$health['model']}") : 'Server offline' ?></span>
</header>

<main>
    <form method="post" class="card">
        <label for="text"><strong>Text to translate</strong></label>
        <textarea id="text" name="text" maxlength="5000" placeholder="Type English, French, Spanish…" autofocus><?= e($text) ?></textarea>
        <div class="row">
            <label>From
                <select name="sourceLang">
                    <?php foreach ($languages as $code => $label): ?>
                        <option value="<?= e($code) ?>" <?= $code === $sourceLang ? 'selected' : '' ?>><?= e($label) ?></option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Script
                <select name="script">
                    <option value="arabic" <?= $script === 'arabic' ? 'selected' : '' ?>>Arabic letters (عربية)</option>
                    <option value="latin" <?= $script === 'latin' ? 'selected' : '' ?>>Latin letters (Arabizi)</option>
                </select>
            </label>
            <button class="primary" type="submit">Translate to Darija</button>
        </div>
    </form>

    <?php if ($error !== null): ?>
        <div class="error" role="alert"><?= e($error) ?></div>
    <?php endif; ?>

    <?php if ($result !== null): ?>
        <section class="card">
            <div class="output" id="output" dir="<?= $script === 'latin' ? 'ltr' : 'rtl' ?>"><?= e($result['translation']) ?></div>
            <p class="meta">
                <?= e(($result['sourceLang'] === 'auto' ? 'auto-detected' : $result['sourceLang'])
                    . " → Darija · {$result['model']} · {$result['elapsedMs']} ms"
                    . ($result['cached'] ? ' · cached' : '')) ?>
            </p>
            <div class="row">
                <button type="button" onclick="navigator.clipboard.writeText(document.getElementById('output').innerText)">📋 Copy</button>
                <button type="button" onclick="readAloud()">🔊 Read aloud</button>
            </div>
        </section>
    <?php endif; ?>

    <?php if ($_SESSION['history']): ?>
        <section class="card">
            <strong>Recent translations</strong>
            <ul style="margin-top:10px">
                <?php foreach ($_SESSION['history'] as $item): ?>
                    <li><small><?= e($item['src']) ?></small>
                        <span dir="<?= $item['script'] === 'latin' ? 'ltr' : 'rtl' ?>"><?= e($item['dst']) ?></span></li>
                <?php endforeach; ?>
            </ul>
            <form method="post"><button class="link" name="clear_history" value="1">Clear history</button></form>
        </section>
    <?php endif; ?>
</main>

<script>
    // Text-to-speech in the browser (Web Speech API); prefers a Moroccan/Arabic voice.
    function readAloud() {
        const text = document.getElementById('output').innerText;
        const latin = <?= json_encode($script === 'latin') ?>;
        const voices = speechSynthesis.getVoices();
        const wanted = latin ? ['fr'] : ['ar-MA', 'ar'];
        const voice = wanted.map(w => voices.find(v => v.lang.startsWith(w))).find(Boolean);
        const u = new SpeechSynthesisUtterance(text);
        if (voice) u.voice = voice;
        u.lang = voice ? voice.lang : (latin ? 'fr-FR' : 'ar-MA');
        speechSynthesis.cancel();
        speechSynthesis.speak(u);
    }
</script>
</body>
</html>
