#!/usr/bin/env python3
"""Import APK SHA-256 IOCs from Zimperium's public mobile-malware research."""
import json
import re
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

OWNER, REPO, BRANCH = "Zimperium", "IOC", "master"
API = f"https://api.github.com/repos/{OWNER}/{REPO}/git/trees/{BRANCH}?recursive=1"
RAW = f"https://raw.githubusercontent.com/{OWNER}/{REPO}/{BRANCH}/"
FEED = Path("threat-feed.json")
HASH = re.compile(r"^[0-9a-fA-F]{64}$")

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "PhoneGuard-threat-feed/1.0", "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read()

def main():
    feed = json.loads(FEED.read_text(encoding="utf-8"))
    entries = feed.get("entries")
    if not isinstance(entries, list):
        raise SystemExit("Invalid feed: entries is not a list")
    existing = {e["sha256"].lower() for e in entries}
    tree = json.loads(get(API))
    if tree.get("truncated"):
        raise SystemExit("Zimperium repository tree was truncated; refusing partial import")
    candidates = [x["path"] for x in tree.get("tree", [])
                  if x.get("type") == "blob" and re.fullmatch(r"\d{4}(?:-\d{2})?-[^/]+/apks\.csv", x.get("path", ""))]
    added = []
    for path in candidates:
        raw = get(RAW + urllib.parse.quote(path, safe="/")).decode("utf-8", errors="replace")
        family = path.split("/", 1)[0]
        source_url = f"https://github.com/{OWNER}/{REPO}/blob/{BRANCH}/{path}"
        for line in raw.splitlines():
            value = line.strip().strip('"').strip("'")
            if not HASH.fullmatch(value):
                continue
            digest = value.lower()
            if digest in existing:
                continue
            existing.add(digest)
            added.append({
                "sha256": digest,
                "category": "trojan",
                "family": f"{family} Android malware",
                "source": "Zimperium zLabs public mobile malware IOC repository",
                "source_url": source_url,
                "confidence": "high",
                "sample_type": "APK",
                "notes": "SHA-256 listed in the campaign apks.csv; imported automatically. Source attribution retained."
            })
    if not added:
        print(f"No new APK hashes; feed remains version {feed['version']} with {len(entries)} entries.")
        return
    entries.extend(added)
    feed["version"] = int(feed["version"]) + 1
    feed["updated_at"] = int(datetime.now(timezone.utc).timestamp())
    feed["description"] = "Curated Android malware APK SHA-256 indicators with source provenance. Partial blocklist; absence of a match does not indicate an app is safe."
    FEED.write_text(json.dumps(feed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Added {len(added)} new verified-source APK hashes; version {feed['version']}; total {len(entries)}.")

if __name__ == "__main__":
    import urllib.parse
    main()
