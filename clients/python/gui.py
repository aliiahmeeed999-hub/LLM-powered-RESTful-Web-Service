#!/usr/bin/env python3
"""
Desktop GUI (Tkinter) for the Darija Translator web service.

    python gui.py

Uses darija_client.py; the HTTP call runs on a worker thread so the window never freezes.
"""
from __future__ import annotations

import threading
import tkinter as tk
from tkinter import messagebox, ttk

from darija_client import (DEFAULT_PASSWORD, DEFAULT_URL, DEFAULT_USER,
                           DarijaTranslatorClient, DarijaTranslatorError)

RED = "#c1272d"
GREEN = "#006233"
LANGUAGES = {"Detect language": None, "English": "en", "French": "fr", "Spanish": "es",
             "Arabic (MSA)": "ar", "German": "de", "Italian": "it"}


class TranslatorApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Darija Translator - Python client")
        self.geometry("640x560")
        self.minsize(480, 440)

        self.url = tk.StringVar(value=DEFAULT_URL)
        self.user = tk.StringVar(value=DEFAULT_USER)
        self.password = tk.StringVar(value=DEFAULT_PASSWORD)
        self.source = tk.StringVar(value="Detect language")
        self.script = tk.StringVar(value="arabic")
        self.status = tk.StringVar(value="Ready")

        self._build()
        self.after(200, self._check_health)

    # ------------------------------------------------------------- layout
    def _build(self) -> None:
        header = tk.Frame(self, bg=RED, height=6)
        header.pack(fill="x")

        settings = ttk.LabelFrame(self, text="Server")
        settings.pack(fill="x", padx=12, pady=(10, 6))
        ttk.Label(settings, text="URL").grid(row=0, column=0, sticky="w", padx=6, pady=4)
        ttk.Entry(settings, textvariable=self.url).grid(row=0, column=1, columnspan=3, sticky="ew", padx=6)
        ttk.Label(settings, text="User").grid(row=1, column=0, sticky="w", padx=6, pady=4)
        ttk.Entry(settings, textvariable=self.user, width=14).grid(row=1, column=1, sticky="w", padx=6)
        ttk.Label(settings, text="Password").grid(row=1, column=2, sticky="e", padx=6)
        ttk.Entry(settings, textvariable=self.password, show="•", width=14).grid(row=1, column=3, sticky="w", padx=6)
        settings.columnconfigure(1, weight=1)

        options = ttk.Frame(self)
        options.pack(fill="x", padx=12)
        ttk.Label(options, text="From").pack(side="left")
        ttk.Combobox(options, textvariable=self.source, values=list(LANGUAGES), state="readonly",
                     width=16).pack(side="left", padx=6)
        ttk.Radiobutton(options, text="Arabic script", value="arabic", variable=self.script).pack(side="left", padx=6)
        ttk.Radiobutton(options, text="Latin (Arabizi)", value="latin", variable=self.script).pack(side="left")

        self.input = tk.Text(self, height=7, wrap="word", font=("Segoe UI", 12), padx=8, pady=6)
        self.input.pack(fill="both", expand=True, padx=12, pady=8)
        self.input.bind("<Control-Return>", lambda _e: (self.translate(), "break")[1])

        buttons = ttk.Frame(self)
        buttons.pack(fill="x", padx=12)
        self.translate_btn = tk.Button(buttons, text="Translate to Darija  (Ctrl+Enter)", bg=RED, fg="white",
                                       activebackground="#9e1f24", relief="flat", padx=12, pady=6,
                                       command=self.translate)
        self.translate_btn.pack(side="left")
        ttk.Button(buttons, text="Copy result", command=self.copy).pack(side="left", padx=8)

        self.output = tk.Text(self, height=6, wrap="word", font=("Segoe UI", 16), padx=8, pady=6,
                              fg=GREEN, state="disabled")
        self.output.tag_configure("rtl", justify="right")
        self.output.pack(fill="both", expand=True, padx=12, pady=8)

        ttk.Label(self, textvariable=self.status, anchor="w").pack(fill="x", padx=12, pady=(0, 8))

    # ------------------------------------------------------------ actions
    def _client(self) -> DarijaTranslatorClient:
        return DarijaTranslatorClient(self.url.get(), self.user.get(), self.password.get())

    def _check_health(self) -> None:
        def work() -> None:
            try:
                h = self._client().health()
                msg = f"Server online · {h.get('provider')} / {h.get('model')}"
            except DarijaTranslatorError as err:
                msg = f"Server offline: {err}"
            self.after(0, self.status.set, msg)
        threading.Thread(target=work, daemon=True).start()

    def translate(self) -> None:
        text = self.input.get("1.0", "end").strip()
        if not text:
            messagebox.showinfo("Darija Translator", "Type something to translate first.")
            return
        self.translate_btn.config(state="disabled")
        self.status.set("Translating…")
        source, script = LANGUAGES[self.source.get()], self.script.get()

        def work() -> None:
            try:
                result = self._client().translate(text, source, script)
                self.after(0, self._show, result)
            except DarijaTranslatorError as err:
                self.after(0, self._fail, str(err))
        threading.Thread(target=work, daemon=True).start()

    def _show(self, result: dict) -> None:
        self.output.config(state="normal")
        self.output.delete("1.0", "end")
        tags = ("rtl",) if result.get("script") != "latin" else ()
        self.output.insert("1.0", result["translation"], tags)
        self.output.config(state="disabled")
        cached = " · cached" if result.get("cached") else ""
        self.status.set(f"{result.get('model')} · {result.get('elapsedMs')} ms{cached}")
        self.translate_btn.config(state="normal")

    def _fail(self, message: str) -> None:
        self.status.set(f"Error: {message}")
        self.translate_btn.config(state="normal")
        messagebox.showerror("Darija Translator", message)

    def copy(self) -> None:
        text = self.output.get("1.0", "end").strip()
        if text:
            self.clipboard_clear()
            self.clipboard_append(text)
            self.status.set("Copied to clipboard")


if __name__ == "__main__":
    TranslatorApp().mainloop()
