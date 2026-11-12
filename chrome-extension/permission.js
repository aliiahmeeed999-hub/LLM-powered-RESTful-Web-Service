// Requests microphone access for the extension's origin. Once granted here, the
// permission also applies to the side panel (same chrome-extension:// origin).
document.getElementById("grant").addEventListener("click", async () => {
  const result = document.getElementById("result");
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => track.stop());
    result.textContent = "✅ Microphone allowed. You can close this tab and use 🎤 in the side panel.";
    setTimeout(() => window.close(), 2500);
  } catch (err) {
    result.textContent = `❌ ${err.name}: access was denied. Click the camera/microphone icon in the address bar to change it.`;
  }
});
