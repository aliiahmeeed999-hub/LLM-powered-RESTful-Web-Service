// Darija Translator - background service worker (Manifest V3)
//
// Responsibilities:
//  1. register the right-click (context menu) entries;
//  2. when the user picks "Translate to Darija", open the side panel and hand it the
//     selected text through chrome.storage.session (the panel pastes it automatically);
//  3. same thing for the keyboard shortcut (Ctrl+Shift+Y).

const MENU_TRANSLATE = "darija-translate-selection";
const MENU_OPEN = "darija-open-panel";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: MENU_TRANSLATE,
      title: "Translate \"%s\" to Darija",
      contexts: ["selection"],
    });
    chrome.contextMenus.create({
      id: MENU_OPEN,
      title: "Open Darija Translator",
      contexts: ["page", "frame", "editable", "image", "link"],
    });
  });

  // Clicking the toolbar icon also opens the panel.
  chrome.sidePanel
    .setPanelBehavior({ openPanelOnActionClick: true })
    .catch((err) => console.warn("setPanelBehavior failed", err));
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== MENU_TRANSLATE && info.menuItemId !== MENU_OPEN) return;

  // sidePanel.open() must be called synchronously inside the user gesture,
  // i.e. before any await.
  openPanel(tab);

  if (info.menuItemId === MENU_TRANSLATE) {
    handOverSelection(tab, info.selectionText || "", "context-menu");
  }
});

chrome.commands.onCommand.addListener((command, tab) => {
  if (command !== "translate-selection") return;
  openPanel(tab);
  handOverSelection(tab, "", "shortcut");
});

function openPanel(tab) {
  const target = tab && tab.windowId !== undefined ? { windowId: tab.windowId } : null;
  if (!target) return;
  chrome.sidePanel.open(target).catch((err) => console.warn("sidePanel.open failed", err));
}

/**
 * Stores the selected text for the side panel. info.selectionText collapses line breaks,
 * so we try to read the exact selection from the page (activeTab + scripting) and fall
 * back to the context-menu value on pages where scripts cannot run (chrome://, Web Store...).
 */
async function handOverSelection(tab, fallbackText, origin) {
  let text = fallbackText;
  if (tab && tab.id !== undefined) {
    try {
      const results = await chrome.scripting.executeScript({
        target: { tabId: tab.id, allFrames: true },
        func: () => {
          const active = document.activeElement;
          // selections inside <textarea>/<input> are not part of window.getSelection()
          if (active && (active.tagName === "TEXTAREA" || active.tagName === "INPUT")
              && typeof active.selectionStart === "number") {
            return active.value.substring(active.selectionStart, active.selectionEnd);
          }
          return String(window.getSelection() || "");
        },
      });
      const fromPage = results
        .map((r) => (r && typeof r.result === "string" ? r.result : ""))
        .sort((a, b) => b.length - a.length)[0];
      if (fromPage && fromPage.trim().length >= text.trim().length) text = fromPage;
    } catch (err) {
      // restricted page: keep the context-menu text
    }
  }

  text = text.trim();
  if (!text) return;
  await chrome.storage.session.set({
    pendingSelection: { text, origin, ts: Date.now() },
  });
}
