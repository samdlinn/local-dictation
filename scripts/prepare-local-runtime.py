#!/usr/bin/env python3
"""Build the pinned, offline-only cleanup executable into the app resources."""
from pathlib import Path
import os, platform, shutil, subprocess

ROOT = Path(__file__).resolve().parents[1]
REVISION = "7d7f0287530bc8981690f361d8c1627dbae1c99a"
SOURCE = ROOT / ".build/llama.cpp"
BUILD = ROOT / ".build/llama-build"

def run(*args):
    subprocess.run(args, check=True)

if not SOURCE.exists():
    SOURCE.parent.mkdir(parents=True, exist_ok=True)
    run("git", "init", str(SOURCE))
    run("git", "-C", str(SOURCE), "remote", "add", "origin", "https://github.com/ggml-org/llama.cpp.git")
run("git", "-C", str(SOURCE), "fetch", "--depth", "1", "origin", REVISION)
run("git", "-C", str(SOURCE), "checkout", "--detach", "FETCH_HEAD")
run("cmake", "-S", str(SOURCE), "-B", str(BUILD), "-DCMAKE_BUILD_TYPE=Release",
    "-DBUILD_SHARED_LIBS=OFF", "-DLLAMA_OPENSSL=OFF", "-DLLAMA_BUILD_TESTS=OFF",
    "-DLLAMA_BUILD_SERVER=OFF", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF")
run("cmake", "--build", str(BUILD), "--config", "Release", "--target", "llama-completion", "--parallel", os.environ.get("BUILD_JOBS", "3"))
extension = ".exe" if platform.system() == "Windows" else ""
binary = BUILD / "bin" / ("Release" if extension else "") / ("llama-completion" + extension)
dest = ROOT / "src-tauri/resources/bin" / ("local-llama" + extension)
dest.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(binary, dest)
(ROOT / "src-tauri/transcribe-libs").mkdir(exist_ok=True)
print("Prepared", dest)
