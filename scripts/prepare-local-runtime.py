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
portable_flags = []
if platform.machine().lower() in ("x86_64", "amd64"):
    # Keep the first x64 build compatible with older Windows 10 / Intel Macs.
    # Model choice cannot fix a binary that requires an absent CPU instruction.
    portable_flags += ["-DGGML_AVX=OFF", "-DGGML_AVX2=OFF", "-DGGML_FMA=OFF", "-DGGML_F16C=OFF"]
    if platform.system() == "Darwin":
        portable_flags += ["-DGGML_METAL=OFF"]
run("cmake", "-S", str(SOURCE), "-B", str(BUILD), "-DCMAKE_BUILD_TYPE=Release",
    "-DBUILD_SHARED_LIBS=OFF", "-DLLAMA_OPENSSL=OFF", "-DLLAMA_BUILD_TESTS=OFF",
    "-DLLAMA_BUILD_SERVER=OFF", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF", *portable_flags)
run("cmake", "--build", str(BUILD), "--config", "Release", "--target", "llama-completion", "--parallel", os.environ.get("BUILD_JOBS", "3"))
extension = ".exe" if platform.system() == "Windows" else ""
binary = BUILD / "bin" / ("Release" if extension else "") / ("llama-completion" + extension)
dest = ROOT / "src-tauri/resources/bin" / ("local-llama" + extension)
dest.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(binary, dest)
(ROOT / "src-tauri/transcribe-libs").mkdir(exist_ok=True)
if platform.system() == "Windows":
    # A clean Windows 10 machine need not have the developer's VC++ runtime.
    # Stage the redistributable DLLs for both the app and its isolated child exe.
    redist = os.environ.get("VCToolsRedistDir")
    if not redist:
        raise RuntimeError("VCToolsRedistDir is missing. Run from a Visual Studio developer shell.")
    folders = [p for p in (Path(redist) / "x64").glob("Microsoft.VC*") if p.is_dir()]
    if not folders:
        raise RuntimeError("Could not locate the x64 Visual C++ redistributable runtime.")
    for folder in folders:
        for dll in folder.glob("*.dll"):
            if dll.name.lower().startswith(("msvcp140", "vcruntime140", "vcomp140")):
                shutil.copy2(dll, dest.parent / dll.name)
                shutil.copy2(dll, ROOT / "src-tauri/transcribe-libs" / dll.name)
    if os.environ.get("GITHUB_ENV"):
        with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env_file:
            env_file.write("HANDY_VC_REDIST_DIRS=" + os.pathsep.join(map(str, folders)) + "\n")
print("Prepared", dest)
