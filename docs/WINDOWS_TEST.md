# Test the Windows alpha

Use the Windows 10 desktop for the first real-device check. No app account, API key, Python, Rust, or build tools are required.

1. Download the x64 setup `.exe` from [this project's release](https://github.com/samdlinn/local-dictation/releases/tag/v0.1.0-alpha.1). Only use a package actually listed there; the build page is not a download guarantee.
2. Run the installer and open **LocalDictation**. This alpha has no paid publisher certificate, so Windows may show an unknown-publisher warning. The installer may also install Microsoft's WebView2 runtime if it is missing.
3. In Windows **Settings → Privacy → Microphone**, allow microphone access for desktop apps. Choose the intended microphone in Local Dictation.
4. Complete setup and download a small English speech model to start. Leave optional cleanup off for the first test.
5. Open Notepad. Use the dictation shortcut displayed in Local Dictation's settings, say “This is a test of local dictation,” then stop recording. Confirm the words appear in Notepad.
6. Try the same shortcut with the cursor in ChatGPT's message box. Check the text before sending it. Also try a second recording and changing focus between apps.
7. Under **Local cleanup**, download the optional 1.83 GB cleanup model and enable it. Dictate “Um, please send the report tomorrow at nine.” Check punctuation, the time, and whether cleanup preserves the meaning.
8. Disconnect the network and repeat a short dictation. The downloaded models should still work. Reconnect when finished.

If something fails, report the app version, Windows version (`winver`), RAM, CPU model, selected speech model, the step that failed, and any visible error. A screenshot of the error is useful; avoid including private dictated text. Settings, app data, and downloaded models are local to that machine.

Automated checks cover installer creation and native engine startup on GitHub's Windows runner. They do not establish Windows 10 microphone, shortcut, or paste behavior; the steps above do that.
