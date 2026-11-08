#!/usr/bin/env python3
"""Verify that the packaged app and offline engine start without a dev setup."""
from pathlib import Path
import platform, shutil, subprocess

root = Path(__file__).resolve().parents[1]
release = root / "src-tauri/target/release"
system = platform.system()
if system == "Darwin":
    package = release / "bundle/macos/LocalDictation.app"
    app = package / "Contents/MacOS/handy"
    helper = package / "Contents/Resources/resources/bin/local-llama"
    prefix = []
elif system == "Windows":
    package = root / ".build/installed-smoke"
    installer = next((release / "bundle/nsis").glob("*.exe"))
    subprocess.run([str(installer), "/S", f"/D={package}"], check=True, timeout=90)
    app = package / "handy.exe"
    helper = package / "resources/bin/local-llama.exe"
    prefix = []
else:
    package = root / ".build/installed-smoke"
    package.mkdir(parents=True, exist_ok=True)
    installer = next((release / "bundle/deb").glob("*.deb"))
    subprocess.run(["dpkg-deb", "-x", str(installer), str(package)], check=True)
    app = package / "usr/bin/handy"
    helper = next(package.glob("usr/lib/**/resources/bin/local-llama"))
    prefix = ["xvfb-run", "-a"] if shutil.which("xvfb-run") else []

for command in [prefix + [str(app), "--list-devices"], [str(helper), "--version"]]:
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    print(result.stdout[-2000:])
    if result.returncode:
        raise RuntimeError(f"Packaged executable failed ({result.returncode}): {result.stderr[-3000:]}")
print("Packaged application and local cleanup engine start successfully.")
