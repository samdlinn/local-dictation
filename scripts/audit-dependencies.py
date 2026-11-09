#!/usr/bin/env python3
"""Fail on new Rust advisory matches; allow only reviewed maintenance-only notices.

OSV receives public package names and versions, never user data or credentials.
Network failures fail the check rather than claiming an audit succeeded.
"""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sys
import tomllib
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def request(url, body=None):
    payload = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def batch(queries):
    results = request("https://api.osv.dev/v1/querybatch", {"queries": queries})["results"]
    if len(results) != len(queries):
        raise RuntimeError("Incomplete advisory response")
    paired = list(zip(queries, results))
    complete = []
    for query, result in paired:
        vulns = result.get("vulns", [])
        while result.get("next_page_token"):
            result = request("https://api.osv.dev/v1/query", {**query, "page_token": result["next_page_token"]})
            vulns += result.get("vulns", [])
        complete.append((query, vulns))
    return complete


def main():
    lock = tomllib.loads((ROOT / "src-tauri/Cargo.lock").read_text(encoding="utf-8"))
    queries = [{"package": {"name": p["name"], "ecosystem": "crates.io"}, "version": p["version"]}
               for p in lock["package"] if p.get("source", "").startswith("registry+")]
    backports = json.loads((ROOT / "scripts/security-backports.json").read_text(encoding="utf-8"))
    for name, patch in backports.items():
        if hashlib.sha256((ROOT / patch["file"]).read_bytes()).hexdigest() != patch["sha256"]:
            raise RuntimeError("Reviewed backport source has changed: " + name)
        queries.append({"package": {"name": name, "ecosystem": "crates.io"}, "version": patch["version"]})
    exceptions = json.loads((ROOT / "scripts/security-maintenance-exceptions.json").read_text(encoding="utf-8"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = [item for group in pool.map(batch, [queries[i:i + 100] for i in range(0, len(queries), 100)]) for item in group]
    failures = []
    for query, vulnerabilities in results:
        for vulnerability in vulnerabilities:
            identifier = vulnerability["id"]
            exception = exceptions.get(identifier)
            name, version = query["package"]["name"], query["version"]
            patch = backports.get(name)
            if patch and version == patch["version"] and identifier in patch["advisories"]:
                print(f"Verified source backport: {name} {version}, {identifier}")
                continue
            if exception and (name, version) == (exception["name"], exception["version"]):
                details = request("https://api.osv.dev/v1/vulns/" + identifier)
                affected = [a for a in details.get("affected", []) if a["package"]["name"] == name]
                if affected and all(a.get("database_specific", {}).get("informational") == "unmaintained" for a in affected):
                    print(f"Maintenance notice: {name} {version}, {identifier}: {exception['reason']}")
                    continue
            failures.append(f"{name} {version}: {identifier}")
    print(f"Queried {len(queries)} registry/vendored package versions; {len(failures)} unreviewed/security advisory matches.")
    if failures:
        raise SystemExit("\n".join(failures))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Advisory check could not complete: {type(error).__name__}", file=sys.stderr)
        raise SystemExit(1)
