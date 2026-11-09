"""Sign a CI APK with the persistent alpha key; never retain the private key."""

import base64
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request

APKSIG_URL = "https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.9.1/apksig-8.9.1.jar"
APKSIG_SHA256 = "c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89"


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: sign-android.py path/to/app.apk")
    apk = Path(sys.argv[1]).resolve(strict=True)
    encoded = os.environ.get("ANDROID_KEYSTORE_BASE64")
    if not encoded:
        raise SystemExit("The persistent Android signing key is required for publication.")
    with tempfile.TemporaryDirectory(prefix="local-dictation-sign-") as directory:
        temporary = Path(directory)
        key = temporary / "alpha.keystore"
        key.write_bytes(base64.b64decode(encoded, validate=True))
        key.chmod(0o600)
        library = temporary / "apksig.jar"
        with urllib.request.urlopen(APKSIG_URL, timeout=60) as response:
            library.write_bytes(response.read())
        if hashlib.sha256(library.read_bytes()).hexdigest() != APKSIG_SHA256:
            raise SystemExit("Android signing library checksum mismatch.")
        signed = temporary / "signed.apk"
        subprocess.run([
            "java", "-cp", str(library),
            str(Path(__file__).with_name("SignAndroid.java")),
            str(apk), str(signed), str(key),
        ], check=True)
        # Native libraries must remain directly mappable on 16 KB-page devices.
        import struct
        import zipfile
        with zipfile.ZipFile(signed) as archive, signed.open("rb") as stream:
            for entry in archive.infolist():
                if entry.filename.startswith("lib/") and entry.filename.endswith(".so"):
                    if entry.filename.endswith("/libai-chat.so"):
                        binary = archive.read(entry)
                        for forbidden in [b"Formatted and added %s message", b"User prompt received:", b"System prompt received:", b"cached: `%s`"]:
                            if forbidden in binary:
                                raise SystemExit("Sensitive native logging literal detected in APK.")
                    if entry.compress_type != zipfile.ZIP_STORED:
                        raise SystemExit("Native library was unexpectedly compressed.")
                    stream.seek(entry.header_offset + 26)
                    name_length, extra_length = struct.unpack("<HH", stream.read(4))
                    offset = entry.header_offset + 30 + name_length + extra_length
                    if offset % 16384:
                        raise SystemExit(f"Native library is not 16 KB aligned: {entry.filename}")
        shutil.copyfile(signed, apk)
    print(f"Signed and verified {apk.name}; native-library ZIP alignment checked.")


if __name__ == "__main__":
    main()
